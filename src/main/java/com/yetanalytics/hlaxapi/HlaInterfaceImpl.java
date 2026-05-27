package com.yetanalytics.hlaxapi;

import hla.rti1516e.*;
import hla.rti1516e.encoding.DecoderException;
import hla.rti1516e.encoding.EncoderFactory;
import hla.rti1516e.exceptions.*;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class HlaInterfaceImpl extends NullFederateAmbassador implements HlaInterface {

    private static final Logger logger = LogManager.getLogger(HlaInterfaceImpl.class);

    private RTIambassador _ambassador;

    private String _federateName = "";
    private String _federationName;

    private final List<InteractionListener> _listeners = new CopyOnWriteArrayList<InteractionListener>();

    private HLAunicodeStringCoder _unicodeStringCoder;
    private HLAunicodeStringCoder _callbackUnicodeStringCoder;
    private TimeScaleFactorFloat32Coder _callbackTimeScaleFactorCoder;

    private InteractionClassHandle _startInteractionClassHandle;
    private ParameterHandle _timeScaleFactorParameterHandle;
    private InteractionClassHandle _stopInteractionClassHandle;

    

    public HlaInterfaceImpl() {
    }

    public void start(String localSettingsDesignator, String fomPath, String federationName, String federateName)
            throws ConnectionFailed, InvalidLocalSettingsDesignator, RTIinternalError, NotConnected, ErrorReadingFDD,
            CouldNotOpenFDD, InconsistentFDD, RestoreInProgress, SaveInProgress,
            FederateServiceInvocationsAreBeingReportedViaMOM {
        RtiFactory rtiFactory = RtiFactoryFactory.getRtiFactory();
        _ambassador = rtiFactory.getRtiAmbassador();

        EncoderFactory encoderFactory = rtiFactory.getEncoderFactory();
        _unicodeStringCoder = new HLAunicodeStringCoder(encoderFactory);
        _callbackUnicodeStringCoder = new HLAunicodeStringCoder(encoderFactory);
        _callbackTimeScaleFactorCoder = new TimeScaleFactorFloat32Coder(encoderFactory);

        try {
            _ambassador.connect(this, CallbackModel.HLA_IMMEDIATE, localSettingsDesignator);
        } catch (AlreadyConnected ignored) {
        } catch (UnsupportedCallbackModel e) {
            throw new RTIinternalError("HlaInterfaceFailure", e);
        } catch (CallNotAllowedFromWithinCallback e) {
            throw new RTIinternalError("HlaInterfaceFailure", e);
        }

        _federationName = federationName;
        try {
            _ambassador.destroyFederationExecution(federationName);
        } catch (FederatesCurrentlyJoined ignored) {
        } catch (FederationExecutionDoesNotExist ignored) {
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
                    _federateName = federateName + federateNameSuffix;
                } catch (FederateNameAlreadyInUse e) {
                    federateNameSuffix = "-" + federateNameIndex++;
                }
            }
        } catch (FederateAlreadyExecutionMember ignored) {
        } catch (CouldNotCreateLogicalTimeFactory e) {
            throw new RTIinternalError("HlaInterfaceFailure", e);
        } catch (FederationExecutionDoesNotExist e) {
            throw new RTIinternalError("HlaInterfaceFailure", e);
        } catch (CallNotAllowedFromWithinCallback e) {
            throw new RTIinternalError("HlaInterfaceFailure", e);
        }
    

        try {
            getHandles();
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
            } catch (FederateNotExecutionMember ignored) {
            } catch (FederateOwnsAttributes e) {
                throw new RTIinternalError("HlaInterfaceFailure", e);
            } catch (OwnershipAcquisitionPending e) {
                throw new RTIinternalError("HlaInterfaceFailure", e);
            } catch (CallNotAllowedFromWithinCallback e) {
                throw new RTIinternalError("HlaInterfaceFailure", e);
            } catch (InvalidResignAction e) {
                throw new RTIinternalError("HlaInterfaceFailure", e);
            }

            if (_federationName != null) {
                try {
                    _ambassador.destroyFederationExecution(_federationName);
                } catch (FederatesCurrentlyJoined ignored) {
                } catch (FederationExecutionDoesNotExist ignored) {
                }
            }

            try {
                _ambassador.disconnect();
            } catch (FederateIsExecutionMember e) {
                throw new RTIinternalError("HlaInterfaceFailure", e);
            } catch (CallNotAllowedFromWithinCallback e) {
                throw new RTIinternalError("HlaInterfaceFailure", e);
            }
        } catch (NotConnected ignored) {
        }
    }

    private void getHandles() throws RTIinternalError, FederateNotExecutionMember, NotConnected {
        try {

            _startInteractionClassHandle = _ambassador.getInteractionClassHandle("Start");
            _timeScaleFactorParameterHandle = _ambassador.getParameterHandle(_startInteractionClassHandle,
                    "TimeScaleFactor");
            _stopInteractionClassHandle = _ambassador.getInteractionClassHandle("Stop");

        } catch (NameNotFound e) {
            throw new RTIinternalError("HlaInterfaceFailure", e);
        } catch (InvalidInteractionClassHandle e) {
            throw new RTIinternalError("HlaInterfaceFailure", e);
        }
    }

    private void subscribeInteractions() throws FederateNotExecutionMember, RestoreInProgress, SaveInProgress,
            NotConnected, RTIinternalError, FederateServiceInvocationsAreBeingReportedViaMOM {
        try {
            _ambassador.subscribeInteractionClass(_startInteractionClassHandle);
            _ambassador.subscribeInteractionClass(_stopInteractionClassHandle);
        } catch (InteractionClassNotDefined e) {
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

    public void addInteractionListener(InteractionListener listener) {
        _listeners.add(listener);
    }

    public void removeInteractionListener(InteractionListener listener) {
        _listeners.add(listener);
    }

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
        if (interactionClass.equals(_startInteractionClassHandle)) {
            logger.info("Received Start Interaction");
            try {
                float timeScaleFactor = _callbackTimeScaleFactorCoder
                        .decode(theParameters.get(_timeScaleFactorParameterHandle));
                for (InteractionListener listener : _listeners) {
                    listener.receivedStart(timeScaleFactor);
                }
            } catch (DecoderException e) {
                System.out.println("Failed to decode parameters for Start interaction");
                e.printStackTrace();
            }
        }
    }
}
