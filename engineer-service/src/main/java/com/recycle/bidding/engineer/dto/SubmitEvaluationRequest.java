package com.recycle.bidding.engineer.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SubmitEvaluationRequest {
    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @NotNull(message = "工程师ID不能为空")
    private Long engineerId;

    @NotNull(message = "估价不能为空")
    @DecimalMin(value = "0.01", message = "估价必须大于0")
    private BigDecimal initialEstimate;

    @NotNull(message = "外观评分不能为空")
    @Min(value = 0, message = "评分不能低于0")
    @Max(value = 10, message = "评分不能高于10")
    private Integer appearanceScore;

    @NotNull(message = "屏幕评分不能为空")
    @Min(value = 0, message = "评分不能低于0")
    @Max(value = 10, message = "评分不能高于10")
    private Integer screenScore;

    @NotNull(message = "功能评分不能为空")
    @Min(value = 0, message = "评分不能低于0")
    @Max(value = 10, message = "评分不能高于10")
    private Integer functionScore;

    @NotNull(message = "电池评分不能为空")
    @Min(value = 0, message = "评分不能低于0")
    @Max(value = 10, message = "评分不能高于10")
    private Integer batteryScore;

    private String remark;
}
