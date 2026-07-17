package com.yetanalytics.hlaxapi.exception;

public class XapiConfigurationException extends RuntimeException {

    public XapiConfigurationException() {
        super();
    }

    public XapiConfigurationException(String message) {
        super(message);
    }

    public XapiConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }

    public XapiConfigurationException(Throwable cause) {
        super(cause);
    }

    @Override
    public String getMessage() {
        return "XapiConfigurationException: " + super.getMessage();
    }
}
