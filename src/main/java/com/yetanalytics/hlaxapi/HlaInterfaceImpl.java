package com.yetanalytics.hlaxapi;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import hla.rti1516e.CallbackModel;
import hla.rti1516e.InteractionClassHandle;
import hla.rti1516e.LogicalTime;
import hla.rti1516e.MessageRetractionHandle;
import hla.rti1516e.NullFederateAmbassador;
import hla.rti1516e.OrderType;
import hla.rti1516e.ParameterHandle;
import hla.rti1516e.ParameterHandleValueMap;
import hla.rti1516e.RTIambassador;
import hla.rti1516e.ResignAction;
import hla.rti1516e.RtiFactory;
import hla.rti1516e.RtiFactoryFactory;
import hla.rti1516e.TransportationTypeHandle;
import hla.rti1516e.encoding.EncoderFactory;
import hla.rti1516e.exceptions.AlreadyConnected;
import hla.rti1516e.exceptions.CallNotAllowedFromWithinCallback;
import hla.rti1516e.exceptions.ConnectionFailed;
import hla.rti1516e.exceptions.CouldNotCreateLogicalTimeFactory;
import hla.rti1516e.exceptions.CouldNotOpenFDD;
import hla.rti1516e.exceptions.ErrorReadingFDD;
import hla.rti1516e.exceptions.FederateAlreadyExecutionMember;
import hla.rti1516e.exceptions.FederateInternalError;
import hla.rti1516e.exceptions.FederateIsExecutionMember;
import hla.rti1516e.exceptions.FederateNameAlreadyInUse;
import hla.rti1516e.exceptions.FederateNotExecutionMember;
import hla.rti1516e.exceptions.FederateOwnsAttributes;
import hla.rti1516e.exceptions.FederateServiceInvocationsAreBeingReportedViaMOM;
import hla.rti1516e.exceptions.FederatesCurrentlyJoined;
import hla.rti1516e.exceptions.FederationExecutionAlreadyExists;
import hla.rti1516e.exceptions.FederationExecutionDoesNotExist;
import hla.rti1516e.exceptions.InconsistentFDD;
import hla.rti1516e.exceptions.InteractionClassNotDefined;
import hla.rti1516e.exceptions.InvalidInteractionClassHandle;
import hla.rti1516e.exceptions.InvalidLocalSettingsDesignator;
import hla.rti1516e.exceptions.InvalidResignAction;
import hla.rti1516e.exceptions.NameNotFound;
import hla.rti1516e.exceptions.NotConnected;
import hla.rti1516e.exceptions.OwnershipAcquisitionPending;
import hla.rti1516e.exceptions.RTIinternalError;
import hla.rti1516e.exceptions.RestoreInProgress;
import hla.rti1516e.exceptions.SaveInProgress;
import hla.rti1516e.exceptions.UnsupportedCallbackModel;

class HlaInterfaceImpl extends NullFederateAmbassador implements HlaInterface {

    private static final Logger logger = LogManager.getLogger(HlaInterfaceImpl.class);

    private RTIambassador _ambassador;

    private String _federationName;

    private TimeScaleFactorFloat32Coder _callbackTimeScaleFactorCoder;

    private ParameterHandle _timeScaleFactorParameterHandle;
    
    

    public HlaInterfaceImpl() {
    }

    public void start(String localSettingsDesignator, String fomPath, String federationName, String federateName)
            throws ConnectionFailed, InvalidLocalSettingsDesignator, RTIinternalError, NotConnected, ErrorReadingFDD,
            CouldNotOpenFDD, InconsistentFDD, RestoreInProgress, SaveInProgress,
            FederateServiceInvocationsAreBeingReportedViaMOM {
        RtiFactory rtiFactory = RtiFactoryFactory.getRtiFactory();
        _ambassador = rtiFactory.getRtiAmbassador();

        EncoderFactory encoderFactory = rtiFactory.getEncoderFactory();
        _callbackTimeScaleFactorCoder = new TimeScaleFactorFloat32Coder(encoderFactory);

        try {
            _ambassador.connect(this, CallbackModel.HLA_IMMEDIATE, localSettingsDesignator);
        } catch (UnsupportedCallbackModel | CallNotAllowedFromWithinCallback e) {
            throw new RTIinternalError("HlaInterfaceFailure", e);
        } catch (AlreadyConnected ignored) {
        }

        _federationName = federationName;
        try {
            _ambassador.destroyFederationExecution(federationName);
        } catch (FederatesCurrentlyJoined | FederationExecutionDoesNotExist ignored) {
        }

        File fddFile = new File(fomPath);
        URL url = null;
        try {
            url = fddFile.toURI().toURL();
        } catch (MalformedURLException ignored) {
        }

        try {
            _ambassador.createFederationExecution(federationName, url);
        } catch (FederationExecutionAlreadyExists ignored) {
        }

        try {
            boolean joined = false;
            String federateNameSuffix = "";
            int federateNameIndex = 1;
            while (!joined) {
                try {
                    _ambassador.joinFederationExecution(federateName + federateNameSuffix, "xAPI Interaction Processor", federationName,
                            new URL[] { url });
                    joined = true;
                } catch (FederateNameAlreadyInUse e) {
                    federateNameSuffix = "-" + federateNameIndex++;
                }
            }
        } catch (FederateAlreadyExecutionMember ignored) {
        } catch (CouldNotCreateLogicalTimeFactory | FederationExecutionDoesNotExist | CallNotAllowedFromWithinCallback e) {
            throw new RTIinternalError("HlaInterfaceFailure", e);
        } 
    

        try {
            subscribeInteractions();
            logger.info("Started Subscription");

        } catch (FederateNotExecutionMember e) {
            throw new RTIinternalError("HlaInterfaceFailure", e);
        }
    }

