package com.recycle.bidding.order.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recycle.bidding.common.constant.OrderStatus;
import com.recycle.bidding.common.constant.RocketMQConstants;
import com.recycle.bidding.common.constant.SystemConstants;
import com.recycle.bidding.order.entity.Order;
import com.recycle.bidding.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 消费竞拍结束事件（定义在另一个 consumer 中）
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQConstants.TOPIC_AUCTION,
        consumerGroup = "order-auction-consumer-group",
        selectorExpression = RocketMQConstants.TAG_AUCTION_ENDED
)
public class AuctionEndedListener implements RocketMQListener<MessageExt> {

    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(MessageExt message) {
        String body = new String(message.getBody());
        log.info("收到竞拍结束消息: body={}", body);

        try {
            JsonNode jsonNode = objectMapper.readTree(body);
            Long orderId = jsonNode.get("orderId").asLong();
            Long winnerMerchantId = jsonNode.get("winnerMerchantId").asLong();
            BigDecimal finalPrice = new BigDecimal(jsonNode.get("finalPrice").asText());

            Order order = orderRepository.selectById(orderId);
            if (order == null) {
                log.warn("订单不存在: orderId={}", orderId);
                return;
            }

            if (OrderStatus.AUCTION_ENDED.equals(order.getStatus())) {
                log.info("订单已是竞拍结束状态，跳过: orderId={}", orderId);
                return;
            }

            order.setStatus(OrderStatus.AUCTION_ENDED);
            order.setWinnerMerchantId(winnerMerchantId);
            order.setFinalPrice(finalPrice);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.updateById(order);

            log.info("竞拍结束订单更新成功: orderId={}, winner={}, price={}",
                    orderId, winnerMerchantId, finalPrice);

        } catch (Exception e) {
            log.error("处理竞拍结束消息失败: body={}", body, e);
        }
    }
}