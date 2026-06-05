package com.recycle.bidding.engineer.controller;

import com.recycle.bidding.common.result.PageResult;
import com.recycle.bidding.common.result.Result;
import com.recycle.bidding.engineer.dto.AcceptTaskRequest;
import com.recycle.bidding.engineer.dto.SubmitEvaluationRequest;
import com.recycle.bidding.engineer.entity.Engineer;
import com.recycle.bidding.engineer.entity.EngineerTask;
import com.recycle.bidding.engineer.entity.EvaluationRecord;
import com.recycle.bidding.engineer.service.EngineerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/engineer")
@RequiredArgsConstructor
public class EngineerController {

    private final EngineerService engineerService;

    /**
     * 工程师接单
     */
    @PostMapping("/task/accept")
    public Result<EngineerTask> acceptTask(@Valid @RequestBody AcceptTaskRequest req) {
        EngineerTask task = engineerService.acceptTask(req.getTaskId(), req.getEngineerId());
        return Result.ok(task);
    }

    /**
     * 开始验机
     */
    @PutMapping("/task/{taskId}/start-inspect")
    public Result<EngineerTask> startInspect(@PathVariable Long taskId) {
        EngineerTask task = engineerService.startInspect(taskId);
        return Result.ok(task);
    }

    /**
     * 提交验机记录
     */
    @PostMapping("/evaluation/submit")
    public Result<EvaluationRecord> submitEvaluation(@Valid @RequestBody SubmitEvaluationRequest req) {
        EvaluationRecord record = engineerService.submitEvaluation(
                req.getOrderId(), req.getEngineerId(), req.getInitialEstimate(),
                req.getAppearanceScore(), req.getScreenScore(),
                req.getFunctionScore(), req.getBatteryScore(), req.getRemark());
        return Result.ok(record);
    }

    /**
     * 查询工程师信息
     */
    @GetMapping("/{engineerId}")
    public Result<Engineer> getEngineer(@PathVariable Long engineerId) {
        Engineer engineer = engineerService.getEngineerById(engineerId);
        return Result.ok(engineer);
    }

    /**
     * 查询工程师任务列表
     */
    @GetMapping("/{engineerId}/tasks")
    public Result<PageResult<EngineerTask>> getTasks(@PathVariable Long engineerId,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        PageResult<EngineerTask> result = engineerService.getTasksByEngineer(engineerId, page, size);
        return Result.ok(result);
    }
}
