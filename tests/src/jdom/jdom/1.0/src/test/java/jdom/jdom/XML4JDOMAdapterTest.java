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
import org.jdom.adapters.XML4JDOMAdapter;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class XML4JDOMAdapterTest {
    @Test
    void getDocumentLoadsXercesParserAndFindsSaxInputSourceParseMethod() throws Exception {
        XML4JDOMAdapter adapter = new XML4JDOMAdapter();
        String xml = """
                <root id="parsed">
                    <child>content</child>
                </root>
                """;

        try (InputStream inputStream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            Document document = adapter.getDocument(inputStream, false);

            Element root = document.getDocumentElement();
            Element child = (Element) root.getElementsByTagName("child").item(0);
            assertThat(root.getNodeName()).isEqualTo("root");
            assertThat(root.getAttribute("id")).isEqualTo("parsed");
            assertThat(child.getTextContent()).isEqualTo("content");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void getDocumentWithValidationInstallsBuilderErrorHandler() throws Exception {
        XML4JDOMAdapter adapter = new XML4JDOMAdapter();
        String xml = """
                <!DOCTYPE root [
                    <!ELEMENT root (child)>
                    <!ATTLIST root id CDATA #REQUIRED>
                    <!ELEMENT child (#PCDATA)>
                ]>
                <root id="validated">
                    <child>content</child>
                </root>
                """;

        try (InputStream inputStream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            Document document = adapter.getDocument(inputStream, true);

            Element root = document.getDocumentElement();
            Element child = (Element) root.getElementsByTagName("child").item(0);
            assertThat(root.getNodeName()).isEqualTo("root");
            assertThat(root.getAttribute("id")).isEqualTo("validated");
            assertThat(child.getTextContent()).isEqualTo("content");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }
}
