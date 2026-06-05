package com.recycle.bidding.auction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class StartAuctionRequest {
    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @NotNull(message = "起拍价不能为空")
    @DecimalMin(value = "0.01", message = "起拍价必须大于0")
    private BigDecimal basePrice;
}