    public void stop() throws RTIinternalError {
        try {
            try {
                _ambassador.resignFederationExecution(ResignAction.CANCEL_THEN_DELETE_THEN_DIVEST);
            } catch (FederateOwnsAttributes | OwnershipAcquisitionPending 
                | CallNotAllowedFromWithinCallback | InvalidResignAction e) {
                throw new RTIinternalError("HlaInterfaceFailure", e);
            } catch (FederateNotExecutionMember ignored) {
            } 

            if (_federationName != null) {
                try {
                    _ambassador.destroyFederationExecution(_federationName);
                } catch (FederatesCurrentlyJoined 
                    | FederationExecutionDoesNotExist ignored) {}
            }

            try {
                _ambassador.disconnect();
            } catch (FederateIsExecutionMember | CallNotAllowedFromWithinCallback e) {
                throw new RTIinternalError("HlaInterfaceFailure", e);
            } 
        } catch (NotConnected ignored) {
        }
    }

    private void subscribeInteractions() throws FederateNotExecutionMember, RestoreInProgress, SaveInProgress,
            NotConnected, RTIinternalError, FederateServiceInvocationsAreBeingReportedViaMOM {
        try {
            //All Interactions
            InteractionClassHandle rootHandle = _ambassador.getInteractionClassHandle("HLAinteractionRoot");
            _ambassador.subscribeInteractionClass(rootHandle);
            
        } catch (InteractionClassNotDefined | NameNotFound e) {
            throw new RTIinternalError("HlaInterfaceFailure", e);
        }
    }

    

    @Override
    public void connectionLost(String faultDescription) throws FederateInternalError {
        System.out.println("Lost Connection because: " + faultDescription);
    }

    /*
     * Interactions
     */



    @Override
    public void receiveInteraction(InteractionClassHandle interactionClass, ParameterHandleValueMap theParameters,
            byte[] userSuppliedTag, OrderType sentOrdering, TransportationTypeHandle theTransport,
            SupplementalReceiveInfo receiveInfo) throws FederateInternalError {
        receiveInteraction(interactionClass, theParameters);
    }

    @Override
    public void receiveInteraction(InteractionClassHandle interactionClass, ParameterHandleValueMap theParameters,
            byte[] userSuppliedTag, OrderType sentOrdering, TransportationTypeHandle theTransport, LogicalTime theTime,
            OrderType receivedOrdering, SupplementalReceiveInfo receiveInfo) throws FederateInternalError {
        receiveInteraction(interactionClass, theParameters);
    }

    @Override
    public void receiveInteraction(InteractionClassHandle interactionClass, ParameterHandleValueMap theParameters,
            byte[] userSuppliedTag, OrderType sentOrdering, TransportationTypeHandle theTransport, LogicalTime theTime,
            OrderType receivedOrdering, MessageRetractionHandle retractionHandle, SupplementalReceiveInfo receiveInfo)
            throws FederateInternalError {
        receiveInteraction(interactionClass, theParameters);
    }

    private void receiveInteraction(InteractionClassHandle interactionClass, ParameterHandleValueMap theParameters) {
        logger.info("Received Interaction");
        try {
            String interactionName = _ambassador.getInteractionClassName(interactionClass);
            logger.info("Interaction Handle: {}", interactionName);
        } catch (InvalidInteractionClassHandle | FederateNotExecutionMember | NotConnected | RTIinternalError e) {
            logger.error("Error ascertaining interaction details!", e);
        }
    }
}
