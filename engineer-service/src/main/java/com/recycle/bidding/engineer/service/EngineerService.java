package com.recycle.bidding.engineer.service;

import com.recycle.bidding.common.result.PageResult;
import com.recycle.bidding.engineer.entity.Engineer;
import com.recycle.bidding.engineer.entity.EngineerTask;
import com.recycle.bidding.engineer.entity.EvaluationRecord;

import java.math.BigDecimal;

public interface EngineerService {

    /**
     * 分配工程师给订单
     */
    EngineerTask assignEngineer(Long orderId);

    /**
     * 工程师接单
     */
    EngineerTask acceptTask(Long taskId, Long engineerId);

    /**
     * 开始验机
     */
    EngineerTask startInspect(Long taskId);

    /**
     * 提交验机记录
     */
    EvaluationRecord submitEvaluation(Long orderId, Long engineerId, BigDecimal initialEstimate,
                                       Integer appearanceScore, Integer screenScore,
                                       Integer functionScore, Integer batteryScore,
                                       String remark);

    /**
     * 查询任务列表
     */
    PageResult<EngineerTask> getTasksByEngineer(Long engineerId, int page, int size);

    /**
     * 根据ID查询工程师
     */
    Engineer getEngineerById(Long engineerId);
}
