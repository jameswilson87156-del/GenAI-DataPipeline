package com.genai.datapipeline.dataservice.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "genai.pipeline.python-worker")
public class PythonWorkerProperties {

    @NotBlank
    private String baseUrl = "http://localhost:8000";

    @Positive
    private long connectTimeoutMillis = 2000L;

    @Positive
    private long readTimeoutMillis = 5000L;
}
