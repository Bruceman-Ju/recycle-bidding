package com.recycle.bidding.common.constant;

/**
 * 订单状态常量
 * 定义订单从创建到完成的全部 10 个状态
 */
public class OrderStatus {

    /**
     * 待估价：用户下单，等待工程师接单
     */
    public static final String PENDING_EVALUATION = "PENDING_EVALUATION";

    /**
     * 工程师已分配：系统已分配工程师，等待上门
     */
    public static final String ENGINEER_ASSIGNED = "ENGINEER_ASSIGNED";

    /**
     * 验机中：工程师正在上门验机
     */
    public static final String INSPECTING = "INSPECTING";

    /**
     * 二次估价完成：工程师提交验机报告，等待用户确认
     */
    public static final String SECOND_EVALUATED = "SECOND_EVALUATED";

    /**
     * 用户已确认：用户接受估价，等待发起竞拍
     */
    public static final String USER_CONFIRMED = "USER_CONFIRMED";

    /**
     * 竞拍中：系统已发起3分钟竞拍
     */
    public static final String AUCTIONING = "AUCTIONING";

    /**
     * 竞拍结束：3分钟竞拍已结束
     */
    public static final String AUCTION_ENDED = "AUCTION_ENDED";

    /**
     * 竞拍异常：因系统宕机或数据不一致导致竞拍失败，等待工程师处理
     */
    public static final String AUCTION_FAILED = "AUCTION_FAILED";

    /**
     * 支付完成：工程师确认打款成功
     */
    public static final String PAYMENT_COMPLETED = "PAYMENT_COMPLETED";

    /**
     * 订单完成：优惠券已发放，交易闭环
     */
    public static final String ORDER_COMPLETED = "ORDER_COMPLETED";

    /**
     * 订单关闭：用户取消或异常终止
     */
    public static final String ORDER_CLOSED = "ORDER_CLOSED";

    private OrderStatus() {
        // 常量类，禁止实例化
    }
}
