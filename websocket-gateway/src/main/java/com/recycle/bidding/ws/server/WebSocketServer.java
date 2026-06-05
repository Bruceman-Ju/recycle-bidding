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
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
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
