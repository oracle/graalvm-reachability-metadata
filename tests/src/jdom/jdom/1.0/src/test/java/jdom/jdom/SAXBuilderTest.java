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
    void buildsNamespaceAwareDocumentWithDefaultJaxpParser() throws Exception {
        SAXBuilder builder = new SAXBuilder();
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <catalog xmlns="urn:jdom:catalog" xmlns:item="urn:jdom:item" id="catalog-1">
                    <item:book code="native-image">
                        <item:title>GraalVM Native Image</item:title>
                    </item:book>
                </catalog>
                """;

        Document document = builder.build(new StringReader(xml));

        Namespace catalogNamespace = Namespace.getNamespace("urn:jdom:catalog");
        Namespace itemNamespace = Namespace.getNamespace("item", "urn:jdom:item");
        Element root = document.getRootElement();
        Element book = root.getChild("book", itemNamespace);
        Element title = book.getChild("title", itemNamespace);

        assertThat(root.getName()).isEqualTo("catalog");
        assertThat(root.getNamespace()).isEqualTo(catalogNamespace);
        assertThat(root.getAttributeValue("id")).isEqualTo("catalog-1");
        assertThat(book.getAttributeValue("code")).isEqualTo("native-image");
        assertThat(title.getTextNormalize()).isEqualTo("GraalVM Native Image");
    }
}
