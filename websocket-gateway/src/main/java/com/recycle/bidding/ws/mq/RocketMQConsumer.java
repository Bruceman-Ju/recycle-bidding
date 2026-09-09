package com.recycle.bidding.ws.mq;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.recycle.bidding.ws.session.SessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 竞拍开始通知消费者（广播模式）
 *
 * 职责：消费 AUCTION_STARTED 消息，通过 WebSocket 推送给所有在线商户。
 * 使用 BROADCASTING 模式确保每个 ws-gateway 实例都收到并推送本地商户。
 *
 * 注意：
 *  - ws-gateway 不再处理出价、竞拍结束等消息——这些业务已交由 HTTP API 处理
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "auction-topic",
        consumerGroup = "ws-gateway-broadcast",
        selectorExpression = "AUCTION_STARTED",
        messageModel = MessageModel.BROADCASTING,
        consumeMode = ConsumeMode.CONCURRENTLY
)
public class RocketMQConsumer implements RocketMQListener<String> {

    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String message) {
        try {
            JsonNode jsonNode = objectMapper.readTree(message);
            String type = jsonNode.has("type") ? jsonNode.get("type").asText() : "";

            if ("AUCTION_STARTED".equals(type)) {
                handleAuctionStarted(jsonNode);
            } else {
                log.warn("不处理的消息类型: type={}", type);
            }
        } catch (Exception e) {
            log.error("消费竞拍消息失败: message={}", message, e);
        }
    }

    /**
     * 竞拍开始：广播给所有在线商户。
     * 只透传必要字段（auctionId/orderId/basePrice）
     * 客户端以 GET /api/v1/auction/active 为全量权威，WS 仅作实时增量加速。
     */
    private void handleAuctionStarted(JsonNode jsonNode) {
        String auctionId = jsonNode.path("auctionId").asText("");
        long orderId = jsonNode.path("orderId").asLong(0L);
        String basePrice = jsonNode.path("basePrice").asText("0");
        log.info("竞拍开始广播: auctionId={}", auctionId);

        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("type", "AUCTION_STARTED");
        summary.put("auctionId", auctionId);
        summary.put("orderId", orderId);
        summary.put("basePrice", new BigDecimal(basePrice));
        sessionManager.broadcastToAll(summary.toString());
    }
}
