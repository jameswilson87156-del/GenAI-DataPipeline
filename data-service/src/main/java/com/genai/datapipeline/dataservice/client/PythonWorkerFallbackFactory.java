package com.genai.datapipeline.dataservice.client;

import com.genai.datapipeline.dataservice.client.dto.PythonCleanRequest;
import com.genai.datapipeline.dataservice.client.dto.PythonCleanResponse;
import com.genai.datapipeline.dataservice.client.exception.PythonWorkerUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PythonWorkerFallbackFactory implements FallbackFactory<PythonWorkerClient> {

    @Override
    public PythonWorkerClient create(Throwable cause) {
        return new PythonWorkerClient() {
            @Override
            public PythonCleanResponse clean(PythonCleanRequest request) {
                String message = String.format(
                        "Python clean worker unavailable, itemId=%s, dataType=%s",
                        request.getItemId(),
                        request.getDataType()
                );
                log.error(message, cause);
                throw new PythonWorkerUnavailableException(message, cause);
            }
        };
    }
}
