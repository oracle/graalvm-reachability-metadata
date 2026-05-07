/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.xerces.parsers;

import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class DOMParser {
    private static int documentAccessCount;
    private static int instanceCount;
    private static int parseCount;
    private static int setErrorHandlerCount;
    private static int setFeatureCount;

    private final DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();

    private ErrorHandler errorHandler;
    private Document document;

    public DOMParser() {
        instanceCount++;
    }

    public static void resetInvocationCounts() {
        documentAccessCount = 0;
        instanceCount = 0;
        parseCount = 0;
        setErrorHandlerCount = 0;
        setFeatureCount = 0;
    }

    public static int getDocumentAccessCount() {
        return documentAccessCount;
    }

    public static int getInstanceCount() {
        return instanceCount;
    }

    public static int getParseCount() {
        return parseCount;
    }

    public static int getSetErrorHandlerCount() {
        return setErrorHandlerCount;
    }

    public static int getSetFeatureCount() {
        return setFeatureCount;
    }

    public void setFeature(String name, boolean value) throws ParserConfigurationException {
        setFeatureCount++;
        if ("http://xml.org/sax/features/namespaces".equals(name)) {
            factory.setNamespaceAware(value);
        } else if (!"http://xml.org/sax/features/validation".equals(name)) {
            factory.setFeature(name, value);
        }
    }

    public void setErrorHandler(ErrorHandler errorHandler) {
        setErrorHandlerCount++;
        this.errorHandler = errorHandler;
    }

    public void parse(InputSource inputSource) throws IOException, SAXException {
        parseCount++;
        try {
            DocumentBuilder documentBuilder = factory.newDocumentBuilder();
            if (errorHandler != null) {
                documentBuilder.setErrorHandler(errorHandler);
            }
            document = documentBuilder.parse(inputSource);
        } catch (ParserConfigurationException e) {
            throw new SAXException("Unable to create an XML4J-compatible document builder", e);
        }
    }

    public Document getDocument() {
        documentAccessCount++;
        return document;
    }
}
