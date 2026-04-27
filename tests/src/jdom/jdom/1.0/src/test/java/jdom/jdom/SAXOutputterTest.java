/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdom.jdom;

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
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DeclHandler;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class SAXOutputterTest {
    private static final String SAX_PARSER_FACTORY_PROPERTY = "javax.xml.parsers.SAXParserFactory";

    private String previousSaxParserFactoryClass;

    @BeforeEach
    void captureSaxParserFactoryProperty() {
        previousSaxParserFactoryClass = System.getProperty(SAX_PARSER_FACTORY_PROPERTY);
    }

    @AfterEach
    void restoreSaxParserFactoryProperty() {
        if (previousSaxParserFactoryClass == null) {
            System.clearProperty(SAX_PARSER_FACTORY_PROPERTY);
        } else {
            System.setProperty(SAX_PARSER_FACTORY_PROPERTY, previousSaxParserFactoryClass);
        }
    }

    @Test
    void outputWithDeclHandlerCreatesJaxpXmlReaderForDtdCallbacks() throws Exception {
        System.setProperty(SAX_PARSER_FACTORY_PROPERTY, PublicSAXParserFactory.class.getName());
        RecordingContentHandler contentHandler = new RecordingContentHandler();
        RecordingDeclHandler declHandler = new RecordingDeclHandler();
        SAXOutputter outputter = new SAXOutputter();
        outputter.setContentHandler(contentHandler);
        outputter.setDeclHandler(declHandler);

        outputter.output(catalogDocument());

        assertThat(declHandler.elementNames).contains("catalog", "book");
        assertThat(declHandler.attributeDeclarations).contains("book:id:ID");
        assertThat(declHandler.entityDeclarations).isEmpty();
        assertThat(contentHandler.startedElements).containsExactly("catalog", "book");
        assertThat(contentHandler.characterData).containsExactly("The XML Handbook");
    }

    private Document catalogDocument() {
        DocType docType = new DocType("catalog");
        docType.setInternalSubset("""
                <!ELEMENT catalog (book*)>
                <!ELEMENT book (#PCDATA)>
                <!ATTLIST book id ID #REQUIRED>
                """);
        Element catalog = new Element("catalog");
        catalog.addContent(new Element("book").setAttribute("id", "b1").setText("The XML Handbook"));
        return new Document(catalog, docType);
    }

    public static final class PublicSAXParserFactory extends SAXParserFactory {
        private final SAXParserFactory delegate = SAXParserFactory.newDefaultInstance();

        @Override
        public SAXParser newSAXParser() throws ParserConfigurationException, SAXException {
            delegate.setNamespaceAware(isNamespaceAware());
            delegate.setValidating(isValidating());
            return new PublicSAXParser(delegate.newSAXParser());
        }

        @Override
        public void setFeature(String name, boolean value) throws ParserConfigurationException, SAXException {
            delegate.setFeature(name, value);
        }

        @Override
        public boolean getFeature(String name) throws ParserConfigurationException, SAXException {
            return delegate.getFeature(name);
        }
    }

    public static final class PublicSAXParser extends SAXParser {
        private final SAXParser delegate;

        PublicSAXParser(SAXParser delegate) {
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
        public void setProperty(String name, Object value) throws SAXException {
            delegate.setProperty(name, value);
        }

        @Override
        public Object getProperty(String name) throws SAXException {
            return delegate.getProperty(name);
        }
    }

    private static final class RecordingContentHandler extends DefaultHandler {
        private final List<String> startedElements = new ArrayList<>();
        private final List<String> characterData = new ArrayList<>();

        @Override
        public void startElement(String uri, String localName, String qualifiedName, Attributes attributes) {
            startedElements.add(qualifiedName);
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            String text = new String(ch, start, length).trim();
            if (!text.isEmpty()) {
                characterData.add(text);
            }
        }
    }

    private static final class RecordingDeclHandler implements DeclHandler {
        private final List<String> elementNames = new ArrayList<>();
        private final List<String> attributeDeclarations = new ArrayList<>();
        private final List<String> entityDeclarations = new ArrayList<>();

        @Override
        public void elementDecl(String name, String model) {
            elementNames.add(name);
        }

        @Override
        public void attributeDecl(String elementName, String attributeName, String type, String mode, String value) {
            attributeDeclarations.add(elementName + ":" + attributeName + ":" + type);
        }

        @Override
        public void internalEntityDecl(String name, String value) {
            entityDeclarations.add("internal:" + name + ":" + value);
        }

        @Override
        public void externalEntityDecl(String name, String publicId, String systemId) {
            entityDeclarations.add("external:" + name + ":" + publicId + ":" + systemId);
        }
    }
}
