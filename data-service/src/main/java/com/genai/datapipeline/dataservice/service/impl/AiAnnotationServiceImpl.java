package com.genai.datapipeline.dataservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genai.datapipeline.dataservice.common.StatusConstants;
import com.genai.datapipeline.dataservice.entity.AiAnnotation;
import com.genai.datapipeline.dataservice.entity.DataItem;
import com.genai.datapipeline.dataservice.exception.BizException;
import com.genai.datapipeline.dataservice.service.AiAnnotationService;
import com.genai.datapipeline.dataservice.service.DataItemService;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnnotationServiceImpl implements AiAnnotationService {

    private final DataItemService dataItemService;

    private final ChatModel chatModel;

    private final ObjectMapper objectMapper;

    @Override
    public void preAnnotateItem(Long itemId) {
        DataItem item = dataItemService.getById(itemId);
        if (item == null) {
            throw new BizException("Data item not found: " + itemId);
        }

        if (!"CODE".equalsIgnoreCase(item.getDataType())) {
            boolean updated = dataItemService.update(new LambdaUpdateWrapper<DataItem>()
                    .eq(DataItem::getId, itemId)
                    .eq(DataItem::getStatus, StatusConstants.ITEM_PENDING)
                    .set(DataItem::getStatus, StatusConstants.ITEM_PENDING_EXPERT_ANNOTATION));
            log.info("Skip LLM code review because data type is not CODE, itemId={}, dataType={}, updated={}",
                    itemId, item.getDataType(), updated);
            return;
        }

        String prompt = buildCodeAnnotationPrompt(item);
        String responseText = chatModel.chat(prompt);
        AiAnnotation aiAnnotation = parseAnnotation(responseText, itemId);

        boolean updated = dataItemService.update(new LambdaUpdateWrapper<DataItem>()
                .eq(DataItem::getId, itemId)
                .eq(DataItem::getStatus, StatusConstants.ITEM_PENDING)
                .set(DataItem::getAiAnnotation, aiAnnotation)
                .set(DataItem::getStatus, StatusConstants.ITEM_PENDING_EXPERT_ANNOTATION));

        if (!updated) {
            log.warn("Skip AI annotation update because item status changed concurrently, itemId={}", itemId);
        }
    }

    private String buildCodeAnnotationPrompt(DataItem item) {
        return """
                你是一个严格的软件代码审查助手。
                请审查下面这段代码是否存在明显 Bug。
                你必须、无条件只返回一个 JSON 字符串，不要输出任何解释，不要输出 markdown，不要输出 ```。
                返回格式必须完全符合：
                {"is_bug": true, "bug_type": "NullPointer", "suggestion": "..."}
                                
                规则：
                1. is_bug 必须是 true 或 false。
                2. bug_type 必须是简短类型名；如果没有明显 Bug，返回 "None"。
                3. suggestion 必须给出简洁、可执行的修复建议。
                4. 严禁输出 JSON 之外的任何文本。
                                
                item_id=%d
                code:
                %s
                """.formatted(item.getId(), item.getCleanedContent() == null ? "" : item.getCleanedContent());
    }

    private AiAnnotation parseAnnotation(String responseText, Long itemId) {
        try {
            return objectMapper.readValue(normalizeJsonResponse(responseText), AiAnnotation.class);
        } catch (Exception exception) {
            throw new BizException("Failed to parse AI annotation result for item " + itemId + ": " + responseText);
        }
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
