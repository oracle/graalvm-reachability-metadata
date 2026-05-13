/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.jackrabbit.webdav.observation.Filter;
import org.apache.jackrabbit.webdav.xml.Namespace;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class FilterTest {
    @Test
    public void valueConstructorStoresFieldsAndSerializesToXml() throws Exception {
        Namespace namespace = Namespace.getNamespace("obs", "http://example.test/observation");

        Filter filter = new Filter("event-user-id", namespace, "arthur");

        assertThat(filter.getName()).isEqualTo("event-user-id");
        assertThat(filter.getNamespace()).isEqualTo(namespace);
        assertThat(filter.getValue()).isEqualTo("arthur");
        assertThat(filter.isMatchingFilter("event-user-id", namespace)).isTrue();
        assertThat(filter.isMatchingFilter("event-user-id", Namespace.EMPTY_NAMESPACE)).isFalse();
        assertThat(filter.isMatchingFilter("other", namespace)).isFalse();

        Element xml = filter.toXml(newDocument());

        assertThat(xml.getLocalName()).isEqualTo("event-user-id");
        assertThat(xml.getNamespaceURI()).isEqualTo("http://example.test/observation");
        assertThat(xml.getTextContent()).isEqualTo("arthur");
    }

    @Test
    public void elementConstructorReadsNameNamespaceAndTrimmedText() throws Exception {
        Namespace namespace = Namespace.getNamespace("o", "http://example.test/filters");
        Document document = newDocument();
        Element element = document.createElementNS(namespace.getURI(), "o:eventtype");
        element.appendChild(document.createTextNode("  node-added  "));

        Filter filter = new Filter(element);

        assertThat(filter.getName()).isEqualTo("eventtype");
        assertThat(filter.getNamespace()).isEqualTo(namespace);
        assertThat(filter.getValue()).isEqualTo("node-added");
        assertThat(filter.isMatchingFilter("eventtype", namespace)).isTrue();

        Element xml = filter.toXml(newDocument());
        assertThat(xml.getLocalName()).isEqualTo("eventtype");
        assertThat(xml.getNamespaceURI()).isEqualTo(namespace.getURI());
        assertThat(xml.getTextContent()).isEqualTo("node-added");
    }

    @Test
    public void valueConstructorRejectsNullFilterName() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Filter(null, Namespace.EMPTY_NAMESPACE, "value"))
                .withMessage("filterName must not be null.");
    }

    private static Document newDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().newDocument();
    }
}
