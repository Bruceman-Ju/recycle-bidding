package com.recycle.bidding.engineer.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("evaluation_record")
public class EvaluationRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long engineerId;

    private Integer appearanceScore;

    private Integer screenScore;

    private Integer functionScore;

    private Integer batteryScore;

    private String overallCondition;

    private BigDecimal secondEstimate;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
