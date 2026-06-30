/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_jackrabbit.jackrabbit_webdav;

import org.apache.jackrabbit.webdav.version.DeltaVConstants;
import org.apache.jackrabbit.webdav.version.report.CompareBaselineReport;
import org.apache.jackrabbit.webdav.version.report.ReportInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CompareBaselineReportTest {
    @Test
    void exposesRegisteredCompareBaselineReportType() {
        CompareBaselineReport report = new CompareBaselineReport();
        ReportInfo reportInfo = new ReportInfo(CompareBaselineReport.COMPARE_BASELINE);

        assertThat(report.getType()).isSameAs(CompareBaselineReport.COMPARE_BASELINE);
        assertThat(report.isMultiStatusReport()).isFalse();
        assertThat(report.getType().getLocalName()).isEqualTo("compare-baseline");
        assertThat(report.getType().getNamespace()).isEqualTo(DeltaVConstants.NAMESPACE);
        assertThat(report.getType().isRequestedReportType(reportInfo)).isTrue();
    }
}
