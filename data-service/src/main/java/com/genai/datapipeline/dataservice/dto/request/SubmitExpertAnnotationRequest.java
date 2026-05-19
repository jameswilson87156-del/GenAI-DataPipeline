package com.genai.datapipeline.dataservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SubmitExpertAnnotationRequest {

    @NotNull
    private Long itemId;

    @NotNull
    private Long expertId;

    @NotBlank
    private String expertAnnotation;
}
