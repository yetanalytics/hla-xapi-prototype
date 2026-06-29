package com.yetanalytics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Parameter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.portico.impl.hla1516e.types.encoding.HLA1516eEncoderFactory;

import com.yetanalytics.hlaxapi.FOMXML;
import com.yetanalytics.hlaxapi.HLADecoderRegistry;
import com.yetanalytics.hlaxapi.HLAEncodingTestSupport;
import com.yetanalytics.hlaxapi.InjectionHandler;
import com.yetanalytics.hlaxapi.SimulationConfig;
import com.yetanalytics.hlaxapi.TriggerProcessor;
import com.yetanalytics.hlaxapi.config.ConfigConverter;
import com.yetanalytics.hlaxapi.config.ConfigParser;
import com.yetanalytics.hlaxapi.config.XapiConfig;
import com.yetanalytics.hlaxapi.config.model.ComparisonOperator;
import com.yetanalytics.hlaxapi.config.model.Criterion;
import com.yetanalytics.hlaxapi.config.model.Expression;
import com.yetanalytics.hlaxapi.config.model.LogicalExpression;
import com.yetanalytics.hlaxapi.config.model.LogicalOperator;
import com.yetanalytics.hlaxapi.config.model.Target;
import com.yetanalytics.hlaxapi.config.model.ThisExpression;
import com.yetanalytics.hlaxapi.injection.InjectionContext;
import com.yetanalytics.hlaxapi.injection.InteractionInjectionContext;

import hla.rti1516e.ParameterHandle;
import hla.rti1516e.ParameterHandleValueMap;
import hla.rti1516e.encoding.ByteWrapper;

public class ConfigParserTest {

    private static final Logger logger = LogManager.getLogger(ConfigParserTest.class);

    final static String CONFIG_STATEMENT_RESULT = "{\"actor\":{\"objectType\":\"Agent\",\"name\":\"c5988e0e-c521-4ff7-ba83-df1a63eb72bf\",\"account\":{\"homePage\":\"https://homepage.system.io\",\"name\":\"Mr. c5988e0e-c521-4ff7-ba83-df1a63eb72bf\"}},\"context\":{\"extensions\":{\"https://yetanalytics.com/extensions/from-x\":4,\"https://yetanalytics.com/extensions/from-y\":12}},\"result\":{\"response\":\"[5, 13]\"}}";

    @Test
    public void parsesConfigFile() throws IOException {
        XapiConfig config = ConfigParser.fromFile("src/test/resources/config-test.json").parse();

        SimulationConfig simConfig = new SimulationConfig(null, null, null, null, 
            "config/HlaFedereplFOM.xml");
        HLADecoderRegistry decoderRegistry = new HLADecoderRegistry(new HLA1516eEncoderFactory());
        InjectionHandler ih = new InjectionHandler();
        ih.setFomXml(new FOMXML(simConfig, decoderRegistry));
        ih.setHLADecoderRegistry(decoderRegistry);


        TriggerProcessor triggerProcessor = new TriggerProcessor(ih);

        Map<String, byte[]> paramMap = new HashMap<String, byte[]>();
        paramMap.put("EntityId", HLAEncodingTestSupport.asciiString("c5988e0e-c521-4ff7-ba83-df1a63eb72bf"));
        
        byte[] fromX = HLAEncodingTestSupport.int32(4, ByteOrder.BIG_ENDIAN);
        byte[] fromY = HLAEncodingTestSupport.int32(12, ByteOrder.BIG_ENDIAN);
        paramMap.put("FromPosition", HLAEncodingTestSupport.fixedRecord(fromX, fromY));

        byte[] toX = HLAEncodingTestSupport.int32(5, ByteOrder.BIG_ENDIAN);
        byte[] toY = HLAEncodingTestSupport.int32(13, ByteOrder.BIG_ENDIAN);
        paramMap.put("ToPosition", HLAEncodingTestSupport.fixedRecord(toX, toY));
        

        InteractionInjectionContext injectionContext = new InteractionInjectionContext("EntityMoved", paramMap);

        assertNotNull(config);
        assertNotNull(config.statementTriggers);
        assertEquals(1, config.statementTriggers.size());
        config.statementTriggers.forEach(trigger -> {
            assertNotNull(trigger.type);
            assertNotNull(trigger.criteria);
            assertNotNull(trigger.clazz);
            assertNotNull(trigger.statement);
            logger.info(trigger);

            String statement = triggerProcessor.processTrigger(trigger, injectionContext);
            logger.info(statement);
            assertEquals(statement, CONFIG_STATEMENT_RESULT);

        });

        assertEquals(config.lrsConfig.batch, 4);
        assertEquals(config.lrsConfig.host, "host string");
        assertEquals(config.lrsConfig.key, "key string");
        assertEquals(config.lrsConfig.secret, "secret string");
        assertNotNull(config.lrsConfig);
    }

