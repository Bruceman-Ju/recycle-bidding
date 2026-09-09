package com.recycle.bidding.ws.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 心跳检测处理器
 * 超时未收到消息则关闭连接。
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class HeartbeatHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private static final long MAX_IDLE_MS = 60_000; // 60秒超时

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg) {
        String text = msg.text();

        // 处理 PONG 响应
        if (text.contains("\"type\":\"PONG\"") || text.contains("\"type\":\"pong\"")) {
            log.debug("收到心跳响应: remoteAddr={}", ctx.channel().remoteAddress());
            return;
        }

        // 处理 PING（兼容其他客户端主动心跳）
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
                // 读空闲超过60秒，发送 ping
                log.debug("发送心跳ping: remoteAddr={}", ctx.channel().remoteAddress());
                ctx.channel().writeAndFlush(new TextWebSocketFrame(
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
