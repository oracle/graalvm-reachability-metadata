/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.jackrabbit.webdav.version.DeltaVConstants;
import org.apache.jackrabbit.webdav.version.report.ReportInfo;
import org.apache.jackrabbit.webdav.version.report.ReportType;
import org.apache.jackrabbit.webdav.version.report.SupportedReportSetProperty;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.ParserConfigurationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class SupportedReportSetPropertyTest {
    @Test
    void exposesSupportedReportsAndSerializesReportSet() throws ParserConfigurationException {
        ReportType[] reportTypes = {ReportType.VERSION_TREE, ReportType.EXPAND_PROPERTY};

        SupportedReportSetProperty property = new SupportedReportSetProperty(reportTypes);

        assertThat(property.getName()).isEqualTo(DeltaVConstants.SUPPORTED_REPORT_SET);
        Set<?> actualReportTypes = (Set<?>) property.getValue();
        assertThat(actualReportTypes).hasSize(2);
        assertThat(actualReportTypes.contains(ReportType.VERSION_TREE)).isTrue();
        assertThat(actualReportTypes.contains(ReportType.EXPAND_PROPERTY)).isTrue();
        assertThat(property.isInvisibleInAllprop()).isTrue();
        assertThat(property.isSupportedReport(new ReportInfo(ReportType.VERSION_TREE))).isTrue();
        assertThat(property.isSupportedReport(new ReportInfo(ReportType.LOCATE_BY_HISTORY))).isFalse();

        Element supportedReportSet = property.toXml(newDocument());

        assertThat(DomUtil.matches(
                supportedReportSet,
                DeltaVConstants.SUPPORTED_REPORT_SET.getName(),
                DeltaVConstants.SUPPORTED_REPORT_SET.getNamespace())).isTrue();
        assertThat(serializedReportNames(supportedReportSet)).containsExactlyInAnyOrder(
                DeltaVConstants.XML_VERSION_TREE,
                DeltaVConstants.XML_EXPAND_PROPERTY);
    }

    private static List<String> serializedReportNames(Element supportedReportSet) {
        NodeList supportedReportNodes = supportedReportSet.getElementsByTagNameNS(
                DeltaVConstants.NAMESPACE.getURI(),
                DeltaVConstants.XML_SUPPORTED_REPORT);
        List<String> reportNames = new ArrayList<>();
        for (int i = 0; i < supportedReportNodes.getLength(); i++) {
            Element supportedReport = (Element) supportedReportNodes.item(i);
            Element report = DomUtil.getChildElement(
                    supportedReport,
                    DeltaVConstants.XML_REPORT,
                    DeltaVConstants.NAMESPACE);
            reportNames.add(DomUtil.getFirstChildElement(report).getLocalName());
        }
        return reportNames;
    }

    private static Document newDocument() throws ParserConfigurationException {
        return DomUtil.BUILDER_FACTORY.newDocumentBuilder().newDocument();
    }
}
