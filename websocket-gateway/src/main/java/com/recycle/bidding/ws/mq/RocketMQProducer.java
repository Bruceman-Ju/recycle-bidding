package com.recycle.bidding.ws.mq;

import com.recycle.bidding.common.constant.AuctionConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

/**
 * WebSocket Gateway MQ 消息发送器
 *
 * 接收来自商户的出价消息，发送到 auction-topic。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RocketMQProducer {

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 发送出价消息到 auction-topic
     */
    public void sendBidMessage(String auctionId, Long merchantId, String bidPrice, Long orderId) {
        String payload = String.format(
                "{\"auctionId\":\"%s\",\"merchantId\":%d,\"bidPrice\":%s,\"orderId\":%d,\"type\":\"%s\"}",
                auctionId, merchantId, bidPrice, orderId, AuctionConstants.TAG_NEW_BID
        );

        rocketMQTemplate.syncSend(
                AuctionConstants.TOPIC_AUCTION + ":" + AuctionConstants.TAG_NEW_BID,
                payload
        );

        log.info("WS转发出价消息到MQ: auctionId={}, merchantId={}, bidPrice={}",
                auctionId, merchantId, bidPrice);
    }

    /**
     * 发送竞拍状态变化消息
     */
    public void sendAuctionMessage(String topic, String tag, String payload) {
        rocketMQTemplate.syncSend(topic + ":" + tag, payload);
        log.debug("发送竞拍消息: topic={}, tag={}", topic, tag);
    }
}
