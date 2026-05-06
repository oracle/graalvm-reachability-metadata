/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdom.jdom;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.jdom.output.SAXOutputter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Parser;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

public class SAXOutputterTest {
    private static final String SAX_PARSER_FACTORY_PROPERTY = "javax.xml.parsers.SAXParserFactory";

    private static String previousSaxParserFactory;

    @BeforeAll
    static void configureAccessibleJaxpFactory() {
        previousSaxParserFactory = System.getProperty(SAX_PARSER_FACTORY_PROPERTY);
        System.setProperty(
                SAX_PARSER_FACTORY_PROPERTY,
                AccessibleSaxParserFactory.class.getName());
    }

    @AfterAll
    static void restoreJaxpFactory() {
        if (previousSaxParserFactory == null) {
            System.clearProperty(SAX_PARSER_FACTORY_PROPERTY);
        } else {
            System.setProperty(SAX_PARSER_FACTORY_PROPERTY, previousSaxParserFactory);
        }
    }

    @Test
    void createsXmlReaderThroughJaxpReflection() throws Exception {
        AccessibleSaxOutputter outputter = new AccessibleSaxOutputter();
        RecordingContentHandler handler = new RecordingContentHandler();

        XMLReader reader = outputter.createXmlReader();
        reader.setContentHandler(handler);
        reader.parse(new InputSource(new StringReader("<root><child>value</child></root>")));

        assertThat(handler.elementNames).containsExactly("root", "child");
        assertThat(handler.characters.toString()).isEqualTo("value");
    }

    private static final class AccessibleSaxOutputter extends SAXOutputter {
        XMLReader createXmlReader() throws Exception {
            return createParser();
        }
    }

    public static class AccessibleSaxParserFactory extends SAXParserFactory {
        private final SAXParserFactory delegate = SAXParserFactory.newDefaultInstance();

        @Override
        public SAXParser newSAXParser() throws ParserConfigurationException, SAXException {
            delegate.setNamespaceAware(isNamespaceAware());
            delegate.setValidating(isValidating());
            return new AccessibleSaxParser(delegate.newSAXParser());
        }

        @Override
        public void setFeature(String name, boolean value)
                throws ParserConfigurationException,
                        SAXNotRecognizedException,
                        SAXNotSupportedException {
            delegate.setFeature(name, value);
        }

        @Override
        public boolean getFeature(String name)
                throws ParserConfigurationException,
                        SAXNotRecognizedException,
                        SAXNotSupportedException {
            return delegate.getFeature(name);
        }
    }

    public static class AccessibleSaxParser extends SAXParser {
        private final SAXParser delegate;

        public AccessibleSaxParser(SAXParser delegate) {
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
        public void setProperty(String name, Object value)
                throws SAXNotRecognizedException, SAXNotSupportedException {
            delegate.setProperty(name, value);
        }

        @Override
        public Object getProperty(String name)
                throws SAXNotRecognizedException, SAXNotSupportedException {
            return delegate.getProperty(name);
        }
    }

    private static final class RecordingContentHandler extends DefaultHandler {
        private final List<String> elementNames = new ArrayList<>();
        private final StringBuilder characters = new StringBuilder();

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            elementNames.add(qName);
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            characters.append(ch, start, length);
        }
    }
}
