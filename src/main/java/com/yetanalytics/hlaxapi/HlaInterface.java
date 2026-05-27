package com.yetanalytics.hlaxapi;

import hla.rti1516e.exceptions.*;


public interface HlaInterface {

   /**
    * Connect to a CRC and join federation
    *
    * @param localSettingsDesignator The name to load settings for or "" to load default settings
    * @param fomPath                 path to FOM file
    * @param federationName          Name of the federation to join
    * @param federateName            The name you want for your federate
    */
   void start(String localSettingsDesignator, String fomPath, String federationName, String federateName)
         throws RestoreInProgress,
                SaveInProgress,
                NotConnected,
                FederateServiceInvocationsAreBeingReportedViaMOM,
                RTIinternalError,
                ConnectionFailed,
                InvalidLocalSettingsDesignator,
                ErrorReadingFDD,
                CouldNotOpenFDD,
                InconsistentFDD;

   /**
    * Resign and disconnect from CRC
    */
   void stop() throws RTIinternalError;

   public static class Factory {
      public static HlaInterface newInterface() {
         return new HlaInterfaceImpl();
      }
   }
}