    @Test
    public void fixedRecordHelperConcatenatesEncodedFieldsInOrder() {
        byte[] first = HLAEncodingTestSupport.int32(12, ByteOrder.BIG_ENDIAN);
        byte[] second = HLAEncodingTestSupport.asciiString("abc");

        byte[] record = HLAEncodingTestSupport.fixedRecord(first, second);
        byte[] expected = ByteBuffer.allocate(first.length + second.length)
                .order(ByteOrder.BIG_ENDIAN)
                .put(first)
                .put(second)
                .array();

        assertArrayEquals(expected, record);
    }

    @Test
    public void handlesFixedRecordFieldAccess() {
        SimulationConfig simConfig = new SimulationConfig(null, null, null, null,
                "src/test/resources/SISO-STD-025.3-2024.xml");
        HLADecoderRegistry decoderRegistry = new HLADecoderRegistry(new HLA1516eEncoderFactory());
        InjectionHandler ih = new InjectionHandler();
        ih.setFomXml(new FOMXML(simConfig, decoderRegistry));
        ih.setHLADecoderRegistry(decoderRegistry);

        byte[] eventTime = java.nio.ByteBuffer.allocate(Integer.BYTES * 2)
                .order(java.nio.ByteOrder.BIG_ENDIAN)
                .putInt(12)
                .putInt(34567)
                .array();

        Map<String, byte[]> paramMap = new java.util.HashMap<>();
        paramMap.put("EventTime", eventTime);

        InteractionInjectionContext injectionContext = new InteractionInjectionContext("CyberEvent", paramMap);
        com.yetanalytics.hlaxapi.config.model.Target target = new com.yetanalytics.hlaxapi.config.model.Target(java.util.List.of("EventTime", "Hours"));

        Object result = ih.handleThis(target, injectionContext);
        assertEquals(12, result);
    }

    @Test
    public void handlesFixedRecordGridPositionFieldAccess() {
        SimulationConfig simConfig = new SimulationConfig(null, null, null, null,
                "config/HlaFedereplFOM.xml");
        HLADecoderRegistry decoderRegistry = new HLADecoderRegistry(new HLA1516eEncoderFactory());
        InjectionHandler ih = new InjectionHandler();
        ih.setFomXml(new FOMXML(simConfig, decoderRegistry));
        ih.setHLADecoderRegistry(decoderRegistry);

        byte[] gridPosition = java.nio.ByteBuffer.allocate(Integer.BYTES * 2)
                .order(java.nio.ByteOrder.BIG_ENDIAN)
                .putInt(5)
                .putInt(7)
                .array();

        Map<String, byte[]> paramMap = new java.util.HashMap<>();
        paramMap.put("ToPosition", gridPosition);

        InteractionInjectionContext injectionContext = new InteractionInjectionContext("EntityMoved", paramMap);
        com.yetanalytics.hlaxapi.config.model.Target xTarget = new com.yetanalytics.hlaxapi.config.model.Target(java.util.List.of("ToPosition", "X"));
        com.yetanalytics.hlaxapi.config.model.Target yTarget = new com.yetanalytics.hlaxapi.config.model.Target(java.util.List.of("ToPosition", "Y"));

        Object xResult = ih.handleThis(xTarget, injectionContext);
        Object yResult = ih.handleThis(yTarget, injectionContext);

        assertEquals(5, xResult);
        assertEquals(7, yResult);
    }

