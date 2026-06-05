package com.recycle.bidding.ws.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recycle.bidding.common.constant.AuctionConstants;
import com.recycle.bidding.ws.pubsub.RedisPubSubListener;
import com.recycle.bidding.ws.session.AuctionSessionManager;
import com.recycle.bidding.ws.session.SessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MQ 消息消费 + WebSocket 推送 + Redis PubSub 跨实例广播
 *
 * 每个 WS 网关实例消费 MQ 消息后：
 *   1. 推送给本地在线商户（SessionManager）
 *   2. 再通过 Redis PubSub 广播给其他实例（跨实例）
 *
 * 参与商户列表从 Redis SET auction:merchants:{auctionId} 读取，
 * 不依赖实例本地内存（AuctionSessionManager 为兜底）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = AuctionConstants.TOPIC_AUCTION,
        consumerGroup = "ws-gateway-consumer",
        selectorExpression = "AUCTION_STARTED || AUCTION_NEW_BID || AUCTION_ENDED || AUCTION_TIMEOUT"
)
public class RocketMQConsumer implements RocketMQListener<String> {

    private final SessionManager sessionManager;
    private final AuctionSessionManager auctionSessionManager;
    private final ObjectMapper objectMapper;
    private final RedisPubSubListener redisPubSubListener;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void onMessage(String message) {
        log.debug("WS-Gateway 收到MQ消息: {}", message);

        try {
            JsonNode jsonNode = objectMapper.readTree(message);
            String type = jsonNode.has("type") ? jsonNode.get("type").asText() : "";
            String auctionId = jsonNode.has("auctionId") ? jsonNode.get("auctionId").asText() : "";

            switch (type) {
                case "AUCTION_STARTED":
                    handleAuctionStarted(auctionId, jsonNode);
                    break;
                case "AUCTION_NEW_BID":
                    handleNewBid(auctionId, jsonNode);
                    break;
                case "AUCTION_ENDED":
                    handleAuctionEnded(auctionId, jsonNode);
                    break;
                case "AUCTION_TIMEOUT":
                    handleAuctionTimeout(auctionId, jsonNode);
                    break;
                default:
                    log.warn("未知的竞拍消息类型: type={}", type);
            }
        } catch (Exception e) {
            log.error("处理竞拍消息失败: message={}", message, e);
        }
    }

    /**
     * 获取参与竞拍的商户列表
     *
     * 优先从 Redis SET 读取（共享存储，各实例一致），
     * 再合并本地 AuctionSessionManager（兜底）。
     */
    private Set<Long> getParticipatingMerchants(String auctionId) {
        Set<Long> merchants = readRedisMerchants(auctionId);

        // 合并本地 AuctionSessionManager 中的商户
        Set<Long> localMerchants = auctionSessionManager.getMerchants(auctionId);
        if (!localMerchants.isEmpty()) {
            merchants.addAll(localMerchants);
        }
        return merchants;
    }

    /**
     * 从 Redis 读取参与商户列表
     */
    private Set<Long> readRedisMerchants(String auctionId) {
        String key = AuctionConstants.REDIS_KEY_PREFIX_MERCHANTS + auctionId;
        Set<String> members = redisTemplate.keys(key);
        if (members.isEmpty()) {
            return Collections.emptySet();
        }
        return members.stream().map(Long::parseLong).collect(Collectors.toSet());
    }

    /**
     * 处理竞拍开始事件 → 广播给所有在线商户 + Redis PubSub 跨实例广播
     */
    private void handleAuctionStarted(String auctionId, JsonNode jsonNode) {
        log.info("竞拍开始广播: auctionId={}", auctionId);
        String payload = jsonNode.toString();

        // 1. 推给本地在线商户
        sessionManager.broadcastToAll(payload);

        // 2. Redis PubSub 跨实例广播
        redisPubSubListener.publish("ALL", payload);
    }

    /**
     * 处理新出价事件 → 推送给参与商户 + Redis PubSub
     */
    private void handleNewBid(String auctionId, JsonNode jsonNode) {
        Set<Long> merchants = getParticipatingMerchants(auctionId);
        if (merchants.isEmpty()) {
            log.debug("竞拍没有参与商户，跳过: auctionId={}", auctionId);
            return;
        }

        String payload = jsonNode.toString();
        log.debug("新出价推送: auctionId={}, merchants={}", auctionId, merchants);

        // 1. 推给本地匹配的商户
        for (Long merchantId : merchants) {
            sessionManager.sendToMerchant(merchantId, payload);
        }

        // 2. Redis PubSub 跨实例广播（其他实例推给自己连接的商户）
        redisPubSubListener.publish("ALL", payload);
    }

    /**
     * 处理竞拍结束事件 → 推送给参与商户 + Redis PubSub
     */
    private void handleAuctionEnded(String auctionId, JsonNode jsonNode) {
        Set<Long> merchants = getParticipatingMerchants(auctionId);
        log.info("竞拍结束广播: auctionId={}, merchants={}", auctionId, merchants);

        String payload = jsonNode.toString();

        // 1. 推给本地匹配的商户
        for (Long merchantId : merchants) {
            sessionManager.sendToMerchant(merchantId, payload);
        }

        // 2. Redis PubSub 跨实例广播
        redisPubSubListener.publish("ALL", payload);

        // 3. 清理本地竞拍房间
        auctionSessionManager.clearAuction(auctionId);
    }

    /**
     * 处理竞拍超时事件 → 推送给参与商户 + Redis PubSub
     */
    private void handleAuctionTimeout(String auctionId, JsonNode jsonNode) {
        Set<Long> merchants = getParticipatingMerchants(auctionId);
        String payload = jsonNode.toString();

        // 1. 推给本地匹配的商户
        for (Long merchantId : merchants) {
            sessionManager.sendToMerchant(merchantId, payload);
        }

        // 2. Redis PubSub 跨实例广播
        redisPubSubListener.publish("ALL", payload);
    }
}
