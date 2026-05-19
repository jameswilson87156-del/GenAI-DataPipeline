package com.genai.datapipeline.dataservice.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.genai.datapipeline.dataservice.entity.DataItem;
import com.genai.datapipeline.dataservice.entity.DataTask;
import com.genai.datapipeline.dataservice.entity.WorkerNode;
import org.apache.ibatis.builder.MapperBuilderAssistant;

public final class MybatisPlusTestSupport {

    private MybatisPlusTestSupport() {
    }

    public static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant builderAssistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(builderAssistant, DataItem.class);
        TableInfoHelper.initTableInfo(builderAssistant, DataTask.class);
        TableInfoHelper.initTableInfo(builderAssistant, WorkerNode.class);
    }
}
