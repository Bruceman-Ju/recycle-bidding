package com.recycle.bidding.ws.session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 竞拍-商户映射管理器
 *
 * 管理 auctionId → 参与商户列表的映射关系。
 */
@Slf4j
@Component
public class AuctionSessionManager {

    private final ConcurrentHashMap<String, Set<Long>> auctionMerchantMap = new ConcurrentHashMap<>();

    /**
     * 商户加入竞拍
     */
    public void joinAuction(String auctionId, Long merchantId) {
        auctionMerchantMap.computeIfAbsent(auctionId, k -> new CopyOnWriteArraySet<>()).add(merchantId);
        log.info("商户加入竞拍房间: auctionId={}, merchantId={}", auctionId, merchantId);
    }

    /**
     * 商户离开竞拍
     */
    public void leaveAuction(String auctionId, Long merchantId) {
        Set<Long> merchants = auctionMerchantMap.get(auctionId);
        if (merchants != null) {
            merchants.remove(merchantId);
            if (merchants.isEmpty()) {
                auctionMerchantMap.remove(auctionId);
            }
            log.info("商户离开竞拍房间: auctionId={}, merchantId={}", auctionId, merchantId);
        }
    }

    /**
     * 获取参与某竞拍的所有商户
     */
    public Set<Long> getMerchants(String auctionId) {
        Set<Long> merchants = auctionMerchantMap.get(auctionId);
        return merchants != null ? merchants : Collections.emptySet();
    }

    /**
     * 清理竞拍房间
     */
    public void clearAuction(String auctionId) {
        auctionMerchantMap.remove(auctionId);
        log.info("竞拍房间已清理: auctionId={}", auctionId);
    }
}
