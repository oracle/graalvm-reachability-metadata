/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdom.jdom;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SAXBuilderTest {
    @Test
    @Order(1)
    void createParserUsesDefaultJaxpParserFactory() throws Exception {
        ExposedSAXBuilder builder = new ExposedSAXBuilder();

        XMLReader parser = builder.createXmlReader();

        assertThat(parser).isNotNull();
    }

    @Test
    @Order(2)
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

    private static final class ExposedSAXBuilder extends SAXBuilder {
        XMLReader createXmlReader() throws JDOMException {
            return createParser();
        }
    }
}
