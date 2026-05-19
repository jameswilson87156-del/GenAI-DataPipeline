package com.genai.datapipeline.dataservice.service;

import com.genai.datapipeline.dataservice.dto.response.ImportDataItemsResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DataImportService {

    ImportDataItemsResponse importRawTexts(Long taskId, List<String> rawContents, String sourcePrefix, boolean autoStart);

    ImportDataItemsResponse importTextFile(Long taskId, MultipartFile file, String sourcePrefix, boolean autoStart);
}
