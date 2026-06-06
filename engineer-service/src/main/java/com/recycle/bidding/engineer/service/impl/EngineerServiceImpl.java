package com.recycle.bidding.engineer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.recycle.bidding.common.exception.BizException;
import com.recycle.bidding.common.exception.ErrorCode;
import com.recycle.bidding.common.result.PageResult;
import com.recycle.bidding.engineer.entity.Engineer;
import com.recycle.bidding.engineer.entity.EngineerTask;
import com.recycle.bidding.engineer.entity.EvaluationRecord;
import com.recycle.bidding.engineer.entity.Order;
import com.recycle.bidding.engineer.repository.EngineerRepository;
import com.recycle.bidding.engineer.repository.EngineerTaskRepository;
import com.recycle.bidding.engineer.repository.EvaluationRecordRepository;
import com.recycle.bidding.engineer.repository.OrderRepository;
import com.recycle.bidding.engineer.service.EngineerService;
import com.recycle.bidding.engineer.service.EvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static com.recycle.bidding.common.constant.OrderStatus.SECOND_EVALUATED;

@Slf4j
@Service
@RequiredArgsConstructor
public class EngineerServiceImpl implements EngineerService {

    private final EngineerRepository engineerRepository;
    private final EngineerTaskRepository engineerTaskRepository;
    private final EvaluationRecordRepository evaluationRecordRepository;
    private final OrderRepository orderRepository;
    private final EvaluationService evaluationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EngineerTask assignEngineer(Long orderId) {
        // 查找空闲的工程师(status=1)
        LambdaQueryWrapper<Engineer> wrapper = new LambdaQueryWrapper<Engineer>()
                .eq(Engineer::getStatus, 1)
                .last("LIMIT 1");
        Engineer engineer = engineerRepository.selectOne(wrapper);
        if (engineer == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "暂无空闲工程师");
        }

        EngineerTask task = EngineerTask.builder()
                .engineerId(engineer.getId())
                .orderId(orderId)
                .taskStatus("ASSIGNED")
                .assignedAt(LocalDateTime.now())
                .build();
        engineerTaskRepository.insert(task);

        // 更新工程师状态为忙碌
        engineer.setStatus(2);
        engineerRepository.updateById(engineer);

        log.info("工程师分配成功: engineerId={}, orderId={}, taskId={}", engineer.getId(), orderId, task.getId());
        return task;
    }

    @Override
    public EngineerTask acceptTask(Long taskId, Long engineerId) {
        EngineerTask task = engineerTaskRepository.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "任务不存在");
        }

        task.setEngineerId(engineerId);
        task.setTaskStatus("ACCEPTED");
        task.setAcceptedAt(LocalDateTime.now());
        engineerTaskRepository.updateById(task);

        log.info("工程师接单成功: taskId={}, engineerId={}", taskId, engineerId);
        return task;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EngineerTask startInspect(Long taskId) {
        EngineerTask task = engineerTaskRepository.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "任务不存在");
        }
        if (!"ACCEPTED".equals(task.getTaskStatus())) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID.getCode(), "任务状态不允许开始验机");
        }

        task.setTaskStatus("INSPECTING");
        engineerTaskRepository.updateById(task);

        log.info("工程师开始验机: taskId={}", taskId);
        return task;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EvaluationRecord submitEvaluation(Long orderId, Long engineerId, BigDecimal initialEstimate,
                                              Integer appearanceScore, Integer screenScore,
                                              Integer functionScore, Integer batteryScore,
                                              String remark) {
        // 检查工程师是否存在
        Engineer engineer = engineerRepository.selectById(engineerId);
        if (engineer == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "工程师不存在");
        }

        // 计算二次估价
        BigDecimal secondEstimate = evaluationService.calculateSecondEstimate(
                initialEstimate, appearanceScore, screenScore, functionScore, batteryScore);

        // 存储验机记录
        EvaluationRecord record = EvaluationRecord.builder()
                .orderId(orderId)
                .engineerId(engineerId)
                .appearanceScore(appearanceScore)
                .screenScore(screenScore)
                .functionScore(functionScore)
                .batteryScore(batteryScore)
                .secondEstimate(secondEstimate)
                .remark(remark)
                .build();
        evaluationRecordRepository.insert(record);

        Order order = orderRepository.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "订单不存在");
        }

        LambdaUpdateWrapper<Order> updateWrapper = new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .set(Order::getStatus, SECOND_EVALUATED)
                .set(Order::getSecondEstimate, secondEstimate);
        boolean updated = orderRepository.update(null, updateWrapper) > 0;
        if (!updated) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "订单已被其他操作更新，请重试");
        }

        // 更新工程师任务状态
        LambdaQueryWrapper<EngineerTask> taskWrapper = new LambdaQueryWrapper<EngineerTask>()
                .eq(EngineerTask::getOrderId, orderId);
        EngineerTask task = engineerTaskRepository.selectOne(taskWrapper);
        if (task != null) {
            task.setTaskStatus("COMPLETED");
            task.setCompletedAt(LocalDateTime.now());
            engineerTaskRepository.updateById(task);
        }

        // 更新工程师累计任务数
        engineer.setTotalTasks(engineer.getTotalTasks() + 1);
        engineer.setStatus(1); // 变回空闲
        engineerRepository.updateById(engineer);

        log.info("验机记录提交成功: orderId={}, engineerId={}, secondEstimate={}", orderId, engineerId, secondEstimate);
        return record;
    }

    @Override
    public PageResult<EngineerTask> getTasksByEngineer(Long engineerId, int page, int size) {
        LambdaQueryWrapper<EngineerTask> wrapper = new LambdaQueryWrapper<EngineerTask>()
                .eq(EngineerTask::getEngineerId, engineerId)
                .orderByDesc(EngineerTask::getCreatedAt);

        IPage<EngineerTask> result = engineerTaskRepository.selectPage(new Page<>(page, size), wrapper);
        return new PageResult<>(result.getTotal(), (int) result.getCurrent(), (int) result.getSize(), result.getRecords());
    }

    @Override
    public Engineer getEngineerById(Long engineerId) {
        Engineer engineer = engineerRepository.selectById(engineerId);
        if (engineer == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "工程师不存在");
        }
        return engineer;
    }
}
