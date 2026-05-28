package com.yetanalytics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.xml.xpath.XPathExpressionException;

import com.yetanalytics.hlaxapi.fom.FOMXMLMapper;

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
            FOMXMLMapper.initInstance("config/fom.xml");
            String type = FOMXMLMapper.getInstance().checkInteractionParameterDatatype("LoadScenario", "ScenarioName");
            logger.info("Load Scenario Name Type: {}", type);
            assertTrue(type.equals("HLAunicodeString"));
        } catch (XPathExpressionException e) {
            logger.error("Error unmarshalling", e);
        }
        
        
    }
}
