package com.genai.datapipeline.dataservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "data_item", autoResultMap = true)
public class DataItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long taskId;

    private String sourceId;

    private String dataType;

    private String rawContent;

    private String cleanedContent;

    @TableField(value = "ai_annotation", typeHandler = JacksonTypeHandler.class)
    private AiAnnotation aiAnnotation;

    @TableField(value = "expert_annotation", typeHandler = JacksonTypeHandler.class)
    private AiAnnotation expertAnnotation;

    private Long expertId;

    private String contentHash;

    private Integer status;

    private Integer tokenCount;

    private BigDecimal qualityScore;

    private String errorMessage;

    private LocalDateTime cleanedAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
