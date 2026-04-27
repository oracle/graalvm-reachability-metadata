/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdom.jdom;

import org.jdom.adapters.OracleV1DOMAdapter;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class OracleV1DOMAdapterTest {
    @Test
    void getDocumentParsesInputThroughOracleParserAdapter() throws Exception {
        OracleV1DOMAdapter adapter = new OracleV1DOMAdapter();
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <oracle:root xmlns:oracle="urn:jdom-oracle-adapter">
                  <oracle:child name="parsed">text</oracle:child>
                </oracle:root>
                """;

        Document document = adapter.getDocument(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), false);
        Element root = document.getDocumentElement();
        Element child = (Element) root.getElementsByTagNameNS("urn:jdom-oracle-adapter", "child").item(0);

        assertThat(root.getNamespaceURI()).isEqualTo("urn:jdom-oracle-adapter");
        assertThat(root.getLocalName()).isEqualTo("root");
        assertThat(child.getAttribute("name")).isEqualTo("parsed");
        assertThat(child.getTextContent()).isEqualTo("text");
    }
}
