package com.recycle.bidding.gateway.filter;

import com.recycle.bidding.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
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

/**
 * 鉴权全局过滤器
 *
 * 白名单路径直接放行，其余路径需要验证 Authorization header 中的 JWT。
 * 验证通过后将 userId 和 role 放入 x-user-id / x-user-role header 透传到下游。
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    /** 白名单路径 */
    private static final String[] WHITE_LIST = {
            "/auth/login",
            "/auth/register",
            "/merchant/create",
            "/eureka/"
    };

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 白名单直接放行
        for (String whitePath : WHITE_LIST) {
            if (path.contains(whitePath)) {
                return chain.filter(exchange);
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
