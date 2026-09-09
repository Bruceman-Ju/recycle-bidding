package com.recycle.bidding.ws.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 心跳检测处理器
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class HeartbeatHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    /** 读空闲阈值（秒），与 WebSocketChannelInitializer 中 IdleStateHandler 配置保持一致 */
    public static final int IDLE_TIMEOUT_SECONDS = 60;

    private static final AttributeKey<Long> LAST_PING_KEY = AttributeKey.valueOf("lastPingTime");
    private static final AttributeKey<Long> LAST_PONG_KEY = AttributeKey.valueOf("lastPongTime");

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg) {
        String text = msg.text();

        // 收到心跳响应：记录时间，用于判断后续 PING 是否得到回应
        if (text.contains("\"type\":\"PONG\"") || text.contains("\"type\":\"pong\"")) {
            ctx.channel().attr(LAST_PONG_KEY).set(System.currentTimeMillis());
            return;
        }

        // 兼容客户端主动发来的 PING，回应 PONG
        if (text.contains("\"type\":\"PING\"") || text.contains("\"type\":\"ping\"")) {
            ctx.channel().writeAndFlush(new TextWebSocketFrame(
                    "{\"type\":\"PONG\",\"timestamp\":" + System.currentTimeMillis() + "}"));
            return;
        }

        // 其他消息传递到下一个 handler
        ctx.fireChannelRead(msg.retain());
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                Channel channel = ctx.channel();
                Long lastPing = channel.attr(LAST_PING_KEY).get();
                Long lastPong = channel.attr(LAST_PONG_KEY).get();

                // 上一次发的 PING 至今没收到 PONG → 连接已死，关闭（清理僵尸连接）
                if (lastPing != null && (lastPong == null || lastPong < lastPing)) {
                    log.warn("心跳超时(已发PING未收PONG)，关闭连接: remoteAddr={}", channel.remoteAddress());
                    channel.close();
                    return;
                }

                // 否则发 PING 探活，并记录发送时间
                channel.attr(LAST_PING_KEY).set(System.currentTimeMillis());
                channel.writeAndFlush(new TextWebSocketFrame(
                        "{\"type\":\"PING\",\"timestamp\":" + System.currentTimeMillis() + "}"));
            }
        } else {
            ctx.fireUserEventTriggered(evt);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("HeartbeatHandler 异常", cause);
        ctx.close();
    }
}
