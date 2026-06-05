package com.recycle.bidding.auction.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Redis Lua 脚本 Bean 注册
 */
@Configuration
public class RedisLuaConfig {

    @Bean
    public DefaultRedisScript<Long> bidScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/bid.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
