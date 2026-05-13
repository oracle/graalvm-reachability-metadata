/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jackrabbit.webdav.version.DeltaVConstants;
import org.apache.jackrabbit.webdav.version.report.LatestActivityVersionReport;
import org.apache.jackrabbit.webdav.version.report.ReportInfo;
import org.apache.jackrabbit.webdav.version.report.ReportType;
import org.junit.jupiter.api.Test;

public class LatestActivityVersionReportTest {
    @Test
    public void latestActivityVersionReportTypeIsRegisteredByClassInitialization() {
        ReportType reportType = LatestActivityVersionReport.LATEST_ACTIVITY_VERSION;
        LatestActivityVersionReport report = new LatestActivityVersionReport();
        ReportInfo reportInfo = new ReportInfo(reportType);

        assertThat(reportType.getLocalName()).isEqualTo("latest-activity-version");
        assertThat(reportType.getNamespace()).isSameAs(DeltaVConstants.NAMESPACE);
        assertThat(report.getType()).isSameAs(reportType);
        assertThat(report.isMultiStatusReport()).isFalse();
        assertThat(reportType.isRequestedReportType(reportInfo)).isTrue();
        assertThat(ReportType.getType(reportInfo)).isSameAs(reportType);
    }
}
