package com.recycle.bidding.common.util;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * 全链路追踪ID工具类
 *
 * 使用 SLF4J MDC 存储 traceId，在日志 pattern 中通过 %X{traceId} 输出。
 * Gateway 层生成 traceId 后通过 MDC 透传到整个请求链路。
 */
public class TraceIdUtil {

    /**
     * MDC key 名称
     */
    private static final String TRACE_ID_KEY = "traceId";

    /**
     * 生成并设置 traceId 到 MDC
     *
     * @return 生成的 traceId
     */
    public static String generateTraceId() {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        TraceIdUtil.setTraceId(traceId);
        return traceId;
    }

    // public static void main(String[] args) {
    //     System.out.println(TraceIdUtil.generateTraceId());
    // }

    /**
     * 设置 traceId 到 MDC
     *
     * @param traceId 追踪ID
     */
    public static void setTraceId(String traceId) {
        if (traceId != null && !traceId.isEmpty()) {
            MDC.put(TRACE_ID_KEY, traceId);
        }
    }

    /**
     * 从 MDC 获取当前 traceId
     * <p>
     * 如果 MDC 中没有 traceId，自动生成一个并设置到 MDC。
     * 兜底策略确保任何代码路径调用 getTraceId() 都不会返回 null。
     *
     * @return traceId（不会返回 null）
     */
    public static String getTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId == null || traceId.isEmpty()) {
            traceId = generateTraceId();
        }
        return traceId;
    }

    /**
     * 清除 MDC 中的 traceId
     */
    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
    }

    private TraceIdUtil() {
        // 工具类，禁止实例化
    }
}
