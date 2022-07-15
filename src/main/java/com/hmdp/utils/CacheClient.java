package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * @author PuQiong
 * @create 2022-07-15 15:02
 */
@Slf4j
@Component
public class CacheClient {

    private StringRedisTemplate stringRedisTemplate;

    @Resource(name = "redisLogicalExpirePool")
    private ThreadPoolTaskExecutor redisLogicalExpirePool;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit timeUnit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,timeUnit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit){
        // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusNanos(timeUnit.toNanos(time)));
        // 写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R,ID> R queryWithPassThrough(String keyPrefix,ID id, Class<R> clazz, Function<ID,R> dbFallback, Long time, TimeUnit timeUnit){
        String key = keyPrefix + id;
        // 从redis 查询商户缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 判断是否存在
        if (StrUtil.isNotBlank(json)) {
            // 存在，直接返回
            return JSONUtil.toBean(json, clazz);
        }
        if (json != null) {
            return null;
        }
        // 不存在 根据id查询数据库
        R r = dbFallback.apply(id);
        // 不存在 返回错误
        if (r == null) {
            // 将空值写入 Redis 防止缓存穿透
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);

            return null;
        }
        // 存在 写入redis
        this.set(key,r,time,timeUnit);
        // 返回
        return r;
    }


    public <R,ID> R queryWithLogicalExpired(String keyPrefix, ID id, Class<R> clazz,Function<ID,R> dbFallback, Long time, TimeUnit timeUnit){
        String key = keyPrefix + id;
        // 从redis 查询商户缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 判断是否存在
        if (StrUtil.isBlank(json)) {
            // 不存在，直接返回空
            return null;
        }
        // 命中 先反序列化对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), clazz);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 未过期直接返回对象
            return r;
        }
        // 已经过期 需要缓存重建
        // 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // 成功后开启独立线程 实现缓存重建
        if (isLock) {
            redisLogicalExpirePool.submit(()->{
                try {
                    // 重建缓存
                    R r1 = dbFallback.apply(id);
                    // 写入redis
                    this.setWithLogicalExpire(key, r1, time, timeUnit);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
        }
        // 返回过期的商户信息
        return r;
    }


    /**
     * @description: 尝试获取锁
     * @author: PQ
     * @date: 2022/7/14 17:41
     * @param: [key]
     * @return: boolean
     **/
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * @description: 释放锁
     * @author: PQ
     * @date: 2022/7/14 17:42
     * @param: [key]
     * @return: void
     **/
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}
