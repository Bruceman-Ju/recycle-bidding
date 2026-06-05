package com.recycle.bidding.recovery.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.recycle.bidding.recovery.entity.RecoveryOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RecoveryOrderRepository extends BaseMapper<RecoveryOrder> {
}
