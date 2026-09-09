package com.recycle.bidding.common.constant;

/**
 * 系统常量定义
 */
public class SystemConstants {

    //  业务参数
    /**
     * 竞拍持续时间（秒）
     */
    public static final int AUCTION_DURATION_SECONDS = 180;

    /**
     * Redis 竞拍数据 TTL 缓冲区（秒）
     * TTL = AUCTION_DURATION_SECONDS + AUCTION_TTL_BUFFER_EXTRA
     * 给 endAuction() 留出充足的读取窗口
     */
    public static final int AUCTION_TTL_BUFFER_EXTRA = 300;

    /**
     * Redis 竞拍数据实际 TTL（秒）
     */
    public static final int AUCTION_TTL_BUFFER_SECONDS = AUCTION_DURATION_SECONDS + AUCTION_TTL_BUFFER_EXTRA;

    /**
     * RocketMQ 延迟级别：7 = 3分钟
     */
    public static final int AUCTION_TIMEOUT_DELAY_LEVEL = 7;

    /**
     * WebSocket 心跳空闲超时（秒）
     */
    public static final int WS_IDLE_TIMEOUT_SECONDS = 60;

    /**
     * 优惠券默认有效期（天）
     */
    public static final int COUPON_DEFAULT_VALID_DAYS = 7;

    /**
     * 私有构造函数，防止实例化
     */
    private SystemConstants() {

    }
}
