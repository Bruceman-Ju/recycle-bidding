package com.recycle.bidding.payment.service;

import com.recycle.bidding.payment.entity.PaymentRecord;

import java.math.BigDecimal;

public interface PaymentService {

    /**
     * 创建支付记录
     */
    PaymentRecord createPayment(Long orderId, Long payerId, Long payeeId, BigDecimal amount);

    /**
     * 执行支付
     */
    PaymentRecord processPayment(Long paymentId);

    /**
     * 处理支付回调
     */
    PaymentRecord handleCallback(Long paymentId, String status);

    /**
     * 查询支付状态
     */
    PaymentRecord getPaymentById(Long paymentId);
}
