package com.recycle.bidding.engineer.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recycle.bidding.common.exception.BizException;
import com.recycle.bidding.common.exception.ErrorCode;
import com.recycle.bidding.engineer.entity.EngineerTask;
import com.recycle.bidding.engineer.service.EngineerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 工程师服务 MQ 监听器
 * ORDER_CREATED → 分配空闲工程师并创建工程师任务
 *
 * 消费可靠性约定：
 * 1. onMessage 返回 void 且未声明 throws，因此任何异常必须以「未受检异常」(RuntimeException) 形式抛出，
 *    由 Spring RocketMQ 容器捕获后向 broker 返回 RECONSUME_LATER，从而触发重试；
 *    绝不能 catch 后只打日志就正常返回，否则 broker 视为消费成功，消息静默丢失。
 * 2. 非法消息(无法解析 / 缺失 orderId)同样抛异常，进入重试→死信队列，而非无声丢弃。
 * 3. maxReconsumeTimes 显式声明重试上限，耗尽后进 %DLQ% 死信队列，需监控 + 补偿程序/人工重放。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "order-topic",
        consumerGroup = "engineer-consumer",
        selectorExpression = "ORDER_CREATED",
        maxReconsumeTimes = 16
)
public class EngineerMessageListener implements RocketMQListener<String> {

    private final ObjectMapper objectMapper;
    private final EngineerService engineerService;

    @Override
    public void onMessage(String payload) {
        log.info("工程师服务收到订单消息: {}", payload);

        // 1) 解析消息体：解析失败属于「毒消息」，抛异常交由重试/死信队列处理，不静默吞掉
        JsonNode jsonNode;
        try {
            jsonNode = objectMapper.readTree(payload);
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(),
                    "消息体解析失败(毒消息): payload=" + payload);
        }

        // 2) 参数校验：缺失 / 非法 orderId 属于结构性错误，直接抛异常进死信，避免无意义重试风暴
        if (!jsonNode.has("orderId") || jsonNode.get("orderId").isNull()) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(),
                    "消息缺失 orderId 字段: payload=" + payload);
        }
        Long orderId = jsonNode.get("orderId").asLong();
        if (orderId <= 0) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "orderId 非法: " + orderId);
        }

        // 3) 业务处理：assignEngineer 内部抛出的异常(如暂无空闲工程师、DB 异常)向上透传，
        //    触发 RocketMQ 重试。engineer_task 已建唯一索引 idx_order_id，重试天然幂等。
        EngineerTask task = engineerService.assignEngineer(orderId);
        log.info("订单 {} 已分配工程师，任务ID: {}", orderId, task.getId());
    }
}
