package com.yetanalytics.hlaxapi.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.yetanalytics.hlaxapi.config.model.StatementTrigger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QueryReferenceCollectorTest {

    @Test
    void findsWholeNodeAndInlineQueryInjections() {
        StatementTrigger wholeNode = trigger("""
                {"actor":{"name":["query","Car",["Name"],[["FuelLevel"],">",50]]}}
                """);
        StatementTrigger inline = trigger("""
                {"result":{"response":"at=<<[\\"query\\",\\"Car\\",[\\"Position\\",\\"Long\\"],[[\\"Position\\",\\"Lat\\"],\\"<\\",40.0]]>>"}}
                """);

        Map<String, Set<String>> references = QueryReferenceCollector.collect(List.of(wholeNode, inline));

        assertEquals(Set.of("Name", "FuelLevel", "Position"), references.get("Car"));
    }

    @Test
    void ignoresThisExpressionTargetsInsideQueryCriteria() {
        StatementTrigger trigger = trigger("""
                {"actor":{"name":["query","Car",["Name"],[["FuelLevel"],">",["this",["DesiredFuel"]]]]}}
                """);

        Map<String, Set<String>> references = QueryReferenceCollector.collect(List.of(trigger));

        assertEquals(Set.of("Name", "FuelLevel"), references.get("Car"));
        assertFalse(references.get("Car").contains("DesiredFuel"));
    }

    private StatementTrigger trigger(String statement) {
        StatementTrigger trigger = new StatementTrigger();
        trigger.statement = statement;
        return trigger;
    }
}
