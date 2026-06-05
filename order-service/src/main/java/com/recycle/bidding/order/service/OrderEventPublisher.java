package com.recycle.bidding.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recycle.bidding.common.constant.SystemConstants;
import com.recycle.bidding.common.util.TraceIdUtil;
import com.recycle.bidding.order.entity.Order;
import com.recycle.bidding.order.entity.OrderEventLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 订单事件 MQ 消息发布器
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final RocketMQTemplate rocketMQTemplate;

    private final ObjectMapper objectMapper;

    /**
     * 发布订单创建事件
     */
    public void publishOrderCreated(Order order) {
        if (order == null || order.getId() == null) {
            log.warn("发布订单创建事件失败: order 或 orderId 为空");
            return;
        }

        String traceId = TraceIdUtil.getTraceId();

        try {
            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("orderId", order.getId());
            payloadMap.put("orderNo", order.getOrderNo());
            payloadMap.put("userId", order.getUserId());
            payloadMap.put("phoneModelId", order.getPhoneModelId());
            payloadMap.put("initialEstimate", order.getInitialEstimate() != null ? order.getInitialEstimate().toPlainString() : null);
            payloadMap.put("status", order.getStatus());
            payloadMap.put("traceId", traceId);

            String payload = objectMapper.writeValueAsString(payloadMap);

            Message<String> message = MessageBuilder.withPayload(payload)
                    .setHeader("traceId", traceId)
                    .build();

            rocketMQTemplate.syncSend(
                    SystemConstants.TOPIC_ORDER + ":" + SystemConstants.TAG_ORDER_CREATED,
                    message
            );

            log.info("发送订单创建消息: orderId={}, tag={}", order.getId(), SystemConstants.TAG_ORDER_CREATED);

        } catch (Exception e) {
            log.error("发送订单创建消息失败: orderId={}", order.getId(), e);
        }
    }

    /**
     * 发布订单状态变更事件
     * <p>
     * 消息体直接传入 Map，避免手拼 JSON 字符串引入格式错误。
     */
    public void publishStatusChanged(Order order, OrderEventLog eventLog) {
        if (order == null || eventLog == null) {
            log.warn("发布状态变更事件失败: order 或 eventLog 为空");
            return;
        }

        String traceId = TraceIdUtil.getTraceId();

        try {
            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("orderId", order.getId());
            payloadMap.put("orderNo", order.getOrderNo());
            payloadMap.put("fromStatus", eventLog.getFromStatus());
            payloadMap.put("toStatus", eventLog.getToStatus());
            payloadMap.put("operator", eventLog.getOperator());
            payloadMap.put("remark", eventLog.getRemark());
            payloadMap.put("traceId", traceId);

            String payload = objectMapper.writeValueAsString(payloadMap);

            Message<String> message = MessageBuilder.withPayload(payload)
                    .setHeader("traceId", traceId)
                    .build();

            rocketMQTemplate.syncSend(
                    SystemConstants.TOPIC_ORDER + ":" + SystemConstants.TAG_ORDER_STATUS_CHANGED,
                    message
            );
            log.info("发送状态变更消息: orderId={}, from={}, to={}",
                    order.getId(), eventLog.getFromStatus(), eventLog.getToStatus());

        } catch (Exception e) {
            log.error("发送状态变更消息失败: orderId={}", order.getId(), e);
        }
    }
}
