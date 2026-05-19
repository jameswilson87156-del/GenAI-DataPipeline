package com.genai.datapipeline.dataservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI dataServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("GenAI DataPipeline Data Service API")
                        .description("APIs for data cleaning tasks, data items, and worker nodes.")
                        .version("v0.1.0"));
    }
}
