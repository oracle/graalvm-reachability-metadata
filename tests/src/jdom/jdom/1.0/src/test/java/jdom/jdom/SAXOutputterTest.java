/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdom.jdom;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.jdom.DocType;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.output.SAXOutputter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.Attributes;
import org.xml.sax.Parser;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DeclHandler;
import org.xml.sax.helpers.DefaultHandler;

public class SAXOutputterTest extends SAXParserFactory {
    private static final String FACTORY_PROPERTY = "javax.xml.parsers.SAXParserFactory";
    private static final String FACTORY_CLASS_NAME = SAXOutputterTest.class.getName();

    private final SAXParserFactory delegate = SAXParserFactory.newDefaultInstance();
    private String previousFactoryClassName;

    @BeforeEach
    void useAccessibleFactory() {
        previousFactoryClassName = System.getProperty(FACTORY_PROPERTY);
        System.setProperty(FACTORY_PROPERTY, FACTORY_CLASS_NAME);
    }

    @AfterEach
    void restoreFactory() {
        if (previousFactoryClassName == null) {
            System.clearProperty(FACTORY_PROPERTY);
        } else {
            System.setProperty(FACTORY_PROPERTY, previousFactoryClassName);
        }
    }

    @Test
    void outputDocumentWithInternalDoctypeReportsSaxDeclarations() throws Exception {
        RecordingDeclHandler declHandler = new RecordingDeclHandler();
        RecordingContentHandler contentHandler = new RecordingContentHandler();
        SAXOutputter outputter = new SAXOutputter(contentHandler);
        outputter.setDeclHandler(declHandler);

        outputter.output(createDocumentWithInternalDoctype());

        assertThat(declHandler.elementDeclarations).contains("root", "child");
        assertThat(declHandler.attributeDeclarations).contains("root:id:CDATA:#IMPLIED");
        assertThat(declHandler.entityDeclarations).contains("sample:entity value");
        assertThat(contentHandler.elements).containsExactly("root", "child");
    }

    private static Document createDocumentWithInternalDoctype() {
        DocType docType = new DocType("root");
        docType.setInternalSubset("""
                <!ELEMENT root (child)>
                <!ATTLIST root id CDATA #IMPLIED>
                <!ELEMENT child (#PCDATA)>
                <!ENTITY sample "entity value">
                """);

        Element root = new Element("root");
        root.setAttribute("id", "sample-id");
        root.addContent(new Element("child").setText("sample text"));
        return new Document(root, docType);
    }

    @Override
    public SAXParser newSAXParser() throws ParserConfigurationException, SAXException {
        return new AccessibleSAXParser(delegate.newSAXParser());
    }

    @Override
    public void setNamespaceAware(boolean awareness) {
        delegate.setNamespaceAware(awareness);
    }

    @Override
    public void setValidating(boolean validating) {
        delegate.setValidating(validating);
    }

    @Override
    public boolean isNamespaceAware() {
        return delegate.isNamespaceAware();
    }

    @Override
    public boolean isValidating() {
        return delegate.isValidating();
    }

    @Override
    public void setFeature(String name, boolean value) throws ParserConfigurationException, SAXNotRecognizedException,
            SAXNotSupportedException {
        delegate.setFeature(name, value);
    }

    @Override
    public boolean getFeature(String name) throws ParserConfigurationException, SAXNotRecognizedException,
            SAXNotSupportedException {
        return delegate.getFeature(name);
    }

    public static class AccessibleSAXParser extends SAXParser {
        private final SAXParser delegate;

        public AccessibleSAXParser(SAXParser delegate) {
            this.delegate = delegate;
        }

        @Override
        public Parser getParser() throws SAXException {
            return delegate.getParser();
        }

        @Override
        public XMLReader getXMLReader() throws SAXException {
            return delegate.getXMLReader();
        }

        @Override
        public boolean isNamespaceAware() {
            return delegate.isNamespaceAware();
        }

        @Override
        public boolean isValidating() {
            return delegate.isValidating();
        }

        @Override
        public void setProperty(String name, Object value) throws SAXNotRecognizedException, SAXNotSupportedException {
            delegate.setProperty(name, value);
        }

        @Override
        public Object getProperty(String name) throws SAXNotRecognizedException, SAXNotSupportedException {
            return delegate.getProperty(name);
        }
    }

    private static class RecordingDeclHandler implements DeclHandler {
        private final List<String> attributeDeclarations = new ArrayList<>();
        private final List<String> elementDeclarations = new ArrayList<>();
        private final List<String> entityDeclarations = new ArrayList<>();

        @Override
        public void elementDecl(String name, String model) {
            elementDeclarations.add(name);
        }

        @Override
        public void attributeDecl(String elementName, String attributeName, String type, String mode, String value) {
            attributeDeclarations.add(elementName + ":" + attributeName + ":" + type + ":" + mode);
        }

        @Override
        public void internalEntityDecl(String name, String value) {
            entityDeclarations.add(name + ":" + value);
        }

        @Override
        public void externalEntityDecl(String name, String publicId, String systemId) {
            entityDeclarations.add(name + ":" + publicId + ":" + systemId);
        }
    }

    private static class RecordingContentHandler extends DefaultHandler {
        private final List<String> elements = new ArrayList<>();

        @Override
        public void startElement(String uri, String localName, String qualifiedName, Attributes attributes) {
            elements.add(qualifiedName);
        }
    }
}
