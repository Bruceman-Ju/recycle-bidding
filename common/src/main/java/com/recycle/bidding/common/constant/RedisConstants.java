package com.recycle.bidding.common.constant;

/**
 * Redis 常量定义
 */
public class RedisConstants {

    /**
     * 幂等键前缀
     */
    public static final String REDIS_KEY_IDEMPOTENT = "idempotent";

    /**
     * 竞拍数据前缀
     */
    public static final String REDIS_KEY_AUCTION = "auction";

    /**
     * 限流键前缀
     */
    public static final String REDIS_KEY_RATELIMIT = "ratelimit";

    /**
     * 私有构造函数，防止实例化
     */
    private RedisConstants() {}

}
