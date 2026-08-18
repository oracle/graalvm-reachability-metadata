/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.version.DeltaVConstants;
import org.apache.jackrabbit.webdav.version.report.LatestActivityVersionReport;
import org.apache.jackrabbit.webdav.version.report.ReportInfo;
import org.apache.jackrabbit.webdav.version.report.ReportType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LatestActivityVersionReportTest {
    private static final int HTTP_BAD_REQUEST = 400;

    @Test
    void exposesRegisteredLatestActivityVersionReportType() {
        LatestActivityVersionReport report = new LatestActivityVersionReport();
        ReportType reportType = LatestActivityVersionReport.LATEST_ACTIVITY_VERSION;
        ReportInfo reportInfo = new ReportInfo(reportType);

        assertThat(report.getType()).isSameAs(reportType);
        assertThat(report.isMultiStatusReport()).isFalse();
        assertThat(reportType.getLocalName()).isEqualTo("latest-activity-version");
        assertThat(reportType.getNamespace()).isEqualTo(DeltaVConstants.NAMESPACE);
        assertThat(reportType.isRequestedReportType(reportInfo)).isTrue();
        assertThat(ReportType.getType(reportInfo)).isSameAs(reportType);
    }

    @Test
    void registeredTypeCreatesReportInstanceAndValidatesResource() {
        ReportType reportType = LatestActivityVersionReport.LATEST_ACTIVITY_VERSION;
        ReportInfo reportInfo = new ReportInfo(reportType);

        DavException exception = assertThrows(
                DavException.class,
                () -> reportType.createReport(null, reportInfo));

        assertThat(exception.getErrorCode()).isEqualTo(HTTP_BAD_REQUEST);
    }
}
