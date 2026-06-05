package com.recycle.bidding.auction.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recycle.bidding.auction.entity.AuctionRecord;
import com.recycle.bidding.auction.repository.AuctionRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 出价记录异步落盘消费者
 *
 * 当 Nacos 配置 bid.db.write.mode=async 时，
 * placeBid() 将 auction_record 写入改为 MQ 异步方式，
 * 此消费者消费消息后进行实际的 MySQL 插入。
 *
 * 分离同步/异步路径的原因：
 * - 同步写：保证实时一致性，适合低流量
 * - 异步写：削峰填谷，适合高流量场景（出价并发高时）
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "bid-db-sync-topic",
        consumerGroup = "auction-bid-db-consumer",
        selectorExpression = "SYNC_BID_RECORD"
)
public class BidDbSyncListener implements RocketMQListener<String> {

    private final AuctionRecordRepository auctionRecordRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String message) {
        log.debug("收到异步落盘消息: {}", message);

        try {
            JsonNode json = objectMapper.readTree(message);

            AuctionRecord record = AuctionRecord.builder()
                    .orderId(json.get("orderId").asLong())
                    .auctionId(json.get("auctionId").asText())
                    .merchantId(json.get("merchantId").asLong())
                    .bidPrice(new BigDecimal(json.get("bidPrice").asText()))
                    .bidTime(LocalDateTime.now())
                    .isWinner(0)
                    .source("lua_async")
                    .build();

            auctionRecordRepository.insert(record);

            log.debug("异步出价记录已落盘: auctionId={}, merchantId={}, bidPrice={}",
                    record.getAuctionId(), record.getMerchantId(), record.getBidPrice());
        } catch (Exception e) {
            log.error("异步出价记录落盘失败: message={}", message, e);
            // 不抛异常，避免 RocketMQ 重试刷日志
            // 后续由补偿机制兜底：endAuction 时会重新从 Redis 落盘
        }
    }
}
