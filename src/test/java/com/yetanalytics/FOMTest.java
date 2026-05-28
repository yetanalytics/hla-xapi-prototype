package com.yetanalytics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.xml.xpath.XPathExpressionException;

import com.yetanalytics.hlaxapi.fom.FOMXML;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

/**
 * Tests for FOM Parsing.
 */
public class FOMTest {

    private static final Logger logger = LogManager.getLogger(FOMTest.class);

    @Test
    public void EatsXML() {


        try {
            FOMXML.initInstance("config/fom.xml");
            FOMXML fomXml = FOMXML.getInstance();

            String scenarioNameType = fomXml.checkInteractionParameterDatatype("LoadScenario", "ScenarioName");
            logger.info("Load Scenario Name Type: {}", scenarioNameType);
            assertTrue(scenarioNameType.equals("HLAunicodeString"));

            String initialFuelAmountType = fomXml.checkInteractionParameterDatatype("LoadScenario", "InitialFuelAmount");
            assertTrue(initialFuelAmountType.equals("FuelInt32"));

            String fuelIntRepresentation = fomXml.checkCustomDatatypes(initialFuelAmountType);
            logger.info("Custom FuelInt representation: {}", fuelIntRepresentation);
            assertTrue(fuelIntRepresentation.equals("HLAinteger32BE"));



        } catch (XPathExpressionException e) {
            logger.error("Error unmarshalling", e);
        }
        
        
    }
}
