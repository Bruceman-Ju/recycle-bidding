package com.recycle.bidding.auction.controller;

import com.recycle.bidding.auction.dto.JoinAuctionRequest;
import com.recycle.bidding.auction.dto.PlaceBidRequest;
import com.recycle.bidding.auction.dto.StartAuctionRequest;
import com.recycle.bidding.auction.entity.AuctionRecord;
import com.recycle.bidding.auction.service.AuctionService;
import com.recycle.bidding.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auction")
@RequiredArgsConstructor
public class AuctionController {

    private final AuctionService auctionService;

    /**
     * 发起竞拍
     */
    @PostMapping("/start")
    public Result<Map<String, Object>> startAuction(@Valid @RequestBody StartAuctionRequest req) {
        Map<String, Object> result = auctionService.startAuction(req.getOrderId(), req.getBasePrice());
        return Result.ok(result);
    }

    /**
     * 商户出价（盲拍模式）
     * <p>
     * 每个商户每场竞拍最多出价 2 次，由 Lua 脚本在 Redis 内原子判断。
     */
    @PostMapping("/bid")
    public Result<AuctionRecord> placeBid(@Valid @RequestBody PlaceBidRequest req) {
        AuctionRecord record = auctionService.placeBid(
                req.getAuctionId(), req.getMerchantId(), req.getOrderId(),
                req.getBidPrice());
        return Result.ok(record);
    }

    /**
     * 查询竞拍状态
     */
    @GetMapping("/{auctionId}")
    public Result<Map<String, Object>> getAuctionStatus(@PathVariable String auctionId) {
        Map<String, Object> status = auctionService.getAuctionStatus(auctionId);
        return Result.ok(status);
    }

    /**
     * 查询活跃竞拍列表
     */
    @GetMapping("/active")
    public Result<List<Map<String, Object>>> getActiveAuctions() {
        List<Map<String, Object>> auctions = auctionService.getActiveAuctions();
        return Result.ok(auctions);
    }

    /**
     * 商户加入竞拍
     */
    @PostMapping("/{auctionId}/join")
    public Result<Void> joinAuction(@PathVariable String auctionId,
                                    @Valid @RequestBody JoinAuctionRequest req) {
        auctionService.joinAuction(auctionId, req.getMerchantId());
        return Result.ok();
    }

    /**
     * 重新发起竞拍（异常恢复场景）
     * 工程师处理 AUCTION_FAILED 订单时调用，生成新竞拍重新开始
     */
    @PostMapping("/restart")
    public Result<Map<String, Object>> restartAuction(@Valid @RequestBody StartAuctionRequest req) {
        Map<String, Object> result = auctionService.restartAuction(req.getOrderId(), req.getBasePrice());
        return Result.ok(result);
    }
}
