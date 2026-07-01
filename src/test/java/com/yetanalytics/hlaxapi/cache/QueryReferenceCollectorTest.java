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
                {"actor":{"name":["query","Rabbit",["EntityId"],[["Hunger"],">",50]]}}
                """);
        StatementTrigger inline = trigger("""
                {"result":{"response":"at=<<[\\"query\\",\\"Rabbit\\",[\\"Position\\",\\"Y\\"],[[\\"Position\\",\\"X\\"],\\"<\\",15]]>>"}}
                """);

        Map<String, Set<String>> references = QueryReferenceCollector.collect(List.of(wholeNode, inline));

        assertEquals(Set.of("EntityId", "Hunger", "Position"), references.get("Rabbit"));
    }

    @Test
    void ignoresThisExpressionTargetsInsideQueryCriteria() {
        StatementTrigger trigger = trigger("""
                {"actor":{"name":["query","Rabbit",["EntityId"],[["Hunger"],">",["this",["DesiredHunger"]]]]}}
                """);

        Map<String, Set<String>> references = QueryReferenceCollector.collect(List.of(trigger));

        assertEquals(Set.of("EntityId", "Hunger"), references.get("Rabbit"));
        assertFalse(references.get("Rabbit").contains("DesiredHunger"));
    }

    private StatementTrigger trigger(String statement) {
        StatementTrigger trigger = new StatementTrigger();
        trigger.statement = statement;
        return trigger;
    }
}
