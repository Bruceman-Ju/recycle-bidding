package com.recycle.bidding.recovery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.recycle.bidding.common.constant.AuctionConstants;
import com.recycle.bidding.common.constant.OrderStatus;
import com.recycle.bidding.recovery.entity.RecoveryOrder;
import com.recycle.bidding.recovery.repository.RecoveryOrderRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 竞拍补偿服务
 *
 * 职责：
 * 逐页扫描 orders 表中 status=AUCTIONING 的订单，
 * 检查竞拍开始时间是否超时（> 阈值），超时则标记 AUCTION_FAILED。
 *
 * 主路径由 RocketMQ 延迟消息触发 endAuction 终止竞拍，
 * 补偿服务是兜底：在 MQ 消息丢失、服务宕机等情况下，
 * 通过 DB 时间戳判断来清理卡住的竞拍。
 *
 * 补偿阈值 = 竞拍时长(180s) + 缓冲区(60s) = 240s
 * 大于 MQ 延迟消息的 180s，优先让 MQ 主路径处理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionRecoveryService {

    private final RecoveryOrderRepository recoveryOrderRepository;
    private final StringRedisTemplate redisTemplate;

    @Value("${recovery.scan.interval-ms:30000}")
    private int scanIntervalMs;

    @Value("${recovery.auction-timeout-threshold-seconds:240}")
    private int timeoutThresholdSeconds;

    @Value("${recovery.scan.page-size:200}")
    private int pageSize;

    @PostConstruct
    public void init() {
        log.info("竞拍补偿服务已启动, 扫描间隔={}ms, 超时阈值={}s, 每页={}条",
                scanIntervalMs, timeoutThresholdSeconds, pageSize);
    }

    /**
     * 定时扫描卡住的竞拍
     * 使用 fixedDelay，等上一轮处理完再开始下一轮，避免堆积
     */
    @Scheduled(fixedDelayString = "${recovery.scan.interval-ms:30000}")
    public void scanStuckAuctions() {
        log.debug("开始扫描卡住竞拍...");

        try {
            int pageNo = 1;
            int totalProcessed = 0;

            while (true) {
                Page<RecoveryOrder> page = recoveryOrderRepository.selectPage(
                        new Page<>(pageNo, pageSize),
                        new LambdaQueryWrapper<RecoveryOrder>()
                                .eq(RecoveryOrder::getStatus, OrderStatus.AUCTIONING)
                                .orderByAsc(RecoveryOrder::getId)
                );

                List<RecoveryOrder> orders = page.getRecords();
                if (orders.isEmpty()) break;

                for (RecoveryOrder order : orders) {
                    processOrder(order);
                    totalProcessed++;
                }

                if (!page.hasNext()) break;
                pageNo++;
            }

            if (totalProcessed > 0) {
                log.info("本次扫描完成，已处理 {} 个卡住订单", totalProcessed);
            }
        } catch (Exception e) {
            log.error("扫描卡住竞拍失败", e);
        }
    }

    /**
     * 处理单个卡住订单
     * 核心判断：竞拍开始时间(updatedAt) + 阈值 > 当前时间 → 超时 → 标记失败
     */
    private void processOrder(RecoveryOrder order) {
        // 使用 updatedAt 作为竞拍开始时间（startAuction 最后一次更新 order 的时间）
        LocalDateTime startTime = order.getUpdatedAt();
        if (startTime == null) {
            // 没有更新时间，跳过（理论上不应发生）
            log.warn("订单没有更新时间，跳过: orderId={}", order.getId());
            return;
        }

        long elapsedSeconds = ChronoUnit.SECONDS.between(startTime, LocalDateTime.now());
        if (elapsedSeconds < timeoutThresholdSeconds) {
            // 还未超时，跳过
            return;
        }

        log.warn("竞拍超时未结束: orderId={}, elapsed={}s, threshold={}s",
                order.getId(), elapsedSeconds, timeoutThresholdSeconds);

        // 清理 Redis 中可能残留的竞拍数据
        try {
            var keys = redisTemplate.keys("auction:{AUC*}:info");
            if (keys != null) {
                for (String key : keys) {
                    String storedOrderId = (String) redisTemplate.opsForHash().get(key, "orderId");
                    if (storedOrderId != null && storedOrderId.equals(String.valueOf(order.getId()))) {
                        // 从 "auction:{AUC123}:info" 中提取 "AUC123"
                        String auctionId = key.substring(key.indexOf('{') + 1, key.indexOf('}'));
                        redisTemplate.delete("auction:{" + auctionId + "}:bids");
                        redisTemplate.delete("auction:{" + auctionId + "}:info");
                        redisTemplate.delete("auction:{" + auctionId + "}:merchants");
                        log.info("已清理 Redis 竞拍数据: auctionId={}, orderId={}", auctionId, order.getId());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("清理 Redis 数据失败（不影响 DB 标记）: orderId={}", order.getId(), e);
        }

        // 标记订单为 AUCTION_FAILED（乐观锁，失败重试一次）
        try {
            LambdaUpdateWrapper<RecoveryOrder> updateWrapper = new LambdaUpdateWrapper<RecoveryOrder>()
                    .eq(RecoveryOrder::getId, order.getId())
                    .eq(RecoveryOrder::getVersion, order.getVersion())
                    .set(RecoveryOrder::getStatus, OrderStatus.AUCTION_FAILED)
                    .set(RecoveryOrder::getVersion, order.getVersion() + 1);
            boolean updated = recoveryOrderRepository.update(null, updateWrapper) > 0;

            if (updated) {
                log.warn("订单已标记 AUCTION_FAILED: orderId={}", order.getId());
            } else {
                // 乐观锁冲突，重试一次
                RecoveryOrder refreshed = recoveryOrderRepository.selectById(order.getId());
                if (refreshed != null && OrderStatus.AUCTIONING.equals(refreshed.getStatus())) {
                    LambdaUpdateWrapper<RecoveryOrder> retryWrapper = new LambdaUpdateWrapper<RecoveryOrder>()
                            .eq(RecoveryOrder::getId, order.getId())
                            .eq(RecoveryOrder::getVersion, refreshed.getVersion())
                            .set(RecoveryOrder::getStatus, OrderStatus.AUCTION_FAILED)
                            .set(RecoveryOrder::getVersion, refreshed.getVersion() + 1);
                    recoveryOrderRepository.update(null, retryWrapper);
                }
            }
        } catch (Exception e) {
            log.error("标记 AUCTION_FAILED 失败: orderId={}", order.getId(), e);
        }
    }
}
