package com.genai.datapipeline.dataservice.config;

import feign.Request;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class FeignPythonWorkerConfig {

    @Bean
    public Request.Options pythonWorkerFeignRequestOptions(PythonWorkerProperties properties) {
        return new Request.Options(
                properties.getConnectTimeoutMillis(),
                TimeUnit.MILLISECONDS,
                properties.getReadTimeoutMillis(),
                TimeUnit.MILLISECONDS,
                true
        );
    }
}
