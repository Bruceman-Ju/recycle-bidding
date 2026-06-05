package com.recycle.bidding.auction.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class JoinAuctionRequest {
    @NotNull(message = "商户ID不能为空")
    private Long merchantId;
}
