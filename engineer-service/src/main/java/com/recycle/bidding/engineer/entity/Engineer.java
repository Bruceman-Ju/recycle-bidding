package com.recycle.bidding.engineer.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("engineer")
public class Engineer {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String phone;

    private Integer status;

    @Builder.Default
    private Integer totalTasks = 0;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
