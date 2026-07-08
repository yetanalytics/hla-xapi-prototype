package com.yetanalytics.hlaxapi;

import hla.rti1516e.RtiFactory;
import hla.rti1516e.RtiFactoryFactory;
import hla.rti1516e.exceptions.RTIinternalError;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.yetanalytics.hlaxapi.config.ConfigParser;
import com.yetanalytics.hlaxapi.config.XapiConfig;
import com.yetanalytics.xapi.util.StatementValidator;

import hla.rti1516e.encoding.EncoderFactory;


/**
 * Spring configuration class for beans related to HLA and simulation configuration.
 */
@Configuration
@EnableScheduling
public class AppConfig {

    private static final Logger logger = LogManager.getLogger(AppConfig.class);

    @Bean
    public EncoderFactory encoderFactory() {
        try {
            RtiFactory rtiFactory = RtiFactoryFactory.getRtiFactory();
            return rtiFactory.getEncoderFactory();
        } catch (RTIinternalError e) {
            throw new RuntimeException("Could not obtain EncoderFactory from RTI", e);
        }
    }

    @Bean
    public HLADecoderRegistry hlaDecoderRegistry(EncoderFactory encoderFactory) {
        return new HLADecoderRegistry(encoderFactory);
    }

    @Bean
    public StatementValidator statementValidator() {
        return new StatementValidator();
    }

    @Bean
    public XapiConfig xapiConfig() {
        XapiConfig xapiConfig;
        try {
            xapiConfig = ConfigParser.fromEnvOrDefault().parse();
            logger.info(
                    "Loaded xapi config: {} triggers",
                    xapiConfig.statementTriggers == null ? 0 : xapiConfig.statementTriggers.size());
            return xapiConfig;
        } catch (Exception e) {
            logger.warn("Could not load xapi config", e);
            throw new RuntimeException(e);
        }
    }

    @Bean
    public SimulationConfig simulationConfig() {
        String path = System.getenv().getOrDefault("SIM_CONFIG", "config/Simulation.config");

        final SimulationConfig config;
        try {
            config = new SimulationConfig(path);
            return config;
        } catch (IOException e) {
            logger.error("Could not read Simulation config: " + path, e);
            throw new RuntimeException(e);
        }
    }
}
