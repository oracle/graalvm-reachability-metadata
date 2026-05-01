/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdom.jdom;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;
import org.junit.jupiter.api.Test;

public class SAXBuilderTest {
    @Test
    void buildWithDefaultJaxpParserReadsNamespaceAwareXml() throws Exception {
        SAXBuilder builder = new SAXBuilder();
        String xml = """
                <sample:root xmlns:sample="urn:jdom-sax-builder-test" sample:id="parsed">
                    <sample:child>content</sample:child>
                </sample:root>
                """;

        Document document = builder.build(new StringReader(xml));

        Namespace namespace = Namespace.getNamespace("sample", "urn:jdom-sax-builder-test");
        Element root = document.getRootElement();
        Element child = root.getChild("child", namespace);
        assertThat(root.getName()).isEqualTo("root");
        assertThat(root.getNamespaceURI()).isEqualTo("urn:jdom-sax-builder-test");
        assertThat(root.getAttributeValue("id", namespace)).isEqualTo("parsed");
        assertThat(child.getText()).isEqualTo("content");
    }
}
