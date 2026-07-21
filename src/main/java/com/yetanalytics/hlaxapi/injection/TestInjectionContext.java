package com.yetanalytics.hlaxapi.injection;

public class TestInjectionContext extends InjectionContext {

    public TestInjectionContext() {
    }

    public TestInjectionContext(String hlaClass) {
        setHlaClass(hlaClass);
    }

}
