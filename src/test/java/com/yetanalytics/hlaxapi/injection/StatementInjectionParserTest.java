package com.yetanalytics.hlaxapi.injection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yetanalytics.hlaxapi.config.model.Criterion;
import com.yetanalytics.hlaxapi.config.model.InjectionType;
import com.yetanalytics.hlaxapi.injection.StatementInjectionParser.InlineInjection;
import com.yetanalytics.hlaxapi.injection.StatementInjectionParser.LookupInjection;
import com.yetanalytics.hlaxapi.injection.StatementInjectionParser.ParseResult;
import com.yetanalytics.hlaxapi.injection.StatementInjectionParser.QueryInjection;
import com.yetanalytics.hlaxapi.injection.StatementInjectionParser.ThisInjection;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatementInjectionParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void parsesTypedWholeNodeInjections() throws Exception {
        ParseResult thisResult = StatementInjectionParser.parse(
                MAPPER.readTree("[\"this\",[\"EntityId\"]]"));
        ThisInjection thisInjection = assertInstanceOf(ThisInjection.class, thisResult.injection());
        assertEquals(List.of("EntityId"), thisInjection.target().parts);

        ParseResult queryResult = StatementInjectionParser.parse(MAPPER.readTree(
                "[\"QUERY\",\"Rabbit\",[\"Position\",\"X\"],[[\"Hunger\"],\">\",50]]"));
        QueryInjection query = assertInstanceOf(QueryInjection.class, queryResult.injection());
        assertEquals("Rabbit", query.className());
        assertEquals(List.of("Position", "X"), query.target().parts);
        assertInstanceOf(Criterion.class, query.criteria());

        ParseResult lookupResult = StatementInjectionParser.parse(MAPPER.readTree(
                "[\"lookup\",\"predator\",[\"EntityType\"],{\"nullable\":true}]"));
        LookupInjection lookup = assertInstanceOf(LookupInjection.class, lookupResult.injection());
        assertEquals("predator", lookup.alias());
        assertEquals(List.of("EntityType"), lookup.target().parts);
        assertTrue(lookup.options().nullable());
    }

    @Test
    void findsInlineInjectionsAndPreservesNonInjectionCandidates() {
        String text = "before <<[\"this\",[\"EntityId\"]]>> and "
                + "<<[\"lookup\",\"predator\",[\"EntityType\"]]>> after <<not-json>>";

        List<InlineInjection> inline = StatementInjectionParser.findInline(text);

        assertEquals(3, inline.size());
        assertInstanceOf(ThisInjection.class, inline.get(0).result().injection());
        assertInstanceOf(LookupInjection.class, inline.get(1).result().injection());
        assertFalse(inline.get(2).result().recognized());
        assertEquals("<<not-json>>", inline.get(2).source());
        assertEquals("before ", text.substring(0, inline.get(0).start()));
    }

    @Test
    void distinguishesMalformedKnownTagsFromUnknownArrays() throws Exception {
        ParseResult malformed = StatementInjectionParser.parse(MAPPER.readTree("[\"query\",\"Rabbit\"]"));
        assertTrue(malformed.recognized());
        assertFalse(malformed.valid());
        assertEquals(InjectionType.QUERY, malformed.type());

        ParseResult unknown = StatementInjectionParser.parse(MAPPER.readTree("[\"custom\",1,2]"));
        assertFalse(unknown.recognized());
        assertFalse(unknown.valid());
    }
}
