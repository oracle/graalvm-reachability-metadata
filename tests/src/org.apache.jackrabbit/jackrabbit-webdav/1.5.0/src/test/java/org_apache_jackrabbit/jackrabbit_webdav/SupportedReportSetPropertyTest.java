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
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.jackrabbit.webdav.version.DeltaVConstants;
import org.apache.jackrabbit.webdav.version.report.ReportInfo;
import org.apache.jackrabbit.webdav.version.report.ReportType;
import org.apache.jackrabbit.webdav.version.report.SupportedReportSetProperty;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class SupportedReportSetPropertyTest {
    @Test
    public void constructorStoresReportTypesAndMatchesRequestedReportInfo() {
        ReportType[] reportTypes = new ReportType[] {
            ReportType.VERSION_TREE,
            ReportType.EXPAND_PROPERTY
        };

        SupportedReportSetProperty property = new SupportedReportSetProperty(reportTypes);

        @SuppressWarnings("unchecked")
        Set<ReportType> actualReportTypes = (Set<ReportType>) property.getValue();

        assertThat(property.getName()).isEqualTo(DeltaVConstants.SUPPORTED_REPORT_SET);
        assertThat(property.isInvisibleInAllprop()).isTrue();
        assertThat(actualReportTypes).containsExactlyInAnyOrder(reportTypes);
        assertThat(property.isSupportedReport(new ReportInfo(ReportType.VERSION_TREE))).isTrue();
        assertThat(property.isSupportedReport(new ReportInfo(ReportType.EXPAND_PROPERTY))).isTrue();
        assertThat(property.isSupportedReport(new ReportInfo(ReportType.LOCATE_BY_HISTORY))).isFalse();
        assertThat(property.isSupportedReport(null)).isFalse();
    }

    @Test
    public void addReportTypeAndToXmlWriteSupportedReportElements() throws Exception {
        SupportedReportSetProperty property = new SupportedReportSetProperty();
        property.addReportType(ReportType.VERSION_TREE);
        property.addReportType(ReportType.LOCATE_BY_HISTORY);

        Element element = property.toXml(newDocument());

        assertThat(element.getLocalName()).isEqualTo("supported-report-set");
        assertThat(element.getNamespaceURI()).isEqualTo(DeltaVConstants.NAMESPACE.getURI());
        assertThat(supportedReportNames(element)).containsExactlyInAnyOrder(
                ReportType.VERSION_TREE.getLocalName(),
                ReportType.LOCATE_BY_HISTORY.getLocalName());
    }

    private static Document newDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().newDocument();
    }

    private static List<String> supportedReportNames(Element element) {
        NodeList reportElements = element.getElementsByTagNameNS(
                DeltaVConstants.NAMESPACE.getURI(), DeltaVConstants.XML_REPORT);
        List<String> reportNames = new ArrayList<>();
        for (int i = 0; i < reportElements.getLength(); i++) {
            Element reportElement = (Element) reportElements.item(i);
            Element reportTypeElement = firstElementChild(reportElement);
            reportNames.add(reportTypeElement.getLocalName());
            assertThat(reportTypeElement.getNamespaceURI()).isEqualTo(DeltaVConstants.NAMESPACE.getURI());
        }
        return reportNames;
    }

    private static Element firstElementChild(Element element) {
        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            if (childNodes.item(i) instanceof Element) {
                return (Element) childNodes.item(i);
            }
        }
        throw new IllegalStateException("Expected report element to contain a report type element");
    }
}
