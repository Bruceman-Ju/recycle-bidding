package com.recycle.bidding.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ConfirmPaymentRequest {
    @NotNull(message = "中标商户ID不能为空")
    private Long winnerMerchantId;

    @NotNull(message = "成交金额不能为空")
    @DecimalMin(value = "0.01", message = "金额必须大于0")
    private BigDecimal finalPrice;
}
