package com.yetanalytics.hlaxapi;

import com.yetanalytics.hlaxapi.exception.XapiConfigurationException;

import hla.rti1516e.exceptions.ConnectionFailed;
import hla.rti1516e.exceptions.CouldNotOpenFDD;
import hla.rti1516e.exceptions.ErrorReadingFDD;
import hla.rti1516e.exceptions.FederateServiceInvocationsAreBeingReportedViaMOM;
import hla.rti1516e.exceptions.InconsistentFDD;
import hla.rti1516e.exceptions.InvalidLocalSettingsDesignator;
import hla.rti1516e.exceptions.NotConnected;
import hla.rti1516e.exceptions.RTIinternalError;
import hla.rti1516e.exceptions.RestoreInProgress;
import hla.rti1516e.exceptions.SaveInProgress;

public interface HlaInterface {

    /**
     * Connect to a CRC and join federation
     *
     * @param localSettingsDesignator The name to load settings for or "" to load
     *                                default settings
     * @param fomPath                 path to FOM file
     * @param federationName          Name of the federation to join
     * @param federateName            The name you want for your federate
     */
    void start()
            throws RestoreInProgress,
            SaveInProgress,
            NotConnected,
            FederateServiceInvocationsAreBeingReportedViaMOM,
            RTIinternalError,
            ConnectionFailed,
            InvalidLocalSettingsDesignator,
            ErrorReadingFDD,
            CouldNotOpenFDD,
            InconsistentFDD,
            XapiConfigurationException;

    /**
     * Resign and disconnect from CRC
     */
    void stop() throws RTIinternalError;

}
