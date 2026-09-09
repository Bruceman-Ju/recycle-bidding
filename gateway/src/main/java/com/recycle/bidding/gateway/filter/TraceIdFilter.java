package com.recycle.bidding.gateway.filter;

import com.recycle.bidding.common.constant.HttpConstants;
import com.recycle.bidding.common.constant.SystemConstants;
import com.recycle.bidding.common.util.TraceIdUtil;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 全局过滤器 — traceId 生成与透传
 *
 * 最高优先级（order=-100），在每个请求进入时生成或透传 traceId。
 * 下游服务可通过 Feign 拦截器从 request header 获取并设置到 MDC。
 */
@Component
public class TraceIdFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String rawTraceId = exchange.getRequest().getHeaders()
                .getFirst(HttpConstants.TRACE_ID_HEADER);
        final String traceId;
        if (ObjectUtils.isEmpty(rawTraceId)) {
            traceId = TraceIdUtil.generateTraceId();
        } else {
            traceId = rawTraceId;
        }
        // 设置到 response header
        exchange.getResponse().getHeaders().set(HttpConstants.TRACE_ID_HEADER, traceId);
        // 透传到下游服务
        return chain.filter(exchange.mutate()
                .request(r -> r.header(HttpConstants.TRACE_ID_HEADER, traceId))
                .build());
    }

    @Override
    public int getOrder() {
        return -100; // 最高优先级
    }
}
