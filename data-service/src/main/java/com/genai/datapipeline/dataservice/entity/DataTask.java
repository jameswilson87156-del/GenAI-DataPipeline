package com.genai.datapipeline.dataservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "data_task", autoResultMap = true)
public class DataTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private String taskName;

    private String taskType;

    private String sourceType;

    private String sourceUri;

    private Integer status;

    private Long totalCount;

    private Long processedCount;

    private Long successCount;

    private Long failedCount;

    private Long assignedWorkerId;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
