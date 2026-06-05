package com.recycle.bidding.auction.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recycle.bidding.auction.entity.AuctionOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单数据访问（竞拍服务模块直接访问 orders 表）
 *
 * auction-service 和 order-service 共享同一个数据库，
 * 在竞拍流程的关键节点（开始/结束/异常）需要更新订单状态。
 * 如果后续做数据库拆分，可改为 Feign 调用 order-service。
 */
@Mapper
public interface AuctionOrderRepository extends BaseMapper<AuctionOrder> {
}
