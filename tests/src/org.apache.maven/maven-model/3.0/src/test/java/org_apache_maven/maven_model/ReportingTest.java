/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_model;

import org.apache.maven.model.Reporting;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReportingTest {
    @Test
    void addsNullPluginToPlugins() {
        Reporting reporting = new Reporting();

        reporting.addPlugin(null);

        assertThat(reporting.getPlugins()).hasSize(1);
        assertThat(reporting.getPlugins().get(0)).isNull();
    }
}
