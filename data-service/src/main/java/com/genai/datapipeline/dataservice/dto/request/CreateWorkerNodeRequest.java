package com.genai.datapipeline.dataservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Create worker node request")
public class CreateWorkerNodeRequest {

    @NotBlank
    @Size(max = 128)
    @Schema(description = "Worker node code", example = "worker-node-001")
    private String nodeCode;

    @NotBlank
    @Size(max = 128)
    @Schema(description = "Worker host", example = "127.0.0.1")
    private String host;

    @NotNull
    @Min(1)
    @Max(65535)
    @Schema(description = "Worker port", example = "9001")
    private Integer port;

    @Min(1)
    @Schema(description = "Max concurrent task count", example = "4")
    private Integer maxConcurrency;

    @Size(max = 64)
    @Schema(description = "Worker version", example = "v0.1.0")
    private String version;

    @Size(max = 512)
    @Schema(description = "Remark", example = "Local worker node")
    private String remark;
}
