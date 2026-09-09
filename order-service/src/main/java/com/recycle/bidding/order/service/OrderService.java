package com.recycle.bidding.order.service;

import com.recycle.bidding.common.result.PageResult;
import com.recycle.bidding.order.entity.Order;

import java.math.BigDecimal;

public interface OrderService {

    /**
     * 创建订单
     * @return 订单号（发送前生成）；创建成败以是否抛异常判定
     */
    String createOrder(Long userId, Long phoneModelId, BigDecimal initialEstimate);

    /**
     * 用户确认估价
     */
    Order confirmOrder(Long orderId);

    /**
     * 工程师确认打款
     */
    Order confirmPayment(Long orderId);

    /**
     * 更新订单状态（带状态机校验和事件日志）
     */
    Order updateOrderStatus(Long orderId, String fromStatus, String toStatus, String operator, Long operatorId, String remark);

    /**
     * 根据ID查询订单
     */
    Order getOrderById(Long orderId);

    /**
     * 查询用户订单列表
     */
    PageResult<Order> getUserOrders(Long userId, int page, int size);

    /**
     * 更新订单为竞拍结束
     */
    Order updateOrderToAuctionEnded(Long orderId, Long winnerMerchantId, BigDecimal finalPrice);
}
