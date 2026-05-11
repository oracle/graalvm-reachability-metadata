/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tika.tika_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.SAXParser;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.utils.XMLReaderUtils;

public class XMLReaderUtilsTest {

    @Test
    public void documentBuilderInitializesSecureXercesConfigurationAndParsesXml()
            throws Exception {
        DocumentBuilder builder = XMLReaderUtils.getDocumentBuilder();

        Document document = builder.parse(xmlStream("<root><child>value</child></root>"));

        assertThat(document.getDocumentElement().getNodeName()).isEqualTo("root");
        assertThat(document.getElementsByTagName("child").item(0).getTextContent())
                .isEqualTo("value");
    }

    @Test
    public void saxParserInitializesSecureXercesConfigurationAndParsesXml() throws Exception {
        SAXParser parser = XMLReaderUtils.getSAXParser();
        ElementCollectingHandler handler = new ElementCollectingHandler();

        parser.parse(xmlStream("<root><child>value</child></root>"), handler);

        assertThat(handler.elements).containsExactly("root", "child");
    }

    private static InputStream xmlStream(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }

    private static class ElementCollectingHandler extends DefaultHandler {
        private final List<String> elements = new ArrayList<>();

        @Override
        public void startElement(String uri, String localName, String qName,
                Attributes attributes) {
            elements.add(localName.isEmpty() ? qName : localName);
        }
    }
}
