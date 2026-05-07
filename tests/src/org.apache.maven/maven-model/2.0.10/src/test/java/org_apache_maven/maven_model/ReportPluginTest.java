/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_model;

import org.apache.maven.model.ReportPlugin;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ReportPluginTest {
    @Test
    void rejectsNullReportSetWithTypedErrorMessage() {
        ReportPlugin reportPlugin = new ReportPlugin();

        ClassCastException exception = assertThrows(ClassCastException.class,
                () -> reportPlugin.addReportSet(null));

        assertThat(exception).hasMessageContaining("org.apache.maven.model.ReportSet");
        assertThat(reportPlugin.getReportSets()).isEmpty();
    }
}
