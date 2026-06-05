package com.recycle.bidding.auction.service;

import com.recycle.bidding.auction.entity.AuctionRecord;
import com.recycle.bidding.common.result.PageResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface AuctionService {

    /**
     * 发起竞拍
     */
    Map<String, Object> startAuction(Long orderId, BigDecimal basePrice);

    /**
     * 出价（盲拍模式）
     *
     * @param auctionId  竞拍ID
     * @param merchantId 商户ID
     * @param orderId    订单ID（用于记录）
     * @param bidPrice   出价金额
     */
    AuctionRecord placeBid(String auctionId, Long merchantId, Long orderId, BigDecimal bidPrice);

    /**
     * 结束竞拍
     */
    Map<String, Object> endAuction(String auctionId);

    /**
     * 查询竞拍状态
     */
    Map<String, Object> getAuctionStatus(String auctionId);

    /**
     * 获取活跃竞拍列表
     */
    List<Map<String, Object>> getActiveAuctions();

    /**
     * 商户加入竞拍
     */
    void joinAuction(String auctionId, Long merchantId);

    /**
     * 重新发起竞拍（补偿/异常恢复场景）
     * 工程师处理 AUCTION_FAILED 订单时调用，生成新 auctionId 重新开始竞拍
     */
    Map<String, Object> restartAuction(Long orderId, BigDecimal basePrice);
}
