package com.recycle.bidding.ws.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 *  - 消费者能力在这个业务量级下远未到瓶颈（日峰值约几十条/秒）
 *  - 客户端有 30 秒轮询兜底，MQ 短暂不可用时商户可自行拉取
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
     * 竞拍开始：广播给本实例所有在线商户
     */
    private void handleAuctionStarted(JsonNode jsonNode) {
        String auctionId = jsonNode.has("auctionId") ? jsonNode.get("auctionId").asText() : "";
        log.info("竞拍开始广播: auctionId={}", auctionId);

        String payload = jsonNode.toString();
        sessionManager.broadcastToAll(payload);
    }
}
