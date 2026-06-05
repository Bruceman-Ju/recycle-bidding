package com.recycle.bidding.common.constant;

/**
 * 竞拍模块常量定义
 */
public class AuctionConstants {

    private AuctionConstants() {}

    /** 竞拍 Topic */
    public static final String TOPIC_AUCTION = "auction-topic";

    // ========== 消息标签 ==========
    /** 竞拍开始 */
    public static final String TAG_AUCTION_STARTED = "AUCTION_STARTED";
    /** 新出价 */
    public static final String TAG_NEW_BID = "AUCTION_NEW_BID";
    /** 竞拍超时 */
    public static final String TAG_AUCTION_TIMEOUT = "AUCTION_TIMEOUT";
    /** 竞拍结束 */
    public static final String TAG_AUCTION_ENDED = "AUCTION_ENDED";

    // ========== 消息类型（JSON中type字段） ==========
    public static final String MSG_TYPE_AUCTION_STARTED = "AUCTION_STARTED";
    public static final String MSG_TYPE_AUCTION_TIMEOUT = "AUCTION_TIMEOUT";
    public static final String MSG_TYPE_AUCTION_ENDED = "AUCTION_ENDED";

    // ========== 竞拍状态 ==========
    /** 竞拍进行中 */
    public static final String AUCTION_STATUS_RUNNING = "RUNNING";
    /** 竞拍已结束 */
    public static final String AUCTION_STATUS_ENDED = "ENDED";

    // ========== Redis Key 前缀 ==========
    /** 出价有序集合前缀 */
    public static final String REDIS_KEY_PREFIX_BIDS = "auction:bids:";
    /** 竞拍信息HASH前缀 */
    public static final String REDIS_KEY_PREFIX_INFO = "auction:info:";
    /** 竞拍商户集合前缀 */
    public static final String REDIS_KEY_PREFIX_MERCHANTS = "auction:merchants:";
    /** 出价锁前缀（分布式锁，Layer 1 幂等） */
    public static final String REDIS_KEY_PREFIX_LOCK = "bid:lock:";
}
