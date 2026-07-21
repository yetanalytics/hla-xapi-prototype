package com.yetanalytics.hlaxapi;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class App {

    private static final Logger logger = LogManager.getLogger(App.class);

    public static void main(String[] args) {
        logger.info("Initializing Application Context");

        @SuppressWarnings("resource")
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();

        try {
            ctx.scan("com.yetanalytics.hlaxapi");
            ctx.refresh();
            ctx.registerShutdownHook();

            Federate federate = ctx.getBean(Federate.class);
            federate.run(args);
        } finally {
            if (ctx.isActive()) {
                ctx.close();
            }
        }
    }
}
