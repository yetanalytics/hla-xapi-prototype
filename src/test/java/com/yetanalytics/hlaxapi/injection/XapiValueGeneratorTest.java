package com.yetanalytics.hlaxapi.injection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.yetanalytics.hlaxapi.FOMXML;
import com.yetanalytics.hlaxapi.HLADecoderRegistry;
import com.yetanalytics.hlaxapi.InjectionHandler;
import com.yetanalytics.hlaxapi.SimulationConfig;
import com.yetanalytics.hlaxapi.TriggerProcessor;
import com.yetanalytics.hlaxapi.TriggerProcessor.TriggerProcessingResult;
import com.yetanalytics.hlaxapi.config.model.StatementTrigger;
import com.yetanalytics.hlaxapi.config.model.Target;
import com.yetanalytics.hlaxapi.config.model.StatementTrigger.Type;
import com.yetanalytics.xapi.util.StatementValidator;
import com.yetanalytics.xapi.util.StatementValidator.StatementValidationResult;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.portico.impl.hla1516e.types.encoding.HLA1516eEncoderFactory;

class XapiValueGeneratorTest {

    @Test
    void usesPresetUriForObjectIdPaths() {
        Object value = XapiValueGenerator.getRandomValue(
                List.of("object", "id"), 
                new Target(List.of("object", "id")), "Activity", String.class, false);

        assertInstanceOf(String.class, value);
        assertEquals("https://example.com/object", value);
    }

    @Test
    void usesPresetUuidForObjectIdPaths() {
        Object value = XapiValueGenerator.getRandomValue(
                List.of("object", "id"), 
                new Target(List.of("object", "id")), "StatementRef", String.class, false);

        assertInstanceOf(String.class, value);
        assertEquals("00000000-0000-4000-8000-000000000000", value);
    }

    @Test
    void returnsRandomStringForActorNamePaths() {
        Object value = XapiValueGenerator.getRandomValue(
                List.of("actor", "name"), 
                new Target(List.of("actor", "name")), "Activity", String.class, false);

        assertNotNull(value);
        assertInstanceOf(String.class, value);
        assertFalse(((String) value).isBlank());
    }

    /**Possibly break out into validation injection tests */

    private record XapiValidationTestEntry (String statement, InjectionContext ic, boolean passInjection, String errorSubString, boolean passValidation) {}

    List<XapiValidationTestEntry> testList = List.of(
        new XapiValidationTestEntry(
            """
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
            """,
            new InteractionInjectionContext("EntityAte", null),
            true, null, true
        ),
        new XapiValidationTestEntry(
            """
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
            """,
            new InteractionInjectionContext("EntityAte", null),
            false,
            "Mismatch between statement path [result, score, min] and FOM type class java.lang.String",
            true
        ),
        // Numeric in result.duration
        new XapiValidationTestEntry(
            """
            {
                "result": {"duration": ["trigger", ["StepNumber"]]}
            }
            """,
            new InteractionInjectionContext("StepCompleted", null),
            false,
            "Mismatch",
            false

        ),
        // Numeric in embedded result.duration, ignore HLA type and feed generative value
        new XapiValidationTestEntry(
            """
            {
                "result": {"duration": "PT<<[\\"trigger\\", [\\"StepNumber\\"]]>>S"}
            }
            """,
            new InteractionInjectionContext("StepCompleted", null),
            true,
            null,
            false
        ),
        
        // InitializeWorld with string in InitialCarrots path (type mismatch)
        new XapiValidationTestEntry(
            """
            {
                "context": {"language": ["trigger", ["InitialCarrots"]]}
            }
            """,
            new InteractionInjectionContext("InitializeWorld", null),
            false,
            "Mismatch between statement path [context, language] and FOM type class java.lang.Integer",
            false
        ),
        // Invalid Injection (non-primitive injection)
        new XapiValidationTestEntry(
            """
            {
                "context": {
                    "contextActivities": {
                        "grouping": ["trigger", ["Position"]]
                    }
                }
            }
            """,
            new InteractionInjectionContext("EntityCreated", null),
            false,
            "hlaType",
            false
        ),
        // Injection into bad place (pass injection, fail validation)
        new XapiValidationTestEntry(
            """
            {
                "context": {
                    "contextActivities": {
                        "grouping": ["trigger", ["Position", "X"]]
                    }
                }
            }
            """,
            new InteractionInjectionContext("EntityCreated", null),
            true,
            "hlaType",
            false
        )
    );


    @Test
    void validationInjectionTests() {
        SimulationConfig simConfig = new SimulationConfig(null, null, null, null,
                "config/HlaFedereplFOM.xml");
        HLADecoderRegistry decoderRegistry = new HLADecoderRegistry(new HLA1516eEncoderFactory());
        InjectionHandler ih = new InjectionHandler();
        ih.setFomXml(new FOMXML(simConfig, decoderRegistry));
        ih.setHLADecoderRegistry(decoderRegistry);
        StatementValidator validator = new StatementValidator();

        TriggerProcessor tp = new TriggerProcessor(ih);
        for (XapiValidationTestEntry testEntry : testList) {
            testEntry.ic.setValidationInjection(true);
            StatementTrigger st = new StatementTrigger();
            st.type = (testEntry.ic instanceof InteractionInjectionContext) ? Type.INTERACTION : Type.OBJECT_UPDATE;
            st.clazz = testEntry.ic.getHlaClass();
            st.statement = testEntry.statement;
            TriggerProcessingResult result = tp.processTrigger(st, testEntry.ic);
            if (testEntry.passInjection) {
                assertTrue(result.success());
                assertNotNull(result.statement());
                StatementValidationResult valRes = validator.validateStatement(result.statement());
                assertEquals(testEntry.passValidation, valRes.isValid());
            } else {
                assertFalse(result.success());
                assertNull(result.statement());
                assertNotNull(result.error());
                assertTrue(result.error().getMessage().contains(testEntry.errorSubString()));
            }
        }
    }
}


