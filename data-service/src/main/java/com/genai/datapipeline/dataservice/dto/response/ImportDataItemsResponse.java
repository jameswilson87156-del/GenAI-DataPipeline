package com.genai.datapipeline.dataservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Import data items response")
public class ImportDataItemsResponse {

    @Schema(description = "Task id")
    private Long taskId;

    @Schema(description = "Imported data item count")
    private Integer importedCount;

    @Schema(description = "Skipped blank line/content count")
    private Integer skippedBlankCount;

    @Schema(description = "Current total data item count under the task")
    private Long totalCount;

    @Schema(description = "Whether async cleaning was triggered")
    private Boolean autoStarted;

    @Schema(description = "Imported data item ids")
    private List<Long> itemIds;
}
