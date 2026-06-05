package com.recycle.bidding.ws.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recycle.bidding.ws.mq.RocketMQProducer;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 出价请求处理器
 *
 * 解析 TextWebSocketFrame 为 JSON，
 * 将出价消息发送到 RocketMQ auction-topic (TAG_NEW_BID)。
 */
@Slf4j
@Component
@ChannelHandler.Sharable
@RequiredArgsConstructor
public class BidRequestHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private final ObjectMapper objectMapper;
    private final RocketMQProducer rocketMQProducer;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg) {
        String text = msg.text();

        try {
            JsonNode jsonNode = objectMapper.readTree(text);
            String type = jsonNode.has("type") ? jsonNode.get("type").asText() : "";

            // 只处理 BID 类型的消息
            if (!"BID".equalsIgnoreCase(type)) {
                ctx.fireChannelRead(msg.retain());
                return;
            }

            String auctionId = jsonNode.has("auctionId") ? jsonNode.get("auctionId").asText() : null;
            Long merchantId = jsonNode.has("merchantId") ? jsonNode.get("merchantId").asLong() : null;
            String bidPrice = jsonNode.has("bidPrice") ? jsonNode.get("bidPrice").asText() : null;
            Long orderId = jsonNode.has("orderId") ? jsonNode.get("orderId").asLong() : null;

            if (auctionId == null || merchantId == null || bidPrice == null) {
                ctx.channel().writeAndFlush(new TextWebSocketFrame(
                        "{\"type\":\"ERROR\",\"message\":\"出价参数缺失(auctionId/merchantId/bidPrice)\"}"));
                return;
            }

            // 发送出价消息到 RocketMQ（auction-service 消费后执行出价）
            rocketMQProducer.sendBidMessage(auctionId, merchantId, bidPrice, orderId);

            // 响应商户
            ctx.channel().writeAndFlush(new TextWebSocketFrame(
                    "{\"type\":\"BID_ACK\",\"auctionId\":\"" + auctionId
                    + "\",\"merchantId\":" + merchantId
                    + ",\"bidPrice\":" + bidPrice
                    + ",\"message\":\"出价已提交\"}"));

            log.info("商户出价已转发到MQ: auctionId={}, merchantId={}, bidPrice={}",
                    auctionId, merchantId, bidPrice);

        } catch (Exception e) {
            log.error("出价消息解析失败: text={}", text, e);
            ctx.channel().writeAndFlush(new TextWebSocketFrame(
                    "{\"type\":\"ERROR\",\"message\":\"出价消息格式错误\"}"));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("BidRequestHandler 异常", cause);
        ctx.close();
    }
}
