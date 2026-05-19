package com.genai.datapipeline.dataservice.service;

import com.genai.datapipeline.dataservice.dto.response.NextAnnotationItemResponse;

public interface AnnotationService {

    NextAnnotationItemResponse getNextAnnotationItem(Long taskId);

    void submitExpertAnnotation(Long itemId, Long expertId, String expertAnnotationText);
}
