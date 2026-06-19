package com.yetanalytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import com.yetanalytics.hlaxapi.HLAEncodingTestSupport;
import com.yetanalytics.hlaxapi.InjectionHandler;
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
import com.yetanalytics.hlaxapi.injection.InteractionInjectionContext;

import hla.rti1516e.ParameterHandle;
import hla.rti1516e.ParameterHandleValueMap;
import hla.rti1516e.encoding.ByteWrapper;

public class ConfigParserTest {

    private static final Logger logger = LogManager.getLogger(ConfigParserTest.class);

    final static String CONFIG_STATEMENT_RESULT = "{\"actor\":{\"objectType\":\"Agent\",\"name\":[THIS(interaction):Target{[ScenarioName]}:CONTEXT:LoadScenario],\"account\":{\"homePage\":\"https://homepage.system.io\",\"name\":[QUERY:Player:Target{[Name]}:Criterion{Value(Number) = Value(0)}]}},\"context\":{\"extensions\":{\"http://www.extensions.com/car-color\":[QUERY:Car:Target{[carColor]}:Criterion{Target{[carId]} = Value(4)}],\"http://www.extensions.com/nested-example\":[QUERY:Car:Target{[carColor]}:Criterion{Target{[carId]} = This(Target{[CarId]})}]}}}";

    @Test
    public void parsesConfigFile() throws IOException {
        XapiConfig config = ConfigParser.fromFile("src/test/resources/config-test.json").parse();

        TriggerProcessor triggerProcessor = new TriggerProcessor(new InjectionHandler());

        TestParameterHandleValueMap paramMap = new TestParameterHandleValueMap();
        // populate an example parameter (encoded as ASCII string here)
        paramMap.put(new TestParameterHandle("ScenarioName"), "ScenarioNameTest".getBytes());

        InteractionInjectionContext injectionContext = new InteractionInjectionContext("LoadScenario", paramMap);

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
