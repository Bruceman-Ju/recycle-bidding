package com.recycle.bidding.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateOrderRequest {
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "手机型号ID不能为空")
    private Long phoneModelId;

    @NotNull(message = "初始估价不能为空")
    @DecimalMin(value = "0.01", message = "估价必须大于0")
    private BigDecimal initialEstimate;
}
