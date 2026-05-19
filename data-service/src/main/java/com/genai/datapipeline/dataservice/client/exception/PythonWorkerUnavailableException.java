package com.genai.datapipeline.dataservice.client.exception;

public class PythonWorkerUnavailableException extends RuntimeException {

    public PythonWorkerUnavailableException(String message) {
        super(message);
    }

    public PythonWorkerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
