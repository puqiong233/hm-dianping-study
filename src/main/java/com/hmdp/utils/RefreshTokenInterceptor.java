package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author PuQiong
 * @create 2022-07-05 14:57
 */

public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1. 获取session
        //HttpSession session = request.getSession();
        // 获取请求头中的token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            //4. 不存在，拦截
            return true;
        }

        String userKey = RedisConstants.LOGIN_USER_KEY + token;
        //2.获取session中的用户
        //Object user = session.getAttribute("user");

        // 基于Token获取Redis中的用户
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(userKey);

        //3. 判断用户是否存在
        if (userMap.isEmpty()){
            return true;
        }
        // 将查询到的Hash数据转为UserDTO
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        //5. 存在 保存用户信息到ThreadLocal
        //UserHolder.saveUser((UserDTO) user);
        UserHolder.saveUser(userDTO);

        // 刷新token有效期
        stringRedisTemplate.expire(userKey,RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        //6. 放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //移除用户
        UserHolder.removeUser();
    }
}
