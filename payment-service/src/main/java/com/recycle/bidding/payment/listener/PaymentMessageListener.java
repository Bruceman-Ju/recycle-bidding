package com.recycle.bidding.payment.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recycle.bidding.common.constant.SystemConstants;
import com.recycle.bidding.payment.entity.PaymentRecord;
import com.recycle.bidding.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 支付消息消费者
 *
 * 消费 payment-topic 的 TAG_PAYMENT_REQUEST → 执行支付
 * 消费 payment-topic 的 TAG_PAYMENT_SUCCESS → 后续处理（通知 coupon-service 等）
 */
@Slf4j
@Component
public class PaymentMessageListener {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    public PaymentMessageListener(PaymentService paymentService, ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
    }

    /**
     * 消费 PAYMENT_REQUEST → 执行支付
     */
    @Component
    @RequiredArgsConstructor
    @RocketMQMessageListener(
            topic = SystemConstants.TOPIC_PAYMENT,
            consumerGroup = "payment-request-consumer",
            selectorExpression = SystemConstants.TAG_PAYMENT_REQUEST
    )
    public static class PaymentRequestListener implements RocketMQListener<String> {

        private final PaymentService paymentService;
        private final ObjectMapper objectMapper;

        @Override
        public void onMessage(String message) {
            log.info("收到支付请求消息: {}", message);
            try {
                JsonNode jsonNode = objectMapper.readTree(message);
                Long paymentId = jsonNode.get("paymentId").asLong();
                paymentService.processPayment(paymentId);
            } catch (Exception e) {
                log.error("处理支付请求失败: message={}", message, e);
            }
        }
    }

    /**
     * 消费 PAYMENT_SUCCESS → 后续处理
     */
    @Component
    @RequiredArgsConstructor
    @RocketMQMessageListener(
            topic = SystemConstants.TOPIC_PAYMENT,
            consumerGroup = "payment-success-consumer",
            selectorExpression = SystemConstants.TAG_PAYMENT_SUCCESS
    )
    public static class PaymentSuccessListener implements RocketMQListener<String> {

        private final ObjectMapper objectMapper;

        @Override
        public void onMessage(String message) {
            log.info("收到支付成功消息: {}", message);
            try {
                JsonNode jsonNode = objectMapper.readTree(message);
                Long paymentId = jsonNode.get("paymentId").asLong();
                Long orderId = jsonNode.get("orderId").asLong();
                String transactionId = jsonNode.get("transactionId").asText();

                log.info("支付成功记录: paymentId={}, orderId={}, transactionId={}",
                        paymentId, orderId, transactionId);

                // 此处后续可触发优惠券发放、订单状态更新等
            } catch (Exception e) {
                log.error("处理支付成功消息失败: message={}", message, e);
            }
        }
    }
}
