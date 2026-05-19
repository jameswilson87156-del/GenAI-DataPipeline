package com.genai.datapipeline.dataservice.controller;

import com.genai.datapipeline.dataservice.common.Result;
import com.genai.datapipeline.dataservice.dto.request.SubmitExpertAnnotationRequest;
import com.genai.datapipeline.dataservice.dto.response.NextAnnotationItemResponse;
import com.genai.datapipeline.dataservice.service.AnnotationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Annotation", description = "Expert annotation APIs")
@RequestMapping("/api/v1/annotation")
public class AnnotationController {

    private final AnnotationService annotationService;

    @Operation(summary = "Get next item waiting expert annotation")
    @GetMapping("/next")
    public Result<NextAnnotationItemResponse> getNextAnnotationItem(@RequestParam Long taskId) {
        return Result.ok(annotationService.getNextAnnotationItem(taskId));
    }

    @Operation(summary = "Submit expert final annotation")
    @PostMapping("/submit")
    public Result<Void> submitExpertAnnotation(@Valid @RequestBody SubmitExpertAnnotationRequest request) {
        annotationService.submitExpertAnnotation(
                request.getItemId(),
                request.getExpertId(),
                request.getExpertAnnotation()
        );
        return Result.ok();
    }
}
