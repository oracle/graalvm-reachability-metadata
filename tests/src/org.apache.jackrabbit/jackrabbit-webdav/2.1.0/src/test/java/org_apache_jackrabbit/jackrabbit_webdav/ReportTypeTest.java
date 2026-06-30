/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.version.report.ReportInfo;
import org.apache.jackrabbit.webdav.version.report.ReportType;
import org.apache.jackrabbit.webdav.version.report.VersionTreeReport;
import org.apache.jackrabbit.webdav.xml.Namespace;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ReportTypeTest {
    private static final int HTTP_BAD_REQUEST = 400;
    private static final Namespace TEST_NAMESPACE = Namespace.getNamespace(
            "t",
            "urn:jackrabbit-webdav-report-type-test");

    @Test
    void registersReportTypeAndAttemptsToCreateReportInstance() {
        String reportName = "version-tree-probe-" + System.nanoTime();

        ReportType reportType = ReportType.register(reportName, TEST_NAMESPACE, VersionTreeReport.class);
        ReportInfo reportInfo = new ReportInfo(reportType);

        assertThat(reportType.getLocalName()).isEqualTo(reportName);
        assertThat(reportType.getNamespace()).isEqualTo(TEST_NAMESPACE);
        assertThat(reportType.isRequestedReportType(reportInfo)).isTrue();
        assertThat(ReportType.getType(reportInfo)).isSameAs(reportType);

        DavException exception = assertThrows(
                DavException.class,
                () -> reportType.createReport(null, reportInfo));
        assertThat(exception.getErrorCode()).isEqualTo(HTTP_BAD_REQUEST);
    }
}
