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
import org.jdom.adapters.XercesDOMAdapter;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class XercesDOMAdapterTest {
    @Test
    void getDocumentLoadsXercesParserAndConfiguresValidation() throws Exception {
        XercesDOMAdapter adapter = new XercesDOMAdapter();
        String xml = """
                <!DOCTYPE sample:root [
                    <!ELEMENT sample:root (sample:child)>
                    <!ATTLIST sample:root
                        xmlns:sample CDATA #FIXED "urn:jdom-xerces-adapter-test"
                        sample:id CDATA #REQUIRED>
                    <!ELEMENT sample:child (#PCDATA)>
                ]>
                <sample:root xmlns:sample="urn:jdom-xerces-adapter-test" sample:id="parsed">
                    <sample:child>content</sample:child>
                </sample:root>
                """;

        try (InputStream inputStream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            Document document = adapter.getDocument(inputStream, true);

            Element root = document.getDocumentElement();
            Element child = (Element) root.getElementsByTagNameNS("urn:jdom-xerces-adapter-test", "child").item(0);
            assertThat(root.getNamespaceURI()).isEqualTo("urn:jdom-xerces-adapter-test");
            assertThat(root.getLocalName()).isEqualTo("root");
            assertThat(root.getAttributeNS("urn:jdom-xerces-adapter-test", "id")).isEqualTo("parsed");
            assertThat(child.getTextContent()).isEqualTo("content");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }
}
