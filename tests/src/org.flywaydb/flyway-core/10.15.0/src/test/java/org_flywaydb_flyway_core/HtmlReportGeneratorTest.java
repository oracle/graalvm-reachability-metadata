/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_flywaydb.flyway_core;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.CompositeResult;
import org.flywaydb.core.api.output.HtmlResult;
import org.flywaydb.core.internal.reports.html.HtmlReportGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HtmlReportGeneratorTest {

    @Test
    void generatesHtmlDocumentForAnEmptyReport() {
        String html = HtmlReportGenerator.generateHtml(
                new CompositeResult<HtmlResult>(),
                new FluentConfiguration());

        assertThat(html).contains("Flyway Reports").endsWith("</html>\n");
    }
}
