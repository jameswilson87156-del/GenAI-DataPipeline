package com.genai.datapipeline.dataservice.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.genai.datapipeline.dataservice.common.Result;
import com.genai.datapipeline.dataservice.common.StatusConstants;
import com.genai.datapipeline.dataservice.dto.request.CreateWorkerNodeRequest;
import com.genai.datapipeline.dataservice.dto.request.UpdateWorkerNodeRequest;
import com.genai.datapipeline.dataservice.entity.WorkerNode;
import com.genai.datapipeline.dataservice.exception.BizException;
import com.genai.datapipeline.dataservice.service.WorkerNodeService;
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
@Tag(name = "Worker Node", description = "Worker node management APIs")
@RequestMapping("/api/worker-nodes")
public class WorkerNodeController {

    private final WorkerNodeService workerNodeService;

    @Operation(summary = "Create worker node")
    @PostMapping
    public Result<WorkerNode> create(@Valid @RequestBody CreateWorkerNodeRequest request) {
        LocalDateTime now = LocalDateTime.now();
        WorkerNode workerNode = new WorkerNode();
        workerNode.setNodeCode(request.getNodeCode());
        workerNode.setHost(request.getHost());
        workerNode.setPort(request.getPort());
        workerNode.setMaxConcurrency(request.getMaxConcurrency() == null ? 1 : request.getMaxConcurrency());
        workerNode.setVersion(request.getVersion());
        workerNode.setRemark(request.getRemark());
        workerNode.setCreateTime(now);
        workerNode.setUpdateTime(now);
        workerNode.setStatus(StatusConstants.WORKER_OFFLINE);
        workerNode.setCurrentLoad(0);
        workerNode.setDeleted(0);
        workerNodeService.save(workerNode);
        return Result.ok(workerNode);
    }

    @Operation(summary = "Get worker node detail")
    @GetMapping("/{id}")
    public Result<WorkerNode> detail(@PathVariable Long id) {
        WorkerNode workerNode = workerNodeService.getById(id);
        if (workerNode == null) {
            throw new BizException(404, "Worker node not found: " + id);
        }
        return Result.ok(workerNode);
    }

    @Operation(summary = "Page query worker nodes")
    @GetMapping
    public Result<Page<WorkerNode>> page(@RequestParam(defaultValue = "1") long current,
                                         @RequestParam(defaultValue = "10") long size,
                                         @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<WorkerNode> wrapper = new LambdaQueryWrapper<WorkerNode>()
                .eq(status != null, WorkerNode::getStatus, status)
                .orderByDesc(WorkerNode::getCreateTime);
        return Result.ok(workerNodeService.page(Page.of(current, size), wrapper));
    }

    @Operation(summary = "Update worker node")
    @PutMapping("/{id}")
    public Result<WorkerNode> update(@PathVariable Long id, @Valid @RequestBody UpdateWorkerNodeRequest request) {
        WorkerNode workerNode = new WorkerNode();
        workerNode.setId(id);
        workerNode.setNodeCode(request.getNodeCode());
        workerNode.setHost(request.getHost());
        workerNode.setPort(request.getPort());
        workerNode.setStatus(request.getStatus());
        workerNode.setMaxConcurrency(request.getMaxConcurrency());
        workerNode.setCurrentLoad(request.getCurrentLoad());
        workerNode.setVersion(request.getVersion());
        workerNode.setRemark(request.getRemark());
        workerNode.setUpdateTime(LocalDateTime.now());
        workerNodeService.updateById(workerNode);
        return Result.ok(workerNodeService.getById(id));
    }

    @Operation(summary = "Report worker heartbeat")
    @PostMapping("/{id}/heartbeat")
    public Result<WorkerNode> heartbeat(@PathVariable Long id) {
        WorkerNode workerNode = workerNodeService.getById(id);
        if (workerNode == null) {
            throw new BizException(404, "Worker node not found: " + id);
        }
        workerNode.setLastHeartbeatTime(LocalDateTime.now());
        workerNode.setStatus(StatusConstants.WORKER_ONLINE);
        workerNode.setUpdateTime(LocalDateTime.now());
        workerNodeService.updateById(workerNode);
        return Result.ok(workerNode);
    }

    @Operation(summary = "Delete worker node")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        workerNodeService.removeById(id);
        return Result.ok();
    }
}
