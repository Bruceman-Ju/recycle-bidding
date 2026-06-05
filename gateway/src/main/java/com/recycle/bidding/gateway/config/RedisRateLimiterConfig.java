package com.recycle.bidding.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Redis 令牌桶限流配置
 */
@Configuration
public class RedisRateLimiterConfig {

    /**
     * 按 x-user-id 限流（已鉴权的用户），无则按 IP
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("x-user-id");
            if (userId != null && !userId.isEmpty()) {
                return Mono.just(userId);
            }
            String ip = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            return Mono.just(ip);
        };
    }

    /**
     * Redis 令牌桶限流器
     * replenishRate=100: 每秒补充100个令牌
     * burstCapacity=200: 最多突发200个令牌
     * requestedTokens=1: 每次请求消耗1个令牌
     */
    @Bean
    public RedisRateLimiter redisRateLimiter() {
        return new RedisRateLimiter(100, 200, 1);
    }
}
