package com.yetanalytics.hlaxapi.injection;

import java.util.Map;


public class InteractionInjectionContext extends InjectionContext {

    Map<String, byte[]> parameterMap;

    public InteractionInjectionContext() {
    }

    public InteractionInjectionContext(String hlaClass, Map<String, byte[]> parameterMap) {
        setHlaClass(hlaClass);
        this.parameterMap = parameterMap;
    }

    public Map<String, byte[]> getParameterMap() {
        return parameterMap;
    }

    public void setParameterMap(Map<String, byte[]> parameterMap) {
        this.parameterMap = parameterMap;
    }

}
