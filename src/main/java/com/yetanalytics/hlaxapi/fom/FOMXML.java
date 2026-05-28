package com.yetanalytics.hlaxapi.fom;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.yetanalytics.hlaxapi.App;

import java.io.File;
import java.io.IOException;

public class FOMXML {

    private static final Logger logger = LogManager.getLogger(App.class);

    private static volatile FOMXML instance;

    // 2. Create the Unmarshaller instance
    private Document doc;
    private XPath xPath;

    // 1. Private constructor
    private FOMXML(String fomFileLocation) {
        File xmlFile = new File(fomFileLocation);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);

        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            setDoc(builder.parse(xmlFile));
        } catch (SAXException | IOException | ParserConfigurationException e) {
            logger.error("Could not parse FOM XML.", e);
        }

        setxPath(XPathFactory.newInstance().newXPath());
    }

    // 2. Initialization point (Thread-safe)
    public static synchronized FOMXML initInstance(String fomFileLocation) {
        if (instance == null) {
            instance = new FOMXML(fomFileLocation);
        }
        return instance;
    }

    // 3. Global access point (Throws exception if accessed before init)
    public static FOMXML getInstance() {
        if (instance == null) {
            throw new IllegalStateException("FOMXMLMapper is not initialized. Call initInstance() first.");
        }
        return instance;
    }

    private final String findInteractionByNameExp = 
        "//interactionClass[name[text()='%s']]/parameter[name[text()='%s']]/dataType";

    public String checkInteractionParameterDatatype(String interactionName, String paramName) 
            throws XPathExpressionException {
        String exp = String.format(findInteractionByNameExp, interactionName, paramName);
        
        return (String) xPath.compile(exp).evaluate(doc, XPathConstants.STRING);
        
    }

    private final String checkSimpleDataTypesExp = 
        "//simpleData[name[text()='%s']]/representation";

    public String checkCustomDatatypes(String dataTypeName) 
            throws XPathExpressionException {
        String exp = String.format(checkSimpleDataTypesExp, dataTypeName);
        
        return (String) xPath.compile(exp).evaluate(doc, XPathConstants.STRING);
        
    }

    public Document getDoc() {
        return doc;
    }

    public void setDoc(Document doc) {
        this.doc = doc;
    }

    public XPath getxPath() {
        return xPath;
    }

    public void setxPath(XPath xPath) {
        this.xPath = xPath;
    }

}
