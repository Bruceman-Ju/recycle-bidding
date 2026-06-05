package com.recycle.bidding.ws.pubsub;

import com.recycle.bidding.ws.session.SessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * Redis PubSub 跨实例广播监听器
 *
 * 用于多实例部署时，一个实例收到的竞拍消息同步广播给其他实例。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisPubSubListener implements MessageListener, InitializingBean {

    private static final String REDIS_CHANNEL = "auction:broadcast";

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisMessageListenerContainer redisMessageListenerContainer;
    private final SessionManager sessionManager;

    @Override
    public void afterPropertiesSet() {
        redisMessageListenerContainer.addMessageListener(this, new ChannelTopic(REDIS_CHANNEL));
        log.info("Redis PubSub 监听器已订阅频道: {}", REDIS_CHANNEL);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody());
        log.debug("收到Redis广播消息: {}", body);

        try {
            // 广播消息格式：merchantId:message 或 ALL:message
            int colonIndex = body.indexOf(':');
            if (colonIndex <= 0) {
                log.warn("Redis广播消息格式错误: {}", body);
                return;
            }

            String target = body.substring(0, colonIndex);
            String payload = body.substring(colonIndex + 1);

            if ("ALL".equals(target)) {
                // 广播给所有在线商户
                sessionManager.broadcastToAll(payload);
            } else {
                // 发送给指定商户
                Long merchantId = Long.parseLong(target);
                sessionManager.sendToMerchant(merchantId, payload);
            }

        } catch (Exception e) {
            log.error("处理Redis广播消息失败: body={}", body, e);
        }
    }

    /**
     * 发布消息到 Redis 频道（供其他实例使用）
     *
     * @param target   目标 merchantId 或 "ALL"
     * @param payload  消息内容
     */
    public void publish(String target, String payload) {
        String message = target + ":" + payload;
        redisTemplate.convertAndSend(REDIS_CHANNEL, message);
        log.debug("发布Redis广播消息: target={}", target);
    }
}
