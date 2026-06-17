package com.yetanalytics.hlaxapi;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.yetanalytics.hlaxapi.config.ConfigParser;
import com.yetanalytics.hlaxapi.config.XapiConfig;

import hla.rti1516e.exceptions.RTIexception;
import hla.rti1516e.exceptions.RTIinternalError;

public class App {

    private static final Logger logger = LogManager.getLogger(App.class);

    public static void main(String[] args) {

        logger.info("Starting Custom Federate");

        final XapiConfig xapiConfig;
        try {
            xapiConfig = ConfigParser.fromEnvOrDefault().parse();
            logger.info("Loaded xapi config: {} triggers", xapiConfig.statementTriggers == null ? 0 : xapiConfig.statementTriggers.size());
        } catch (Exception e) {
            logger.warn("Could not load xapi config", e);
            throw new RuntimeException(e);
        }

        final String configPath = args.length > 0 ? args[0] : "config/Simulation.config";

        final SimulationConfig config;
        try {
            config = new SimulationConfig(configPath);
        } catch (IOException e) {
            System.out.println("Could not read Simulation config: " + configPath);
            return;
        }

        final HlaInterface hlaInterface = HlaInterface.Factory.newInterface();
        final CountDownLatch shutdownLatch = new CountDownLatch(1);
        final AtomicBoolean shuttingDown = new AtomicBoolean(false);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shutdown(hlaInterface, shutdownLatch, shuttingDown);
        }, "hla-xapi-shutdown-hook"));

        try {
            logger.info("Attempting RTI Connection");
            hlaInterface.start(config.getLocalSettingsDesignator(), config.getFom(), config.getFederationName(),
                    config.getFederateName(), xapiConfig);
        } catch (RTIexception e) {
            System.out.println("Could not connect to the RTI using the local settings designator \""
                    + config.getLocalSettingsDesignator() + "\"");
            e.printStackTrace();
            return;
        }

        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Main thread interrupted. Shutting down");
            shutdown(hlaInterface, shutdownLatch, shuttingDown);
        }
    }

    private static void shutdown(
            HlaInterface hlaInterface,
            CountDownLatch shutdownLatch,
            AtomicBoolean shuttingDown) {

        if (!shuttingDown.compareAndSet(false, true)) {
            return;
        }

        logger.info("Shutting down Custom Federate");

        try {
            hlaInterface.stop();
        } catch (RTIinternalError e) {
            logger.warn("Error while stopping HLA interface", e);
        } finally {
            shutdownLatch.countDown();

        }
    }
}
