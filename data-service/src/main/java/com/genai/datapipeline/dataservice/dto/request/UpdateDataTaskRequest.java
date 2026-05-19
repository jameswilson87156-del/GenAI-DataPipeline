package com.genai.datapipeline.dataservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Update data task request")
public class UpdateDataTaskRequest {

    @Size(max = 128)
    @Schema(description = "Task name", example = "web-text-cleaning-001")
    private String taskName;

    @Size(max = 64)
    @Schema(description = "Task type", example = "clean")
    private String taskType;

    @Size(max = 64)
    @Schema(description = "Source type", example = "file")
    private String sourceType;

    @Size(max = 512)
    @Schema(description = "Source URI", example = "s3://bucket/raw-data/demo.jsonl")
    private String sourceUri;

    @Min(0)
    @Max(5)
    @Schema(description = "Task status", example = "0")
    private Integer status;

    @Min(0)
    @Schema(description = "Total item count", example = "1000")
    private Long totalCount;

    @Min(0)
    @Schema(description = "Processed item count", example = "100")
    private Long processedCount;

    @Min(0)
    @Schema(description = "Successful item count", example = "95")
    private Long successCount;

    @Min(0)
    @Schema(description = "Failed item count", example = "5")
    private Long failedCount;

    @Schema(description = "Assigned worker node id", example = "1")
    private Long assignedWorkerId;

    @Size(max = 512)
    @Schema(description = "Remark", example = "Updated by operator")
    private String remark;
}
