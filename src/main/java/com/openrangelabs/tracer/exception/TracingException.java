package com.openrangelabs.tracer.exception;

/**
 * Exception thrown when tracing operations fail
 */
public class TracingException extends RuntimeException {

    public TracingException(String message) {
        super(message);
    }

    public TracingException(String message, Throwable cause) {
        super(message, cause);
    }
}
