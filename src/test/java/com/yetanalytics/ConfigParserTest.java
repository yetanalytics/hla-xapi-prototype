package com.yetanalytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

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

public class ConfigParserTest {

    private static final Logger logger = LogManager.getLogger(ConfigParserTest.class);

    final static String CONFIG_STATEMENT_RESULT = "{\"actor\":{\"objectType\":\"Agent\",\"name\":[\"this\",[\"ScenarioName\"]],\"account\":{\"homePage\":\"https://homepage.system.io\",\"name\":[\"Object\",\"Player\",[\"Name\"],[\"Number\",\"=\",0]]}},\"context\":{\"extensions\":{\"http://www.extensions.com/car-color\":[\"query\",\"Car\",[\"carColor\"],[[\"carId\"],\"=\",4]],\"http://www.extensions.com/nested-example\":[\"query\",\"Car\",[\"carColor\"],[[\"carId\"],\"=\",[\"this\",[\"CarId\"]]]]}}}";

    @Test
    public void parsesConfigFile() throws IOException {
        XapiConfig config = ConfigParser.fromFile("src/test/resources/config-test.json").parse();

        TriggerProcessor triggerProcessor = new TriggerProcessor(new InjectionHandler());

        assertNotNull(config);
        assertNotNull(config.statementTriggers);
        assertEquals(1, config.statementTriggers.size());
        config.statementTriggers.forEach(trigger -> {
            assertNotNull(trigger.type);
            assertNotNull(trigger.criteria);
            assertNotNull(trigger.clazz);
            assertNotNull(trigger.statement);
            logger.info(trigger);


            String statement = triggerProcessor.processTrigger(trigger);
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
}
