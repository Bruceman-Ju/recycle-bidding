package com.recycle.bidding.ws.server;

import com.recycle.bidding.ws.handler.AuthHandler;
import com.recycle.bidding.ws.handler.HeartbeatHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * WebSocket Channel 初始化器
 *
 * Pipeline 处理器顺序（ws-gateway 仅负责推送竞拍通知，不处理出价等业务）：
 * 1. HttpServerCodec — HTTP 编解码（握手阶段）
 * 2. HttpObjectAggregator — HTTP 聚合
 * 3. WebSocketServerProtocolHandler — WS 协议升级
 * 4. IdleStateHandler — 60s 读空闲检测
 * 5. AuthHandler — 鉴权
 * 6. HeartbeatHandler — 心跳
 *
 * 出价已改为 HTTP API 方式，不再经过 WebSocket 通道。
 */
@Component
@RequiredArgsConstructor
public class WebSocketChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final AuthHandler authHandler;
    private final HeartbeatHandler heartbeatHandler;

    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline()
                .addLast(new HttpServerCodec())
                .addLast(new HttpObjectAggregator(65536))
                .addLast(new WebSocketServerProtocolHandler("/ws"))
                .addLast(new IdleStateHandler(HeartbeatHandler.IDLE_TIMEOUT_SECONDS, 0, 0))
                .addLast(authHandler)
                .addLast(heartbeatHandler);
    }
}
