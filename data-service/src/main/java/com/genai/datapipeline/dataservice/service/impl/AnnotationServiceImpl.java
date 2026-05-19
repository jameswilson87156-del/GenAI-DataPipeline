package com.genai.datapipeline.dataservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genai.datapipeline.dataservice.common.StatusConstants;
import com.genai.datapipeline.dataservice.dto.response.NextAnnotationItemResponse;
import com.genai.datapipeline.dataservice.entity.AiAnnotation;
import com.genai.datapipeline.dataservice.entity.DataItem;
import com.genai.datapipeline.dataservice.entity.DataTask;
import com.genai.datapipeline.dataservice.exception.BizException;
import com.genai.datapipeline.dataservice.service.AnnotationService;
import com.genai.datapipeline.dataservice.service.DataItemService;
import com.genai.datapipeline.dataservice.service.DataTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnnotationServiceImpl implements AnnotationService {

    private final DataItemService dataItemService;

    private final DataTaskService dataTaskService;

    private final ObjectMapper objectMapper;

    @Override
    public NextAnnotationItemResponse getNextAnnotationItem(Long taskId) {
        DataItem item = dataItemService.getOne(new LambdaQueryWrapper<DataItem>()
                .eq(DataItem::getTaskId, taskId)
                .eq(DataItem::getStatus, StatusConstants.ITEM_PENDING_EXPERT_ANNOTATION)
                .orderByAsc(DataItem::getId)
                .last("LIMIT 1"));

        if (item == null) {
            return null;
        }

        return NextAnnotationItemResponse.builder()
                .id(item.getId())
                .dataType(item.getDataType())
                .cleanedContent(item.getCleanedContent())
                .aiAnnotation(item.getAiAnnotation())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitExpertAnnotation(Long itemId, Long expertId, String expertAnnotationText) {
        AiAnnotation expertAnnotation = parseExpertAnnotation(expertAnnotationText);
        DataItem item = dataItemService.getById(itemId);
        if (item == null) {
            throw new BizException("Data item not found: " + itemId);
        }
        Long taskId = item.getTaskId();

        boolean updated = dataItemService.update(new LambdaUpdateWrapper<DataItem>()
                .eq(DataItem::getId, itemId)
                .eq(DataItem::getStatus, StatusConstants.ITEM_PENDING_EXPERT_ANNOTATION)
                .set(DataItem::getExpertAnnotation, expertAnnotation)
                .set(DataItem::getExpertId, expertId)
                .set(DataItem::getStatus, StatusConstants.ITEM_COMPLETED));

        if (!updated) {
            throw new BizException("Annotation already submitted or item status changed: " + itemId);
        }

        refreshTaskIfCompleted(taskId);
    }

    private AiAnnotation parseExpertAnnotation(String expertAnnotationText) {
        try {
            return objectMapper.readValue(normalizeJsonResponse(expertAnnotationText), AiAnnotation.class);
        } catch (Exception exception) {
            return AiAnnotation.builder()
                    .isBug(null)
                    .bugType("ExpertNote")
                    .suggestion(expertAnnotationText == null ? "" : expertAnnotationText.trim())
                    .build();
        }
    }

    private void refreshTaskIfCompleted(Long taskId) {
        long unfinishedCount = dataItemService.count(new LambdaQueryWrapper<DataItem>()
                .eq(DataItem::getTaskId, taskId)
                .in(DataItem::getStatus,
                        StatusConstants.ITEM_PENDING,
                        StatusConstants.ITEM_PROCESSING,
                        StatusConstants.ITEM_PENDING_EXPERT_ANNOTATION));

        long successCount = dataItemService.count(new LambdaQueryWrapper<DataItem>()
                .eq(DataItem::getTaskId, taskId)
                .eq(DataItem::getStatus, StatusConstants.ITEM_COMPLETED));
        long failedCount = dataItemService.count(new LambdaQueryWrapper<DataItem>()
                .eq(DataItem::getTaskId, taskId)
                .eq(DataItem::getStatus, StatusConstants.ITEM_FAILED));
        long totalCount = dataItemService.count(new LambdaQueryWrapper<DataItem>()
                .eq(DataItem::getTaskId, taskId));
        int taskStatus = unfinishedCount == 0 && failedCount == 0
                ? StatusConstants.TASK_COMPLETED
                : unfinishedCount == 0 ? StatusConstants.TASK_FAILED : StatusConstants.TASK_RUNNING;

        dataTaskService.update(new LambdaUpdateWrapper<DataTask>()
                .eq(DataTask::getId, taskId)
                .set(DataTask::getStatus, taskStatus)
                .set(DataTask::getTotalCount, totalCount)
                .set(DataTask::getProcessedCount, successCount + failedCount)
                .set(DataTask::getSuccessCount, successCount)
                .set(DataTask::getFailedCount, failedCount)
                .set(DataTask::getFinishedAt, unfinishedCount == 0 ? java.time.LocalDateTime.now() : null)
                .set(DataTask::getUpdateTime, java.time.LocalDateTime.now()));
    }

    private String normalizeJsonResponse(String responseText) {
        if (responseText == null) {
            return "";
        }
        String trimmed = responseText.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed.trim();
    }
}
