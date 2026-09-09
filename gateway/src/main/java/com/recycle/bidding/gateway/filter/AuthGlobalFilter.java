package com.recycle.bidding.gateway.filter;

import com.recycle.bidding.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 鉴权全局过滤器
 *
 * 白名单路径直接放行，其余路径需要验证 Authorization header 中的 JWT。
 * 白名单从 Nacos (gateway-auth.yml) 动态加载，支持热刷新；
 * Nacos 不可用时兜底使用 application.yml 中的本地配置。
 *
 * 白名单配置格式（Nacos 或本地 yml）：
 *   gateway:
 *     auth:
 *       white-list:
 *         - /auth/login
 *         - /auth/register
 */
@RefreshScope
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    @Value("${gateway.auth.white-list:}")
    private List<String> whiteList;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 白名单直接放行
        if (whiteList != null) {
            for (String whitePath : whiteList) {
                if (path.contains(whitePath)) {
                    return chain.filter(exchange);
                }
            }
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (StringUtils.isEmpty(authHeader) || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = JwtUtil.parseToken(token);
            String userId = claims.getSubject();
            String role = claims.get("role", String.class);

            return chain.filter(exchange.mutate()
                    .request(r -> r.header("x-user-id", userId)
                                   .header("x-user-role", role))
                    .build());
        } catch (Exception e) {
            return unauthorized(exchange, "Invalid token: " + e.getMessage());
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String msg) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        String body = "{\"code\":401,\"message\":\"" + msg + "\"}";
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -50;
    }
}
