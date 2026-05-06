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

import org.jdom.adapters.OracleV1DOMAdapter;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class OracleV1DOMAdapterTest {
    @Test
    void parsesNamespaceAwareDocumentThroughOracleV1ParserReflection() throws Exception {
        OracleV1DOMAdapter adapter = new OracleV1DOMAdapter();
        String xml = """
                <root xmlns="urn:jdom-oracle" xmlns:item="urn:jdom-oracle-item">
                    <item:child id="oracle-v1">value</item:child>
                </root>
                """;

        try (InputStream inputStream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            Document document = adapter.getDocument(inputStream, false);

            Element root = document.getDocumentElement();
            Element child = (Element) root.getElementsByTagNameNS("urn:jdom-oracle-item", "child").item(0);
            assertThat(root.getNamespaceURI()).isEqualTo("urn:jdom-oracle");
            assertThat(child.getAttribute("id")).isEqualTo("oracle-v1");
            assertThat(child.getTextContent()).isEqualTo("value");
        }
    }
}
