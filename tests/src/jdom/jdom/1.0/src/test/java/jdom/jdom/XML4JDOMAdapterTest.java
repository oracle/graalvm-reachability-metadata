/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdom.jdom;

import org.jdom.adapters.XML4JDOMAdapter;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class XML4JDOMAdapterTest {
    @Test
    void getDocumentParsesInputThroughXml4jParserAdapter() throws Exception {
        XML4JDOMAdapter adapter = new XML4JDOMAdapter();
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <root>
                  <child name="parsed">text</child>
                </root>
                """;

        Document document = adapter.getDocument(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), false);
        Element root = document.getDocumentElement();
        Element child = (Element) root.getElementsByTagName("child").item(0);

        assertThat(root.getTagName()).isEqualTo("root");
        assertThat(child.getAttribute("name")).isEqualTo("parsed");
        assertThat(child.getTextContent()).isEqualTo("text");
    }
}
