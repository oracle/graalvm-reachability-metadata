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

import org.graalvm.internal.tck.NativeImageSupport;
import org.jdom.adapters.OracleV2DOMAdapter;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class OracleV2DOMAdapterTest {
    @Test
    void getDocumentLoadsOracleV2ParserAndFindsSaxInputSourceParseMethod() throws Exception {
        OracleV2DOMAdapter adapter = new OracleV2DOMAdapter();
        String xml = """
                <sample:root xmlns:sample="urn:jdom-oracle-v2-adapter-test" sample:id="parsed">
                    <sample:child>content</sample:child>
                </sample:root>
                """;

        try (InputStream inputStream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            Document document = adapter.getDocument(inputStream, false);

            Element root = document.getDocumentElement();
            Element child = (Element) root.getElementsByTagNameNS("urn:jdom-oracle-v2-adapter-test", "child").item(0);
            assertThat(root.getNamespaceURI()).isEqualTo("urn:jdom-oracle-v2-adapter-test");
            assertThat(root.getLocalName()).isEqualTo("root");
            assertThat(root.getAttributeNS("urn:jdom-oracle-v2-adapter-test", "id")).isEqualTo("parsed");
            assertThat(child.getTextContent()).isEqualTo("content");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }
}
