package com.genai.datapipeline.dataservice.controller;

import com.genai.datapipeline.dataservice.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@Tag(name = "Health", description = "Service health APIs")
public class HealthController {

    @Operation(summary = "Check service health")
    @GetMapping("/api/health")
    public Result<Map<String, Object>> health() {
        return Result.ok(Map.of(
                "service", "data-service",
                "status", "UP",
                "time", LocalDateTime.now()
        ));
    }
}
