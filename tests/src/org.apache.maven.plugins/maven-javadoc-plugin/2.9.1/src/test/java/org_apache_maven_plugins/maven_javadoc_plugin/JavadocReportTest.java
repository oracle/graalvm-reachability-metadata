/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_plugins.maven_javadoc_plugin;

import java.util.Locale;

import org.apache.maven.plugin.javadoc.JavadocReport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JavadocReportTest {
    @Test
    void suppliesEnglishReportLabels() {
        JavadocReport report = new JavadocReport();

        assertThat(report.getName(Locale.ENGLISH)).isEqualTo("JavaDocs");
        assertThat(report.getDescription(Locale.ENGLISH)).isEqualTo("JavaDoc API documentation.");
    }

    @Test
    void fallsBackForAnUntranslatedLocale() {
        JavadocReport report = new JavadocReport();
        Locale locale = Locale.forLanguageTag("zz");

        assertThat(report.getName(locale)).isEqualTo(report.getName(Locale.ENGLISH));
        assertThat(report.getDescription(locale)).isEqualTo(report.getDescription(Locale.ENGLISH));
    }
}
