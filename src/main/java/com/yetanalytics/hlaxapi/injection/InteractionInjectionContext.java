package com.yetanalytics.hlaxapi.injection;

import hla.rti1516e.ParameterHandleValueMap;

public class InteractionInjectionContext extends InjectionContext {

    ParameterHandleValueMap parameterMap;


    public InteractionInjectionContext() {
    }

    public InteractionInjectionContext(String hlaClass, ParameterHandleValueMap parameterMap) {
        setHlaClass(hlaClass);
        this.parameterMap = parameterMap;
    }

    public ParameterHandleValueMap getParameterMap() {
        return parameterMap;
    }

    public void setParameterMap(ParameterHandleValueMap parameterMap) {
        this.parameterMap = parameterMap;
    }

}
