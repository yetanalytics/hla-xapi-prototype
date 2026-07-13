package com.yetanalytics.hlaxapi.config.model;

import java.util.Map;

public class StatementTrigger {

    public Type type;
    public String clazz; // "class" is a Java keyword, map json "class" to this field via parser
    public Expression criteria;
    public Map<String, ObjectLookup> lookups;
    // keep the original statement as a JSON string (we'll process injections at
    // runtime)
    public String statement;

    @Override
    public String toString() {
        return String.format("StatementTrigger{type=%s,clazz=%s,criteria=%s,statement=%s}",
                type, clazz, criteria, statement);
    }

    public enum Type {

        INTERACTION, OBJECT_UPDATE;

        public static Type fromString(String s) {
            if (s == null) return null;
            switch (s.trim().toLowerCase()) {
                case "interaction": return INTERACTION;
                case "objectupdate": return OBJECT_UPDATE;
                default: return null;
            }
        }
    }
}
