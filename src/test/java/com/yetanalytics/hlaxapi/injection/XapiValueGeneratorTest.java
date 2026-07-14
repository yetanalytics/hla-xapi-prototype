package com.yetanalytics.hlaxapi.injection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.yetanalytics.hlaxapi.FOMXML;
import com.yetanalytics.hlaxapi.HLADecoderRegistry;
import com.yetanalytics.hlaxapi.InjectionHandler;
import com.yetanalytics.hlaxapi.SimulationConfig;
import com.yetanalytics.hlaxapi.TriggerProcessor;
import com.yetanalytics.hlaxapi.config.model.StatementTrigger;
import com.yetanalytics.hlaxapi.config.model.Target;
import com.yetanalytics.hlaxapi.config.model.StatementTrigger.Type;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.portico.impl.hla1516e.types.encoding.HLA1516eEncoderFactory;

class XapiValueGeneratorTest {

    @Test
    void usesPresetUriForObjectIdPaths() {
        Object value = XapiValueGenerator.getRandomValue(
                List.of("object", "id"), 
                new Target(List.of("object", "id")), "Activity", String.class);

        assertInstanceOf(String.class, value);
        assertEquals("https://example.com/object", value);
    }

    @Test
    void usesPresetUuidForObjectIdPaths() {
        Object value = XapiValueGenerator.getRandomValue(
                List.of("object", "id"), 
                new Target(List.of("object", "id")), "StatementRef", String.class);

        assertInstanceOf(String.class, value);
        assertEquals("00000000-0000-4000-8000-000000000000", value);
    }

    @Test
    void returnsRandomStringForActorNamePaths() {
        Object value = XapiValueGenerator.getRandomValue(
                List.of("actor", "name"), 
                new Target(List.of("actor", "name")), "Activity", String.class);

        assertNotNull(value);
        assertInstanceOf(String.class, value);
        assertFalse(((String) value).isBlank());
    }

    /**Possibly break out into validation injection tests */

    @Test
    void injectsRandomValuesForTemplate() {
        SimulationConfig simConfig = new SimulationConfig(null, null, null, null,
                "config/HlaFedereplFOM.xml");
        HLADecoderRegistry decoderRegistry = new HLADecoderRegistry(new HLA1516eEncoderFactory());
        InjectionHandler ih = new InjectionHandler();
        ih.setFomXml(new FOMXML(simConfig, decoderRegistry));
        ih.setHLADecoderRegistry(decoderRegistry);

        TriggerProcessor tp = new TriggerProcessor(ih);
        String statementTemplate = """
                {
                    "actor": {
                        "objectType": "Agent",
                        "name": ["trigger", ["PredatorType"]],
                        "account": {
                            "homePage": "https://hla-federepl.example/entities",
                            "name": ["trigger", ["PredatorId"]]
                        }
                    },
                    "verb": {
                        "id": "http://example.com/verbs/ate",
                        "display": {"en-US": "Ate"}
                    },
                    "object": {
                        "id": ["trigger", ["PreyId"]]
                    }
                }
                """;
        InteractionInjectionContext iic = new InteractionInjectionContext("EntityAte", null);
        iic.setValidationInjection(true);
        StatementTrigger st = new StatementTrigger();
        st.type = Type.INTERACTION;
        st.statement = statementTemplate;

        String result = tp.processTrigger(st, iic);
        assertNotNull(result);
    }

    @Test
    void injectsMismatchValuesForTemplate() {
        SimulationConfig simConfig = new SimulationConfig(null, null, null, null,
                "config/HlaFedereplFOM.xml");
        HLADecoderRegistry decoderRegistry = new HLADecoderRegistry(new HLA1516eEncoderFactory());
        InjectionHandler ih = new InjectionHandler();
        ih.setFomXml(new FOMXML(simConfig, decoderRegistry));
        ih.setHLADecoderRegistry(decoderRegistry);

        TriggerProcessor tp = new TriggerProcessor(ih);
        String statementTemplate = """
                {
                    "actor": {
                        "name": ["trigger", ["PredatorType"]],
                        "mbox": ["trigger", ["PreyId"]]
                    },
                    "verb": {
                        "id": "http://example.com/verbs/ate"
                    },
                    "object": {
                        "id": ["trigger", ["PreyId"]]
                    },
                    "result": {"score": {"min": ["trigger", ["PreyId"]]}}
                }
                """;
        InteractionInjectionContext iic = new InteractionInjectionContext("EntityAte", null);
        iic.setValidationInjection(true);
        StatementTrigger st = new StatementTrigger();
        st.type = Type.INTERACTION;
        st.statement = statementTemplate;

        String result = tp.processTrigger(st, iic);
        assertNotNull(result);
    }
}
