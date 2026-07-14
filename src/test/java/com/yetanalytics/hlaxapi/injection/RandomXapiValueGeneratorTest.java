package com.yetanalytics.hlaxapi.injection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.yetanalytics.hlaxapi.config.model.Target;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class RandomXapiValueGeneratorTest {

    @Test
    void usesPresetUriForObjectIdPaths() {
        Object value = RandomXapiValueGenerator.getRandomValue(
                List.of("object", "id"), new Target(List.of("object", "id")), "Activity");

        assertInstanceOf(String.class, value);
        assertEquals("https://example.com/object", value);
    }

    @Test
    void usesPresetUuidForObjectIdPaths() {
        Object value = RandomXapiValueGenerator.getRandomValue(
                List.of("object", "id"), new Target(List.of("object", "id")), "StatementRef");

        assertInstanceOf(String.class, value);
        assertEquals("00000000-0000-4000-8000-000000000000", value);
    }

    @Test
    void returnsRandomStringForActorNamePaths() {
        Object value = RandomXapiValueGenerator.getRandomValue(
                List.of("actor", "name"), new Target(List.of("actor", "name")), "Activity");

        assertNotNull(value);
        assertInstanceOf(String.class, value);
        assertFalse(((String) value).isBlank());
    }
}