    @Test
    public void handlesFixedRecordFieldAccessInsideArray() {
        SimulationConfig simConfig = new SimulationConfig(null, null, null, null,
                "src/test/resources/SISO-STD-025.3-2024.xml");
        HLADecoderRegistry decoderRegistry = new HLADecoderRegistry(new HLA1516eEncoderFactory());
        InjectionHandler ih = new InjectionHandler();
        ih.setFomXml(new FOMXML(simConfig, decoderRegistry));
        ih.setHLADecoderRegistry(decoderRegistry);

        byte[] key = HLAEncodingTestSupport.asciiString("sample-key");
        byte[] value = HLAEncodingTestSupport.variableBytes("sample-value".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        byte[] pair = java.nio.ByteBuffer.allocate(key.length + value.length)
                .order(java.nio.ByteOrder.BIG_ENDIAN)
                .put(key)
                .put(value)
                .array();
        byte[] arrayBytes = HLAEncodingTestSupport.variableArray(pair);

        Map<String, byte[]> paramMap = new java.util.HashMap<>();
        paramMap.put("TargetModifiers", arrayBytes);

        InteractionInjectionContext injectionContext = new InteractionInjectionContext("CyberEvent", paramMap);
        com.yetanalytics.hlaxapi.config.model.Target target = new com.yetanalytics.hlaxapi.config.model.Target(java.util.List.of("TargetModifiers", 0, "Key"));

        Object result = ih.handleThis(target, injectionContext);
        assertEquals("sample-key", result);
    }

    @Test
    public void convertsBinaryCriterion() {
        // raw form: [ ["Event"], "=", 5 ]
        List<Object> target = List.of("Event");
        List<Object> raw = List.of(target, "=", 5);

        Expression e = ConfigConverter.toExpression(raw);
        assertTrue(e instanceof Criterion);
        Criterion c = (Criterion) e;
        assertTrue(c.left instanceof Target);
        assertEquals(ComparisonOperator.EQ, c.operator);
        assertTrue(c.right instanceof com.yetanalytics.hlaxapi.config.model.ValueExpression);
    }

    @Test
    public void convertsLogicalExpression() {
        // raw: [ [["A"],"=",1], "or", [["B"],">",2] ]
        List<Object> left = List.of(List.of("A"), "=", 1);
        List<Object> right = List.of(List.of("B"), ">", 2);
        List<Object> raw = new ArrayList<>();
        raw.add(left);
        raw.add("or");
        raw.add(right);

        Expression e = ConfigConverter.toExpression(raw);
        assertTrue(e instanceof LogicalExpression || e instanceof Criterion);
        if (e instanceof LogicalExpression) {
            LogicalExpression le = (LogicalExpression) e;
            assertEquals(LogicalOperator.OR, le.operator);
            assertEquals(2, le.operands.size());
        }
    }

    @Test
    public void convertsThisExpressionInsideCriterion() {
        // raw: [ ["X"], "=", ["this", ["attr"]] ]
        List<Object> rawThis = List.of("this", List.of("attr"));
        List<Object> raw = List.of(List.of("X"), "=", rawThis);

        Expression e = ConfigConverter.toExpression(raw);
        assertTrue(e instanceof Criterion);
        Criterion c = (Criterion) e;
        assertTrue(c.right instanceof ThisExpression);
        ThisExpression te = (ThisExpression) c.right;
        assertNotNull(te.target);
        assertEquals(List.of("attr"), te.target.parts);
    }

    @Test
    public void inlinePlaceholderProcessing() throws IOException {
        // Setup same as parsesConfigFile
        SimulationConfig simConfig = new SimulationConfig(null, null, null, null,
                "src/test/resources/SISO-STD-025.3-2024.xml");
        HLADecoderRegistry decoderRegistry = new HLADecoderRegistry(new HLA1516eEncoderFactory());
        InjectionHandler ih = new InjectionHandler();
        ih.setFomXml(new FOMXML(simConfig, decoderRegistry));
        ih.setHLADecoderRegistry(decoderRegistry);

        TriggerProcessor triggerProcessor = new TriggerProcessor(ih);

        Map<String, byte[]> paramMap = new HashMap<String, byte[]>();
        paramMap.put("Description", HLAEncodingTestSupport.asciiString("description!"));

        InteractionInjectionContext injectionContext = new InteractionInjectionContext("CyberEvent", paramMap);

        // Statement contains an inline placeholder in a string using <<...>> containing JSON array
        String stmt = "{\"actor\":{\"name\":\"prefix-<<[\\\"this\\\", [\\\"Description\\\"]]>>-suffix\"}}";

        com.yetanalytics.hlaxapi.config.model.StatementTrigger st = new com.yetanalytics.hlaxapi.config.model.StatementTrigger();
        st.statement = stmt;

        String out = triggerProcessor.processTrigger(st, injectionContext);
        assertNotNull(out);
        // Expect the placeholder to be replaced by the parameter value (description!) inside the string
        assertTrue(out.contains("prefix-description!-suffix"));
    }

    @Test
    public void inlinePlaceholderProcessingHandlesMultiplePlaceholders() throws IOException {
        SimulationConfig simConfig = new SimulationConfig(null, null, null, null,
            "src/test/resources/SISO-STD-025.3-2024.xml");
        HLADecoderRegistry decoderRegistry = new HLADecoderRegistry(new HLA1516eEncoderFactory());
        InjectionHandler ih = new InjectionHandler();
        ih.setFomXml(new FOMXML(simConfig, decoderRegistry));
        ih.setHLADecoderRegistry(decoderRegistry);

        TriggerProcessor triggerProcessor = new TriggerProcessor(ih);

        Map<String, byte[]> paramMap = new java.util.HashMap<String, byte[]>();
        paramMap.put("Description", HLAEncodingTestSupport.asciiString("description!"));

        InteractionInjectionContext injectionContext = new InteractionInjectionContext("CyberEvent", paramMap);

        String stmt = "{\"actor\":{\"name\":\"first=<<[\\\"this\\\", [\\\"Description\\\"]]>>, second=<<[\\\"this\\\", [\\\"Description\\\"]]>>\"}}";
        com.yetanalytics.hlaxapi.config.model.StatementTrigger st = new com.yetanalytics.hlaxapi.config.model.StatementTrigger();
        st.statement = stmt;

        String out = triggerProcessor.processTrigger(st, injectionContext);
        assertNotNull(out);
        assertTrue(out.contains("first=description!, second=description!"));
    }

    @Test
    public void inlinePlaceholderKeepsWholeValueAsStringWhenInjectionReturnsStructuredData() {
        InjectionHandler ih = new InjectionHandler() {
            @Override
            public Object handleThis(Target t, InjectionContext context) {
                return List.of("alpha", "beta");
            }
        };

        TriggerProcessor triggerProcessor = new TriggerProcessor(ih);
        InteractionInjectionContext injectionContext = new InteractionInjectionContext("CyberEvent", new HashMap<>());

        String stmt = "{\"actor\":{\"name\":\"<<[\\\"this\\\", [\\\"Description\\\"]]>>\"}}";
        com.yetanalytics.hlaxapi.config.model.StatementTrigger st = new com.yetanalytics.hlaxapi.config.model.StatementTrigger();
        st.statement = stmt;

        String out = triggerProcessor.processTrigger(st, injectionContext);
        assertNotNull(out);
        assertTrue(out.contains("\"name\":\"[alpha, beta]\""));
    }

    // Stub implementations of HLA ParameterHandle and ParameterHandleValueMap for
    // testing
    class TestParameterHandle implements ParameterHandle {
        private final String name;

        TestParameterHandle(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof TestParameterHandle
                    && name.equals(((TestParameterHandle) o).name);
        }

        @Override
        public int encodedLength() {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'encodedLength'");
        }

        @Override
        public void encode(byte[] buffer, int offset) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'encode'");
        }
    }

    class TestParameterHandleValueMap extends java.util.HashMap<ParameterHandle, byte[]>
            implements ParameterHandleValueMap {

        @Override
        public ByteWrapper getValueReference(ParameterHandle key) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'getValueReference'");
        }

        @Override
        public ByteWrapper getValueReference(ParameterHandle key, ByteWrapper byteWrapper) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'getValueReference'");
        }
    }
}
