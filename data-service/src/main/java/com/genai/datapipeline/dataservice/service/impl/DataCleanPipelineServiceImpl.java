package com.genai.datapipeline.dataservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.genai.datapipeline.dataservice.client.PythonWorkerClient;
import com.genai.datapipeline.dataservice.client.dto.PythonCleanDataType;
import com.genai.datapipeline.dataservice.client.dto.PythonCleanRequest;
import com.genai.datapipeline.dataservice.client.dto.PythonCleanResponse;
import com.genai.datapipeline.dataservice.common.StatusConstants;
import com.genai.datapipeline.dataservice.config.DataCleanPipelineProperties;
import com.genai.datapipeline.dataservice.entity.DataItem;
import com.genai.datapipeline.dataservice.entity.DataTask;
import com.genai.datapipeline.dataservice.exception.BizException;
import com.genai.datapipeline.dataservice.mapper.DataItemMapper;
import com.genai.datapipeline.dataservice.service.AiAnnotationService;
import com.genai.datapipeline.dataservice.service.DataCleanPipelineService;
import com.genai.datapipeline.dataservice.service.DataItemService;
import com.genai.datapipeline.dataservice.service.DataTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataCleanPipelineServiceImpl implements DataCleanPipelineService {

    private static final String PENDING_QUEUE_KEY_PATTERN = "pipeline:queue:pending:%d";

    private static final String PENDING_QUEUE_KEY_PREFIX = "pipeline:queue:pending:";

    private static final String PROCESSING_QUEUE_KEY_PATTERN = "pipeline:queue:processing:%d";

    private static final String PROCESSING_QUEUE_KEY_PREFIX = "pipeline:queue:processing:";

    private static final String PROCESSING_QUEUE_SCAN_PATTERN = PROCESSING_QUEUE_KEY_PREFIX + "*";

    private static final String PROCESSING_CLAIM_KEY_PATTERN = "pipeline:queue:processing:claim:%d";

    private static final String RETRY_COUNTER_KEY_PATTERN = "pipeline:queue:retry:%d";

    private static final String ITEM_LOCK_KEY_PATTERN = "pipeline:lock:item:%d";

    private static final int PUBLISH_BATCH_SIZE = 1000;

    private static final int RECOVERY_SCAN_COUNT = 1000;

    private static final long CONSUME_BLOCK_SECONDS = 2L;

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1024;

    private static final DefaultRedisScript<Long> ACK_PROCESSING_ITEM_SCRIPT = buildLongScript("""
            local removed = redis.call('LREM', KEYS[1], 1, ARGV[1])
            redis.call('ZREM', KEYS[2], ARGV[1])
            redis.call('HDEL', KEYS[3], ARGV[1])
            return removed
            """);

    private static final DefaultRedisScript<Long> RECOVER_PROCESSING_ITEM_SCRIPT = buildLongScript("""
            local removed = redis.call('LREM', KEYS[1], 1, ARGV[1])
            redis.call('ZREM', KEYS[3], ARGV[1])
            if removed > 0 then
              redis.call('LPUSH', KEYS[2], ARGV[1])
            end
            return removed
            """);

    private final DataItemService dataItemService;

    private final DataTaskService dataTaskService;

    private final DataItemMapper dataItemMapper;

    private final StringRedisTemplate stringRedisTemplate;

    private final RedissonClient redissonClient;

    private final PythonWorkerClient pythonWorkerClient;

    private final AiAnnotationService aiAnnotationService;

    private final DataCleanPipelineProperties pipelineProperties;

    @Qualifier("dataCleanExecutor")
    private final Executor dataCleanExecutor;

    private final Set<Long> activeTasks = ConcurrentHashMap.newKeySet();

    /**
     * 生产者：将当前任务下所有 status=0 的 data_item ID 推入待处理队列。
     *
     * <p>这里按 ID 分批扫描，避免千万级数据一次性加载到 JVM 内存。
     * 重新发布任务时会清空 pending 队列后重新装载 MySQL 中仍然是 pending 的数据；
     * processing 队列不清空，避免误删正在其他节点处理中的备份记录。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishTask(Long taskId) {
        DataTask task = dataTaskService.getById(taskId);
        if (task == null) {
            throw new BizException("Data task not found: " + taskId);
        }

        String pendingKey = pendingQueueKey(taskId);
        stringRedisTemplate.delete(pendingKey);

        long lastId = 0L;
        long publishedCount = 0L;
        while (true) {
            List<String> itemIds = dataItemService.list(new LambdaQueryWrapper<DataItem>()
                            .select(DataItem::getId)
                            .eq(DataItem::getTaskId, taskId)
                            .eq(DataItem::getStatus, StatusConstants.ITEM_PENDING)
                            .gt(DataItem::getId, lastId)
                            .orderByAsc(DataItem::getId)
                            .last("LIMIT " + PUBLISH_BATCH_SIZE))
                    .stream()
                    .map(DataItem::getId)
                    .map(String::valueOf)
                    .toList();

            if (itemIds.isEmpty()) {
                break;
            }

            stringRedisTemplate.opsForList().leftPushAll(pendingKey, itemIds);
            publishedCount += itemIds.size();
            lastId = Long.parseLong(itemIds.get(itemIds.size() - 1));
        }

        LocalDateTime now = LocalDateTime.now();
        long totalCount = countItems(taskId);
        long successCount = countItemsByStatus(taskId, StatusConstants.ITEM_COMPLETED);
        long failedCount = countItemsByStatus(taskId, StatusConstants.ITEM_FAILED);
        dataTaskService.update(new LambdaUpdateWrapper<DataTask>()
                .eq(DataTask::getId, taskId)
                .set(DataTask::getStatus, StatusConstants.TASK_RUNNING)
                .set(DataTask::getTotalCount, totalCount)
                .set(DataTask::getProcessedCount, successCount + failedCount)
                .set(DataTask::getSuccessCount, successCount)
                .set(DataTask::getFailedCount, failedCount)
                .set(DataTask::getStartedAt, now)
                .set(DataTask::getFinishedAt, null)
                .set(DataTask::getUpdateTime, now));

        log.info("Published data clean task, taskId={}, pendingCount={}", taskId, publishedCount);
    }

    /**
     * 消费者入口：异步拉起清洗线程。
     *
     * <p>真正的可靠性不依赖 JVM 内存队列，而依赖 Redis pending/processing 双队列
     * 和 MySQL CAS，所以服务重启后可以再次调用该方法继续消费。</p>
     */
    @Override
    public void executeClean(Long taskId) {
        if (!activeTasks.add(taskId)) {
            log.debug("Clean task is already active on this node, taskId={}", taskId);
            return;
        }

        dataCleanExecutor.execute(() -> {
            try {
                cleanTask(taskId);
            } catch (Exception exception) {
                log.error("Clean task executor failed, taskId={}", taskId, exception);
                refreshTaskProgress(taskId);
            } finally {
                activeTasks.remove(taskId);
            }
        });
    }

    private void cleanTask(Long taskId) {
        String pendingKey = pendingQueueKey(taskId);
        String processingKey = processingQueueKey(taskId);

        while (true) {
            /*
             * BRPOPLPUSH 语义：
             * 1. 从 pending 队列右侧阻塞弹出一个 itemId；
             * 2. 同一个 Redis 原子操作把该 itemId 推入 processing 队列左侧；
             * 3. Java 进程在这之后宕机，itemId 仍保留在 processing 队列，可由定时补偿恢复。
             */
            String value = stringRedisTemplate.opsForList()
                    .rightPopAndLeftPush(pendingKey, processingKey, CONSUME_BLOCK_SECONDS, TimeUnit.SECONDS);
            if (value == null) {
                break;
            }

            recordProcessingClaim(taskId, value);
            Long itemId = parseItemId(value);
            if (itemId == null) {
                ackProcessingItem(taskId, processingKey, value);
                continue;
            }

            ProcessAttempt attempt = cleanItemByCas(itemId);
            if (attempt.result == ProcessResult.SUCCESS || attempt.result == ProcessResult.SKIPPED) {
                ackProcessingItem(taskId, processingKey, value);
                continue;
            }

            if (attempt.result == ProcessResult.RETRYABLE_FAILURE) {
                handleRetryableFailure(taskId, itemId, processingKey, value, attempt.errorMessage);
            }
        }

        refreshTaskProgress(taskId);
    }

    /**
     * 单条清洗：Redisson 锁减少重复清洗开销，MySQL CAS 保证最终不重写。
     *
     * <p>锁使用 watchdog 模式，不传固定 leaseTime。只要 JVM 存活，Redisson 会自动续期；
     * 进程宕机后锁会自动过期，不会形成永久死锁。</p>
     */
    private ProcessAttempt cleanItemByCas(Long itemId) {
        RLock lock = redissonClient.getLock(itemLockKey(itemId));
        boolean locked = false;
        try {
            locked = lock.tryLock(0, TimeUnit.SECONDS);
            if (!locked) {
                log.info("Item is locked by another worker, leave it in processing queue, itemId={}", itemId);
                return ProcessAttempt.of(ProcessResult.RETRY_LATER, null);
            }

            DataItem item = dataItemService.getById(itemId);
            if (item == null || item.getStatus() == null || item.getStatus() != StatusConstants.ITEM_PENDING) {
                return ProcessAttempt.of(ProcessResult.SKIPPED, null);
            }

            String cleanedContent = cleanContent(item);
            LocalDateTime now = LocalDateTime.now();

            /*
             * MySQL CAS 乐观锁：
             * WHERE id = ? AND status = 0 是抢占条件。
             * rows > 0：本节点成功抢占并写入清洗结果，可以 ACK。
             * rows == 0：其他节点已经先一步处理或状态已变化，也可以 ACK 跳过。
             *
             * 注意：当前第三阶段把 status=2 定义为 AI 预标注完成/待专家标注；
             * 因此清洗成功后这里只先落 cleaned_content，status 仍保持 pending(0)，
             * 然后异步触发 AI 预标注服务去写 ai_annotation 并把状态改成 2。
             */
            int rows = dataItemMapper.update(null, new LambdaUpdateWrapper<DataItem>()
                    .eq(DataItem::getId, itemId)
                    .eq(DataItem::getStatus, StatusConstants.ITEM_PENDING)
                    .set(DataItem::getCleanedContent, cleanedContent)
                    .set(DataItem::getCleanedAt, now)
                    .set(DataItem::getErrorMessage, null)
                    .set(DataItem::getUpdateTime, now));

            if (rows > 0) {
                triggerAiPreAnnotation(item.getTaskId(), itemId);
            }

            return rows > 0
                    ? ProcessAttempt.of(ProcessResult.SUCCESS, null)
                    : ProcessAttempt.of(ProcessResult.SKIPPED, null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while locking data item, itemId={}", itemId, exception);
            return ProcessAttempt.of(ProcessResult.RETRY_LATER, null);
        } catch (Exception exception) {
            log.error("Failed to clean data item, leave it in processing queue for recovery, itemId={}", itemId, exception);
            return ProcessAttempt.of(ProcessResult.RETRYABLE_FAILURE, abbreviateErrorMessage(exception.getMessage()));
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * ACK：从 processing 备份队列删除已经确认完成或确认跳过的 itemId。
     */
    private void ackProcessingItem(Long taskId, String processingKey, String itemIdValue) {
        Long removed = stringRedisTemplate.execute(
                ACK_PROCESSING_ITEM_SCRIPT,
                List.of(processingKey, processingClaimKey(taskId), retryCounterKey(taskId)),
                itemIdValue
        );
        log.debug("Ack processing item, processingKey={}, itemId={}, removed={}", processingKey, itemIdValue, removed);
    }

    /**
     * 每 5 分钟扫描 Redis 中所有 processing 队列，把残留 itemId 重新推回 pending 队列。
     *
     * <p>生产环境禁止使用 Redis KEYS 扫全库，这里使用 SCAN 游标分批扫描，避免阻塞 Redis。
     * 如果某个消费者在 BRPOPLPUSH 后宕机，它没有机会 ACK，itemId 会留在 processing；
     * 本方法会通过 RPOPLPUSH 原子地移回 pending，让后续消费者自动重试。</p>
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000L, initialDelay = 5 * 60 * 1000L)
    public void recoverProcessingQueues() {
        ScanOptions options = ScanOptions.scanOptions()
                .match(PROCESSING_QUEUE_SCAN_PATTERN)
                .count(RECOVERY_SCAN_COUNT)
                .build();

        Set<Long> recoveredTaskIds = new LinkedHashSet<>();
        try (Cursor<String> cursor = stringRedisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                Long recoveredTaskId = recoverProcessingQueue(cursor.next());
                if (recoveredTaskId != null) {
                    recoveredTaskIds.add(recoveredTaskId);
                }
            }
        } catch (Exception exception) {
            log.error("Failed to scan processing queues for recovery", exception);
        }

        recoveredTaskIds.forEach(this::executeClean);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resumeRunningTasksOnStartup() {
        recoverProcessingQueues();

        List<Long> runningTaskIds = dataTaskService.list(new LambdaQueryWrapper<DataTask>()
                        .select(DataTask::getId)
                        .eq(DataTask::getStatus, StatusConstants.TASK_RUNNING))
                .stream()
                .map(DataTask::getId)
                .toList();

        runningTaskIds.forEach(this::executeClean);
        if (!runningTaskIds.isEmpty()) {
            log.info("Resumed running clean tasks on startup, taskIds={}", runningTaskIds);
        }
    }

    private Long recoverProcessingQueue(String processingKey) {
        Long taskId = parseTaskIdFromProcessingKey(processingKey);
        if (taskId == null) {
            return null;
        }

        String pendingKey = pendingQueueKey(taskId);
        String claimKey = processingClaimKey(taskId);
        Set<String> candidates = recoverableProcessingItems(processingKey, claimKey);
        int recoveredCount = 0;

        for (String itemIdValue : candidates) {
            Long recovered = stringRedisTemplate.execute(
                    RECOVER_PROCESSING_ITEM_SCRIPT,
                    List.of(processingKey, pendingKey, claimKey),
                    itemIdValue
            );
            if (recovered != null && recovered > 0) {
                recoveredCount++;
            }
        }

        if (recoveredCount > 0) {
            log.warn("Recovered processing queue items, processingKey={}, pendingKey={}, recoveredCount={}",
                    processingKey, pendingKey, recoveredCount);
            return taskId;
        }

        return null;
    }

    private void refreshTaskProgress(Long taskId) {
        LocalDateTime now = LocalDateTime.now();
        long totalCount = countItems(taskId);
        long successCount = countItemsByStatus(taskId, StatusConstants.ITEM_COMPLETED);
        long failedCount = countItemsByStatus(taskId, StatusConstants.ITEM_FAILED);
        long processedCount = successCount + failedCount;
        long pendingQueueSize = queueSize(pendingQueueKey(taskId));
        long processingQueueSize = queueSize(processingQueueKey(taskId));
        boolean hasUnfinishedItems = processedCount < totalCount || pendingQueueSize > 0 || processingQueueSize > 0;
        int status = hasUnfinishedItems
                ? StatusConstants.TASK_RUNNING
                : failedCount == 0 ? StatusConstants.TASK_COMPLETED : StatusConstants.TASK_FAILED;

        dataTaskService.update(new LambdaUpdateWrapper<DataTask>()
                .eq(DataTask::getId, taskId)
                .set(DataTask::getStatus, status)
                .set(DataTask::getTotalCount, totalCount)
                .set(DataTask::getProcessedCount, processedCount)
                .set(DataTask::getSuccessCount, successCount)
                .set(DataTask::getFailedCount, failedCount)
                .set(DataTask::getFinishedAt, hasUnfinishedItems ? null : now)
                .set(DataTask::getUpdateTime, now));
    }

    private long countItems(Long taskId) {
        return dataItemService.count(new LambdaQueryWrapper<DataItem>()
                .eq(DataItem::getTaskId, taskId));
    }

    private long countItemsByStatus(Long taskId, int status) {
        return dataItemService.count(new LambdaQueryWrapper<DataItem>()
                .eq(DataItem::getTaskId, taskId)
                .eq(DataItem::getStatus, status));
    }

    private long queueSize(String queueKey) {
        Long size = stringRedisTemplate.opsForList().size(queueKey);
        return size == null ? 0L : size;
    }

    private void recordProcessingClaim(Long taskId, String itemIdValue) {
        stringRedisTemplate.opsForZSet().add(processingClaimKey(taskId), itemIdValue, System.currentTimeMillis());
    }

    private void handleRetryableFailure(Long taskId,
                                        Long itemId,
                                        String processingKey,
                                        String itemIdValue,
                                        String errorMessage) {
        long retryTimes = incrementRetryCount(taskId, itemIdValue);
        if (retryTimes < pipelineProperties.getMaxRetryTimes()) {
            log.warn("Retryable clean failure, leave item in processing queue, taskId={}, itemId={}, retryTimes={}",
                    taskId, itemId, retryTimes);
            recordProcessingClaim(taskId, itemIdValue);
            return;
        }

        int rows = markItemFailed(itemId, errorMessage);
        log.error("Retry limit exceeded, mark item as failed and ack, taskId={}, itemId={}, retryTimes={}, rows={}",
                taskId, itemId, retryTimes, rows);
        ackProcessingItem(taskId, processingKey, itemIdValue);
    }

    private long incrementRetryCount(Long taskId, String itemIdValue) {
        Long retryTimes = stringRedisTemplate.opsForHash().increment(retryCounterKey(taskId), itemIdValue, 1L);
        return retryTimes == null ? 0L : retryTimes;
    }

    private int markItemFailed(Long itemId, String errorMessage) {
        return dataItemMapper.update(null, new LambdaUpdateWrapper<DataItem>()
                .eq(DataItem::getId, itemId)
                .eq(DataItem::getStatus, StatusConstants.ITEM_PENDING)
                .set(DataItem::getStatus, StatusConstants.ITEM_FAILED)
                .set(DataItem::getErrorMessage, abbreviateErrorMessage(errorMessage))
                .set(DataItem::getUpdateTime, LocalDateTime.now()));
    }

    private Long parseItemId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            log.warn("Invalid data item id in queue: {}", value);
            return null;
        }
    }

    private Long parseTaskIdFromProcessingKey(String processingKey) {
        try {
            return Long.valueOf(processingKey.substring(PROCESSING_QUEUE_KEY_PREFIX.length()));
        } catch (Exception exception) {
            log.warn("Invalid processing queue key: {}", processingKey, exception);
            return null;
        }
    }

    private Set<String> recoverableProcessingItems(String processingKey, String claimKey) {
        Set<String> candidates = new LinkedHashSet<>();
        long timeoutScore = System.currentTimeMillis() - processingTimeoutMillis();

        Set<String> expiredClaimItems = stringRedisTemplate.opsForZSet()
                .rangeByScore(claimKey, Double.NEGATIVE_INFINITY, timeoutScore);
        if (expiredClaimItems != null) {
            candidates.addAll(expiredClaimItems);
        }

        List<String> processingItems = stringRedisTemplate.opsForList().range(processingKey, 0, -1);
        if (processingItems == null || processingItems.isEmpty()) {
            return candidates;
        }

        Set<String> claimedItems = stringRedisTemplate.opsForZSet().range(claimKey, 0, -1);
        Set<String> claimedItemSet = claimedItems == null ? Collections.emptySet() : claimedItems;
        for (String itemIdValue : processingItems) {
            if (!claimedItemSet.contains(itemIdValue)) {
                candidates.add(itemIdValue);
            }
        }
        return candidates;
    }

    private long processingTimeoutMillis() {
        Duration timeout = pipelineProperties.getProcessingTimeout();
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            return Duration.ofMinutes(10).toMillis();
        }
        return timeout.toMillis();
    }

    /**
     * AI 预标注不阻塞当前消费者主流程。
     *
     * <p>如果大模型调用失败，保持 item 仍为 pending，可由后续人工或补偿机制再次处理；
     * 不回滚已经成功的 Python 清洗结果，避免重复清洗和结果丢失。</p>
     */
    private void triggerAiPreAnnotation(Long taskId, Long itemId) {
        dataCleanExecutor.execute(() -> {
            try {
                aiAnnotationService.preAnnotateItem(itemId);
            } catch (Exception exception) {
                log.error("AI pre-annotation failed, itemId={}", itemId, exception);
                markItemFailed(itemId, "AI pre-annotation failed: " + exception.getMessage());
            } finally {
                refreshTaskProgress(taskId);
            }
        });
    }

    /**
     * 通过 Feign 把清洗任务派发给 Python 算力节点。
     *
     * <p>如果 Python 节点不可用，FallbackFactory 会抛出受控异常，
     * 上层 catch 后会继续走我们已有的 Redis 重试 / ITEM_FAILED 兜底状态机。</p>
     */
    private String cleanContent(DataItem item) {
        PythonCleanRequest request = PythonCleanRequest.builder()
                .itemId(item.getId())
                .dataType(detectDataType(item))
                .rawContent(item.getRawContent() == null ? "" : item.getRawContent())
                .build();

        PythonCleanResponse response = pythonWorkerClient.clean(request);
        if (response == null) {
            throw new IllegalStateException("Python clean worker returned null response");
        }
        if (response.getCleanedContent() == null) {
            throw new IllegalStateException("Python clean worker returned null cleaned content");
        }
        return response.getCleanedContent();
    }

    /**
     * 优先使用 data_item.data_type；如果历史数据还没回填，再退回到启发式判断。
     */
    private PythonCleanDataType detectDataType(DataItem item) {
        if ("CODE".equalsIgnoreCase(item.getDataType())) {
            return PythonCleanDataType.CODE;
        }
        if ("TEXT".equalsIgnoreCase(item.getDataType())) {
            return PythonCleanDataType.TEXT;
        }

        String sourceId = item.getSourceId() == null ? "" : item.getSourceId().toLowerCase(Locale.ROOT);
        String rawContent = item.getRawContent() == null ? "" : item.getRawContent();
        String lowerContent = rawContent.toLowerCase(Locale.ROOT);

        boolean fileHintMatches = sourceId.endsWith(".java")
                || sourceId.endsWith(".py")
                || sourceId.endsWith(".js")
                || sourceId.endsWith(".ts")
                || sourceId.endsWith(".go")
                || sourceId.endsWith(".cpp")
                || sourceId.endsWith(".c")
                || sourceId.endsWith(".sql")
                || sourceId.endsWith(".json")
                || sourceId.endsWith(".xml")
                || sourceId.endsWith(".yaml")
                || sourceId.endsWith(".yml")
                || sourceId.endsWith(".sh");

        boolean contentHintMatches = rawContent.contains("{")
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

        return fileHintMatches || contentHintMatches
                ? PythonCleanDataType.CODE
                : PythonCleanDataType.TEXT;
    }

    private String pendingQueueKey(Long taskId) {
        return String.format(PENDING_QUEUE_KEY_PATTERN, taskId);
    }

    private String processingQueueKey(Long taskId) {
        return String.format(PROCESSING_QUEUE_KEY_PATTERN, taskId);
    }

    private String processingClaimKey(Long taskId) {
        return String.format(PROCESSING_CLAIM_KEY_PATTERN, taskId);
    }

    private String retryCounterKey(Long taskId) {
        return String.format(RETRY_COUNTER_KEY_PATTERN, taskId);
    }

    private String itemLockKey(Long itemId) {
        return String.format(ITEM_LOCK_KEY_PATTERN, itemId);
    }

    private String abbreviateErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "clean failed after retry limit";
        }
        if (errorMessage.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return errorMessage;
        }
        return errorMessage.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    private static DefaultRedisScript<Long> buildLongScript(String scriptText) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(scriptText);
        script.setResultType(Long.class);
        return script;
    }

    private enum ProcessResult {
        SUCCESS,
        SKIPPED,
        RETRY_LATER,
        RETRYABLE_FAILURE
    }

    private static final class ProcessAttempt {

        private final ProcessResult result;

        private final String errorMessage;

        private ProcessAttempt(ProcessResult result, String errorMessage) {
            this.result = result;
            this.errorMessage = errorMessage;
        }

        private static ProcessAttempt of(ProcessResult result, String errorMessage) {
            return new ProcessAttempt(result, errorMessage);
        }
    }
}
