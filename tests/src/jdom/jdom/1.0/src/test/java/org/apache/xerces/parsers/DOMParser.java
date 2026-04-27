/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.xerces.parsers;

import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class DOMParser {
    private boolean namespaceAware;
    private boolean validating;
    private ErrorHandler errorHandler;
    private Document document;

    public void setFeature(String name, boolean value) {
        if ("http://xml.org/sax/features/namespaces".equals(name)) {
            namespaceAware = value;
        } else if ("http://xml.org/sax/features/validation".equals(name)) {
            validating = value;
        }
    }

    public void setErrorHandler(ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    public void parse(InputSource inputSource) throws SAXException, IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
            factory.setNamespaceAware(namespaceAware);
            factory.setValidating(validating);
            DocumentBuilder builder = factory.newDocumentBuilder();
            if (errorHandler != null) {
                builder.setErrorHandler(errorHandler);
            }
            document = builder.parse(inputSource);
        } catch (ParserConfigurationException e) {
            throw new SAXException("Could not create DOM document builder", e);
        }
    }

    public Document getDocument() {
        return document;
    }
}
