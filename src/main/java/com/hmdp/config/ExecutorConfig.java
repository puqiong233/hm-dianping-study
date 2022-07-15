package com.hmdp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author PuQiong
 * @create 2022-07-15 14:35
 */
@Slf4j
@EnableAsync
@Configuration
public class ExecutorConfig {

    /**
     * @description: Redis 缓存击穿逻辑过期 线程池
     * @author: PQ
     * @date: 2022/7/15 14:36
     * @param: []
     * @return: org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
    **/
    @Bean
    public ThreadPoolTaskExecutor redisLogicalExpirePool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        //核心线程数
        executor.setCorePoolSize(Runtime.getRuntime().availableProcessors());
        //最大线程数
        executor.setMaxPoolSize(Runtime.getRuntime().availableProcessors()*2);
        //队列中最大的数
        executor.setQueueCapacity(10);
        //线程名称前缀
        executor.setThreadNamePrefix("redisLogicalExpirePool_");
        //rejectionPolicy：当pool已经达到max的时候，如何处理新任务
        //callerRuns：不在新线程中执行任务，而是由调用者所在的线程来执行
        //对拒绝task的处理策略
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        //线程空闲后最大的存活时间
        executor.setKeepAliveSeconds(60);
        //初始化加载
        executor.initialize();
        return executor;
    }
}
