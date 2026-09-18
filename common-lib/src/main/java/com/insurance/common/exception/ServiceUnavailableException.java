package com.insurance.common.exception;

import org.springframework.http.HttpStatus;

/** 503: a downstream service this operation depends on did not answer (raised by client adapters / fallbacks). */
public class ServiceUnavailableException extends BusinessException {

    public ServiceUnavailableException(String serviceName) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                serviceName + " is currently unavailable. Please retry shortly.");
    }
}
