package com.genai.datapipeline.dataservice.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.genai.datapipeline.dataservice.entity.WorkerNode;
import com.genai.datapipeline.dataservice.mapper.WorkerNodeMapper;
import com.genai.datapipeline.dataservice.service.WorkerNodeService;
import org.springframework.stereotype.Service;

@Service
public class WorkerNodeServiceImpl extends ServiceImpl<WorkerNodeMapper, WorkerNode> implements WorkerNodeService {
}
