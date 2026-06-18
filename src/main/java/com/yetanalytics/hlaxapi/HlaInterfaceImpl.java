package com.yetanalytics.hlaxapi;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.yetanalytics.hlaxapi.cache.FomCatalog;
import com.yetanalytics.hlaxapi.cache.HlaObjectCache;
import com.yetanalytics.hlaxapi.config.InjectionHandler;
import com.yetanalytics.hlaxapi.config.XapiConfig;

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
import hla.rti1516e.ParameterHandle;
import hla.rti1516e.ParameterHandleValueMap;
import hla.rti1516e.RTIambassador;
import hla.rti1516e.ResignAction;
import hla.rti1516e.RtiFactory;
import hla.rti1516e.RtiFactoryFactory;
import hla.rti1516e.TransportationTypeHandle;
import hla.rti1516e.encoding.EncoderFactory;
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
import hla.rti1516e.exceptions.FederatesCurrentlyJoined;
import hla.rti1516e.exceptions.FederationExecutionAlreadyExists;
import hla.rti1516e.exceptions.FederationExecutionDoesNotExist;
import hla.rti1516e.exceptions.InconsistentFDD;
import hla.rti1516e.exceptions.InteractionClassNotDefined;
import hla.rti1516e.exceptions.InvalidAttributeHandle;
import hla.rti1516e.exceptions.InvalidInteractionClassHandle;
import hla.rti1516e.exceptions.InvalidLocalSettingsDesignator;
import hla.rti1516e.exceptions.InvalidObjectClassHandle;
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

class HlaInterfaceImpl extends NullFederateAmbassador implements HlaInterface {

    private static final Logger logger = LogManager.getLogger(HlaInterfaceImpl.class);

    private RTIambassador _ambassador;

    private String _federationName;

    private HLADecoderRegistry _decoderRegistry;

    private ParameterHandle _timeScaleFactorParameterHandle;

    private XapiConfig xapiConfig;

    private FomCatalog _fomCatalog;

    private HlaObjectCache _objectCache;

    public HlaInterfaceImpl() {
    }

