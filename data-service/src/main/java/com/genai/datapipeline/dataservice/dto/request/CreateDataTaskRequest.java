package com.genai.datapipeline.dataservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Create data task request")
public class CreateDataTaskRequest {

    @NotBlank
    @Size(max = 128)
    @Schema(description = "Task name", example = "web-text-cleaning-001")
    private String taskName;

    @NotBlank
    @Size(max = 64)
    @Schema(description = "Task type", example = "clean")
    private String taskType;

    @NotBlank
    @Size(max = 64)
    @Schema(description = "Source type", example = "file")
    private String sourceType;

    @NotBlank
    @Size(max = 512)
    @Schema(description = "Source URI", example = "s3://bucket/raw-data/demo.jsonl")
    private String sourceUri;

    @Min(0)
    @Schema(description = "Total item count", example = "1000")
    private Long totalCount;

    @Schema(description = "Assigned worker node id", example = "1")
    private Long assignedWorkerId;

    @Size(max = 512)
    @Schema(description = "Remark", example = "Initial import task")
    private String remark;
}
