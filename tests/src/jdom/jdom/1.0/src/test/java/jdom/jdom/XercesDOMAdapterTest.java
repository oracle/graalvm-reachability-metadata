/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdom.jdom;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.jdom.adapters.XercesDOMAdapter;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class XercesDOMAdapterTest {
    @Test
    void parsesValidatingNamespaceAwareDocumentThroughXercesParserReflection() throws Exception {
        XercesDOMAdapter adapter = new XercesDOMAdapter();
        String xml = """
                <catalog xmlns="urn:jdom-test" xmlns:item="urn:jdom-item">
                    <item:entry sku="xerces">Xerces</item:entry>
                </catalog>
                """;

        try (InputStream inputStream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            Document document = adapter.getDocument(inputStream, true);

            Element root = document.getDocumentElement();
            Element entry = (Element) root.getElementsByTagNameNS("urn:jdom-item", "entry").item(0);
            assertThat(root.getNamespaceURI()).isEqualTo("urn:jdom-test");
            assertThat(entry.getAttribute("sku")).isEqualTo("xerces");
            assertThat(entry.getTextContent()).isEqualTo("Xerces");
        }
    }
}
