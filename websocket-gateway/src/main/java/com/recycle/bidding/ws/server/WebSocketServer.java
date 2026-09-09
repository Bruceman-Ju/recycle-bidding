package com.recycle.bidding.ws.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Netty WebSocket 服务器启动器
 *
 * macOS 下使用 NioEventLoopGroup（Epoll 在 macOS 不可用）。
 * 启动后绑定端口 8086，接受 WebSocket 连接。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketServer implements CommandLineRunner {

    private final WebSocketChannelInitializer channelInitializer;

    @Value("${netty.websocket.port:8090}")
    private int nettyPort;

    @Override
    public void run(String... args) throws Exception {
        // macOS 下使用 NioEventLoopGroup
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors() * 2);

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            // 主从 Reactor 线程组：bossGroup 只负责 accept 新连接，workerGroup 负责已建立连接的 IO 读写
            bootstrap.group(bossGroup, workerGroup)
                    // 服务端使用 NIO 模式（macOS 下用 Nio 而非 Epoll，见上方 EventLoopGroup 选择）
                    .channel(NioServerSocketChannel.class)
                    // 服务端 accept 队列长度：应对瞬时连接风暴，避免握手请求被直接丢弃
                    .option(ChannelOption.SO_BACKLOG, 128)
                    // 子 Channel（客户端连接）启用 TCP 保活探测，及时关闭对端已崩溃的死连接
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    // WS 推送多为小帧，立即发送而非凑批，降低推送延迟
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    // 每个新连接绑定初始化器，装配 HTTP/WS 编解码器与业务 Handler（Auth/Heartbeat）
                    .childHandler(channelInitializer);

            int port = nettyPort;
            bootstrap.bind(port).sync();

            log.info("Netty WebSocket 服务器启动成功，监听端口: {} (Eureka 管理端口: {})", port, 8086);

            // 保持进程运行
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            log.error("Netty WebSocket 服务器启动失败", e);
            Thread.currentThread().interrupt();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
