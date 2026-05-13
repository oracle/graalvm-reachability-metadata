/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.property.DavProperty;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.HrefProperty;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class HrefPropertyTest {
    @Test
    public void toXmlWritesEachHrefAsDavHrefElement() throws Exception {
        HrefProperty property = new HrefProperty(
                DavPropertyName.create("source"),
                new String[] {"/repository/default/", "/repository/workspace/"},
                true);

        Element element = property.toXml(newDocument());

        assertThat(element.getLocalName()).isEqualTo("source");
        assertThat(element.getNamespaceURI()).isEqualTo(DavConstants.NAMESPACE.getURI());
        assertThat(property.isInvisibleInAllprop()).isTrue();
        assertThat(hrefTexts(element)).containsExactly("/repository/default/", "/repository/workspace/");
    }

    @Test
    public void constructorCopiesHrefValuesFromXmlProperty() throws Exception {
        Document document = newDocument();
        List<Element> hrefElements = new ArrayList<>();
        hrefElements.add(createHref(document, "/history/1"));
        hrefElements.add(createHref(document, "/history/2"));
        DavProperty parsedProperty = new SimpleDavProperty(DavPropertyName.create("source"), hrefElements, true);

        HrefProperty property = new HrefProperty(parsedProperty);

        assertThat(property.getName()).isEqualTo(DavPropertyName.create("source"));
        assertThat(property.isInvisibleInAllprop()).isTrue();
        assertThat(property.getHrefs()).containsExactly("/history/1", "/history/2");
        assertThat((String[]) property.getValue()).containsExactly("/history/1", "/history/2");
    }

    private static Element createHref(Document document, String value) {
        Element href = document.createElementNS(DavConstants.NAMESPACE.getURI(), "D:" + DavConstants.XML_HREF);
        href.setTextContent(value);
        return href;
    }

    private static Document newDocument() throws Exception {
        return newDocumentBuilderFactory().newDocumentBuilder().newDocument();
    }

    private static DocumentBuilderFactory newDocumentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory;
    }

    private static String[] hrefTexts(Element element) {
        NodeList hrefElements = element.getElementsByTagNameNS(DavConstants.NAMESPACE.getURI(), DavConstants.XML_HREF);
        String[] hrefs = new String[hrefElements.getLength()];
        for (int i = 0; i < hrefElements.getLength(); i++) {
            hrefs[i] = hrefElements.item(i).getTextContent();
        }
        return hrefs;
    }

    private static final class SimpleDavProperty implements DavProperty {
        private final DavPropertyName name;
        private final Object value;
        private final boolean invisibleInAllprop;

        private SimpleDavProperty(DavPropertyName name, Object value, boolean invisibleInAllprop) {
            this.name = name;
            this.value = value;
            this.invisibleInAllprop = invisibleInAllprop;
        }

        @Override
        public DavPropertyName getName() {
            return name;
        }

        @Override
        public Object getValue() {
            return value;
        }

        @Override
        public boolean isInvisibleInAllprop() {
            return invisibleInAllprop;
        }

        @Override
        public Element toXml(Document document) {
            return name.toXml(document);
        }
    }
}
