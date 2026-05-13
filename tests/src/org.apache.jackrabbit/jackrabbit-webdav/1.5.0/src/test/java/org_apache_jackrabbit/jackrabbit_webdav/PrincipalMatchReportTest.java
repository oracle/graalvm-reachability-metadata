/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jackrabbit.webdav.security.SecurityConstants;
import org.apache.jackrabbit.webdav.security.report.PrincipalMatchReport;
import org.apache.jackrabbit.webdav.version.report.ReportType;
import org.junit.jupiter.api.Test;

public class PrincipalMatchReportTest {
    @Test
    public void reportTypeIsRegisteredForPrincipalMatchReport() {
        PrincipalMatchReport report = new PrincipalMatchReport();
        ReportType reportType = report.getType();

        assertThat(reportType).isSameAs(PrincipalMatchReport.REPORT_TYPE);
        assertThat(reportType.getLocalName()).isEqualTo(PrincipalMatchReport.REPORT_NAME);
        assertThat(reportType.getNamespace()).isSameAs(SecurityConstants.NAMESPACE);
    }
}
