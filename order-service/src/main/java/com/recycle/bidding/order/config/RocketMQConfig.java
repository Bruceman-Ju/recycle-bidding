package com.recycle.bidding.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ 配置
 */
@Configuration
public class RocketMQConfig {

    // RocketMQ 的自动配置由 rocketmq-spring-boot-starter 提供
    // 此配置类可扩展自定义的 messageConverter、producer 等 bean

    /*
     * 如果需要自定义消息转换器，可取消注释：
     *
     * @Bean
     * public MessageConverter messageConverter() {
     *     return new MappingJackson2MessageConverter();
     * }
     */
}
