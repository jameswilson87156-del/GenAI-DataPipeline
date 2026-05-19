package com.genai.datapipeline.dataservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.genai.datapipeline.dataservice.entity.WorkerNode;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkerNodeMapper extends BaseMapper<WorkerNode> {
}
