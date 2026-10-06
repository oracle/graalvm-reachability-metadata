/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_flywaydb.flyway_core;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.CompositeResult;
import org.flywaydb.core.api.output.HtmlResult;
import org.flywaydb.core.api.output.MigrateResult;
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

    @Test
    void rendersMigrationResultsInHtmlReport() {
        FluentConfiguration configuration = new FluentConfiguration()
                .dataSource("jdbc:h2:mem:html-report", "user", "password")
                .resourceProvider(new FixedResourceProvider())
                .loggers("slf4j", "log4j2", "apache-commons");
        Flyway flyway = configuration.load();
        MigrateResult migration = flyway.migrate();

        CompositeResult<HtmlResult> report = new CompositeResult<>();
        report.individualResults.add(migration);
        String html = HtmlReportGenerator.generateHtml(report, configuration);

        assertThat(html)
                .contains("Migration report")
                .contains("2 scripts migrated")
                .endsWith("</html>\n");
    }
}
