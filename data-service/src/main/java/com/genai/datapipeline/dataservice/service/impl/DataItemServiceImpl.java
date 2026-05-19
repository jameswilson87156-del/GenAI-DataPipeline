package com.genai.datapipeline.dataservice.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.genai.datapipeline.dataservice.entity.DataItem;
import com.genai.datapipeline.dataservice.mapper.DataItemMapper;
import com.genai.datapipeline.dataservice.service.DataItemService;
import org.springframework.stereotype.Service;

@Service
public class DataItemServiceImpl extends ServiceImpl<DataItemMapper, DataItem> implements DataItemService {
}
