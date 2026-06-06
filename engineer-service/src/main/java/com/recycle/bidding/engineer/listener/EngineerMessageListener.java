package com.recycle.bidding.engineer.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recycle.bidding.engineer.entity.EngineerTask;
import com.recycle.bidding.engineer.service.EngineerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 工程师服务 MQ 监听器
 * <p>
 * ORDER_CREATED → 分配空闲工程师并创建工程师任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "order-topic",
        consumerGroup = "engineer-consumer",
        selectorExpression = "ORDER_CREATED"
)
public class EngineerMessageListener implements RocketMQListener<String> {

    private final ObjectMapper objectMapper;
    private final EngineerService engineerService;

    @Override
    public void onMessage(String payload) {
        log.info("工程师服务收到订单消息: {}", payload);

        try {
            JsonNode jsonNode = objectMapper.readTree(payload);

            // ORDER_CREATED：分配工程师并创建任务
            if (jsonNode.has("orderId")) {
                Long orderId = jsonNode.get("orderId").asLong();

                EngineerTask task = engineerService.assignEngineer(orderId);
                log.info("订单 {} 已分配工程师，任务ID: {}", orderId, task.getId());
            }

        } catch (Exception e) {
            log.error("处理订单消息失败: payload={}", payload, e);
        }
    }
}
