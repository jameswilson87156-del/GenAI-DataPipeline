package com.genai.datapipeline.dataservice.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.genai.datapipeline.dataservice.entity.DataTask;

public interface DataTaskService extends IService<DataTask> {

    DataTask startTask(Long id);

    DataTask stopTask(Long id);
}
