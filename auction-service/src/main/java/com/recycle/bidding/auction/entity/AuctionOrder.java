package com.recycle.bidding.auction.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体（auction-service 模块的映射）
 *
 * 与 order-service 模块的 Order 实体共享同一个 orders 表。
 * 仅操作 auction_status 相关字段，不涉及订单业务的完整逻辑。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("orders")
public class AuctionOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String status;

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
