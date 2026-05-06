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

import org.jdom.adapters.XML4JDOMAdapter;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class XML4JDOMAdapterTest {
    @Test
    void parsesValidatingDocumentThroughXml4jParserReflection() throws Exception {
        XML4JDOMAdapter adapter = new XML4JDOMAdapter();
        String xml = """
                <catalog>
                    <entry sku="xml4j">XML4J</entry>
                </catalog>
                """;

        try (InputStream inputStream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            Document document = adapter.getDocument(inputStream, true);

            Element root = document.getDocumentElement();
            Element entry = (Element) root.getElementsByTagName("entry").item(0);
            assertThat(root.getTagName()).isEqualTo("catalog");
            assertThat(entry.getAttribute("sku")).isEqualTo("xml4j");
            assertThat(entry.getTextContent()).isEqualTo("XML4J");
        }
    }
}
