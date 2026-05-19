package com.genai.datapipeline.dataservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genai.datapipeline.dataservice.common.StatusConstants;
import com.genai.datapipeline.dataservice.dto.response.NextAnnotationItemResponse;
import com.genai.datapipeline.dataservice.entity.AiAnnotation;
import com.genai.datapipeline.dataservice.entity.DataItem;
import com.genai.datapipeline.dataservice.entity.DataTask;
import com.genai.datapipeline.dataservice.service.DataItemService;
import com.genai.datapipeline.dataservice.service.DataTaskService;
import com.genai.datapipeline.dataservice.support.MybatisPlusTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnotationServiceImplTest {

    @Mock
    private DataItemService dataItemService;

    @Mock
    private DataTaskService dataTaskService;

    private AnnotationServiceImpl annotationService;

    @BeforeAll
    static void initMybatisPlus() {
        MybatisPlusTestSupport.initTableInfo();
    }

    @BeforeEach
    void setUp() {
        annotationService = new AnnotationServiceImpl(
                dataItemService,
                dataTaskService,
                new ObjectMapper()
        );
    }

    @Test
    void getNextAnnotationItemReturnsFirstPendingExpertItem() {
        Long taskId = 9001L;
        DataItem item = DataItem.builder()
                .id(11L)
                .taskId(taskId)
                .dataType("CODE")
                .cleanedContent("public class Demo {}")
                .aiAnnotation(AiAnnotation.builder()
                        .isBug(true)
                        .bugType("NullPointer")
                        .suggestion("add null check")
                        .build())
                .status(StatusConstants.ITEM_PENDING_EXPERT_ANNOTATION)
                .build();
        when(dataItemService.getOne(anyDataItemWrapper())).thenReturn(item);

        NextAnnotationItemResponse response = annotationService.getNextAnnotationItem(taskId);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(11L);
        assertThat(response.getDataType()).isEqualTo("CODE");
        assertThat(response.getAiAnnotation().getBugType()).isEqualTo("NullPointer");
    }

    @Test
    void submitExpertAnnotationCompletesTaskWhenNoUnfinishedItemLeft() {
        Long itemId = 12L;
        Long taskId = 9101L;
        when(dataItemService.getById(itemId)).thenReturn(DataItem.builder()
                .id(itemId)
                .taskId(taskId)
                .status(StatusConstants.ITEM_PENDING_EXPERT_ANNOTATION)
                .build());
        when(dataItemService.update(anyDataItemWrapper())).thenReturn(true);
        when(dataItemService.count(anyDataItemWrapper())).thenReturn(0L);
        when(dataTaskService.update(anyDataTaskWrapper())).thenReturn(true);

        annotationService.submitExpertAnnotation(
                itemId,
                10001L,
                "{\"is_bug\":false,\"bug_type\":\"None\",\"suggestion\":\"approved\"}"
        );

        ArgumentCaptor<Wrapper<DataItem>> itemUpdateCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(dataItemService).update(itemUpdateCaptor.capture());
        assertThat(itemUpdateCaptor.getValue().getSqlSet()).contains("expert_annotation");
        assertThat(itemUpdateCaptor.getValue().getSqlSet()).contains("expert_id");
        assertThat(itemUpdateCaptor.getValue().getSqlSet()).contains("status");

        verify(dataTaskService).update(anyDataTaskWrapper());
    }

    @Test
    void submitExpertAnnotationDoesNotCompleteTaskWhenUnfinishedItemsStillExist() {
        Long itemId = 13L;
        Long taskId = 9201L;
        when(dataItemService.getById(itemId)).thenReturn(DataItem.builder()
                .id(itemId)
                .taskId(taskId)
                .status(StatusConstants.ITEM_PENDING_EXPERT_ANNOTATION)
                .build());
        when(dataItemService.update(anyDataItemWrapper())).thenReturn(true);
        when(dataItemService.count(anyDataItemWrapper())).thenReturn(2L);

        annotationService.submitExpertAnnotation(
                itemId,
                10002L,
                "{\"is_bug\":true,\"bug_type\":\"NullPointer\",\"suggestion\":\"fix it\"}"
        );

        ArgumentCaptor<Wrapper<DataTask>> taskUpdateCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(dataTaskService).update(taskUpdateCaptor.capture());
        assertThat(taskUpdateCaptor.getValue().getSqlSet()).contains("status");
        assertThat(taskUpdateCaptor.getValue().getSqlSet()).contains("processed_count");
    }

    @SuppressWarnings("unchecked")
    private Wrapper<DataItem> anyDataItemWrapper() {
        return any(Wrapper.class);
    }

    @SuppressWarnings("unchecked")
    private Wrapper<DataTask> anyDataTaskWrapper() {
        return any(Wrapper.class);
    }
}
