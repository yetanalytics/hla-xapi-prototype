package com.yetanalytics.extension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class SuppressTestLoggingExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback {

    private Map<String, Level> originalLevels;

    @Override
    public void beforeTestExecution(ExtensionContext context) throws Exception {
        // If a Test has this annotation, get any declared classes and store their current log level
        // in originalLevels and then turn them off
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        originalLevels = new HashMap<String, Level>();
        getAnnotation(context).ifPresent(annotation -> {
            String[] targetClasses = annotation.value();
            for(int i = 0; i < targetClasses.length; i++) {
                String targetClass = targetClasses[i];
                Level originalLevel = config.getLoggerConfig(targetClass).getLevel();
                originalLevels.put(targetClass, originalLevel);
                Configurator.setLevel(targetClass, Level.OFF);
            }
        });
    }

    @Override
    public void afterTestExecution(ExtensionContext context) throws Exception {
        // Return everything to normal
        Configurator.setLevel(originalLevels);
    }

    private Optional<SuppressTestLogging> getAnnotation(ExtensionContext context) {
        return context.getTestMethod().map(method -> method.getAnnotation(SuppressTestLogging.class));
    }
    
}
