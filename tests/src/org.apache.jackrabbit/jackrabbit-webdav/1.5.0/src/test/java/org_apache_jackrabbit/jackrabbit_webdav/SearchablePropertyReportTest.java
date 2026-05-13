/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.security.SecurityConstants;
import org.apache.jackrabbit.webdav.security.report.SearchablePropertyReport;
import org.apache.jackrabbit.webdav.version.report.ReportType;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class SearchablePropertyReportTest {
    @Test
    public void reportTypeIsRegisteredForSearchablePropertyReport() {
        SearchablePropertyReport report = new SearchablePropertyReport();
        ReportType reportType = report.getType();

        assertThat(reportType).isSameAs(SearchablePropertyReport.REPORT_TYPE);
        assertThat(reportType.getLocalName()).isEqualTo(SearchablePropertyReport.REPORT_NAME);
        assertThat(reportType.getNamespace()).isSameAs(SecurityConstants.NAMESPACE);
        assertThat(report.isMultiStatusReport()).isFalse();
    }

    @Test
    public void toXmlSerializesSearchablePropertiesWithDescriptionLanguage() throws Exception {
        SearchablePropertyReport report = new SearchablePropertyReport();
        report.addPrincipalSearchProperty(DavPropertyName.DISPLAYNAME, "Display name", "en");
        report.addPrincipalSearchProperty(DavPropertyName.GETETAG, null, null);

        Element element = report.toXml(newDocument());

        assertThat(element.getLocalName()).isEqualTo(SearchablePropertyReport.XML_PRINCIPAL_SEARCH_PROPERTY_SET);
        assertThat(element.getNamespaceURI()).isEqualTo(SecurityConstants.NAMESPACE.getURI());
        NodeList searchableProperties = element.getElementsByTagNameNS(
                SecurityConstants.NAMESPACE.getURI(), "principal-search-property");
        assertThat(searchableProperties.getLength()).isEqualTo(2);
        assertThat(element.getElementsByTagNameNS(
                DavConstants.NAMESPACE.getURI(), DavConstants.PROPERTY_DISPLAYNAME).getLength()).isEqualTo(1);
        assertThat(element.getElementsByTagNameNS(
                DavConstants.NAMESPACE.getURI(), DavConstants.PROPERTY_GETETAG).getLength()).isEqualTo(1);

        Element description = (Element) element.getElementsByTagNameNS(
                SecurityConstants.NAMESPACE.getURI(), "description").item(0);
        assertThat(description.getTextContent()).isEqualTo("Display name");
        assertThat(description.getAttributeNS("http://www.w3.org/XML/1998/namespace", "lang")).isEqualTo("en");
    }

    @Test
    public void addPrincipalSearchPropertyRejectsNullPropertyName() {
        SearchablePropertyReport report = new SearchablePropertyReport();

        assertThatThrownBy(() -> report.addPrincipalSearchProperty(null, "Description", "en"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null is not a valid DavPropertyName");
    }

    private static Document newDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().newDocument();
    }
}
