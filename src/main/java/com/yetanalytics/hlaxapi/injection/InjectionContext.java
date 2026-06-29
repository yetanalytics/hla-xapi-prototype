package com.yetanalytics.hlaxapi.injection;

public abstract class InjectionContext {

    private String hlaClass;

    public String getHlaClass() {
        return hlaClass;
    }

    public void setHlaClass(String hlaClass) {
        this.hlaClass = hlaClass;
    }
}
