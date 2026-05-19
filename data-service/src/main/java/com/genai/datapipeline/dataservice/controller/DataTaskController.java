package com.genai.datapipeline.dataservice.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.genai.datapipeline.dataservice.common.Result;
import com.genai.datapipeline.dataservice.common.StatusConstants;
import com.genai.datapipeline.dataservice.dto.request.CreateDataTaskRequest;
import com.genai.datapipeline.dataservice.dto.request.UpdateDataTaskRequest;
import com.genai.datapipeline.dataservice.entity.DataTask;
import com.genai.datapipeline.dataservice.exception.BizException;
import com.genai.datapipeline.dataservice.service.DataCleanPipelineService;
import com.genai.datapipeline.dataservice.service.DataTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
@Tag(name = "Data Task", description = "Data cleaning task management APIs")
@RequestMapping
public class DataTaskController {

    private final DataTaskService dataTaskService;

    private final DataCleanPipelineService dataCleanPipelineService;

    @Operation(summary = "Create data task")
    @PostMapping("/api/data-tasks")
    public Result<DataTask> create(@Valid @RequestBody CreateDataTaskRequest request) {
        LocalDateTime now = LocalDateTime.now();
        DataTask dataTask = new DataTask();
        dataTask.setTaskName(request.getTaskName());
        dataTask.setTaskType(request.getTaskType());
        dataTask.setSourceType(request.getSourceType());
        dataTask.setSourceUri(request.getSourceUri());
        dataTask.setTotalCount(request.getTotalCount() == null ? 0L : request.getTotalCount());
        dataTask.setAssignedWorkerId(request.getAssignedWorkerId());
        dataTask.setRemark(request.getRemark());
        dataTask.setCreateTime(now);
        dataTask.setUpdateTime(now);
        dataTask.setStatus(StatusConstants.TASK_CREATED);
        dataTask.setProcessedCount(0L);
        dataTask.setSuccessCount(0L);
        dataTask.setFailedCount(0L);
        dataTask.setDeleted(0);
        dataTaskService.save(dataTask);
        return Result.ok(dataTask);
    }

    @Operation(summary = "Get data task detail")
    @GetMapping("/api/data-tasks/{id}")
    public Result<DataTask> detail(@PathVariable Long id) {
        DataTask dataTask = dataTaskService.getById(id);
        if (dataTask == null) {
            throw new BizException(404, "Data task not found: " + id);
        }
        return Result.ok(dataTask);
    }

    @Operation(summary = "Page query data tasks")
    @GetMapping("/api/data-tasks")
    public Result<Page<DataTask>> page(@RequestParam(defaultValue = "1") long current,
                                       @RequestParam(defaultValue = "10") long size,
                                       @RequestParam(required = false) Integer status,
                                       @RequestParam(required = false) String taskType) {
        LambdaQueryWrapper<DataTask> wrapper = new LambdaQueryWrapper<DataTask>()
                .eq(status != null, DataTask::getStatus, status)
                .eq(taskType != null && !taskType.isBlank(), DataTask::getTaskType, taskType)
                .orderByDesc(DataTask::getCreateTime);
        return Result.ok(dataTaskService.page(Page.of(current, size), wrapper));
    }

    @Operation(summary = "Update data task")
    @PutMapping("/api/data-tasks/{id}")
    public Result<DataTask> update(@PathVariable Long id, @Valid @RequestBody UpdateDataTaskRequest request) {
        DataTask dataTask = new DataTask();
        dataTask.setId(id);
        dataTask.setTaskName(request.getTaskName());
        dataTask.setTaskType(request.getTaskType());
        dataTask.setSourceType(request.getSourceType());
        dataTask.setSourceUri(request.getSourceUri());
        dataTask.setStatus(request.getStatus());
        dataTask.setTotalCount(request.getTotalCount());
        dataTask.setProcessedCount(request.getProcessedCount());
        dataTask.setSuccessCount(request.getSuccessCount());
        dataTask.setFailedCount(request.getFailedCount());
        dataTask.setAssignedWorkerId(request.getAssignedWorkerId());
        dataTask.setRemark(request.getRemark());
        dataTask.setUpdateTime(LocalDateTime.now());
        dataTaskService.updateById(dataTask);
        return Result.ok(dataTaskService.getById(id));
    }

    @Operation(summary = "Start data task")
    @PostMapping("/api/data-tasks/{id}/start")
    public Result<DataTask> start(@PathVariable Long id) {
        return Result.ok(dataTaskService.startTask(id));
    }

    @Operation(summary = "Publish data items and start async cleaning")
    @PostMapping({"/api/task/{taskId}/start", "/api/data-tasks/{taskId}/start-clean"})
    public Result<Void> startCleanPipeline(@PathVariable Long taskId) {
        dataCleanPipelineService.publishTask(taskId);
        dataCleanPipelineService.executeClean(taskId);
        return Result.ok();
    }

    @Operation(summary = "Stop data task")
    @PostMapping("/api/data-tasks/{id}/stop")
    public Result<DataTask> stop(@PathVariable Long id) {
        return Result.ok(dataTaskService.stopTask(id));
    }

    @Operation(summary = "Delete data task")
    @DeleteMapping("/api/data-tasks/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        dataTaskService.removeById(id);
        return Result.ok();
    }
}
