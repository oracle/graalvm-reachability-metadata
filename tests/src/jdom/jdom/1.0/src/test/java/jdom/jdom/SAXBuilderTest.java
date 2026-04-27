/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdom.jdom;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

public class SAXBuilderTest {
    @Test
    void buildUsesDefaultJaxpParserFactory() throws Exception {
        SAXBuilder builder = new SAXBuilder();
        InputSource source = new InputSource(new StringReader("""
                <catalog>
                  <book id="b1">
                    <title>Native XML</title>
                  </book>
                </catalog>
                """));

        Document document = builder.build(source);

        Element root = document.getRootElement();
        Element book = root.getChild("book");
        assertThat(root.getName()).isEqualTo("catalog");
        assertThat(book.getAttributeValue("id")).isEqualTo("b1");
        assertThat(book.getChildText("title")).isEqualTo("Native XML");
    }
}