    public void start(String localSettingsDesignator, String fomPath, String federationName, String federateName,
            XapiConfig xapiConfigInput)
            throws ConnectionFailed, InvalidLocalSettingsDesignator, RTIinternalError, NotConnected, ErrorReadingFDD,
            CouldNotOpenFDD, InconsistentFDD, RestoreInProgress, SaveInProgress,
            FederateServiceInvocationsAreBeingReportedViaMOM {
        RtiFactory rtiFactory = RtiFactoryFactory.getRtiFactory();
        _ambassador = rtiFactory.getRtiAmbassador();

        xapiConfig = xapiConfigInput;

        EncoderFactory encoderFactory = rtiFactory.getEncoderFactory();
        _decoderRegistry = new HLADecoderRegistry(encoderFactory);
        _decoderRegistry.registerAlias("ScaleFactorFloat32", "HLAfloat32BE");
        _fomCatalog = FomCatalog.fromFile(fomPath);
        _fomCatalog.aliasesToPrimitiveTypes().forEach((alias, primitiveType) -> {
            if (!_decoderRegistry.supports(alias)) {
                _decoderRegistry.registerAlias(alias, primitiveType);
            }
        });
        _objectCache = new HlaObjectCache(HlaObjectCache.defaultJdbcUrl(), _fomCatalog, _decoderRegistry,
                encoderFactory);
        InjectionHandler.setQueryService(_objectCache.queryService());

        try {
            if (localSettingsDesignator == null || localSettingsDesignator.isBlank()) {
                _ambassador.connect(this, CallbackModel.HLA_IMMEDIATE);
            } else {
                _ambassador.connect(this, CallbackModel.HLA_IMMEDIATE, localSettingsDesignator);
            }
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
                    _ambassador.joinFederationExecution(federateName + federateNameSuffix, "xAPI Interaction Processor",
                            federationName,
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
                        | FederationExecutionDoesNotExist ignored) {
                }
            }

            try {
                _ambassador.disconnect();
            } catch (FederateIsExecutionMember | CallNotAllowedFromWithinCallback e) {
                throw new RTIinternalError("HlaInterfaceFailure", e);
            }
            if (_objectCache != null) {
                _objectCache.close();
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
        for (FomCatalog.ObjectClassDef clazz : _fomCatalog.objectClasses()) {
            if (clazz.topLevelAttributeNames().isEmpty()) {
                continue;
            }
            try {
                ObjectClassHandle classHandle = _ambassador.getObjectClassHandle(clazz.localName());
                AttributeHandleSet attributeHandles = _ambassador.getAttributeHandleSetFactory().create();
                for (String attributeName : clazz.topLevelAttributeNames()) {
                    attributeHandles.add(_ambassador.getAttributeHandle(classHandle, attributeName));
                }
                _ambassador.subscribeObjectClassAttributes(classHandle, attributeHandles);
            } catch (AttributeNotDefined | InvalidObjectClassHandle | NameNotFound | ObjectClassNotDefined e) {
                logger.error("Could not subscribe object class {}!", clazz.localName(), e);
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
        try {
            String className = StringUtils.substringAfterLast(_ambassador.getObjectClassName(theObjectClass), ".");
            _objectCache.discoverObject(theObject.toString(), objectName, className);
            logger.info("Discovered object {} as {}", objectName, className);
        } catch (InvalidObjectClassHandle | FederateNotExecutionMember | NotConnected | RTIinternalError e) {
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
        try {
            ObjectClassHandle classHandle = _ambassador.getKnownObjectClassHandle(theObject);
            String className = StringUtils.substringAfterLast(_ambassador.getObjectClassName(classHandle), ".");
            for (AttributeHandle attributeHandle : theAttributes.keySet()) {
                String attributeName = _ambassador.getAttributeName(classHandle, attributeHandle);
                _objectCache.reflectAttributeValue(
                        theObject.toString(),
                        className,
                        attributeName,
                        theAttributes.get(attributeHandle));
            }
        } catch (AttributeNotDefined | InvalidAttributeHandle | InvalidObjectClassHandle | ObjectInstanceNotKnown
                | FederateNotExecutionMember | NotConnected | RTIinternalError e) {
            logger.error("Error caching reflected object attributes", e);
        }
    }

    @Override
    public void removeObjectInstance(
            ObjectInstanceHandle theObject,
            byte[] userSuppliedTag,
            OrderType sentOrdering,
            SupplementalRemoveInfo removeInfo) throws FederateInternalError {
        _objectCache.removeObject(theObject.toString());
    }

    @Override
    public void removeObjectInstance(
            ObjectInstanceHandle theObject,
            byte[] userSuppliedTag,
            OrderType sentOrdering,
            LogicalTime theTime,
            OrderType receivedOrdering,
            SupplementalRemoveInfo removeInfo) throws FederateInternalError {
        _objectCache.removeObject(theObject.toString());
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
        _objectCache.removeObject(theObject.toString());
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
        xapiConfig.statementTriggers.forEach(trigger -> {
            try {
                InteractionClassHandle handle = _ambassador.getInteractionClassHandle(trigger.clazz);
                _ambassador.subscribeInteractionClass(handle);
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
            String interactionName = _ambassador.getInteractionClassName(interactionClass);
            logger.info("Interaction Handle: {}", interactionName);
            String interactionKey = StringUtils.substringAfterLast(interactionName, ".");
            // TODO: This is just a naive implementation of matching interactions to triggers.
            // We will need to handle criteria for triggers as well as passing the
            // interaction itself to the trigger processor.
            // ALSO NOTE: we will want to add a similar set of handlers for object updates
            xapiConfig.statementTriggers.stream()
                    .filter(trigger -> trigger.clazz.equals(interactionKey))
                    .forEach(trigger -> {
                        logger.info("Processing trigger for interaction {}", trigger.clazz);
                        TriggerProcessor.processTrigger(trigger);
                    });
        } catch (InvalidInteractionClassHandle | FederateNotExecutionMember | NotConnected | RTIinternalError e) {
            logger.error("Error ascertaining interaction details!", e);
        }
    }
}
