package com.genai.datapipeline.dataservice.service;

public interface DataCleanPipelineService {

    /**
     * 发布任务：扫描任务下所有待清洗数据，并写入 Redis 待处理队列。
     *
     * @param taskId 任务 ID
     */
    void publishTask(Long taskId);

    /**
     * 启动异步消费者：通过 Redis 原子移动 + MySQL CAS 完成可靠清洗。
     *
     * @param taskId 任务 ID
     */
    void executeClean(Long taskId);
}
