package com.yetanalytics.hlaxapi;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import hla.rti1516e.exceptions.RTIexception;
import hla.rti1516e.exceptions.RTIinternalError;

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
                    config.getFederateName(), getInteractionMap());
        } catch (RTIexception e) {
            System.out.println("Could not connect to the RTI using the local settings designator \""
                    + config.getLocalSettingsDesignator() + "\"");
            e.printStackTrace();
            System.exit(0);
        }

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

    //This will be set by config and a parser, not manually
    private static Map<String, String[]> getInteractionMap(){
        Map<String, String[]> interacMap = new HashMap<>();
        interacMap.put("LoadScenario", new String[]{"ScenarioName", "InitialFuelAmount"});
        interacMap.put("ScenarioLoaded", new String[]{"FederateName"});
        interacMap.put("ScenarioLoadFailure", new String[]{"FederateName", "ErrorMessage"});
        interacMap.put("Start", new String[]{"TimeScaleFactor"});
        interacMap.put("Stop", new String[]{});
        return interacMap;
    }
}
