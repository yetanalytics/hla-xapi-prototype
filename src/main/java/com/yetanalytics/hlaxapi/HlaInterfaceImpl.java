package com.yetanalytics.hlaxapi;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.yetanalytics.hlaxapi.config.XapiConfig;
import com.yetanalytics.hlaxapi.config.model.StatementTrigger;
import com.yetanalytics.hlaxapi.injection.InteractionInjectionContext;

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
import hla.rti1516e.exceptions.InteractionParameterNotDefined;
import hla.rti1516e.exceptions.InvalidInteractionClassHandle;
import hla.rti1516e.exceptions.InvalidLocalSettingsDesignator;
import hla.rti1516e.exceptions.InvalidParameterHandle;
import hla.rti1516e.exceptions.InvalidResignAction;
import hla.rti1516e.exceptions.NameNotFound;
import hla.rti1516e.exceptions.NotConnected;
import hla.rti1516e.exceptions.OwnershipAcquisitionPending;
import hla.rti1516e.exceptions.RTIinternalError;
import hla.rti1516e.exceptions.RestoreInProgress;
import hla.rti1516e.exceptions.SaveInProgress;
import hla.rti1516e.exceptions.UnsupportedCallbackModel;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class HlaInterfaceImpl extends NullFederateAmbassador implements HlaInterface {

    private static final Logger logger = LogManager.getLogger(HlaInterfaceImpl.class);

    private RTIambassador ambassador;

    private String federationName;

    private ParameterHandle timeScaleFactorParameterHandle;

    @Autowired
    private XapiConfig xapiConfig;

    @Autowired
    private SimulationConfig simulationConfig;

    @Autowired
    private TriggerProcessor triggerProcessor;

    public void start()
            throws ConnectionFailed, InvalidLocalSettingsDesignator, RTIinternalError, NotConnected, ErrorReadingFDD,
            CouldNotOpenFDD, InconsistentFDD, RestoreInProgress, SaveInProgress,
            FederateServiceInvocationsAreBeingReportedViaMOM {
        RtiFactory rtiFactory = RtiFactoryFactory.getRtiFactory();
        ambassador = rtiFactory.getRtiAmbassador();

        // Use injected HLADecoderRegistry when available; fall back to creating one
        // decoderRegistry.registerAlias("ScaleFactorFloat32", "HLAfloat32BE");

        try {
            if (simulationConfig.getLocalSettingsDesignator() == null
                    || simulationConfig.getLocalSettingsDesignator().isBlank()) {
                ambassador.connect(this, CallbackModel.HLA_IMMEDIATE);
            } else {
                ambassador.connect(this, CallbackModel.HLA_IMMEDIATE, simulationConfig.getLocalSettingsDesignator());
            }
        } catch (UnsupportedCallbackModel | CallNotAllowedFromWithinCallback e) {
            throw new RTIinternalError("HlaInterfaceFailure", e);
        } catch (AlreadyConnected ignored) {
        }

        federationName = simulationConfig.getFederationName();
        try {
            ambassador.destroyFederationExecution(simulationConfig.getFederationName());
        } catch (FederatesCurrentlyJoined | FederationExecutionDoesNotExist ignored) {
        }

        File fddFile = new File(simulationConfig.getFom());
        URL url = null;
        try {
            url = fddFile.toURI().toURL();
        } catch (MalformedURLException ignored) {
        }

        try {
            ambassador.createFederationExecution(simulationConfig.getFederationName(), url);
        } catch (FederationExecutionAlreadyExists ignored) {
        }

        try {
            boolean joined = false;
            String federateNameSuffix = "";
            int federateNameIndex = 1;
            while (!joined) {
                try {
                    ambassador.joinFederationExecution(simulationConfig.getFederateName() + federateNameSuffix,
                            "xAPI Interaction Processor",
                            simulationConfig.getFederationName(),
                            new URL[] { url });
                    joined = true;
                } catch (FederateNameAlreadyInUse e) {
                    federateNameSuffix = "-" + federateNameIndex++;
                }
            }
        } catch (FederateAlreadyExecutionMember ignored) {
        } catch (CouldNotCreateLogicalTimeFactory | FederationExecutionDoesNotExist
                | CallNotAllowedFromWithinCallback e) {
            throw new RTIinternalError("HlaInterfaceFailure", e);
        }

        // Get relevant interactions to subscribe to from the xapiConfig

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
                ambassador.resignFederationExecution(ResignAction.CANCEL_THEN_DELETE_THEN_DIVEST);
            } catch (FederateOwnsAttributes | OwnershipAcquisitionPending
                    | CallNotAllowedFromWithinCallback | InvalidResignAction e) {
                throw new RTIinternalError("HlaInterfaceFailure", e);
            } catch (FederateNotExecutionMember ignored) {
            }

            if (federationName != null) {
                try {
                    ambassador.destroyFederationExecution(federationName);
                } catch (FederatesCurrentlyJoined
                        | FederationExecutionDoesNotExist ignored) {
                }
            }

            try {
                ambassador.disconnect();
            } catch (FederateIsExecutionMember | CallNotAllowedFromWithinCallback e) {
                throw new RTIinternalError("HlaInterfaceFailure", e);
            }
        } catch (NotConnected ignored) {
        }
    }

    @Override
    public void connectionLost(String faultDescription) throws FederateInternalError {
        System.out.println("Lost Connection because: " + faultDescription);
    }

    /*
     * Interactions
     */

    private void subscribeInteractions()
            throws FederateNotExecutionMember, RestoreInProgress, SaveInProgress, NotConnected,
            RTIinternalError, FederateServiceInvocationsAreBeingReportedViaMOM {
        xapiConfig.statementTriggers.forEach(trigger -> {
            try {
                InteractionClassHandle handle = ambassador.getInteractionClassHandle(trigger.clazz);
                ambassador.subscribeInteractionClass(handle);
            } catch (NameNotFound | FederateNotExecutionMember | NotConnected | RTIinternalError
                    | FederateServiceInvocationsAreBeingReportedViaMOM | InteractionClassNotDefined
                    | SaveInProgress | RestoreInProgress e) {
                logger.error("Could not register listener for {}!", trigger.clazz, e);
            }
        });
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
        try {
            String interactionName = ambassador.getInteractionClassName(interactionClass);
            logger.info("Interaction Handle: {}", interactionName);
            String interactionKey = StringUtils.substringAfterLast(interactionName, ".");

            // Create Interaction-specific injection context to pass to trigger processor
            InteractionInjectionContext context = new InteractionInjectionContext(interactionKey,
                    getMapWithParameterNames(interactionClass, theParameters));

            // pass each matching interaction trigger to trigger processor
            xapiConfig.statementTriggers.stream()
                    .filter(trigger -> trigger.clazz.equals(interactionKey)
                            && trigger.type.equals(StatementTrigger.Type.INTERACTION))
                    .forEach(trigger -> {
                        logger.info("Processing trigger for interaction {}", trigger.clazz);
                        triggerProcessor.processTrigger(trigger, context);
                    });
        } catch (InvalidInteractionClassHandle | FederateNotExecutionMember | NotConnected | RTIinternalError e) {
            logger.error("Error ascertaining interaction details!", e);
        }
    }

    private Map<String, byte[]> getMapWithParameterNames(InteractionClassHandle interactionClass,
            ParameterHandleValueMap theParameters) {
        Map<String, byte[]> parameters = new HashMap<String, byte[]>();
        theParameters.forEach((handle, value) -> {
            String paramName;
            try {
                paramName = ambassador.getParameterName(interactionClass, handle);
                parameters.put(paramName, value);
            } catch (InteractionParameterNotDefined | InvalidParameterHandle | InvalidInteractionClassHandle
                    | FederateNotExecutionMember | NotConnected | RTIinternalError e) {
                throw new RuntimeException("Exception processing parameter " + handle + " for interaction "
                        + interactionClass, e);
            }
        });
        return parameters;

    }
}
