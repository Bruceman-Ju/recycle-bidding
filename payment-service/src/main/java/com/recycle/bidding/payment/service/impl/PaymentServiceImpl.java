package com.recycle.bidding.payment.service.impl;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recycle.bidding.common.constant.RocketMQConstants;
import com.recycle.bidding.common.constant.SystemConstants;
import com.recycle.bidding.common.exception.BizException;
import com.recycle.bidding.common.exception.ErrorCode;
import com.recycle.bidding.common.util.TraceIdUtil;
import com.recycle.bidding.payment.entity.PaymentRecord;
import com.recycle.bidding.payment.repository.PaymentRecordRepository;
import com.recycle.bidding.payment.service.PaymentService;
import com.recycle.bidding.payment.service.PaymentTransactionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRecordRepository paymentRecordRepository;
    private final PaymentTransactionManager transactionManager;
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentRecord createPayment(Long orderId, Long payerId, Long payeeId, BigDecimal amount) {
        String paymentNo = "PAY" + IdUtil.getSnowflakeNextIdStr();
        PaymentRecord record = PaymentRecord.builder()
                .paymentNo(paymentNo)
                .orderId(orderId)
                .payerId(payerId)
                .payeeId(payeeId)
                .amount(amount)
                .platformFee(BigDecimal.ZERO)
                .paymentType("MERCHANT_PLATFORM")
                .status("PENDING")
                .build();
        paymentRecordRepository.insert(record);

        // 发送事务消息
        transactionManager.sendPaymentMessage(record);

        log.info("支付记录创建成功: id={}, paymentNo={}, amount={}", record.getId(), paymentNo, amount);
        return record;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentRecord processPayment(Long paymentId) {
        PaymentRecord record = paymentRecordRepository.selectById(paymentId);
        if (record == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "支付记录不存在");
        }
        if (!"PENDING".equals(record.getStatus())) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID.getCode(), "支付状态不允许处理");
        }

        // 更新为处理中
        record.setStatus("PROCESSING");
        paymentRecordRepository.updateById(record);

        // Mock 第三方支付API调用
        try {
            log.info("调用第三方支付API: paymentNo={}, amount={}", record.getPaymentNo(), record.getAmount());
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 支付成功
        String transactionId = "TXN" + IdUtil.getSnowflakeNextIdStr();
        record.setStatus("SUCCESS");
        record.setTransactionId(transactionId);
        record.setCompletedAt(LocalDateTime.now());
        paymentRecordRepository.updateById(record);

        log.info("支付处理成功: paymentId={}, transactionId={}", paymentId, transactionId);

        // 发送 PAYMENT_SUCCESS 消息到 MQ（通知优惠券服务发放优惠券）
        try {
            Map<String, Object> successMsg = new java.util.HashMap<>();
            successMsg.put("paymentId", paymentId);
            successMsg.put("orderId", record.getOrderId());
            successMsg.put("userId", record.getPayeeId());  // 收款方即用户
            successMsg.put("amount", record.getAmount());
            successMsg.put("traceId", TraceIdUtil.getTraceId());

            String payload = objectMapper.writeValueAsString(successMsg);
            rocketMQTemplate.syncSend(
                    RocketMQConstants.TOPIC_PAYMENT + ":" + RocketMQConstants.TAG_PAYMENT_SUCCESS,
                    MessageBuilder.withPayload(payload)
                            .setHeader("traceId", TraceIdUtil.getTraceId())
                            .build()
            );
            log.info("支付成功消息已发送: paymentId={}, orderId={}", paymentId, record.getOrderId());
        } catch (Exception e) {
            log.warn("支付成功消息发送失败（不影响支付主流程）: paymentId={}", paymentId, e);
        }

        return record;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentRecord handleCallback(Long paymentId, String status) {
        PaymentRecord record = paymentRecordRepository.selectById(paymentId);
        if (record == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "支付记录不存在");
        }

        record.setStatus(status);
        if ("SUCCESS".equals(status)) {
            record.setCompletedAt(LocalDateTime.now());
        }
        paymentRecordRepository.updateById(record);

        log.info("支付回调处理: paymentId={}, status={}", paymentId, status);
        return record;
    }

    @Override
    public PaymentRecord getPaymentById(Long paymentId) {
        PaymentRecord record = paymentRecordRepository.selectById(paymentId);
        if (record == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "支付记录不存在");
        }
        return record;
    }
}
