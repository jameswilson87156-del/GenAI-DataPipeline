package com.genai.datapipeline.dataservice.dto.response;

import com.genai.datapipeline.dataservice.entity.AiAnnotation;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NextAnnotationItemResponse {

    private Long id;

    private String dataType;

    private String cleanedContent;

    private AiAnnotation aiAnnotation;
}
