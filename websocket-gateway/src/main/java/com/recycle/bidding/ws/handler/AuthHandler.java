package com.recycle.bidding.ws.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recycle.bidding.common.util.JwtUtil;
import com.recycle.bidding.ws.session.SessionManager;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.AttributeKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 鉴权处理器
 *
 * 商户首次发送 AUTH 消息时携带 JWT token，解析后验证商户身份。
 * 验证通过后将 merchantId 存入 Channel.attr()，注册到 SessionManager。
 */
@Slf4j
@Component
@ChannelHandler.Sharable
@RequiredArgsConstructor
public class AuthHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    public static final AttributeKey<Long> MERCHANT_ID_KEY = AttributeKey.valueOf("merchantId");

    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg) {
        String text = msg.text();
        Channel channel = ctx.channel();

        // 如果已经鉴权通过，直接传递给下一个 handler
        if (channel.attr(MERCHANT_ID_KEY).get() != null) {
            ctx.fireChannelRead(msg.retain());
            return;
        }

        try {
            JsonNode jsonNode = objectMapper.readTree(text);
            String type = jsonNode.has("type") ? jsonNode.get("type").asText() : "";

            // 只处理 AUTH 类型的消息
            if (!"AUTH".equalsIgnoreCase(type)) {
                log.warn("未鉴权的消息，关闭连接: remoteAddr={}", channel.remoteAddress());
                channel.writeAndFlush(new TextWebSocketFrame(
                        "{\"type\":\"ERROR\",\"message\":\"请先发送AUTH消息进行鉴权\"}"));
                return;
            }

            String token = jsonNode.has("token") ? jsonNode.get("token").asText() : "";
            if (token.isEmpty()) {
                channel.writeAndFlush(new TextWebSocketFrame(
                        "{\"type\":\"ERROR\",\"message\":\"token不能为空\"}"));
                return;
            }

            // JWT 验证：解析 token，提取 userId（商户ID）+ role
            Long merchantId = JwtUtil.getUserId(token);
            String role = JwtUtil.getRole(token);

            // 仅允许商户角色连接 WS
            if (!"MERCHANT".equals(role)) {
                log.warn("非商户角色试图连接WS: role={}, userId={}", role, merchantId);
                channel.writeAndFlush(new TextWebSocketFrame(
                        "{\"type\":\"ERROR\",\"message\":\"仅商户可建立WS连接\"}"));
                return;
            }

            // 鉴权通过后注册到 SessionManager
            channel.attr(MERCHANT_ID_KEY).set(merchantId);
            sessionManager.register(merchantId, channel);

            channel.writeAndFlush(new TextWebSocketFrame(
                    "{\"type\":\"AUTH_SUCCESS\",\"merchantId\":" + merchantId + "}"));

            log.info("商户鉴权成功: merchantId={}, remoteAddr={}", merchantId, channel.remoteAddress());

        } catch (Exception e) {
            log.error("鉴权消息解析失败: text={}", text, e);
            channel.writeAndFlush(new TextWebSocketFrame(
                    "{\"type\":\"ERROR\",\"message\":\"token无效或已过期\"}"));
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        Long merchantId = channel.attr(MERCHANT_ID_KEY).get();
        if (merchantId != null) {
            sessionManager.unregister(merchantId);
            log.info("商户连接断开: merchantId={}", merchantId);
        }
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("AuthHandler 异常", cause);
        ctx.close();
    }
}
