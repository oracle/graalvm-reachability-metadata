/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_poi.poi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.poi.util.XMLHelper;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.XMLReader;

public class XMLHelperTest {

    private static final String SIMPLE_XML = """
            <root xmlns="urn:test">
                <child>value</child>
            </root>
            """;

    private static final String XML_WITH_DOCTYPE = """
            <!DOCTYPE root [
              <!ELEMENT root ANY>
              <!ENTITY ext SYSTEM "file:///etc/passwd">
            ]>
            <root>&ext;</root>
            """;

    @Test
    void documentBuilderFactoryUsesSecureDefaultsAndRejectsDoctype() throws Exception {
        DocumentBuilderFactory documentBuilderFactory = XMLHelper.getDocumentBuilderFactory();

        assertThat(documentBuilderFactory.isNamespaceAware()).isTrue();
        assertThat(documentBuilderFactory.isValidating()).isFalse();
        assertThat(documentBuilderFactory.isExpandEntityReferences()).isFalse();

        Document document = XMLHelper.newDocumentBuilder().parse(
                new ByteArrayInputStream(SIMPLE_XML.getBytes(StandardCharsets.UTF_8)));
        assertThat(document.getDocumentElement().getLocalName()).isEqualTo("root");
        assertThat(document.getDocumentElement().getNamespaceURI()).isEqualTo("urn:test");
        assertThat(document.getDocumentElement().getTextContent().trim()).isEqualTo("value");

        assertThatThrownBy(() -> XMLHelper.newDocumentBuilder().parse(
                new ByteArrayInputStream(XML_WITH_DOCTYPE.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(org.xml.sax.SAXException.class);
    }

    @Test
    void xmlReaderParsesNamespacedXml() throws Exception {
        XMLReader xmlReader = XMLHelper.newXMLReader();
        RecordingHandler recordingHandler = new RecordingHandler();
        xmlReader.setContentHandler(recordingHandler);

        xmlReader.parse(new InputSource(new ByteArrayInputStream(SIMPLE_XML.getBytes(StandardCharsets.UTF_8))));

        assertThat(recordingHandler.elementNames).containsExactly("root", "child");
        assertThat(recordingHandler.namespaceUris).containsExactly("urn:test", "urn:test");
        assertThat(recordingHandler.text.toString().trim()).isEqualTo("value");
    }

    private static final class RecordingHandler extends DefaultHandler {
        private final List<String> elementNames = new ArrayList<>();
        private final List<String> namespaceUris = new ArrayList<>();
        private final StringBuilder text = new StringBuilder();

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            elementNames.add(localName.isEmpty() ? qName : localName);
            namespaceUris.add(uri);
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            text.append(ch, start, length);
        }
    }
}
