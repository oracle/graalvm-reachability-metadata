/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.txw2;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.xml.txw2.TXW;
import com.sun.xml.txw2.TypedXmlWriter;
import com.sun.xml.txw2.annotation.XmlAttribute;
import com.sun.xml.txw2.annotation.XmlElement;
import com.sun.xml.txw2.annotation.XmlValue;
import com.sun.xml.txw2.output.StreamSerializer;
import java.io.StringReader;
import java.io.StringWriter;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

public class ContainerElementTest {

    @Test
    void createsProxyBackedWritersAndForwardsTypedWriterMethods() throws Exception {
        StringWriter xml = new StringWriter();
        Catalog catalog = TXW.create(Catalog.class, new StreamSerializer(xml));

        String description = catalog.toString();
        assertThat(description).contains("ContainerElement");
        assertThat(catalog.getDocument()).isNotNull();

        Entry entry = catalog.id("catalog-1").entry();
        entry.code("entry-1").value("hello");
        catalog.summary("done");
        catalog.commit();

        Element catalogElement = parseDocumentElement(xml.toString());
        assertThat(catalogElement.getTagName()).isEqualTo("catalog");
        assertThat(catalogElement.getAttribute("id")).isEqualTo("catalog-1");

        Element entryElement = (Element) catalogElement.getElementsByTagName("entry").item(0);
        assertThat(entryElement).isNotNull();
        assertThat(entryElement.getAttribute("code")).isEqualTo("entry-1");
        assertThat(entryElement.getTextContent()).isEqualTo("hello");

        Element summaryElement = (Element) catalogElement.getElementsByTagName("summary").item(0);
        assertThat(summaryElement).isNotNull();
        assertThat(summaryElement.getTextContent()).isEqualTo("done");
    }

    private static Element parseDocumentElement(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml))).getDocumentElement();
    }

    @XmlElement("catalog")
    public interface Catalog extends TypedXmlWriter {

        @XmlAttribute
        Catalog id(String id);

        @XmlElement("entry")
        Entry entry();

        @XmlElement("summary")
        void summary(String value);
    }

    @XmlElement("entry")
    public interface Entry extends TypedXmlWriter {

        @XmlAttribute
        Entry code(String code);

        @XmlValue
        Entry value(String value);
    }
}
