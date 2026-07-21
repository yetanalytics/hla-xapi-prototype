package com.yetanalytics.hlaxapi.exception;

public class InjectionDatatypeMismatchException extends RuntimeException {

    public InjectionDatatypeMismatchException() {
        super();
    }

    public InjectionDatatypeMismatchException(String message) {
        super(message);
    }

    public InjectionDatatypeMismatchException(String message, Throwable cause) {
        super(message, cause);
    }

    public InjectionDatatypeMismatchException(Throwable cause) {
        super(cause);
    }

    @Override
    public String getMessage() {
        return "InjectionDatatypeMismatchException: " + super.getMessage();
    }
}
