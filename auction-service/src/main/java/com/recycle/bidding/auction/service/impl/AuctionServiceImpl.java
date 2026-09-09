package com.recycle.bidding.auction.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recycle.bidding.auction.config.NacosConfigManager;
import com.recycle.bidding.auction.entity.AuctionOrder;
import com.recycle.bidding.auction.entity.AuctionRecord;
import com.recycle.bidding.auction.repository.AuctionOrderRepository;
import com.recycle.bidding.auction.repository.AuctionRecordRepository;
import com.recycle.bidding.auction.repository.AuctionRedisRepository;
import com.recycle.bidding.auction.service.AuctionBidService;
import com.recycle.bidding.auction.service.AuctionSettlementService;
import com.recycle.bidding.auction.service.AuctionService;
import com.recycle.bidding.common.constant.AuctionConstants;
import com.recycle.bidding.common.constant.OrderStatus;
import com.recycle.bidding.common.constant.RocketMQConstants;
import com.recycle.bidding.common.constant.SystemConstants;
import com.recycle.bidding.common.exception.BizException;
import com.recycle.bidding.common.exception.ErrorCode;
import com.recycle.bidding.common.util.SnowflakeIdGenerator;
import com.recycle.bidding.common.util.TraceIdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionServiceImpl implements AuctionService {

    private final AuctionRedisRepository auctionRedisRepository;
    private final AuctionRecordRepository auctionRecordRepository;
    private final AuctionBidService auctionBidService;
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;
    private final AuctionOrderRepository auctionOrderRepository;
    private final NacosConfigManager nacosConfigManager;
    private final AuctionSettlementService auctionSettlementService;

    @Override
    public Map<String, Object> startAuction(Long orderId, BigDecimal basePrice) {
        // 判断订单是否存在
        AuctionOrder order = auctionOrderRepository.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "订单不存在: " + orderId);
        }

        // 判断订单状态是否可竞拍
        String currentStatus = order.getStatus();
        if (!OrderStatus.USER_CONFIRMED.equals(currentStatus)
                && !OrderStatus.AUCTION_FAILED.equals(currentStatus)) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID.getCode(),
                    "当前订单状态不允许发起竞拍: " + currentStatus);
        }

        String auctionId = "AUC" + SnowflakeIdGenerator.nextIdStr();

        // 1. 初始化 Redis（TTL = 竞拍时长 + 缓冲区，防止 endAuction 读取时过期）
        // todo: 暂时两步 redis，未来修改成 lua 脚本。
        auctionRedisRepository.initAuction(auctionId, basePrice);
        auctionRedisRepository.setAuctionOrderId(auctionId, orderId);

        // 2. 写 DB 订单状态（权威数据源写入成功后竞拍才算正式开始）
        LambdaUpdateWrapper<AuctionOrder> updateWrapper = new LambdaUpdateWrapper<AuctionOrder>()
                .eq(AuctionOrder::getId, orderId)
                .eq(AuctionOrder::getVersion, order.getVersion())
                .set(AuctionOrder::getStatus, OrderStatus.AUCTIONING)
                .set(AuctionOrder::getVersion, order.getVersion() + 1);
        boolean updated = auctionOrderRepository.update(null, updateWrapper) > 0;
        if (!updated) {
            auctionRedisRepository.endAuction(auctionId);
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "订单状态更新失败，请重试");
        }

        // 3. 发送 RocketMQ 延迟消息（delayLevel=7 约 3 分钟），触发 endAuction
        String timeoutMsg = String.format(
                "{\"auctionId\":\"%s\",\"type\":\"%s\",\"traceId\":\"%s\"}",
                auctionId, AuctionConstants.MSG_TYPE_AUCTION_TIMEOUT, TraceIdUtil.getTraceId()
        );
        Message<String> timeoutMessage = MessageBuilder.withPayload(timeoutMsg).build();
        rocketMQTemplate.syncSend(
                AuctionConstants.TOPIC_AUCTION + ":" + AuctionConstants.TAG_AUCTION_TIMEOUT,
                timeoutMessage,
                3000L,
                SystemConstants.AUCTION_TIMEOUT_DELAY_LEVEL
        );

        // 4. 发送 AUCTION_STARTED 消息到 MQ（广播消费，通知所有在线商户）
        try {
            Map<String, Object> msgBody = new HashMap<>();
            msgBody.put("auctionId", auctionId);
            msgBody.put("orderId", orderId);
            msgBody.put("basePrice", basePrice);
            msgBody.put("type", AuctionConstants.MSG_TYPE_AUCTION_STARTED);
            msgBody.put("traceId", TraceIdUtil.getTraceId());

            String payload = objectMapper.writeValueAsString(msgBody);
            Message<String> message = MessageBuilder.withPayload(payload)
                    .setHeader("traceId", TraceIdUtil.getTraceId())
                    .build();
            rocketMQTemplate.syncSend(
                    AuctionConstants.TOPIC_AUCTION + ":" + AuctionConstants.TAG_AUCTION_STARTED,
                    message
            );
            log.info("竞拍开始消息已发送: auctionId={}, orderId={}", auctionId, orderId);
        } catch (Exception e) {
            log.warn("竞拍开始消息发送失败（不影响主流程，客户端会通过拉取发现新竞拍）: auctionId={}", auctionId, e);
        }

        log.info("竞拍已发起: auctionId={}, orderId={}, basePrice={}", auctionId, orderId, basePrice);

        return Map.of(
                "auctionId", auctionId,
                "orderId", orderId,
                "basePrice", basePrice,
                "status", AuctionConstants.AUCTION_STATUS_RUNNING,
                "startTime", LocalDateTime.now().toString()
        );
    }

    @Override
    public AuctionRecord placeBid(String auctionId, Long merchantId, Long orderId, BigDecimal bidPrice) {
        // 1. 竞拍是否已结束
        String status = auctionRedisRepository.getAuctionStatus(auctionId);
        if (AuctionConstants.AUCTION_STATUS_ENDED.equals(status)) {
            throw new BizException(20002, "竞拍已结束");
        }
        if (status == null) {
            throw new BizException(20002, "竞拍不存在或已过期");
        }
        // 2. 分布式锁
        // 同一商户在同一竞拍中并发出价时，只有第一个请求能获取锁
        // TTL 覆盖整个竞拍周期，锁过期后商户可再次出价
        if (!auctionRedisRepository.acquireBidLock(auctionId, merchantId)) {
            log.warn("出价正在处理中，拒绝重复请求: auctionId={}, merchantId={}", auctionId, merchantId);
            throw new BizException(ErrorCode.BID_PROCESSING);
        }

        // 3. Lua 业务约束（出价次数 + 金额校验）
        long result = auctionBidService.executeBid(auctionId, merchantId, bidPrice);
        if (result == -1) {
            throw new BizException(20002, "竞拍已结束或不存在");
        }
        if (result == -2) {
            throw new BizException(20003, "出价过低");
        }
        if (result == -3) {
            throw new BizException(20005, "已达到最大出价次数（每商户最多2次）");
        }
        if (result != 1) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "出价失败");
        }

        AuctionRecord record = AuctionRecord.builder()
                .orderId(orderId)
                .auctionId(auctionId)
                .merchantId(merchantId)
                .bidPrice(bidPrice)
                .bidTime(LocalDateTime.now())
                .isWinner(0)
                .source("lua")
                .build();

        if (nacosConfigManager.isAsyncMode()) {
            try {
                String payload = objectMapper.writeValueAsString(record);
                SendResult sendResult =rocketMQTemplate.syncSend(
                        RocketMQConstants.TOPIC_BID_DB_SYNC + ":" + RocketMQConstants.TAG_SYNC_BID_RECORD,
                        payload
                );
                if (sendResult.getSendStatus().equals(SendStatus.SEND_OK)){
                    log.debug("出价记录已异步发送到MQ: auctionId={}, merchantId={}", auctionId, merchantId);
                }else {
                    log.debug("出价记录发送到 MQ 失败: auctionId={}, merchantId={}", auctionId, merchantId);
                }
            } catch (Exception e) {
                log.warn("出价记录异步发送失败（不影响出价主流程）: auctionId={}", auctionId, e);
            }
        } else {
            auctionRecordRepository.insert(record);
        }

        log.info("盲拍出价成功: auctionId={}, merchantId={}, bidPrice={}, 第 {} 次出价, mode={}",
                auctionId, merchantId, bidPrice, result, nacosConfigManager.getBidDbWriteMode());
        return record;
    }

    @Override
    public Map<String, Object> endAuction(String auctionId) {
        //  幂等检查：先读 DB，确认订单还是 AUCTIONING 
        Long orderId = auctionRedisRepository.getAuctionOrderId(auctionId);
        final Long effectiveOrderId = (orderId != null) ? orderId : 0L;

        // 从 DB 查订单状态（DB 是权威数据源）
        if (effectiveOrderId > 0) {
            AuctionOrder curOrder = auctionOrderRepository.selectById(effectiveOrderId);
            if (curOrder != null && !OrderStatus.AUCTIONING.equals(curOrder.getStatus())) {
                log.info("竞拍已处理，无需重复结算: auctionId={}, orderStatus={}", auctionId, curOrder.getStatus());
                return Map.of("auctionId", auctionId, "status", "ALREADY_PROCESSED");
            }
        }

        //  尝试读 Redis 数据 
        Set<ZSetOperations.TypedTuple<String>> bidsData;
        String winnerMerchantId;
        BigDecimal finalPrice;
        List<Long> merchants;

        try {
            bidsData = auctionRedisRepository.getAllBids(auctionId);
            if (bidsData == null) bidsData = Collections.emptySet();

            // 标记竞拍结束（缩短 TTL，等待清理）
            auctionRedisRepository.endAuction(auctionId);

            winnerMerchantId = auctionRedisRepository.getWinner(auctionId);
            finalPrice = auctionRedisRepository.getCurrentBid(auctionId);
            merchants = auctionRedisRepository.getAuctionMerchants(auctionId);
        } catch (Exception e) {
            // Redis 不可用 → 无法裁决 → 标记 AUCTION_FAILED
            // 比卡在 AUCTIONING 永远不动要好
            log.error("Redis 不可用，竞拍标记为失败: auctionId={}", auctionId, e);
            if (effectiveOrderId > 0) {
                updateOrderStatusWithRetry(effectiveOrderId, OrderStatus.AUCTION_FAILED);
            }
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "Redis不可用，竞拍已标记失败");
        }

        //  批量落盘出价记录到 MySQL 
        List<AuctionRecord> records = bidsData.stream()
                .map(tuple -> {
                    String member = tuple.getValue();
                    String[] parts = member != null ? member.split(":") : new String[]{"0", "0"};
                    long mid = Long.parseLong(parts[0]);
                    long timestamp = Long.parseLong(parts[1]);
                    BigDecimal price = BigDecimal.valueOf(tuple.getScore());
                    String merchantIdStr = String.valueOf(mid);
                    return AuctionRecord.builder()
                            .orderId(effectiveOrderId)
                            .auctionId(auctionId)
                            .merchantId(mid)
                            .bidPrice(price)
                            .bidTime(LocalDateTime.ofInstant(
                                    java.time.Instant.ofEpochMilli(timestamp),
                                    ZoneId.systemDefault()))
                            .isWinner(merchantIdStr.equals(winnerMerchantId) ? 1 : 0)
                            .source("redis")
                            .build();
                })
                .toList();

        //  ★ 幂等闸门：乐观锁翻转 AUCTIONING→AUCTION_ENDED 作为互斥，
        //    翻转成功者在同一事务内由 AuctionSettlementService 落盘出价记录；
        //    失败者视为已处理，不再重复落盘（解决并发/重试导致的重复结算）。
        //    effectiveOrderId==0（Redis 丢失订单映射）时无法走 DB 闸门，保持原行为直接落盘。
        if (effectiveOrderId > 0) {
            boolean settled = auctionSettlementService.settle(effectiveOrderId, records);
            if (!settled) {
                return Map.of("auctionId", auctionId, "status", "ALREADY_PROCESSED");
            }
        } else if (!records.isEmpty()) {
            records.forEach(auctionRecordRepository::insert);
        }

        //  清理 Redis 数据（主动删除，不等 TTL 过期）
        try {
            auctionRedisRepository.deleteAuction(auctionId);
        } catch (Exception e) {
            log.warn("Redis 清理失败（不影响主流程）: auctionId={}", auctionId, e);
        }

        //  发送 AUCTION_ENDED 通知 
        Map<String, Object> endedMsg = new HashMap<>();
        endedMsg.put("auctionId", auctionId);
        endedMsg.put("winnerMerchantId", winnerMerchantId != null ? Long.parseLong(winnerMerchantId) : null);
        endedMsg.put("finalPrice", finalPrice);
        endedMsg.put("orderId", effectiveOrderId);
        endedMsg.put("type", AuctionConstants.MSG_TYPE_AUCTION_ENDED);
        endedMsg.put("traceId", TraceIdUtil.getTraceId());
        try {
            String endedPayload = objectMapper.writeValueAsString(endedMsg);
            Message<String> endedMessage = MessageBuilder.withPayload(endedPayload)
                    .setHeader("traceId", TraceIdUtil.getTraceId())
                    .build();
            rocketMQTemplate.syncSend(
                    AuctionConstants.TOPIC_AUCTION + ":" + AuctionConstants.TAG_AUCTION_ENDED,
                    endedMessage
            );
        } catch (Exception e) {
            log.warn("竞拍结束消息发送失败: auctionId={}", auctionId, e);
        }

        log.info("竞拍已结束: auctionId={}, winner={}, finalPrice={}, totalBids={}",
                auctionId, winnerMerchantId, finalPrice, records.size());

        Map<String, Object> result = new HashMap<>();
        result.put("auctionId", auctionId);
        result.put("winnerMerchantId", winnerMerchantId != null ? Long.parseLong(winnerMerchantId) : null);
        result.put("finalPrice", finalPrice);
        result.put("status", AuctionConstants.AUCTION_STATUS_ENDED);
        result.put("totalBids", records.size());
        result.put("participatingMerchants", merchants);
        return result;
    }

    /**
     * 更新订单状态（带重试的封装，乐观锁冲突时自动重试一次）
     */
    private void updateOrderStatusWithRetry(Long orderId, String targetStatus) {
        AuctionOrder order = auctionOrderRepository.selectById(orderId);
        if (order == null) return;

        LambdaUpdateWrapper<AuctionOrder> updateWrapper = new LambdaUpdateWrapper<AuctionOrder>()
                .eq(AuctionOrder::getId, orderId)
                .eq(AuctionOrder::getVersion, order.getVersion())
                .set(AuctionOrder::getStatus, targetStatus)
                .set(AuctionOrder::getVersion, order.getVersion() + 1);
        boolean updated = auctionOrderRepository.update(null, updateWrapper) > 0;

        if (!updated) {
            // 乐观锁冲突，重试一次
            AuctionOrder refreshed = auctionOrderRepository.selectById(orderId);
            if (refreshed != null) {
                LambdaUpdateWrapper<AuctionOrder> retryWrapper = new LambdaUpdateWrapper<AuctionOrder>()
                        .eq(AuctionOrder::getId, orderId)
                        .eq(AuctionOrder::getVersion, refreshed.getVersion())
                        .set(AuctionOrder::getStatus, targetStatus)
                        .set(AuctionOrder::getVersion, refreshed.getVersion() + 1);
                auctionOrderRepository.update(null, retryWrapper);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> restartAuction(Long orderId, BigDecimal basePrice) {
        AuctionOrder order = auctionOrderRepository.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "订单不存在: " + orderId);
        }
        if (!OrderStatus.AUCTION_FAILED.equals(order.getStatus())) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID.getCode(),
                    "仅 AUCTION_FAILED 状态的订单可以重新发起竞拍，当前: " + order.getStatus());
        }
        return startAuction(orderId, basePrice);
    }

    @Override
    public Map<String, Object> getAuctionStatus(String auctionId) {
        String status = auctionRedisRepository.getAuctionStatus(auctionId);
        BigDecimal currentBid = auctionRedisRepository.getCurrentBid(auctionId);
        String winnerId = auctionRedisRepository.getWinner(auctionId);
        List<Long> merchants = auctionRedisRepository.getAuctionMerchants(auctionId);

        Map<String, Object> result = new HashMap<>();
        result.put("auctionId", auctionId);
        result.put("status", status != null ? status : "NOT_FOUND");
        result.put("currentBid", currentBid);
        result.put("winnerMerchantId", winnerId != null ? Long.parseLong(winnerId) : null);
        result.put("participatingMerchants", merchants);
        return result;
    }

    @Override
    public List<Map<String, Object>> getActiveAuctions() {
        List<String> activeIds = auctionRedisRepository.getActiveAuctionIds();
        if (activeIds.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> result = new ArrayList<>(activeIds.size());
        for (String auctionId : activeIds) {
            result.add(getAuctionStatus(auctionId));
        }
        return result;
    }

    @Override
    public void joinAuction(String auctionId, Long merchantId) {
        auctionRedisRepository.addAuctionMerchant(auctionId, merchantId);
        log.info("商户加入竞拍: auctionId={}, merchantId={}", auctionId, merchantId);
    }
}
