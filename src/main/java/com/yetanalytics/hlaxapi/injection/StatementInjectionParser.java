package com.yetanalytics.hlaxapi.injection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yetanalytics.hlaxapi.config.ConfigConverter;
import com.yetanalytics.hlaxapi.config.model.Expression;
import com.yetanalytics.hlaxapi.config.model.InjectionType;
import com.yetanalytics.hlaxapi.config.model.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Canonical parser for whole-node and inline statement injection syntax.
 */
public final class StatementInjectionParser {

    private static final Pattern INLINE_PLACEHOLDER = Pattern.compile("<<(.+?)>>", Pattern.DOTALL);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private StatementInjectionParser() {
    }

    /**
     * Inspect a JSON node and, when it starts with a known injection token, parse
     * it into a typed injection.
     */
    public static ParseResult parse(JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty() || !node.get(0).isTextual()) {
            return ParseResult.notInjection();
        }

        InjectionType type = InjectionType.fromString(node.get(0).asText());
        if (type == null) {
            return ParseResult.notInjection();
        }

        try {
            return switch (type) {
                case THIS -> node.size() < 2
                        ? ParseResult.malformed(type)
                        : ParseResult.valid(new ThisInjection(
                                target(node.get(1)),
                                options(node, 2)));
                case QUERY -> node.size() < 4
                        ? ParseResult.malformed(type)
                        : ParseResult.valid(new QueryInjection(
                                node.get(1).asText(),
                                target(node.get(2)),
                                expression(node.get(3)),
                                options(node, 4)));
                case LOOKUP -> node.size() < 3
                        ? ParseResult.malformed(type)
                        : ParseResult.valid(new LookupInjection(
                                node.get(1).asText(),
                                target(node.get(2)),
                                options(node, 3)));
            };
        } catch (RuntimeException e) {
            return ParseResult.malformed(type);
        }
    }

    /**
     * Find all inline {@code <<...>>} placeholders in encounter order. Invalid
     * JSON and non-injection placeholders are returned as non-injections so a
     * rendering caller can preserve their original text.
     */
    public static List<InlineInjection> findInline(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        List<InlineInjection> injections = new ArrayList<>();
        Matcher matcher = INLINE_PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            ParseResult result;
            try {
                result = parse(MAPPER.readTree(matcher.group(1)));
            } catch (Exception e) {
                result = ParseResult.notInjection();
            }
            injections.add(new InlineInjection(
                    matcher.start(),
                    matcher.end(),
                    matcher.group(0),
                    result));
        }
        return List.copyOf(injections);
    }

    private static Target target(JsonNode node) {
        return ConfigConverter.toTarget(MAPPER.convertValue(node, Object.class));
    }

    private static Expression expression(JsonNode node) {
        Object raw = MAPPER.convertValue(node, Object.class);
        return ConfigConverter.toExpression(raw);
    }

    private static InjectionOptions options(JsonNode node, int index) {
        if (node.size() <= index || !node.get(index).isObject()) {
            return InjectionOptions.DEFAULT;
        }
        return new InjectionOptions(node.get(index).path(InjectionOptions.NULLABLE_KEY).asBoolean(false));
    }

    public sealed interface StatementInjection
            permits ThisInjection, QueryInjection, LookupInjection {

        InjectionType type();

        Target target();

        InjectionOptions options();
    }

    public record ThisInjection(Target target, InjectionOptions options) implements StatementInjection {

        @Override
        public InjectionType type() {
            return InjectionType.THIS;
        }
    }

    public record QueryInjection(
            String className,
            Target target,
            Expression criteria,
            InjectionOptions options) implements StatementInjection {

        @Override
        public InjectionType type() {
            return InjectionType.QUERY;
        }
    }

    public record LookupInjection(
            String alias,
            Target target,
            InjectionOptions options) implements StatementInjection {

        @Override
        public InjectionType type() {
            return InjectionType.LOOKUP;
        }
    }

    public record InjectionOptions(boolean nullable) {

        public static final String NULLABLE_KEY = "nullable";
        public static final InjectionOptions DEFAULT = new InjectionOptions(false);
    }

    public record ParseResult(
            boolean recognized,
            InjectionType type,
            StatementInjection injection) {

        public static ParseResult notInjection() {
            return new ParseResult(false, null, null);
        }

        public static ParseResult malformed(InjectionType type) {
            return new ParseResult(true, type, null);
        }

        public static ParseResult valid(StatementInjection injection) {
            return new ParseResult(true, injection.type(), injection);
        }

        public boolean valid() {
            return injection != null;
        }
    }

    public record InlineInjection(
            int start,
            int end,
            String source,
            ParseResult result) {
    }
}
