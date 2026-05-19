package com.genai.datapipeline.dataservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Update worker node request")
public class UpdateWorkerNodeRequest {

    @Size(max = 128)
    @Schema(description = "Worker node code", example = "worker-node-001")
    private String nodeCode;

    @Size(max = 128)
    @Schema(description = "Worker host", example = "127.0.0.1")
    private String host;

    @Min(1)
    @Max(65535)
    @Schema(description = "Worker port", example = "9001")
    private Integer port;

    @Min(0)
    @Max(3)
    @Schema(description = "Worker status", example = "1")
    private Integer status;

    @Min(1)
    @Schema(description = "Max concurrent task count", example = "4")
    private Integer maxConcurrency;

    @Min(0)
    @Schema(description = "Current running task count", example = "0")
    private Integer currentLoad;

    @Size(max = 64)
    @Schema(description = "Worker version", example = "v0.1.0")
    private String version;

    @Size(max = 512)
    @Schema(description = "Remark", example = "Updated by operator")
    private String remark;
}
