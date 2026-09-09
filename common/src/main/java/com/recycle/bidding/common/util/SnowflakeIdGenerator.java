package com.recycle.bidding.common.util;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 雪花算法 ID 生成器（统一入口）。
 *
 * <p>设计取舍（架构决策 ADR）：
 * <ul>
 *     <li>为什么不放每个服务里各自 new：workerId 分配策略必须全局一致，
 *     散落各处会出现「改了 A 忘了 B」的不一致，也无法统一做校验与告警。</li>
 *     <li>为什么用环境变量注入 workerId：Docker Compose / K8s 中每个实例拿不同的
 *     环境变量（POD 序号 / 部署序号），无需引入 ZK、Redis 即可保证多实例唯一。</li>
 *     <li>当前为嵌入式雪花（interim 方案）。若未来上线集中式 ID 服务（如美团 Leaf），
 *     只需替换本类的 nextId 实现，所有调用方无感知。</li>
 * </ul>
 *
 * <p>注意：雪花算法本身不处理时钟回拨，默认实现在回拨时会生成可能重复的 ID。
 * 时钟回拨的应对方案（容忍阈值 / 阻塞等待 / ZK 持久化 lastTimestamp）属于独立改造项，
 * 不在本次 workerId 注入范围内。
 */
@Slf4j
public final class SnowflakeIdGenerator {

    private static final String ENV_WORKER_ID = "SNOWFLAKE_WORKER_ID";
    private static final String ENV_DATACENTER_ID = "SNOWFLAKE_DATACENTER_ID";

    /** 5 bit 节点位，合法范围 [0, 31] */
    private static final long MAX_NODE_ID = 31L;

    private static final Snowflake SNOWFLAKE = init();

    private SnowflakeIdGenerator() {
    }

    public static long nextId() {
        return SNOWFLAKE.nextId();
    }

    public static String nextIdStr() {
        return SNOWFLAKE.nextIdStr();
    }

    private static Snowflake init() {
        long workerId = resolveEnv(ENV_WORKER_ID, 0L);
        long datacenterId = resolveEnv(ENV_DATACENTER_ID, 0L);
        checkRange(ENV_WORKER_ID, workerId);
        checkRange(ENV_DATACENTER_ID, datacenterId);
        log.info("SnowflakeIdGenerator 初始化完成: workerId={}, datacenterId={}", workerId, datacenterId);
        return IdUtil.getSnowflake(workerId, datacenterId);
    }

    private static long resolveEnv(String name, long fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            log.warn("环境变量 {} 值非法: {}, 回退到默认值 {}", name, value, fallback);
            return fallback;
        }
    }

    private static void checkRange(String name, long value) {
        if (value < 0 || value > MAX_NODE_ID) {
            throw new IllegalStateException(
                    String.format("环境变量 %s 超出合法范围 [0, %d]: %d", name, MAX_NODE_ID, value));
        }
    }
}
