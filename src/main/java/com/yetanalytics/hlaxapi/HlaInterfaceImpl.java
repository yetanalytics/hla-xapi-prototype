package com.yetanalytics.hlaxapi;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.yetanalytics.hlaxapi.cache.FomCatalog;
import com.yetanalytics.hlaxapi.cache.HlaObjectCache;
import com.yetanalytics.hlaxapi.cache.QueryReferenceCollector;
import com.yetanalytics.hlaxapi.config.XapiConfig;
import com.yetanalytics.hlaxapi.config.model.StatementTrigger;
import com.yetanalytics.hlaxapi.injection.InteractionInjectionContext;

import hla.rti1516e.AttributeHandle;
import hla.rti1516e.AttributeHandleSet;
import hla.rti1516e.AttributeHandleValueMap;
import hla.rti1516e.CallbackModel;
import hla.rti1516e.InteractionClassHandle;
import hla.rti1516e.LogicalTime;
import hla.rti1516e.MessageRetractionHandle;
import hla.rti1516e.NullFederateAmbassador;
import hla.rti1516e.ObjectClassHandle;
import hla.rti1516e.ObjectInstanceHandle;
import hla.rti1516e.OrderType;
import hla.rti1516e.ParameterHandleValueMap;
import hla.rti1516e.RTIambassador;
import hla.rti1516e.ResignAction;
import hla.rti1516e.RtiFactory;
import hla.rti1516e.RtiFactoryFactory;
import hla.rti1516e.TransportationTypeHandle;
import hla.rti1516e.exceptions.AlreadyConnected;
import hla.rti1516e.exceptions.AttributeNotDefined;
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
import hla.rti1516e.exceptions.FederationExecutionAlreadyExists;
import hla.rti1516e.exceptions.FederationExecutionDoesNotExist;
import hla.rti1516e.exceptions.InconsistentFDD;
import hla.rti1516e.exceptions.InteractionClassNotDefined;
import hla.rti1516e.exceptions.InvalidAttributeHandle;
import hla.rti1516e.exceptions.InteractionParameterNotDefined;
import hla.rti1516e.exceptions.InvalidInteractionClassHandle;
import hla.rti1516e.exceptions.InvalidLocalSettingsDesignator;
import hla.rti1516e.exceptions.InvalidObjectClassHandle;
import hla.rti1516e.exceptions.InvalidParameterHandle;
import hla.rti1516e.exceptions.InvalidResignAction;
import hla.rti1516e.exceptions.NameNotFound;
import hla.rti1516e.exceptions.NotConnected;
import hla.rti1516e.exceptions.ObjectClassNotDefined;
import hla.rti1516e.exceptions.ObjectInstanceNotKnown;
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

    @Autowired
    private XapiConfig xapiConfig;

    @Autowired
    private SimulationConfig simulationConfig;

    @Autowired
    private FOMXML fomXml;

    @Autowired
    private HLADecoderRegistry decoderRegistry;

    @Autowired
    private TriggerProcessor triggerProcessor;

    @Autowired
    private InjectionHandler injectionHandler;

    private FomCatalog fomCatalog;

    private HlaObjectCache objectCache;

    private Map<String, Set<String>> objectCacheSubscriptions = Collections.emptyMap();

    public void start()
            throws ConnectionFailed, InvalidLocalSettingsDesignator, RTIinternalError, NotConnected, ErrorReadingFDD,
            CouldNotOpenFDD, InconsistentFDD, RestoreInProgress, SaveInProgress,
            FederateServiceInvocationsAreBeingReportedViaMOM {
        RtiFactory rtiFactory = RtiFactoryFactory.getRtiFactory();
        ambassador = rtiFactory.getRtiAmbassador();

        fomCatalog = FomCatalog.fromFomXml(fomXml);
        objectCacheSubscriptions = QueryReferenceCollector.collect(xapiConfig.statementTriggers);
        if (objectCacheSubscriptions.isEmpty()) {
            injectionHandler.setQueryService(null);
            logger.info("No query injections configured; object cache is disabled");
        } else {
            objectCache = new HlaObjectCache(HlaObjectCache.defaultJdbcUrl(), fomCatalog, fomXml, decoderRegistry);
            injectionHandler.setQueryService(objectCache.queryService());
        }

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
            subscribeObjectClasses();
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

            try {
                ambassador.disconnect();
            } catch (FederateIsExecutionMember | CallNotAllowedFromWithinCallback e) {
                throw new RTIinternalError("HlaInterfaceFailure", e);
            }
            injectionHandler.setQueryService(null);
            if (objectCache != null) {
                objectCache.close();
                objectCache = null;
            }
        } catch (NotConnected ignored) {
        }
    }

    @Override
    public void connectionLost(String faultDescription) throws FederateInternalError {
        System.out.println("Lost Connection because: " + faultDescription);
    }

    /*
     * Objects
     */

    private void subscribeObjectClasses()
            throws FederateNotExecutionMember, RestoreInProgress, SaveInProgress, NotConnected, RTIinternalError {
        if (objectCacheSubscriptions.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Set<String>> subscription : objectCacheSubscriptions.entrySet()) {
            try {
                FomCatalog.ObjectClassDef clazz = fomCatalog.objectClass(subscription.getKey()).orElseThrow(
                        () -> new IllegalArgumentException("No FOM object class " + subscription.getKey()));
                ObjectClassHandle classHandle = ambassador.getObjectClassHandle(clazz.localName());
                AttributeHandleSet attributeHandles = ambassador.getAttributeHandleSetFactory().create();
                for (String attributeName : subscription.getValue()) {
                    attributeHandles.add(ambassador.getAttributeHandle(classHandle, attributeName));
                }
                if (attributeHandles.isEmpty()) {
                    continue;
                }
                ambassador.subscribeObjectClassAttributes(classHandle, attributeHandles);
            } catch (AttributeNotDefined | InvalidObjectClassHandle | NameNotFound | ObjectClassNotDefined
                    | IllegalArgumentException e) {
                logger.error("Could not subscribe object class {}!", subscription.getKey(), e);
            }
        }
    }

    @Override
    public void discoverObjectInstance(
            ObjectInstanceHandle theObject,
            ObjectClassHandle theObjectClass,
            String objectName) throws FederateInternalError {
        discoverObjectInstance(theObject, theObjectClass, objectName, null);
    }

    @Override
    public void discoverObjectInstance(
            ObjectInstanceHandle theObject,
            ObjectClassHandle theObjectClass,
            String objectName,
            hla.rti1516e.FederateHandle producingFederate) throws FederateInternalError {
        if (objectCache == null) {
            return;
        }
        try {
            String className = StringUtils.substringAfterLast(ambassador.getObjectClassName(theObjectClass), ".");
            objectCache.discoverObject(theObject.toString(), objectName, className);
            logger.info("Discovered object {} as {}", objectName, className);
        } catch (InvalidObjectClassHandle | FederateNotExecutionMember | NotConnected | RTIinternalError
                | RuntimeException e) {
            logger.error("Error caching discovered object {}", objectName, e);
        }
    }

    @Override
    public void reflectAttributeValues(
            ObjectInstanceHandle theObject,
            AttributeHandleValueMap theAttributes,
            byte[] userSuppliedTag,
            OrderType sentOrdering,
            TransportationTypeHandle theTransport,
            SupplementalReflectInfo reflectInfo) throws FederateInternalError {
        reflectAttributeValues(theObject, theAttributes);
    }

    @Override
    public void reflectAttributeValues(
            ObjectInstanceHandle theObject,
            AttributeHandleValueMap theAttributes,
            byte[] userSuppliedTag,
            OrderType sentOrdering,
            TransportationTypeHandle theTransport,
            LogicalTime theTime,
            OrderType receivedOrdering,
            SupplementalReflectInfo reflectInfo) throws FederateInternalError {
        reflectAttributeValues(theObject, theAttributes);
    }

    @Override
    public void reflectAttributeValues(
            ObjectInstanceHandle theObject,
            AttributeHandleValueMap theAttributes,
            byte[] userSuppliedTag,
            OrderType sentOrdering,
            TransportationTypeHandle theTransport,
            LogicalTime theTime,
            OrderType receivedOrdering,
            MessageRetractionHandle retractionHandle,
            SupplementalReflectInfo reflectInfo) throws FederateInternalError {
        reflectAttributeValues(theObject, theAttributes);
    }

    private void reflectAttributeValues(ObjectInstanceHandle theObject, AttributeHandleValueMap theAttributes) {
        if (objectCache == null) {
            return;
        }
        try {
            ObjectClassHandle classHandle = ambassador.getKnownObjectClassHandle(theObject);
            String className = StringUtils.substringAfterLast(ambassador.getObjectClassName(classHandle), ".");
            for (AttributeHandle attributeHandle : theAttributes.keySet()) {
                String attributeName = ambassador.getAttributeName(classHandle, attributeHandle);
                objectCache.reflectAttributeValue(
                        theObject.toString(),
                        className,
                        attributeName,
                        theAttributes.get(attributeHandle));
            }
        } catch (AttributeNotDefined | InvalidAttributeHandle | InvalidObjectClassHandle | ObjectInstanceNotKnown
                | FederateNotExecutionMember | NotConnected | RTIinternalError | RuntimeException e) {
            logger.error("Error caching reflected object attributes", e);
        }
    }

    @Override
    public void removeObjectInstance(
            ObjectInstanceHandle theObject,
            byte[] userSuppliedTag,
            OrderType sentOrdering,
            SupplementalRemoveInfo removeInfo) throws FederateInternalError {
        removeCachedObject(theObject);
    }

    @Override
    public void removeObjectInstance(
            ObjectInstanceHandle theObject,
            byte[] userSuppliedTag,
            OrderType sentOrdering,
            LogicalTime theTime,
            OrderType receivedOrdering,
            SupplementalRemoveInfo removeInfo) throws FederateInternalError {
        removeCachedObject(theObject);
    }

    @Override
    public void removeObjectInstance(
            ObjectInstanceHandle theObject,
            byte[] userSuppliedTag,
            OrderType sentOrdering,
            LogicalTime theTime,
            OrderType receivedOrdering,
            MessageRetractionHandle retractionHandle,
            SupplementalRemoveInfo removeInfo) throws FederateInternalError {
        removeCachedObject(theObject);
    }

    private void removeCachedObject(ObjectInstanceHandle theObject) {
        if (objectCache == null) {
            return;
        }
        try {
            objectCache.removeObject(theObject.toString());
        } catch (RuntimeException e) {
            logger.error("Error removing cached object {}", theObject, e);
        }
    }

    /*
     * Interactions
     */

    private void subscribeInteractions()
            throws FederateNotExecutionMember, RestoreInProgress, SaveInProgress, NotConnected,
            RTIinternalError, FederateServiceInvocationsAreBeingReportedViaMOM {
        if (xapiConfig.statementTriggers == null) {
            return;
        }
        xapiConfig.statementTriggers.stream()
                .filter(trigger -> trigger.type == StatementTrigger.Type.INTERACTION)
                .forEach(trigger -> {
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
