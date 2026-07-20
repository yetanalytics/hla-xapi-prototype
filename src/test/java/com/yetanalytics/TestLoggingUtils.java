package com.yetanalytics;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Configurator;

public class TestLoggingUtils {

    public static Map<String, Level> suppressLogs(String targetClass) {
        return suppressLogs(Set.of(targetClass));
    }

    public static Map<String, Level> suppressLogs(Set<String> targetClasses) {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        Map<String, Level> originalLevels = new HashMap<String, Level>();
        for(String targetClass : targetClasses){
            Level originalLevel = config.getLoggerConfig(targetClass).getLevel();
            originalLevels.put(targetClass, originalLevel);
            Configurator.setLevel(targetClass, Level.OFF);
        }
        return originalLevels;
    }

    public static void setLogLevelsByClass(Map<String, Level> levels) {
        Configurator.setLevel(levels);
    }
    
}
