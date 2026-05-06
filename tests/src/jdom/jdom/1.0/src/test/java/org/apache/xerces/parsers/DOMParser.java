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
    private final DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();

    private ErrorHandler errorHandler;
    private Document document;

    public void setFeature(String name, boolean value) throws ParserConfigurationException {
        if ("http://xml.org/sax/features/namespaces".equals(name)) {
            factory.setNamespaceAware(value);
        } else if (!"http://xml.org/sax/features/validation".equals(name)) {
            factory.setFeature(name, value);
        }
    }

    public void setErrorHandler(ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    public void parse(InputSource inputSource) throws IOException, SAXException {
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
        return document;
    }
}
