/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_plugins.maven_javadoc_plugin;

import org.apache.maven.plugin.javadoc.JavadocReport;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

public class JavadocReportTest {
    @Test
    void resolvesDefaultReportTextFromBundledResourceBundle() {
        JavadocReport report = new JavadocReport();

        assertThat(report.getName(Locale.ENGLISH)).isEqualTo("JavaDocs");
        assertThat(report.getDescription(Locale.ENGLISH)).isEqualTo("JavaDoc API documentation.");
    }
}
