package com.genai.datapipeline.dataservice.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.genai.datapipeline.dataservice.entity.DataTask;
import com.genai.datapipeline.dataservice.exception.BizException;
import com.genai.datapipeline.dataservice.mapper.DataTaskMapper;
import com.genai.datapipeline.dataservice.service.DataTaskService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class DataTaskServiceImpl extends ServiceImpl<DataTaskMapper, DataTask> implements DataTaskService {

    private static final int STATUS_RUNNING = 1;

    private static final int STATUS_STOPPED = 5;

    @Override
    public DataTask startTask(Long id) {
        DataTask task = getRequiredTask(id);
        task.setStatus(STATUS_RUNNING);
        task.setStartedAt(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        updateById(task);
        return task;
    }

    @Override
    public DataTask stopTask(Long id) {
        DataTask task = getRequiredTask(id);
        task.setStatus(STATUS_STOPPED);
        task.setFinishedAt(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        updateById(task);
        return task;
    }

    private DataTask getRequiredTask(Long id) {
        DataTask task = getById(id);
        if (task == null) {
            throw new BizException("Data task not found: " + id);
        }
        return task;
    }
}
