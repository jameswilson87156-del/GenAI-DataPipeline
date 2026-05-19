package com.genai.datapipeline.dataservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.genai.datapipeline.dataservice.common.StatusConstants;
import com.genai.datapipeline.dataservice.dto.response.ImportDataItemsResponse;
import com.genai.datapipeline.dataservice.entity.DataItem;
import com.genai.datapipeline.dataservice.entity.DataTask;
import com.genai.datapipeline.dataservice.exception.BizException;
import com.genai.datapipeline.dataservice.service.DataCleanPipelineService;
import com.genai.datapipeline.dataservice.service.DataImportService;
import com.genai.datapipeline.dataservice.service.DataItemService;
import com.genai.datapipeline.dataservice.service.DataTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DataImportServiceImpl implements DataImportService {

    private static final int SAVE_BATCH_SIZE = 500;

    private final DataItemService dataItemService;

    private final DataTaskService dataTaskService;

    private final DataCleanPipelineService dataCleanPipelineService;

    @Override
    public ImportDataItemsResponse importRawTexts(Long taskId, List<String> rawContents, String sourcePrefix, boolean autoStart) {
        requireTask(taskId);

        List<DataItem> dataItems = new ArrayList<>();
        int skippedBlankCount = 0;
        LocalDateTime now = LocalDateTime.now();
        String normalizedSourcePrefix = normalizeSourcePrefix(sourcePrefix);
        long importBatchNo = System.currentTimeMillis();

        for (String rawContent : rawContents) {
            if (rawContent == null || rawContent.isBlank()) {
                skippedBlankCount++;
                continue;
            }

            int index = dataItems.size() + 1;
            DataItem item = new DataItem();
            item.setTaskId(taskId);
            item.setSourceId(buildSourceId(normalizedSourcePrefix, importBatchNo, index));
            item.setDataType(detectDataType(rawContent));
            item.setRawContent(rawContent);
            item.setContentHash(sha256(rawContent));
            item.setStatus(StatusConstants.ITEM_PENDING);
            item.setTokenCount(0);
            item.setCreateTime(now);
            item.setUpdateTime(now);
            item.setDeleted(0);
            dataItems.add(item);
        }

        if (!dataItems.isEmpty()) {
            dataItemService.saveBatch(dataItems, SAVE_BATCH_SIZE);
        }

        long totalCount = countItems(taskId);
        dataTaskService.update(new LambdaUpdateWrapper<DataTask>()
                .eq(DataTask::getId, taskId)
                .set(DataTask::getTotalCount, totalCount)
                .set(DataTask::getUpdateTime, LocalDateTime.now()));

        if (autoStart) {
            dataCleanPipelineService.publishTask(taskId);
            dataCleanPipelineService.executeClean(taskId);
        }

        return ImportDataItemsResponse.builder()
                .taskId(taskId)
                .importedCount(dataItems.size())
                .skippedBlankCount(skippedBlankCount)
                .totalCount(totalCount)
                .autoStarted(autoStart)
                .itemIds(dataItems.stream().map(DataItem::getId).toList())
                .build();
    }

    @Override
    public ImportDataItemsResponse importTextFile(Long taskId, MultipartFile file, String sourcePrefix, boolean autoStart) {
        if (file == null || file.isEmpty()) {
            throw new BizException("Import file must not be empty");
        }

        List<String> rawContents = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                rawContents.add(line);
            }
        } catch (IOException exception) {
            throw new BizException("Failed to read import file: " + exception.getMessage());
        }

        String resolvedSourcePrefix = sourcePrefix;
        if (resolvedSourcePrefix == null || resolvedSourcePrefix.isBlank()) {
            resolvedSourcePrefix = stripFilename(file.getOriginalFilename());
        }
        return importRawTexts(taskId, rawContents, resolvedSourcePrefix, autoStart);
    }

    private void requireTask(Long taskId) {
        if (dataTaskService.getById(taskId) == null) {
            throw new BizException("Data task not found: " + taskId);
        }
    }

    private long countItems(Long taskId) {
        return dataItemService.count(new LambdaQueryWrapper<DataItem>()
                .eq(DataItem::getTaskId, taskId));
    }

    private String normalizeSourcePrefix(String sourcePrefix) {
        if (sourcePrefix == null || sourcePrefix.isBlank()) {
            return "batch";
        }
        return sourcePrefix.trim().replaceAll("[^A-Za-z0-9_-]", "-");
    }

    private String buildSourceId(String sourcePrefix, long importBatchNo, int index) {
        String sourceId = sourcePrefix + "-" + importBatchNo + "-" + index;
        return sourceId.length() > 128 ? sourceId.substring(0, 128) : sourceId;
    }

    private String detectDataType(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return "TEXT";
        }

        String lowerContent = rawContent.toLowerCase();
        boolean codeHintMatches = rawContent.contains("{")
                || rawContent.contains("}")
                || rawContent.contains("();")
                || rawContent.contains("=>")
                || lowerContent.contains("import ")
                || lowerContent.contains("class ")
                || lowerContent.contains("def ")
                || lowerContent.contains("function ")
                || lowerContent.contains("public ")
                || lowerContent.contains("private ")
                || lowerContent.contains("#include")
                || lowerContent.contains("select ");

        return codeHintMatches ? "CODE" : "TEXT";
    }

    private String stripFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "file";
        }
        int dotIndex = filename.lastIndexOf('.');
        String stripped = dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
        return normalizeSourcePrefix(stripped);
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
