package com.genai.datapipeline.dataservice.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "genai.pipeline.clean")
public class DataCleanPipelineProperties {

    @Min(1)
    private int maxRetryTimes = 3;

    private Duration processingTimeout = Duration.ofMinutes(10);
}
