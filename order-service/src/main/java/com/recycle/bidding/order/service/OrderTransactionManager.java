package com.recycle.bidding.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recycle.bidding.common.constant.OrderStatus;
import com.recycle.bidding.common.constant.RocketMQConstants;
import com.recycle.bidding.common.exception.BizException;
import com.recycle.bidding.common.exception.ErrorCode;
import com.recycle.bidding.common.util.SnowflakeIdGenerator;
import com.recycle.bidding.common.util.TraceIdUtil;
import com.recycle.bidding.order.entity.Order;
import com.recycle.bidding.order.entity.OrderEventLog;
import com.recycle.bidding.order.entity.OrderTask;
import com.recycle.bidding.order.repository.OrderEventLogRepository;
import com.recycle.bidding.order.repository.OrderRepository;
import com.recycle.bidding.order.repository.OrderTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * RocketMQ 事务消息 —— 订单创建
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTransactionManager implements RocketMQLocalTransactionListener {

    private final RocketMQTemplate rocketMQTemplate;
    private final OrderRepository orderRepository;
    private final OrderEventLogRepository orderEventLogRepository;
    private final OrderTaskRepository orderTaskRepository;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 发送订单创建事务消息
     *
     * @return 订单号（发送前生成，不依赖本地事务取回，规避读写分离从库延迟）
     */
    public String sendCreateOrderMessage(Long userId, Long phoneModelId, BigDecimal initialEstimate) {

        // 临时方案，集中式 ID 服务（如美团 Leaf）为后续演进方向
        String orderNo = "ORD" + SnowflakeIdGenerator.nextIdStr();
        String traceId = TraceIdUtil.getTraceId();

        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("orderNo", orderNo);
        payloadMap.put("userId", userId);
        payloadMap.put("phoneModelId", phoneModelId);
        payloadMap.put("initialEstimate", initialEstimate);
        payloadMap.put("eventType", "ORDER_CREATED");
        payloadMap.put("traceId", traceId);

        String payload;
        try {
            payload = objectMapper.writeValueAsString(payloadMap);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "序列化事务消息失败");
        }

        Message<String> message = MessageBuilder.withPayload(payload)
                .setHeader("orderNo", orderNo)
                .setHeader("userId", userId.toString())
                .setHeader("phoneModelId", phoneModelId.toString())
                .setHeader("traceId", traceId)
                .setHeader("txId", "TXN_ORDER_" + orderNo)
                .build();

        // arg 传 null：业务参数已全部编码进 Message，监听器从 msg 取参，无需堆内载体
        TransactionSendResult result = rocketMQTemplate.sendMessageInTransaction(
                RocketMQConstants.TOPIC_ORDER + ":" + RocketMQConstants.TAG_ORDER_CREATED,
                message,
                null
        );

        // 半消息发送失败 → 本地事务未执行，创建直接失败
        if (result.getSendStatus() != SendStatus.SEND_OK) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "订单创建失败：消息发送异常");
        }
        // 本地事务执行失败（ROLLBACK）→ 半消息被 broker 丢弃，创建失败
        if (result.getLocalTransactionState() == LocalTransactionState.ROLLBACK_MESSAGE) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "订单创建失败");
        }

        return orderNo;
    }

    /**
     * 执行本地事务
     */
    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        String orderNo = (String) msg.getHeaders().get("orderNo");
        try {
            Object rawPayload = msg.getPayload();
            JsonNode payload;
            if (rawPayload instanceof byte[]) {
                payload = objectMapper.readTree((byte[]) rawPayload);
            } else {
                payload = objectMapper.readTree(rawPayload.toString());
            }

            Long userId = payload.get("userId").asLong();
            Long phoneModelId = payload.get("phoneModelId").asLong();
            BigDecimal initialEstimate = new BigDecimal(payload.get("initialEstimate").asText());
            String traceId = (String) msg.getHeaders().get("traceId");

            transactionTemplate.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(TransactionStatus status) {
                    Order order = Order.builder()
                            .orderNo(orderNo)
                            .userId(userId)
                            .phoneModelId(phoneModelId)
                            .initialEstimate(initialEstimate)
                            .version(1)
                            .status(OrderStatus.PENDING_EVALUATION)
                            .build();
                    orderRepository.insert(order);

                    OrderEventLog eventLog = OrderEventLog.builder()
                            .orderId(order.getId())
                            .fromStatus(null)
                            .toStatus(OrderStatus.PENDING_EVALUATION)
                            .operator("SYSTEM")
                            .operatorId(userId)
                            .remark("用户下单，初始估价：" + initialEstimate)
                            .traceId(traceId)
                            .build();
                    orderEventLogRepository.insert(eventLog);

                    OrderTask task = OrderTask.builder()
                            .orderId(order.getId())
                            .engineerId(null)
                            .taskStatus("ASSIGNED")
                            .assignedAt(LocalDateTime.now())
                            .build();
                    orderTaskRepository.insert(task);

                    log.info("本地事务执行成功: orderNo={}, orderId={}", orderNo, order.getId());
                }
            });
            return RocketMQLocalTransactionState.COMMIT;
        } catch (Exception e) {
            log.error("本地事务执行失败: orderNo={}", orderNo, e);
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }

    /**
     * 回查本地事务状态
     */
    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        String orderNo = (String) msg.getHeaders().get("orderNo");
        if (orderNo == null) {
            log.warn("事务回查: 缺少 orderNo header");
            return RocketMQLocalTransactionState.UNKNOWN;
        }

        try {
            Order order = orderRepository.selectOne(
                    new LambdaQueryWrapper<Order>()
                            .eq(Order::getOrderNo, orderNo)
            );

            if (order == null) {
                log.info("事务回查: 订单不存在，返回 ROLLBACK. orderNo={}", orderNo);
                return RocketMQLocalTransactionState.ROLLBACK;
            }

            log.info("事务回查: 订单存在，返回 COMMIT. orderId={}, orderNo={}", order.getId(), orderNo);
            return RocketMQLocalTransactionState.COMMIT;
        } catch (Exception e) {
            log.error("事务回查异常: orderNo={}", orderNo, e);
            return RocketMQLocalTransactionState.UNKNOWN;
        }
    }
}
