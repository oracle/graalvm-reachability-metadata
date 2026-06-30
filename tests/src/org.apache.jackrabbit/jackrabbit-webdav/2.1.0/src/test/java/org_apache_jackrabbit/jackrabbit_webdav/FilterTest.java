/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.jackrabbit.webdav.observation.Filter;
import org.apache.jackrabbit.webdav.observation.ObservationConstants;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.apache.jackrabbit.webdav.xml.Namespace;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class FilterTest {
    @Test
    void storesFilterPropertiesAndMatchesByNameAndNamespace() {
        Filter filter = new Filter("eventid", ObservationConstants.NAMESPACE, "42");

        assertThat(filter.getName()).isEqualTo("eventid");
        assertThat(filter.getNamespace()).isEqualTo(ObservationConstants.NAMESPACE);
        assertThat(filter.getValue()).isEqualTo("42");
        assertThat(filter.isMatchingFilter("eventid", ObservationConstants.NAMESPACE)).isTrue();
        assertThat(filter.isMatchingFilter("eventid", Namespace.EMPTY_NAMESPACE)).isFalse();
        assertThat(filter.isMatchingFilter("other", ObservationConstants.NAMESPACE)).isFalse();
    }

    @Test
    void rejectsNullFilterName() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Filter(null, ObservationConstants.NAMESPACE, "value"))
                .withMessage("filterName must not be null.");
    }

    @Test
    void createsFilterFromElementAndSerializesItBack() throws Exception {
        Document document = newDocument();
        Element filterElement = DomUtil.createElement(
                document,
                "subscription-filter",
                ObservationConstants.NAMESPACE,
                "  accepted  ");

        Filter filter = new Filter(filterElement);

        assertThat(filter.getName()).isEqualTo("subscription-filter");
        assertThat(filter.getNamespace()).isEqualTo(ObservationConstants.NAMESPACE);
        assertThat(filter.getValue()).isEqualTo("accepted");
        assertThat(filter.isMatchingFilter("subscription-filter", ObservationConstants.NAMESPACE)).isTrue();

        Element serialized = filter.toXml(document);
        assertThat(DomUtil.matches(serialized, "subscription-filter", ObservationConstants.NAMESPACE)).isTrue();
        assertThat(DomUtil.getText(serialized)).isEqualTo("accepted");
    }

    @Test
    void matchesNullNamespaceOnlyWhenFilterNamespaceIsNull() {
        Filter filter = new Filter("custom-filter", null, "value");

        assertThat(filter.getNamespace()).isNull();
        assertThat(filter.isMatchingFilter("custom-filter", null)).isTrue();
        assertThat(filter.isMatchingFilter("custom-filter", ObservationConstants.NAMESPACE)).isFalse();
    }

    private static Document newDocument() throws Exception {
        DocumentBuilder builder = newDocumentBuilder();
        return builder.newDocument();
    }

    private static DocumentBuilder newDocumentBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder();
    }
}
