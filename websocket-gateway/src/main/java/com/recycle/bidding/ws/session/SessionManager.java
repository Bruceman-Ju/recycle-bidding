package com.recycle.bidding.ws.session;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 连接管理器（最核心组件）
 *
 * 管理 merchantId → Channel 的映射关系。
 * 提供注册、注销、发送消息等功能。
 */
@Slf4j
@Component
public class SessionManager {

    private final ConcurrentHashMap<Long, Channel> sessionMap = new ConcurrentHashMap<>();

    /**
     * 注册连接
     */
    public void register(Long merchantId, Channel channel) {
        Channel oldChannel = sessionMap.put(merchantId, channel);
        if (oldChannel != null && oldChannel.isActive()) {
            // 如果商户已有旧连接，关闭旧连接
            oldChannel.close();
            log.info("商户旧连接已关闭: merchantId={}", merchantId);
        }
        log.info("商户连接已注册: merchantId={}, remoteAddr={}", merchantId, channel.remoteAddress());
    }

    /**
     * 注销连接
     */
    public void unregister(Long merchantId) {
        Channel channel = sessionMap.remove(merchantId);
        if (channel != null) {
            log.info("商户连接已注销: merchantId={}", merchantId);
        }
    }

    /**
     * 获取商户的 Channel
     */
    public Channel getChannel(Long merchantId) {
        return sessionMap.get(merchantId);
    }

    /**
     * 向指定商户发送消息
     */
    public void sendToMerchant(Long merchantId, String message) {
        Channel channel = sessionMap.get(merchantId);
        if (channel != null && channel.isActive()) {
            channel.eventLoop().execute(() -> {
                channel.writeAndFlush(new TextWebSocketFrame(message))
                        .addListener(future -> {
                            if (!future.isSuccess()) {
                                log.error("发送消息给商户失败: merchantId={}", merchantId, future.cause());
                            }
                        });
            });
        } else {
            log.warn("商户不在线或连接已断开: merchantId={}", merchantId);
        }
    }

    /**
     * 广播消息给所有在线商户
     */
    public void broadcastToAll(String message) {
        for (Map.Entry<Long, Channel> entry : sessionMap.entrySet()) {
            Long merchantId = entry.getKey();
            Channel channel = entry.getValue();
            if (channel != null && channel.isActive()) {
                channel.eventLoop().execute(() -> {
                    channel.writeAndFlush(new TextWebSocketFrame(message))
                            .addListener(future -> {
                                if (!future.isSuccess()) {
                                    log.error("广播消息给商户失败: merchantId={}", merchantId, future.cause());
                                }
                            });
                });
            }
        }
    }
}
