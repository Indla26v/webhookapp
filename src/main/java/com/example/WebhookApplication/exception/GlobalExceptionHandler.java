package com.example.WebhookApplication.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RestClientException.class)
    public void handleRestClientException(RestClientException ex) {
        logger.error("REST client error: ", ex);
    }

    @ExceptionHandler(ResourceAccessException.class)
    public void handleResourceAccessException(ResourceAccessException ex) {
        logger.error("Resource access error (likely timeout): ", ex);
    }

    @ExceptionHandler(Exception.class)
    public void handleGenericException(Exception ex) {
        logger.error("Unexpected error: ", ex);
    }
}