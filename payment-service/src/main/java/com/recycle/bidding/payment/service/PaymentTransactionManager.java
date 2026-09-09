package com.recycle.bidding.payment.service;

import cn.hutool.core.util.IdUtil;
import com.recycle.bidding.common.constant.RocketMQConstants;
import com.recycle.bidding.common.constant.SystemConstants;
import com.recycle.bidding.payment.entity.PaymentRecord;
import com.recycle.bidding.payment.repository.PaymentRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 事务消息管理器
 *
 * 负责发送事务消息和回查本地事务状态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentTransactionManager implements RocketMQLocalTransactionListener {

    private final RocketMQTemplate rocketMQTemplate;
    private final PaymentRecordRepository paymentRecordRepository;

    /**
     * 发送支付事务消息
     */
    public void sendPaymentMessage(PaymentRecord record) {
        String transactionId = "TXN" + IdUtil.getSnowflakeNextIdStr();
        String payload = String.format(
                "{\"paymentId\":%d,\"paymentNo\":\"%s\",\"orderId\":%d,\"amount\":%s,\"transactionId\":\"%s\"}",
                record.getId(), record.getPaymentNo(), record.getOrderId(),
                record.getAmount().toPlainString(), transactionId
        );

        Message<String> message = MessageBuilder.withPayload(payload)
                .setHeader("transactionId", transactionId)
                .setHeader("paymentId", record.getId().toString())
                .build();

        rocketMQTemplate.sendMessageInTransaction(
                RocketMQConstants.TOPIC_PAYMENT + ":" + RocketMQConstants.TAG_PAYMENT_REQUEST,
                message,
                record
        );

        log.info("发送支付事务消息: paymentId={}, transactionId={}", record.getId(), transactionId);
    }

    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        // 本地事务已在 createPayment 中执行完成
        log.debug("事务消息本地事务已执行: msg={}", msg.getPayload());
        return RocketMQLocalTransactionState.UNKNOWN;
    }

    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        String paymentIdStr = (String) msg.getHeaders().get("paymentId");

        if (paymentIdStr == null) {
            log.warn("事务回查: 缺少paymentId");
            return RocketMQLocalTransactionState.UNKNOWN;
        }

        Long paymentId = Long.parseLong(paymentIdStr);
        PaymentRecord record = paymentRecordRepository.selectById(paymentId);

        if (record == null) {
            return RocketMQLocalTransactionState.UNKNOWN;
        }

        switch (record.getStatus()) {
            case "SUCCESS":
                return RocketMQLocalTransactionState.COMMIT;
            case "FAILED":
                return RocketMQLocalTransactionState.ROLLBACK;
            default:
                return RocketMQLocalTransactionState.UNKNOWN;
        }
    }
}
