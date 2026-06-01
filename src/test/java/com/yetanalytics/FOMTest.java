package com.yetanalytics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import javax.xml.xpath.XPathExpressionException;

import com.yetanalytics.hlaxapi.fom.FOMXML;
import com.yetanalytics.hlaxapi.fom.FOMXML.PathCheckResult;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests for FOM Parsing.
 */
public class FOMTest {

    private static final Logger logger = LogManager.getLogger(FOMTest.class);

    @BeforeAll
    public static void setUp() {
        FOMXML.initInstance("src/test/resources/SISO-STD-025.3-2024.xml");
    }

    @Test
    public void InteractionsXML() {

        FOMXML fomXml = FOMXML.getInstance();

        // primitive parameter
        PathCheckResult isRandomResult = fomXml.checkInteractionParameterPath("Degrade", "IsRandom");
        logger.info("Degrade, IsRandom Type: {}", isRandomResult);
        assertTrue(isRandomResult.primitiveType.equals("HLAboolean"));

        // simple data type
        PathCheckResult PercentageResult = fomXml.checkInteractionParameterPath("Degrade", "Percentage");
        logger.info("Degrade, Percentage Type: {}", PercentageResult);
        assertTrue(PercentageResult.primitiveType.equals("HLAfloat64BE"));

        // fixedRecord + simpledata
        PathCheckResult CyberEventTimeHoursResult = fomXml.checkInteractionParameterPath("CyberEvent",
                List.of("EventTime", "Hours"));
        logger.info("CyberEvent, EventTime, Hours Type: {}", CyberEventTimeHoursResult);
        assertTrue(CyberEventTimeHoursResult.primitiveType.equals("HLAinteger32BE"));

        //add enum test here when we have an enum in the FOM
        PathCheckResult CyberEventPhaseResult = fomXml.checkInteractionParameterPath("CyberEvent", "Phase");
        logger.info("CyberEvent, Phase Type: {}", CyberEventPhaseResult);
        assertTrue(CyberEventPhaseResult.primitiveType.equals("HLAinteger32BE"));

        // array of fixedRecord + simpledata
        PathCheckResult CyberEventTargetModifiersResult = fomXml.checkInteractionParameterPath("CyberEvent",
                List.of("TargetModifiers", 0, "Key"));
        logger.info("CyberEvent, TargetModifiers[], Key: {}", CyberEventTargetModifiersResult);
        assertTrue(CyberEventTargetModifiersResult.primitiveType.equals("HLAASCIIstring"));

    }
}
