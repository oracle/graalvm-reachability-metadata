/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jackrabbit.webdav.security.SecurityConstants;
import org.apache.jackrabbit.webdav.security.report.PrincipalSearchReport;
import org.apache.jackrabbit.webdav.version.report.ReportType;
import org.junit.jupiter.api.Test;

public class PrincipalSearchReportTest {
    @Test
    public void reportTypeIsRegisteredForPrincipalSearchReport() {
        PrincipalSearchReport report = new PrincipalSearchReport();
        ReportType reportType = report.getType();

        assertThat(reportType).isSameAs(PrincipalSearchReport.REPORT_TYPE);
        assertThat(reportType.getLocalName()).isEqualTo(PrincipalSearchReport.REPORT_NAME);
        assertThat(reportType.getNamespace()).isSameAs(SecurityConstants.NAMESPACE);
    }
}
