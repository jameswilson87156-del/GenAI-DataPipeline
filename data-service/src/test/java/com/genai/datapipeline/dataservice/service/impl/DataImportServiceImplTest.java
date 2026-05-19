package com.genai.datapipeline.dataservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.genai.datapipeline.dataservice.common.StatusConstants;
import com.genai.datapipeline.dataservice.dto.response.ImportDataItemsResponse;
import com.genai.datapipeline.dataservice.entity.DataItem;
import com.genai.datapipeline.dataservice.entity.DataTask;
import com.genai.datapipeline.dataservice.entity.DataTask;
import com.genai.datapipeline.dataservice.service.DataCleanPipelineService;
import com.genai.datapipeline.dataservice.service.DataItemService;
import com.genai.datapipeline.dataservice.service.DataTaskService;
import com.genai.datapipeline.dataservice.support.MybatisPlusTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataImportServiceImplTest {

    @Mock
    private DataItemService dataItemService;

    @Mock
    private DataTaskService dataTaskService;

    @Mock
    private DataCleanPipelineService dataCleanPipelineService;

    @InjectMocks
    private DataImportServiceImpl dataImportService;

    @BeforeAll
    static void initMybatisPlus() {
        MybatisPlusTestSupport.initTableInfo();
    }

    @Test
    void importRawTextsSkipsBlankContentAndCanAutoStartCleaning() {
        Long taskId = 1001L;
        when(dataTaskService.getById(taskId)).thenReturn(new DataTask());
        when(dataItemService.saveBatch(any(Collection.class), eq(500))).thenReturn(true);
        when(dataItemService.count(anyDataItemWrapper())).thenReturn(2L);
        when(dataTaskService.update(anyDataTaskWrapper())).thenReturn(true);

        ImportDataItemsResponse response = dataImportService.importRawTexts(
                taskId,
                Arrays.asList("  <p>Hello</p>  ", "", null, "Second text"),
                "source name",
                true
        );

        assertThat(response.getTaskId()).isEqualTo(taskId);
        assertThat(response.getImportedCount()).isEqualTo(2);
        assertThat(response.getSkippedBlankCount()).isEqualTo(2);
        assertThat(response.getTotalCount()).isEqualTo(2L);
        assertThat(response.getAutoStarted()).isTrue();

        ArgumentCaptor<Collection<DataItem>> dataItemsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(dataItemService).saveBatch(dataItemsCaptor.capture(), eq(500));
        assertThat(dataItemsCaptor.getValue())
                .hasSize(2)
                .allSatisfy(item -> {
                    assertThat(item.getTaskId()).isEqualTo(taskId);
                    assertThat(item.getSourceId()).startsWith("source-name-");
                    assertThat(item.getDataType()).isEqualTo("TEXT");
                    assertThat(item.getContentHash()).hasSize(64);
                    assertThat(item.getStatus()).isEqualTo(StatusConstants.ITEM_PENDING);
                    assertThat(item.getTokenCount()).isZero();
                    assertThat(item.getDeleted()).isZero();
                });

        verify(dataCleanPipelineService).publishTask(taskId);
        verify(dataCleanPipelineService).executeClean(taskId);
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
