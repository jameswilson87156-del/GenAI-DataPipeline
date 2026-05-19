package com.genai.datapipeline.dataservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.genai.datapipeline.dataservice.client.PythonWorkerClient;
import com.genai.datapipeline.dataservice.client.dto.PythonCleanDataType;
import com.genai.datapipeline.dataservice.client.dto.PythonCleanRequest;
import com.genai.datapipeline.dataservice.client.dto.PythonCleanResponse;
import com.genai.datapipeline.dataservice.common.StatusConstants;
import com.genai.datapipeline.dataservice.config.DataCleanPipelineProperties;
import com.genai.datapipeline.dataservice.entity.DataItem;
import com.genai.datapipeline.dataservice.entity.DataTask;
import com.genai.datapipeline.dataservice.mapper.DataItemMapper;
import com.genai.datapipeline.dataservice.service.AiAnnotationService;
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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataCleanPipelineServiceImplTest {

    @Mock
    private DataItemService dataItemService;

    @Mock
    private DataTaskService dataTaskService;

    @Mock
    private DataItemMapper dataItemMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ListOperations<String, String> listOperations;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private PythonWorkerClient pythonWorkerClient;

    @Mock
    private AiAnnotationService aiAnnotationService;

    @Mock
    private RLock lock;

    @Mock
    private Cursor<String> cursor;

    private DataCleanPipelineProperties pipelineProperties;

    private DataCleanPipelineServiceImpl dataCleanPipelineService;

    @BeforeAll
    static void initMybatisPlus() {
        MybatisPlusTestSupport.initTableInfo();
    }

    @BeforeEach
    void setUp() {
        Executor directExecutor = Runnable::run;
        pipelineProperties = new DataCleanPipelineProperties();
        pipelineProperties.setMaxRetryTimes(3);
        pipelineProperties.setProcessingTimeout(Duration.ofMinutes(10));
        dataCleanPipelineService = new DataCleanPipelineServiceImpl(
                dataItemService,
                dataTaskService,
                dataItemMapper,
                stringRedisTemplate,
                redissonClient,
                pythonWorkerClient,
                aiAnnotationService,
                pipelineProperties,
                directExecutor
        );
    }

    @Test
    void publishTaskPushesPendingItemsToReliablePendingQueue() {
        Long taskId = 2001L;
        DataItem firstItem = DataItem.builder().id(101L).build();
        DataItem secondItem = DataItem.builder().id(102L).build();

        when(dataTaskService.getById(taskId)).thenReturn(new DataTask());
        when(dataItemService.list(anyDataItemWrapper())).thenReturn(List.of(firstItem, secondItem), List.of());
        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);
        when(dataItemService.count(anyDataItemWrapper())).thenReturn(3L, 1L, 0L);
        when(dataTaskService.update(anyDataTaskWrapper())).thenReturn(true);

        dataCleanPipelineService.publishTask(taskId);

        verify(stringRedisTemplate).delete("pipeline:queue:pending:" + taskId);
        verify(listOperations).leftPushAll("pipeline:queue:pending:" + taskId, List.of("101", "102"));
        verify(dataTaskService, atLeastOnce()).update(anyDataTaskWrapper());
    }

    @Test
    void executeCleanMovesPendingToProcessingThenCasUpdatesAndAcks() throws InterruptedException {
        Long taskId = 3001L;
        Long itemId = 501L;
        String pendingKey = "pipeline:queue:pending:" + taskId;
        String processingKey = "pipeline:queue:processing:" + taskId;
        DataItem rawItem = DataItem.builder()
                .id(itemId)
                .taskId(taskId)
                .dataType("TEXT")
                .rawContent("<p>Hello</p>&nbsp; \u0000 world")
                .status(StatusConstants.ITEM_PENDING)
                .build();
        String claimKey = "pipeline:queue:processing:claim:" + taskId;
        String retryKey = "pipeline:queue:retry:" + taskId;

        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(listOperations.rightPopAndLeftPush(pendingKey, processingKey, 2, TimeUnit.SECONDS))
                .thenReturn(String.valueOf(itemId), null);
        when(redissonClient.getLock("pipeline:lock:item:" + itemId)).thenReturn(lock);
        when(lock.tryLock(0, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(dataItemService.getById(itemId)).thenReturn(rawItem);
        when(pythonWorkerClient.clean(any(PythonCleanRequest.class))).thenReturn(PythonCleanResponse.builder()
                .itemId(itemId)
                .dataType(PythonCleanDataType.CODE)
                .cleanedContent("Hello world")
                .build());
        when(dataItemMapper.update(isNull(), anyDataItemWrapper())).thenReturn(1);
        when(dataItemService.count(anyDataItemWrapper())).thenReturn(1L, 1L, 0L);
        when(dataTaskService.update(anyDataTaskWrapper())).thenReturn(true);

        dataCleanPipelineService.executeClean(taskId);

        ArgumentCaptor<PythonCleanRequest> requestCaptor = ArgumentCaptor.forClass(PythonCleanRequest.class);
        verify(pythonWorkerClient).clean(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getItemId()).isEqualTo(itemId);
        assertThat(requestCaptor.getValue().getDataType()).isEqualTo(PythonCleanDataType.TEXT);
        assertThat(requestCaptor.getValue().getRawContent()).contains("Hello");
        verify(aiAnnotationService).preAnnotateItem(itemId);

        ArgumentCaptor<Wrapper<DataItem>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(dataItemMapper).update(isNull(), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("status");

        verify(zSetOperations).add(eq(claimKey), eq(String.valueOf(itemId)), anyDouble());
        verify(stringRedisTemplate).execute(any(), eq(List.of(processingKey, claimKey, retryKey)), any(Object[].class));
        verify(lock).unlock();
        verify(dataTaskService, atLeastOnce()).update(anyDataTaskWrapper());
    }

    @Test
    void executeCleanAcksWhenCasRowsAreZeroBecauseAnotherNodeWon() throws InterruptedException {
        Long taskId = 3002L;
        Long itemId = 601L;
        String pendingKey = "pipeline:queue:pending:" + taskId;
        String processingKey = "pipeline:queue:processing:" + taskId;
        DataItem rawItem = DataItem.builder()
                .id(itemId)
                .taskId(taskId)
                .dataType("TEXT")
                .rawContent("already handled")
                .status(StatusConstants.ITEM_PENDING)
                .build();
        String claimKey = "pipeline:queue:processing:claim:" + taskId;
        String retryKey = "pipeline:queue:retry:" + taskId;

        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(listOperations.rightPopAndLeftPush(pendingKey, processingKey, 2, TimeUnit.SECONDS))
                .thenReturn(String.valueOf(itemId), null);
        when(redissonClient.getLock("pipeline:lock:item:" + itemId)).thenReturn(lock);
        when(lock.tryLock(0, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(dataItemService.getById(itemId)).thenReturn(rawItem);
        when(pythonWorkerClient.clean(any(PythonCleanRequest.class))).thenReturn(PythonCleanResponse.builder()
                .itemId(itemId)
                .dataType(PythonCleanDataType.TEXT)
                .cleanedContent("already handled")
                .build());
        when(dataItemMapper.update(isNull(), anyDataItemWrapper())).thenReturn(0);
        when(dataItemService.count(anyDataItemWrapper())).thenReturn(1L, 1L, 0L);
        when(dataTaskService.update(anyDataTaskWrapper())).thenReturn(true);

        dataCleanPipelineService.executeClean(taskId);

        verify(stringRedisTemplate).execute(any(), eq(List.of(processingKey, claimKey, retryKey)), any(Object[].class));
        verify(lock).unlock();
    }

    @Test
    void executeCleanMarksItemFailedAfterRetryLimit() throws InterruptedException {
        Long taskId = 3003L;
        Long itemId = 701L;
        String pendingKey = "pipeline:queue:pending:" + taskId;
        String processingKey = "pipeline:queue:processing:" + taskId;
        String claimKey = "pipeline:queue:processing:claim:" + taskId;
        String retryKey = "pipeline:queue:retry:" + taskId;

        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(listOperations.rightPopAndLeftPush(pendingKey, processingKey, 2, TimeUnit.SECONDS))
                .thenReturn(String.valueOf(itemId), null);
        when(redissonClient.getLock("pipeline:lock:item:" + itemId)).thenReturn(lock);
        when(lock.tryLock(0, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(dataItemService.getById(itemId)).thenReturn(DataItem.builder()
                .id(itemId)
                .taskId(taskId)
                .dataType("CODE")
                .rawContent("print('boom')")
                .status(StatusConstants.ITEM_PENDING)
                .build());
        when(pythonWorkerClient.clean(any(PythonCleanRequest.class))).thenThrow(new RuntimeException("boom"));
        when(hashOperations.increment(retryKey, String.valueOf(itemId), 1L)).thenReturn(3L);
        when(dataItemMapper.update(isNull(), anyDataItemWrapper())).thenReturn(1);

        dataCleanPipelineService.executeClean(taskId);

        verify(hashOperations).increment(retryKey, String.valueOf(itemId), 1L);
        verify(stringRedisTemplate).execute(any(), eq(List.of(processingKey, claimKey, retryKey)), any(Object[].class));
        verify(lock).unlock();
    }

    @Test
    void recoverProcessingQueuesMovesOnlyExpiredOrUntrackedItemsBackToPendingQueue() {
        String processingKey = "pipeline:queue:processing:9001";
        String pendingKey = "pipeline:queue:pending:9001";
        String claimKey = "pipeline:queue:processing:claim:9001";

        when(stringRedisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn(processingKey);
        when(listOperations.range(processingKey, 0, -1)).thenReturn(List.of("9101", "9102", "9103"));
        when(zSetOperations.rangeByScore(eq(claimKey), anyDouble(), anyDouble())).thenReturn(Set.of("9101"));
        when(zSetOperations.range(claimKey, 0, -1)).thenReturn(Set.of("9101", "9102"));
        when(listOperations.rightPopAndLeftPush(pendingKey, processingKey, 2, TimeUnit.SECONDS)).thenReturn(null);
        doReturn(1L, 1L).when(stringRedisTemplate).execute(any(), anyList(), any(Object[].class));

        dataCleanPipelineService.recoverProcessingQueues();

        verify(stringRedisTemplate, times(2)).execute(any(), anyList(), any(Object[].class));
        verify(listOperations).rightPopAndLeftPush(pendingKey, processingKey, 2, TimeUnit.SECONDS);
    }

    @Test
    void resumeRunningTasksOnStartupRestartsRunningConsumers() {
        Long taskId = 9901L;
        String pendingKey = "pipeline:queue:pending:" + taskId;
        String processingKey = "pipeline:queue:processing:" + taskId;

        when(stringRedisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(false);
        when(dataTaskService.list(anyDataTaskWrapper())).thenReturn(List.of(DataTask.builder()
                .id(taskId)
                .status(StatusConstants.TASK_RUNNING)
                .build()));
        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.rightPopAndLeftPush(pendingKey, processingKey, 2, TimeUnit.SECONDS)).thenReturn(null);

        dataCleanPipelineService.resumeRunningTasksOnStartup();

        verify(listOperations).rightPopAndLeftPush(pendingKey, processingKey, 2, TimeUnit.SECONDS);
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
