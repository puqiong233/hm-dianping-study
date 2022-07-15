package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource(name = "redisLogicalExpirePool")
    private ThreadPoolTaskExecutor redisLogicalExpirePool;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        // 解决 缓存穿透 拿到shop
        //Shop shop = queryWithPassThrough(id);
        //Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);

        // 互斥锁解决 缓存击穿
        //Shop shop = queryWithMutex(id);

        // 逻辑过期解决 缓存击穿
        //Shop shop = queryWithLogicalExpired(id);
        Shop shop = cacheClient
                .queryWithLogicalExpired(CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);
        if (shop == null) {
            return Result.fail("店铺id不存在");
        }
        // 返回
        return Result.ok(shop);
    }

    public void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {
        // 查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);
        // 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(redisData));
    }

//    public Shop queryWithLogicalExpired(Long id){
//        String key = CACHE_SHOP_KEY + id;
//        // 从redis 查询商户缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        // 判断是否存在
//        if (StrUtil.isBlank(shopJson)) {
//            // 不存在，直接返回空
//            return null;
//        }
//        // 命中 先反序列化对象
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//        // 判断是否过期
//        if (expireTime.isAfter(LocalDateTime.now())) {
//            // 未过期直接返回对象
//            return shop;
//        }
//        // 已经过期 需要缓存重建
//        // 获取互斥锁
//        String lockKey = LOCK_SHOP_KEY + id;
//        boolean isLock = tryLock(lockKey);
//        // 成功后开启独立线程 实现缓存重建
//        if (isLock) {
//            redisLogicalExpirePool.submit(()->{
//                try {
//                    // 重建缓存
//                    saveShop2Redis(id,20L);
//                }catch (Exception e){
//                    throw new RuntimeException(e);
//                }finally {
//                    // 释放锁
//                    unlock(lockKey);
//                }
//            });
//        }
//        // 返回过期的商户信息
//        return shop;
//    }

//    /**
//     * @description: 互斥锁解决缓存击穿
//     * @author: PQ
//     * @date: 2022/7/15 14:08
//     * @param: [id]
//     * @return: com.hmdp.entity.Shop
//    **/
//    public Shop queryWithMutex(Long id){
//        String key = CACHE_SHOP_KEY + id;
//        // 从redis 查询商户缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        // 判断是否存在
//        Shop shop = new Shop();
//        if (StrUtil.isNotBlank(shopJson)) {
//            // 存在，直接返回
//            shop = JSONUtil.toBean(shopJson, Shop.class);
//            return shop;
//        }
//        if (shopJson != null) {
//            return null;
//        }
//        // 实现缓存重建
//        // 获取互斥锁
//        String lockKey = "lock:key:" + id;
//        try {
//            boolean isLock = tryLock(lockKey);
//            if (!isLock) {
//                // 获取互斥锁失败
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
//            // 获取互斥锁成功
//            // 判断是否存在
//            shopJson = stringRedisTemplate.opsForValue().get(key);
//            if (StrUtil.isNotBlank(shopJson)) {
//                // 存在，直接返回
//                shop = JSONUtil.toBean(shopJson, Shop.class);
//                unlock(lockKey);
//                return shop;
//            }
//            if (shopJson != null) {
//                unlock(lockKey);
//                return null;
//            }
//
//            // 不存在 根据id查询数据库
//            shop = getById(id);
//            // 模拟重建的延时
//            Thread.sleep(200);
//            // 不存在 返回错误
//            if (shop == null) {
//                // 将空值写入 Redis 防止缓存穿透
//                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//            // 存在 写入redis
//            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        }catch (InterruptedException e){
//            throw new RuntimeException(e);
//        }finally {
//            // 释放互斥锁
//            unlock(lockKey);
//        }
//        // 返回
//        return shop;
//    }

//    /**
//     * @description: 解决缓存穿透
//     * @author: PQ
//     * @date: 2022/7/15 15:45
//     * @param: [id]
//     * @return: com.hmdp.entity.Shop
//    **/
//    public Shop queryWithPassThrough(Long id){
//        String key = CACHE_SHOP_KEY + id;
//        // 从redis 查询商户缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        // 判断是否存在
//        if (StrUtil.isNotBlank(shopJson)) {
//            // 存在，直接返回
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return shop;
//        }
//        if (shopJson != null) {
//            return null;
//        }
//        // 不存在 根据id查询数据库
//        Shop shop = getById(id);
//        // 不存在 返回错误
//        if (shop == null) {
//            // 将空值写入 Redis 防止缓存穿透
//            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
//
//            return null;
//        }
//        // 存在 写入redis
//        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        // 返回
//        return shop;
//    }

//    /**
//     * @description: 尝试获取锁
//     * @author: PQ
//     * @date: 2022/7/14 17:41
//     * @param: [key]
//     * @return: boolean
//    **/
//    private boolean tryLock(String key){
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag);
//    }
//
//    /**
//     * @description: 释放锁
//     * @author: PQ
//     * @date: 2022/7/14 17:42
//     * @param: [key]
//     * @return: void
//    **/
//    private void unlock(String key){
//        stringRedisTemplate.delete(key);
//    }

    @Override
    // 事物回滚
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 先更新数据库
        updateById(shop);
        // 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());

        return Result.ok();
    }
}
