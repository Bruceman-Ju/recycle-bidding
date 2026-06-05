package com.recycle.bidding.engineer.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AcceptTaskRequest {
    @NotNull(message = "任务ID不能为空")
    private Long taskId;

    @NotNull(message = "工程师ID不能为空")
    private Long engineerId;
}
