package com.genai.datapipeline.dataservice.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PythonCleanResponse {

    private Long itemId;

    private PythonCleanDataType dataType;

    private String cleanedContent;
}
