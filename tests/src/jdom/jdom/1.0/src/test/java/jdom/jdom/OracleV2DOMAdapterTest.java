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

import org.jdom.adapters.OracleV2DOMAdapter;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class OracleV2DOMAdapterTest {
    @Test
    void parsesNamespaceAwareDocumentThroughOracleV2ParserReflection() throws Exception {
        OracleV2DOMAdapter adapter = new OracleV2DOMAdapter();
        String xml = """
                <catalog xmlns="urn:jdom-oracle-v2">
                    <entry sku="v2">Oracle V2</entry>
                </catalog>
                """;

        try (InputStream inputStream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            Document document = adapter.getDocument(inputStream, false);

            Element root = document.getDocumentElement();
            Element entry = (Element) root.getElementsByTagNameNS("urn:jdom-oracle-v2", "entry").item(0);
            assertThat(root.getNamespaceURI()).isEqualTo("urn:jdom-oracle-v2");
            assertThat(entry.getAttribute("sku")).isEqualTo("v2");
            assertThat(entry.getTextContent()).isEqualTo("Oracle V2");
        }
    }
}
