package com.recycle.bidding.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 工程师任务实体（订单服务视角）
 *
 * 映射 engineer_task 表，由 order-service 在下单时同步创建。
 * engineer_id 在创建时为 NULL，工程师调用 acceptTask 后填充。
 *
 * 与 engineer-service 的 EngineerTask 实体映射同一张表，
 * 各服务只操作自己关心的字段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("engineer_task")
public class OrderTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long engineerId;

    private Long orderId;

    private String taskStatus;

    private LocalDateTime assignedAt;

    private LocalDateTime acceptedAt;

    private LocalDateTime completedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
