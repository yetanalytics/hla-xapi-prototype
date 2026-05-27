package com.yetanalytics.hlaxapi;

import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import hla.rti1516e.exceptions.RTIexception;
import hla.rti1516e.exceptions.RTIinternalError;

/**
 * Hello world!
 */
public class App {

    private static final Logger logger = LogManager.getLogger(App.class);

    public static void main(String[] args) {

        logger.info("Starting Custom Federate");

        final SimulationConfig config;
        try {
            config = new SimulationConfig("config/Simulation.config");
        } catch (IOException e) {
            System.out.println("Could not read Simulation.config");
            return;
        }

        final HlaInterface hlaInterface = HlaInterface.Factory.newInterface();

        try {
            logger.info("Attempting RTI Connection");
            hlaInterface.start(config.getLocalSettingsDesignator(), config.getFom(), config.getFederationName(),
                    config.getFederateName());
        } catch (RTIexception e) {
            System.out.println("Could not connect to the RTI using the local settings designator \""
                    + config.getLocalSettingsDesignator() + "\"");
            e.printStackTrace();
            System.exit(0);
        }

        hlaInterface.addInteractionListener(new InteractionListener() {
            
            public void receivedStart(float timeScaleFactor) {
                logger.info("Scenario Started Event Handler Run");
            }

            public void receivedStop() {
                logger.info("Scenario Stopped Event Handler Run");
            }
        });

        boolean running = true;
        while (running) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                logger.warn("Manually Interrupted. Shutting down");
                try {
                    hlaInterface.stop();
                } catch (RTIinternalError ignored) {
                }
                System.exit(0);
            }
        }
    }
}
