package com.yetanalytics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

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

        // Failure cases
        // Nonexistent parameter
        PathCheckResult missingParam = fomXml.checkInteractionParameterPath("CyberEvent", "NonExistantParam");
        logger.info("Missing Param: {}", missingParam);
        assertTrue(!missingParam.exists);

        // Bad input: null param name
        try {
            fomXml.checkInteractionParameterPath("CyberEvent", (List<Object>) null);
            assertTrue(false); // should not reach
        } catch (IllegalArgumentException e) {
            // expected
        }

        // Array index on a non-array field (e.g., trying to index into a simple field)
        PathCheckResult wrongIndex = fomXml.checkInteractionParameterPath("CyberEvent", List.of("Phase", 0));
        logger.info("Wrong Index on non-array: {}", wrongIndex);
        assertTrue(!wrongIndex.exists);

        // Nonexistent interaction
        PathCheckResult missingInteraction = fomXml.checkInteractionParameterPath("NonInteraction", "Anything");
        logger.info("Missing Interaction: {}", missingInteraction);
        assertTrue(!missingInteraction.exists);

    }

    @Test
    public void ObjectsXML() {
        FOMXML fomXml = FOMXML.getInstance();

        // Simple object attribute
        PathCheckResult objIdResult = fomXml.checkObjectParameterPath("CyberObject", List.of("ObjectID", "Value"));
        logger.info("CyberObject, ObjectID: {}", objIdResult);
        assertTrue(objIdResult.primitiveType.equals("HLAASCIIstring"));
        // We won't duplicate extensive failure cases here; interactions cover them.
    }
}
