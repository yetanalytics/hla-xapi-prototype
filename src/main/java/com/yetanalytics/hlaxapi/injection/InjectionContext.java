package com.yetanalytics.hlaxapi.injection;

import java.util.List;

public abstract class InjectionContext {

    private String hlaClass;
    private List<Object> statementPath = List.of();
    private boolean validationInjection = false;

    public String getHlaClass() {
        return hlaClass;
    }

    public void setHlaClass(String hlaClass) {
        this.hlaClass = hlaClass;
    }

    /**
     * The path to the statement node currently being processed for injection.
     * Object-property names are represented as {@link String}s and array
     * positions as {@link Integer}s.
     */
    public List<Object> getStatementPath() {
        return statementPath;
    }

    public void setStatementPath(List<Object> statementPath) {
        this.statementPath = statementPath == null ? List.of() : List.copyOf(statementPath);
    }

    public boolean isValidationInjection() {
        return validationInjection;
    }

    public void setValidationInjection(boolean validationInjection) {
        this.validationInjection = validationInjection;
    }
}
