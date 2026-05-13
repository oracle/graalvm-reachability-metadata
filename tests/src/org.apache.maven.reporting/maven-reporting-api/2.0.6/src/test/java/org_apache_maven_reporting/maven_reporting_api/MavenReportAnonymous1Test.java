/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_reporting.maven_reporting_api;

import org.apache.maven.reporting.MavenReport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MavenReportAnonymous1Test {
    @Test
    void roleUsesTheMavenReportClassName() {
        assertThat(MavenReport.ROLE).isEqualTo("org.apache.maven.reporting.MavenReport");
    }
}
