/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.txw2;

import com.sun.xml.txw2.TXW;
import com.sun.xml.txw2.TypedXmlWriter;
import com.sun.xml.txw2.annotation.XmlAttribute;
import com.sun.xml.txw2.annotation.XmlElement;
import com.sun.xml.txw2.output.StreamSerializer;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class ContainerElementTest {

    @XmlElement("catalog")
    public interface Catalog extends TypedXmlWriter {
        @XmlAttribute
        Catalog version(String value);

        @XmlElement("book")
        Book book();

        @XmlElement("summary")
        void summary(String value);
    }

    public interface Book extends TypedXmlWriter {
        @XmlElement("title")
        void title(String value);
    }

    @Test
    void castKeepsWritingToTheSameElement() throws Exception {
        StringWriter output = new StringWriter();
        Catalog catalog = TXW.create(Catalog.class, new StreamSerializer(output));

        Catalog castCatalog = catalog._cast(Catalog.class);
        castCatalog._attribute("lang", "en");
        castCatalog.version("1.0");

        Book book = castCatalog.book();
        book._attribute("isbn", "978-0134685991");
        book.title("Effective Java");

        castCatalog.summary("Generated with TXW");
        castCatalog.commit();

        Document document = parseXml(output.toString());
        Element catalogElement = document.getDocumentElement();
        Element bookElement = (Element) catalogElement.getElementsByTagName("book").item(0);
        Element summaryElement = (Element) catalogElement.getElementsByTagName("summary").item(0);

        assertThat(catalogElement.getTagName()).isEqualTo("catalog");
        assertThat(catalogElement.getAttribute("lang")).isEqualTo("en");
        assertThat(catalogElement.getAttribute("version")).isEqualTo("1.0");
        assertThat(bookElement.getAttribute("isbn")).isEqualTo("978-0134685991");
        assertThat(((Element) bookElement.getElementsByTagName("title").item(0)).getTextContent().trim())
                .isEqualTo("Effective Java");
        assertThat(summaryElement.getTextContent().trim()).isEqualTo("Generated with TXW");
    }

    private static Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }
}
