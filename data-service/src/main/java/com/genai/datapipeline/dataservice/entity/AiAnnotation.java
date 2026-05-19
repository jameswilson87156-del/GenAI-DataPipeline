package com.genai.datapipeline.dataservice.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiAnnotation implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("is_bug")
    private Boolean isBug;

    @JsonProperty("bug_type")
    private String bugType;

    private String suggestion;
}
