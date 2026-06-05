package com.recycle.bidding.common.util;

import org.slf4j.MDC;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * 全链路追踪 TraceId 过滤器
 * <p>
 * 从请求头 x-trace-id 中获取 traceId 并设置到 MDC，
 * 响应头也透传 traceId，下游服务可继续传递。
 * 如果请求头中没有 traceId，则自动生成一个。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdWebFilter implements Filter {

    private static final String TRACE_ID_HEADER = "x-trace-id";
    private static final String TRACE_ID_KEY = "traceId";

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain filterChain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // 从请求头获取 traceId，没有则生成
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }

        // 设置到 MDC
        MDC.put(TRACE_ID_KEY, traceId);

        // 响应头也透传
        response.setHeader(TRACE_ID_HEADER, traceId);

        try {
            filterChain.doFilter(servletRequest, servletResponse);
        } finally {
            // 请求结束后清除 MDC，避免线程复用时污染
            MDC.clear();
        }
    }
}
