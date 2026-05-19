package com.genai.datapipeline.dataservice.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.genai.datapipeline.dataservice.common.Result;
import com.genai.datapipeline.dataservice.common.StatusConstants;
import com.genai.datapipeline.dataservice.dto.request.CreateDataItemRequest;
import com.genai.datapipeline.dataservice.dto.request.ImportDataItemsRequest;
import com.genai.datapipeline.dataservice.dto.request.UpdateDataItemRequest;
import com.genai.datapipeline.dataservice.dto.response.ImportDataItemsResponse;
import com.genai.datapipeline.dataservice.entity.DataItem;
import com.genai.datapipeline.dataservice.exception.BizException;
import com.genai.datapipeline.dataservice.service.DataImportService;
import com.genai.datapipeline.dataservice.service.DataItemService;
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
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
@Tag(name = "Data Item", description = "Data item management APIs")
@RequestMapping("/api/data-items")
public class DataItemController {

    private final DataItemService dataItemService;

    private final DataImportService dataImportService;

    @Operation(summary = "Create data item")
    @PostMapping
    public Result<DataItem> create(@Valid @RequestBody CreateDataItemRequest request) {
        LocalDateTime now = LocalDateTime.now();
        DataItem dataItem = new DataItem();
        dataItem.setTaskId(request.getTaskId());
        dataItem.setSourceId(request.getSourceId());
        dataItem.setRawContent(request.getRawContent());
        dataItem.setCleanedContent(request.getCleanedContent());
        dataItem.setContentHash(request.getContentHash());
        dataItem.setTokenCount(request.getTokenCount() == null ? 0 : request.getTokenCount());
        dataItem.setQualityScore(request.getQualityScore());
        dataItem.setCreateTime(now);
        dataItem.setUpdateTime(now);
        dataItem.setStatus(StatusConstants.ITEM_PENDING);
        dataItem.setDeleted(0);
        dataItemService.save(dataItem);
        return Result.ok(dataItem);
    }

    @Operation(summary = "Import raw data items from JSON")
    @PostMapping("/import")
    public Result<ImportDataItemsResponse> importRawTexts(@Valid @RequestBody ImportDataItemsRequest request) {
        boolean autoStart = Boolean.TRUE.equals(request.getAutoStart());
        return Result.ok(dataImportService.importRawTexts(
                request.getTaskId(),
                request.getRawContents(),
                request.getSourcePrefix(),
                autoStart
        ));
    }

    @Operation(summary = "Import raw data items from text file")
    @PostMapping("/import-file")
    public Result<ImportDataItemsResponse> importTextFile(@RequestParam Long taskId,
                                                          @RequestParam MultipartFile file,
                                                          @RequestParam(required = false) String sourcePrefix,
                                                          @RequestParam(defaultValue = "false") boolean autoStart) {
        return Result.ok(dataImportService.importTextFile(taskId, file, sourcePrefix, autoStart));
    }

    @Operation(summary = "Get data item detail")
    @GetMapping("/{id}")
    public Result<DataItem> detail(@PathVariable Long id) {
        DataItem dataItem = dataItemService.getById(id);
        if (dataItem == null) {
            throw new BizException(404, "Data item not found: " + id);
        }
        return Result.ok(dataItem);
    }

    @Operation(summary = "Page query data items")
    @GetMapping
    public Result<Page<DataItem>> page(@RequestParam(defaultValue = "1") long current,
                                       @RequestParam(defaultValue = "10") long size,
                                       @RequestParam(required = false) Long taskId,
                                       @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<DataItem> wrapper = new LambdaQueryWrapper<DataItem>()
                .eq(taskId != null, DataItem::getTaskId, taskId)
                .eq(status != null, DataItem::getStatus, status)
                .orderByDesc(DataItem::getCreateTime);
        return Result.ok(dataItemService.page(Page.of(current, size), wrapper));
    }

    @Operation(summary = "Update data item")
    @PutMapping("/{id}")
    public Result<DataItem> update(@PathVariable Long id, @Valid @RequestBody UpdateDataItemRequest request) {
        DataItem dataItem = new DataItem();
        dataItem.setId(id);
        dataItem.setTaskId(request.getTaskId());
        dataItem.setSourceId(request.getSourceId());
        dataItem.setRawContent(request.getRawContent());
        dataItem.setCleanedContent(request.getCleanedContent());
        dataItem.setContentHash(request.getContentHash());
        dataItem.setStatus(request.getStatus());
        dataItem.setTokenCount(request.getTokenCount());
        dataItem.setQualityScore(request.getQualityScore());
        dataItem.setErrorMessage(request.getErrorMessage());
        dataItem.setUpdateTime(LocalDateTime.now());
        dataItemService.updateById(dataItem);
        return Result.ok(dataItemService.getById(id));
    }

    @Operation(summary = "Delete data item")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        dataItemService.removeById(id);
        return Result.ok();
    }
}
