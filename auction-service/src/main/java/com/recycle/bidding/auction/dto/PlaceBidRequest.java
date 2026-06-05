package com.recycle.bidding.auction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PlaceBidRequest {
    @NotBlank(message = "竞拍ID不能为空")
    private String auctionId;

    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @NotNull(message = "商户ID不能为空")
    private Long merchantId;

    @NotNull(message = "出价不能为空")
    @DecimalMin(value = "0.01", message = "出价必须大于0")
    private BigDecimal bidPrice;

}
