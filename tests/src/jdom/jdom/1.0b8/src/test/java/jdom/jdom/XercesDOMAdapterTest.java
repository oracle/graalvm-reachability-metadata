/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdom.jdom;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.xerces.parsers.DOMParser;
import org.jdom.adapters.XercesDOMAdapter;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class XercesDOMAdapterTest {
    @Test
    void parsesValidatingNamespaceAwareDocumentThroughXercesParserReflection() throws Exception {
        DOMParser.resetInvocationCounts();
        XercesDOMAdapter adapter = new XercesDOMAdapter();
        String xml = """
                <catalog xmlns="urn:jdom-test" xmlns:item="urn:jdom-item">
                    <item:entry sku="xerces">Xerces</item:entry>
                </catalog>
                """;

        Document document = parse(adapter, xml, true);

        Element root = document.getDocumentElement();
        Element entry = (Element) root.getElementsByTagNameNS("urn:jdom-item", "entry").item(0);
        assertThat(root.getNamespaceURI()).isEqualTo("urn:jdom-test");
        assertThat(entry.getAttribute("sku")).isEqualTo("xerces");
        assertThat(entry.getTextContent()).isEqualTo("Xerces");
        assertThat(DOMParser.getInstanceCount()).isEqualTo(1);
        assertThat(DOMParser.getSetFeatureCount()).isEqualTo(2);
        assertThat(DOMParser.getSetErrorHandlerCount()).isEqualTo(1);
        assertThat(DOMParser.getParseCount()).isEqualTo(1);
        assertThat(DOMParser.getDocumentAccessCount()).isEqualTo(1);
    }

    private static Document parse(XercesDOMAdapter adapter, String xml, boolean validate) throws IOException {
        byte[] xmlBytes = xml.getBytes(StandardCharsets.UTF_8);
        try (InputStream inputStream = new ByteArrayInputStream(xmlBytes)) {
            return adapter.getDocument(inputStream, validate);
        }
    }
}
