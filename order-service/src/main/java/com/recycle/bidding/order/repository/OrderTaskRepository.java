package com.recycle.bidding.order.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recycle.bidding.order.entity.OrderTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工程师任务 Mapper（订单服务视角）
 *
 * 下单时同步创建工程师任务记录，
 * 数据与 engineer-service 的 EngineerTask 共享同一张 engineer_task 表。
 */
@Mapper
public interface OrderTaskRepository extends BaseMapper<OrderTask> {
}
