package com.genai.datapipeline.dataservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Import raw data items request")
public class ImportDataItemsRequest {

    @NotNull
    @Schema(description = "Task id", example = "1")
    private Long taskId;

    @NotEmpty
    @Size(max = 10000)
    @Schema(description = "Raw text contents, one data item per entry")
    private List<@NotBlank String> rawContents;

    @Size(max = 64)
    @Schema(description = "Source id prefix", example = "batch")
    private String sourcePrefix = "batch";

    @Schema(description = "Whether to publish Redis queue and start async cleaning after import", example = "true")
    private Boolean autoStart = false;
}
