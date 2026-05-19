package com.genai.datapipeline.dataservice.client;

import com.genai.datapipeline.dataservice.client.dto.PythonCleanRequest;
import com.genai.datapipeline.dataservice.client.dto.PythonCleanResponse;
import com.genai.datapipeline.dataservice.config.FeignPythonWorkerConfig;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "python-clean-worker",
        url = "${genai.pipeline.python-worker.base-url:http://localhost:8000}",
        configuration = FeignPythonWorkerConfig.class,
        fallbackFactory = PythonWorkerFallbackFactory.class
)
public interface PythonWorkerClient {

    @PostMapping("/api/v1/clean")
    PythonCleanResponse clean(@Valid @RequestBody PythonCleanRequest request);
}
