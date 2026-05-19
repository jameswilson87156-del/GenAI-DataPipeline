package com.genai.datapipeline.dataservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Update data item request")
public class UpdateDataItemRequest {

    @Schema(description = "Task id", example = "1")
    private Long taskId;

    @Size(max = 128)
    @Schema(description = "Source record id", example = "raw-0001")
    private String sourceId;

    @Schema(description = "Raw text content", example = "This is a raw text sample.")
    private String rawContent;

    @Schema(description = "Cleaned text content", example = "This is a cleaned text sample.")
    private String cleanedContent;

    @Size(max = 64)
    @Schema(description = "Content hash", example = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824")
    private String contentHash;

    @Min(0)
    @Max(4)
    @Schema(description = "Item status", example = "2")
    private Integer status;

    @Min(0)
    @Schema(description = "Token count", example = "128")
    private Integer tokenCount;

    @DecimalMin("0.00")
    @DecimalMax("100.00")
    @Schema(description = "Quality score", example = "92.50")
    private BigDecimal qualityScore;

    @Size(max = 1024)
    @Schema(description = "Error message", example = "Failed to parse JSON line.")
    private String errorMessage;
}
