package com.yetanalytics.hlaxapi;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.yetanalytics.hlaxapi.cache.ObjectCache;
import com.yetanalytics.hlaxapi.config.XapiConfig;
import hla.rti1516e.encoding.EncoderFactory;
import org.junit.jupiter.api.Test;
import org.portico.impl.hla1516e.types.encoding.HLA1516eEncoderFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class ObjectCacheSpringContextTest {

    @Test
    void springWiresObjectCacheIntoConsumers() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(
                    TestConfig.class,
                    FOMXML.class,
                    HlaInterfaceImpl.class,
                    InjectionHandler.class,
                    TriggerProcessor.class);
            context.refresh();

            ObjectCache objectCache = context.getBean(ObjectCache.class);
            InjectionHandler injectionHandler = context.getBean(InjectionHandler.class);
            HlaInterfaceImpl hlaInterface = context.getBean(HlaInterfaceImpl.class);

            assertNotNull(hlaInterface);
            assertSame(objectCache, injectionHandler.objectCache());
        }
    }

    @Configuration
    static class TestConfig extends AppConfig {

        @Override
        @Bean
        public EncoderFactory encoderFactory() {
            return new HLA1516eEncoderFactory();
        }

        @Override
        @Bean
        public XapiConfig xapiConfig() {
            return new XapiConfig();
        }

        @Override
        @Bean
        public SimulationConfig simulationConfig() {
            return new SimulationConfig(null, null, null, null, "config/HlaFedereplFOM.xml");
        }
    }
}
