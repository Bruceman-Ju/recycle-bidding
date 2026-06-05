package com.recycle.bidding.common.constant;

/**
 * 系统常量定义
 */
public class SystemConstants {

    // ==================== HTTP 头 ====================

    /**
     * 全链路追踪ID请求头
     */
    public static final String TRACE_ID_HEADER = "x-trace-id";

    /**
     * 鉴权 Token 请求头
     */
    public static final String AUTH_HEADER = "Authorization";

    // ==================== 分页默认值 ====================

    /**
     * 默认每页大小
     */
    public static final String DEFAULT_PAGE_SIZE = "20";

    /**
     * 默认页码
     */
    public static final String DEFAULT_PAGE = "1";

    // ==================== API 前缀 ====================

    /**
     * API 统一前缀
     */
    public static final String API_PREFIX = "/api/v1";

    // ==================== RocketMQ Topic ====================

    /**
     * 订单事件主题
     */
    public static final String TOPIC_ORDER = "order-topic";

    /**
     * 竞拍事件主题
     */
    public static final String TOPIC_AUCTION = "auction-topic";

    /**
     * 支付事件主题
     */
    public static final String TOPIC_PAYMENT = "payment-topic";

    /**
     * 出价记录落盘主题（异步写 MySQL 的专用 Topic）
     */
    public static final String TOPIC_BID_DB_SYNC = "bid-db-sync-topic";

    // ==================== RocketMQ Tag ====================

    /** 订单创建 */
    public static final String TAG_ORDER_CREATED = "ORDER_CREATED";
    /** 验机完成 */
    public static final String TAG_EVALUATION_COMPLETED = "EVALUATION_COMPLETED";
    /** 用户确认估价 */
    public static final String TAG_USER_CONFIRMED = "USER_CONFIRMED";
    /** 竞拍开始 */
    public static final String TAG_AUCTION_STARTED = "AUCTION_STARTED";
    /** 新出价 */
    public static final String TAG_NEW_BID = "NEW_BID";
    /** 竞拍结束 */
    public static final String TAG_AUCTION_ENDED = "AUCTION_ENDED";
    /** 支付请求 */
    public static final String TAG_PAYMENT_REQUEST = "PAYMENT_REQUEST";
    /** 支付成功 */
    public static final String TAG_PAYMENT_SUCCESS = "PAYMENT_SUCCESS";
    /** 同步出价记录落盘 */
    public static final String TAG_SYNC_BID_RECORD = "SYNC_BID_RECORD";
    /** 竞拍超时 */
    public static final String TAG_AUCTION_TIMEOUT = "AUCTION_TIMEOUT";
    /** 订单状态变更 */
    public static final String TAG_ORDER_STATUS_CHANGED = "ORDER_STATUS_CHANGED";

    // ==================== Redis Key 前缀 ====================

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

    // ==================== 业务参数 ====================

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

    private SystemConstants() {
        // 常量类，禁止实例化
    }
}
