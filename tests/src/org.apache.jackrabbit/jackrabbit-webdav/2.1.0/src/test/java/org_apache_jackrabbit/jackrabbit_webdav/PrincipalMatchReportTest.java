/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.security.SecurityConstants;
import org.apache.jackrabbit.webdav.security.report.PrincipalMatchReport;
import org.apache.jackrabbit.webdav.version.report.ReportInfo;
import org.apache.jackrabbit.webdav.version.report.ReportType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PrincipalMatchReportTest {
    private static final int HTTP_BAD_REQUEST = 400;

    @Test
    void registersPrincipalMatchReportTypeWhenClassIsInitialized() {
        PrincipalMatchReport report = new PrincipalMatchReport();
        ReportType reportType = report.getType();
        ReportInfo reportInfo = new ReportInfo(reportType);

        assertThat(reportType).isSameAs(PrincipalMatchReport.REPORT_TYPE);
        assertThat(reportType).isSameAs(ReportType.getType(reportInfo));
        assertThat(reportType.getLocalName()).isEqualTo(PrincipalMatchReport.REPORT_NAME);
        assertThat(reportType.getNamespace()).isEqualTo(SecurityConstants.NAMESPACE);
        assertThat(reportType.isRequestedReportType(reportInfo)).isTrue();

        DavException exception = assertThrows(DavException.class, () -> report.init(null, reportInfo));
        assertThat(exception.getErrorCode()).isEqualTo(HTTP_BAD_REQUEST);
    }
}
