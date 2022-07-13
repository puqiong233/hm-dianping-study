package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.hmdp.utils.RedisConstants.SHOP_TYPE_LIST;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public List<ShopType> queryShopList() {
        List<ShopType> list = new ArrayList<>();
        // 先判断redis有没有该list
        Set<String> range = stringRedisTemplate.opsForZSet().range(SHOP_TYPE_LIST, 0, -1);
        if (!range.isEmpty()) {
            for (String typeJson : range) {
                list.add(JSONUtil.toBean(typeJson, ShopType.class, false));
            }
            return list;
        }
        // redis中没有 向数据库取
        list = query().orderByAsc("sort").list();
        // 向redis存
        for (ShopType shopType : list) {
            stringRedisTemplate.opsForZSet().add(SHOP_TYPE_LIST, JSONUtil.toJsonStr(shopType),shopType.getSort());
        }

        return list;
    }
}
