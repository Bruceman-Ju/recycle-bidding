package com.recycle.bidding.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreatePaymentRequest {
    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @NotNull(message = "付款方ID不能为空")
    private Long payerId;

    @NotNull(message = "收款方ID不能为空")
    private Long payeeId;

    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.01", message = "金额必须大于0")
    private BigDecimal amount;
}
