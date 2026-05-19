package com.genai.datapipeline.dataservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genai.datapipeline.dataservice.common.StatusConstants;
import com.genai.datapipeline.dataservice.entity.DataItem;
import com.genai.datapipeline.dataservice.service.DataItemService;
import com.genai.datapipeline.dataservice.support.MybatisPlusTestSupport;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAnnotationServiceImplTest {

    @Mock
    private DataItemService dataItemService;

    @Mock
    private ChatModel chatModel;

    private AiAnnotationServiceImpl aiAnnotationService;

    @BeforeAll
    static void initMybatisPlus() {
        MybatisPlusTestSupport.initTableInfo();
    }

    @BeforeEach
    void setUp() {
        aiAnnotationService = new AiAnnotationServiceImpl(
                dataItemService,
                chatModel,
                new ObjectMapper()
        );
    }

    @Test
    void preAnnotateItemParsesJsonAndUpdatesCodeItem() {
        Long itemId = 801L;
        when(dataItemService.getById(itemId)).thenReturn(DataItem.builder()
                .id(itemId)
                .dataType("CODE")
                .cleanedContent("public class Demo { }")
                .status(StatusConstants.ITEM_PENDING)
                .build());
        when(chatModel.chat(any(String.class))).thenReturn("""
                {"is_bug": true, "bug_type": "NullPointer", "suggestion": "add null check"}
                """);
        when(dataItemService.update(anyDataItemWrapper())).thenReturn(true);

        aiAnnotationService.preAnnotateItem(itemId);

        verify(chatModel).chat(any(String.class));
        ArgumentCaptor<Wrapper<DataItem>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(dataItemService).update(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSet()).contains("ai_annotation");
        assertThat(wrapperCaptor.getValue().getSqlSet()).contains("status");
    }

    @Test
    void preAnnotateItemMarksNonCodeItemAsAiAnnotatedWithoutCallingLlm() {
        Long itemId = 802L;
        when(dataItemService.getById(itemId)).thenReturn(DataItem.builder()
                .id(itemId)
                .dataType("TEXT")
                .cleanedContent("clean text")
                .status(StatusConstants.ITEM_PENDING)
                .build());
        when(dataItemService.update(anyDataItemWrapper())).thenReturn(true);

        aiAnnotationService.preAnnotateItem(itemId);

        verify(chatModel, never()).chat(any(String.class));
        ArgumentCaptor<Wrapper<DataItem>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(dataItemService).update(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSet()).contains("status");
    }

    @SuppressWarnings("unchecked")
    private Wrapper<DataItem> anyDataItemWrapper() {
        return any(Wrapper.class);
    }
}
