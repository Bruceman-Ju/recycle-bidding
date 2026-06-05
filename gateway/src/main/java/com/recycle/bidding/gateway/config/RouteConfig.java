package com.recycle.bidding.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 路由表配置
 *
 * 使用 Java DSL 配置路由规则 + 熔断降级。
 */
@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // 订单服务
                .route("order-service", r -> r.path("/api/v1/order/**")
                        .filters(f -> f.circuitBreaker(config -> config
                                .setName("orderServiceCB")
                                .setFallbackUri("forward:/fallback/order")))
                        .uri("lb://order-service"))
                // 工程师服务
                .route("engineer-service", r -> r.path("/api/v1/engineer/**")
                        .filters(f -> f.circuitBreaker(config -> config
                                .setName("engineerServiceCB")
                                .setFallbackUri("forward:/fallback/engineer")))
                        .uri("lb://engineer-service"))
                // 商户服务
                .route("merchant-service", r -> r.path("/api/v1/merchant/**")
                        .uri("lb://merchant-service"))
                // 竞拍服务
                .route("auction-service", r -> r.path("/api/v1/auction/**")
                        .uri("lb://auction-service"))
                // 支付服务
                .route("payment-service", r -> r.path("/api/v1/payment/**")
                        .uri("lb://payment-service"))
                // 优惠券服务
                .route("coupon-service", r -> r.path("/api/v1/coupon/**")
                        .uri("lb://coupon-service"))
                // 推送服务
                .route("push-service", r -> r.path("/api/v1/push/**")
                        .uri("lb://push-service"))
                .build();
    }
}
