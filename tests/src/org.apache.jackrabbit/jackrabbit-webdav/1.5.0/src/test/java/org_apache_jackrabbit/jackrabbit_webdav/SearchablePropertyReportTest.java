/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.security.SecurityConstants;
import org.apache.jackrabbit.webdav.security.report.SearchablePropertyReport;
import org.apache.jackrabbit.webdav.version.report.ReportInfo;
import org.apache.jackrabbit.webdav.version.report.ReportType;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SearchablePropertyReportTest {
    private static final int HTTP_BAD_REQUEST = 400;

    @Test
    void registersSearchablePropertyReportTypeWhenClassIsInitialized() {
        SearchablePropertyReport report = new SearchablePropertyReport();
        ReportType reportType = report.getType();
        ReportInfo reportInfo = new ReportInfo(reportType);

        assertThat(reportType).isSameAs(SearchablePropertyReport.REPORT_TYPE);
        assertThat(reportType).isSameAs(ReportType.getType(reportInfo));
        assertThat(reportType.getLocalName()).isEqualTo(SearchablePropertyReport.REPORT_NAME);
        assertThat(reportType.getNamespace()).isEqualTo(SecurityConstants.NAMESPACE);
        assertThat(reportType.isRequestedReportType(reportInfo)).isTrue();
        assertThat(report.isMultiStatusReport()).isFalse();

        DavException exception = assertThrows(DavException.class, () -> report.init(null, reportInfo));
        assertThat(exception.getErrorCode()).isEqualTo(HTTP_BAD_REQUEST);
    }

    @Test
    void rendersSearchablePropertiesToPrincipalSearchPropertySet() throws Exception {
        SearchablePropertyReport report = new SearchablePropertyReport();
        report.addPrincipalSearchProperty(DavPropertyName.DISPLAYNAME, "Display name", "en");
        report.addPrincipalSearchProperty(DavPropertyName.DISPLAYNAME, "Duplicate display name", "de");
        report.addPrincipalSearchProperty(DavPropertyName.GETCONTENTTYPE, "Content type", null);

        Element propertySet = report.toXml(newDocument());

        assertThat(propertySet.getLocalName()).isEqualTo(SearchablePropertyReport.XML_PRINCIPAL_SEARCH_PROPERTY_SET);
        assertThat(propertySet.getNamespaceURI()).isEqualTo(SecurityConstants.NAMESPACE.getURI());
        assertThat(propertySet.getChildNodes().getLength()).isEqualTo(2);
    }

    @Test
    void rejectsNullSearchablePropertyName() {
        SearchablePropertyReport report = new SearchablePropertyReport();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> report.addPrincipalSearchProperty(null, "description", "en"));
        assertThat(exception).hasMessageContaining("null is not a valid DavPropertyName");
    }

    private static Document newDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().newDocument();
    }
}
