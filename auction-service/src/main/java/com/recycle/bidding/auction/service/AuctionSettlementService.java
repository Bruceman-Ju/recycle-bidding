package com.recycle.bidding.auction.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.recycle.bidding.auction.entity.AuctionOrder;
import com.recycle.bidding.auction.entity.AuctionRecord;
import com.recycle.bidding.auction.repository.AuctionOrderRepository;
import com.recycle.bidding.auction.repository.AuctionRecordRepository;
import com.recycle.bidding.common.constant.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 竞拍结算服务：把"状态翻转"作为互斥闸门，并保证翻转换落盘在同一事务内完成。
 *
 * 为什么需要它：
 * endAuction 可能被多个自动触发源并发/重复调用（MQ 延迟消息、MQ 重试、未来的补偿服务重触发）。
 * 若只用 DB 状态做"读前检查"，两个线程都会通过检查、各自把出价记录插入 MySQL，
 * 造成重复结算（auction_record 出现重复行、winner 被落两次）。
 * 用乐观锁把 AUCTIONING → AUCTION_ENDED 当作"谁抢到谁结算"的互斥锁，
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionSettlementService {

    private final AuctionOrderRepository auctionOrderRepository;
    private final AuctionRecordRepository auctionRecordRepository;

    @Transactional(rollbackFor = Exception.class)
    public boolean settle(Long orderId, List<AuctionRecord> records) {
        AuctionOrder order = auctionOrderRepository.selectById(orderId);
        if (order == null) {
            return false;
        }
        // 已被其他触发源（MQ 重试 / 补偿服务）结算或标记失败，直接退出
        if (!OrderStatus.AUCTIONING.equals(order.getStatus())) {
            return false;
        }

        // 乐观锁翻转：只有 status 仍为 AUCTIONING 且 version 匹配的线程能成功
        LambdaUpdateWrapper<AuctionOrder> updateWrapper = new LambdaUpdateWrapper<AuctionOrder>()
                .eq(AuctionOrder::getId, orderId)
                .eq(AuctionOrder::getVersion, order.getVersion())
                .set(AuctionOrder::getStatus, OrderStatus.AUCTION_ENDED)
                .set(AuctionOrder::getVersion, order.getVersion() + 1);
        int updated = auctionOrderRepository.update(null, updateWrapper);
        if (updated == 0) {
            // 乐观锁冲突，败者退出，不落盘
            return false;
        }

        // 闸门已抢到，落盘出价记录（同一事务，失败整体回滚）
        if (records != null && !records.isEmpty()) {
            for (AuctionRecord record : records) {
                auctionRecordRepository.insert(record);
            }
        }
        return true;
    }
}
