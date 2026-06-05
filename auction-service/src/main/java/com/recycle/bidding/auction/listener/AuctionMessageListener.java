package com.recycle.bidding.auction.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recycle.bidding.auction.service.AuctionService;
import com.recycle.bidding.common.constant.AuctionConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 竞拍消息消费者
 *
 * 消费 order-topic 的 TAG_USER_CONFIRMED → 发起竞拍
 * 消费 auction-topic 的 TAG_AUCTION_TIMEOUT → 结束竞拍
 * 消费 auction-topic 的 TAG_NEW_BID → 更新竞拍状态（日志记录）
 */
@Slf4j
@Component
public class AuctionMessageListener {

    private final AuctionService auctionService;
    private final ObjectMapper objectMapper;

    public AuctionMessageListener(AuctionService auctionService, ObjectMapper objectMapper) {
        this.auctionService = auctionService;
        this.objectMapper = objectMapper;
    }

    /**
     * 消费 order-topic 的用户确认估价消息 → 发起竞拍
     */
    @Component
    @RequiredArgsConstructor
    @RocketMQMessageListener(
            topic = "order-topic",
            consumerGroup = "auction-order-consumer",
            selectorExpression = "USER_CONFIRMED || ORDER_USER_CONFIRMED"
    )
    public static class OrderConfirmedListener implements RocketMQListener<MessageExt> {

        private final AuctionService auctionService;
        private final ObjectMapper objectMapper;

        @Override
        public void onMessage(MessageExt message) {
            String body = new String(message.getBody());
            log.info("收到用户确认估价消息: {}", body);
            try {
                JsonNode jsonNode = objectMapper.readTree(body);
                Long orderId = jsonNode.has("orderId") ? jsonNode.get("orderId").asLong() : null;
                BigDecimal basePrice = jsonNode.has("initialEstimate")
                        ? new BigDecimal(jsonNode.get("initialEstimate").asText())
                        : BigDecimal.ZERO;

                if (orderId != null) {
                    auctionService.startAuction(orderId, basePrice);
                }
            } catch (Exception e) {
                log.error("处理用户确认消息失败: body={}", body, e);
            }
        }
    }

    /**
     * 消费 auction-topic 的竞拍超时消息 → 结束竞拍
     */
    @Component
    @RequiredArgsConstructor
    @RocketMQMessageListener(
            topic = "auction-topic",
            consumerGroup = "auction-timeout-consumer",
            selectorExpression = "AUCTION_TIMEOUT"
    )
    public static class AuctionTimeoutListener implements RocketMQListener<MessageExt> {

        private final AuctionService auctionService;
        private final ObjectMapper objectMapper;

        @Override
        public void onMessage(MessageExt message) {
            String body = new String(message.getBody());
            log.info("收到竞拍超时消息: {}", body);
            try {
                JsonNode jsonNode = objectMapper.readTree(body);
                String auctionId = jsonNode.get("auctionId").asText();
                auctionService.endAuction(auctionId);
            } catch (Exception e) {
                log.error("处理竞拍超时消息失败: body={}", body, e);
            }
        }
    }

    /**
     * 消费 auction-topic 的新出价消息 → 执行出价
     *
     * WebSocket 网关将商户出价转发到 MQ，此监听器消费后调用 placeBid 执行。
     */
    @Component
    @RequiredArgsConstructor
    @RocketMQMessageListener(
            topic = "auction-topic",
            consumerGroup = "auction-bid-consumer",
            selectorExpression = "NEW_BID || AUCTION_NEW_BID"
    )
    public static class NewBidListener implements RocketMQListener<MessageExt> {

        private final AuctionService auctionService;
        private final ObjectMapper objectMapper;

        @Override
        public void onMessage(MessageExt message) {
            String body = new String(message.getBody());
            log.debug("收到新出价消息: body={}", body);
            try {
                JsonNode jsonNode = objectMapper.readTree(body);
                String auctionId = jsonNode.get("auctionId").asText();
                Long merchantId = jsonNode.get("merchantId").asLong();
                BigDecimal bidPrice = new BigDecimal(jsonNode.get("bidPrice").asText());
                Long orderId = jsonNode.has("orderId") ? jsonNode.get("orderId").asLong() : 0L;

                // 执行出价（复用 placeBid 的全部校验逻辑：Lua 原子出价 + 次数检查 + 金额校验）
                auctionService.placeBid(auctionId, merchantId, orderId, bidPrice);

                log.info("WebSocket 出价成功: auctionId={}, merchantId={}, bidPrice={}", auctionId, merchantId, bidPrice);
            } catch (Exception e) {
                log.warn("WebSocket 出价失败（预期内，如超次数/金额过低/竞拍已结束）: body={}, error={}", body, e.getMessage());
            }
        }
    }
}
