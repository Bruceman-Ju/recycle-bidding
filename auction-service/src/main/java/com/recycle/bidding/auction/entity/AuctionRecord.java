package com.recycle.bidding.auction.entity;

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
@TableName("auction_record")
public class AuctionRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String auctionId;

    private Long merchantId;

    private BigDecimal bidPrice;

    private LocalDateTime bidTime;

    @Builder.Default
    private Integer isWinner = 0;

    @Builder.Default
    private String source = "redis";

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
