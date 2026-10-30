/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_bind.jaxb_core;

import com.sun.xml.txw2.TXW;
import com.sun.xml.txw2.TypedXmlWriter;
import com.sun.xml.txw2.annotation.XmlAttribute;
import com.sun.xml.txw2.annotation.XmlElement;
import com.sun.xml.txw2.annotation.XmlValue;
import com.sun.xml.txw2.output.DomSerializer;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class ContainerElementTest {
    @Test
    void writesAnAnnotatedDocumentThroughTypedWriterProxies() throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Catalog catalog = TXW.create(Catalog.class, new DomSerializer(document));
        catalog.id("books");
        catalog._attribute("edition", "first");
        Item item = catalog.item();
        item.code("A17").text("Native XML & Java");
        catalog.commit();

        Element root = document.getDocumentElement();
        assertThat(root.getLocalName()).isEqualTo("catalog");
        assertThat(root.getNamespaceURI()).isEqualTo("urn:catalog");
        assertThat(root.getAttribute("id")).isEqualTo("books");
        assertThat(root.getAttribute("edition")).isEqualTo("first");
        Element entry = (Element) root.getFirstChild();
        assertThat(entry.getLocalName()).isEqualTo("item");
        assertThat(entry.getNamespaceURI()).isEqualTo("urn:catalog");
        assertThat(entry.getAttribute("code")).isEqualTo("A17");
        assertThat(entry.getTextContent()).isEqualTo("Native XML & Java");
    }

    @XmlElement(value = "catalog", ns = "urn:catalog")
    public interface Catalog extends TypedXmlWriter {
        @XmlAttribute
        Catalog id(String value);

        @XmlElement(value = "item", ns = "urn:catalog")
        Item item();
    }

    public interface Item extends TypedXmlWriter {
        @XmlAttribute
        Item code(String value);

        @XmlValue
        Item text(String value);
    }
}
