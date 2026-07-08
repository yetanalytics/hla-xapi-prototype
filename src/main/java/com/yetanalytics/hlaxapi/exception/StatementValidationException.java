package com.yetanalytics.hlaxapi.exception;

import java.util.Set;

public class StatementValidationException extends RuntimeException {
    
    private Set<String> errors;

    public StatementValidationException() {
        super();
    }

    public StatementValidationException(String message) {
        super(message);
    }

    public StatementValidationException(Set<String> errors) {
        super();
        this.errors = errors;
    }

    public StatementValidationException(String message, Throwable cause) {
        super(message, cause);
    }

    public StatementValidationException(String message, Set<String> errors) {
        super(message);
        this.errors = errors;
    }

    public StatementValidationException(String message, Set<String> errors, Throwable cause) {
        super(message, cause);
        this.errors = errors;
    }

    public StatementValidationException(Throwable cause) {
        super(cause);
    }

    public Set<String> getErrors() {
        return errors;
    }

    @Override
    public String getMessage() {
        return "XApiStatementValidationException: " + super.getMessage();
    }
}
