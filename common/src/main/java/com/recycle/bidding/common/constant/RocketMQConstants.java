package com.recycle.bidding.common.constant;

/**
 * RocketMQ 常量定义
 */
public class RocketMQConstants {

    //  RocketMQ Topic
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

    //  RocketMQ Tag
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

    /**
     * 私有构造函数，防止实例化
     */
    private RocketMQConstants() {}

}
