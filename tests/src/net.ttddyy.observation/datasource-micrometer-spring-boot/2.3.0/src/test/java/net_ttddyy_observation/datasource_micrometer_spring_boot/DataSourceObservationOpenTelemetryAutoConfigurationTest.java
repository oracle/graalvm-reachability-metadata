/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_ttddyy_observation.datasource_micrometer_spring_boot;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.ttddyy.dsproxy.StatementType;
import net.ttddyy.observation.boot.autoconfigure.opentelemetry.DataSourceObservationOpenTelemetryAutoConfiguration;
import net.ttddyy.observation.boot.autoconfigure.opentelemetry.OpenTelemetryConnectionUrlParserCache;
import net.ttddyy.observation.boot.autoconfigure.opentelemetry.OpenTelemetryQueryAnalyzerCache;
import net.ttddyy.observation.tracing.opentelemetry.OpenTelemetryConnectionUrlParser;
import net.ttddyy.observation.tracing.opentelemetry.OpenTelemetryMeterObservationHandler;
import net.ttddyy.observation.tracing.opentelemetry.OpenTelemetryQueryAnalyzer;
import net.ttddyy.observation.tracing.opentelemetry.OpenTelemetryQueryObservationConvention;
import net.ttddyy.observation.tracing.opentelemetry.QueryAnalysisResult;
import net.ttddyy.observation.tracing.opentelemetry.UrlParseResult;
import net.ttddyy.observation.tracing.opentelemetry.jsqlparser.JSqlParserQueryAnalyzer;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/// Covers the OpenTelemetry semantic-conventions auto-configuration: the cached JSqlParser query
/// analyzer, the cached connection URL parser, the span convention and the meter handler, and the
/// properties that switch the analysis and its cache off.
public class DataSourceObservationOpenTelemetryAutoConfigurationTest {

    private static final String QUERY = "SELECT title FROM book WHERE id = 42";

    @Test
    void configuresQueryAnalysisFromProperties() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataSourceObservationOpenTelemetryAutoConfiguration.class))
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new);

        runner.run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            assertThat(context.getBeanNamesForType(OpenTelemetryQueryObservationConvention.class)).hasSize(1);
            assertThat(context.getBeanNamesForType(OpenTelemetryMeterObservationHandler.class)).hasSize(1);

            OpenTelemetryQueryAnalyzer analyzer = context.getBean(OpenTelemetryQueryAnalyzer.class);
            assertThat(analyzer).isInstanceOf(OpenTelemetryQueryAnalyzerCache.class);
            assertThat(((OpenTelemetryQueryAnalyzerCache) analyzer).getDelegate()).isInstanceOf(JSqlParserQueryAnalyzer.class);
            QueryAnalysisResult result = analyzer.analyze(QUERY, false, StatementType.STATEMENT);
            assertThat(result.getOperationName()).isEqualTo("SELECT");
            assertThat(result.getCollectionName()).isEqualTo("book");
            assertThat(result.getQuerySummary()).isEqualTo("SELECT book");
            assertThat(result.getQueryText()).doesNotContain("42");
            assertThat(analyzer.analyze(QUERY, false, StatementType.STATEMENT)).isSameAs(result);

            OpenTelemetryConnectionUrlParser parser = context.getBean(OpenTelemetryConnectionUrlParser.class);
            assertThat(parser).isInstanceOf(OpenTelemetryConnectionUrlParserCache.class);
            UrlParseResult url = parser.parse("jdbc:postgresql://db.example.org:5432/orders");
            assertThat(url.getSystemName()).isEqualTo("postgresql");
            assertThat(url.getNamespace()).isEqualTo("orders");
            assertThat(parser.parse("jdbc:postgresql://db.example.org:5432/orders")).isSameAs(url);
        });

        runner.withPropertyValues("jdbc.opentelemetry.analysis.cache.enabled=false",
                "jdbc.opentelemetry.analysis.sanitize.enabled=false")
                .run(context -> {
                    OpenTelemetryQueryAnalyzer analyzer = analyzer(context);
                    assertThat(analyzer).isInstanceOf(JSqlParserQueryAnalyzer.class);
                    QueryAnalysisResult result = analyzer.analyze(QUERY, false, StatementType.STATEMENT);
                    assertThat(result.getQuerySummary()).isEqualTo("SELECT book");
                    assertThat(result.getQueryText()).isNull();
                });

        runner.withPropertyValues("jdbc.opentelemetry.analysis.enabled=false")
                .run(context -> assertThat(analyzer(context)).isSameAs(OpenTelemetryQueryAnalyzer.NOOP));

        runner.withPropertyValues("jdbc.opentelemetry.spans.enabled=false", "jdbc.opentelemetry.metrics.enabled=false")
                .run(context -> {
                    assertThat(context.getBeanNamesForType(OpenTelemetryQueryObservationConvention.class)).isEmpty();
                    assertThat(context.getBeanNamesForType(OpenTelemetryMeterObservationHandler.class)).isEmpty();
                });
    }

    private static OpenTelemetryQueryAnalyzer analyzer(AssertableApplicationContext context) {
        assertThat(context.getStartupFailure()).isNull();
        return context.getBean(OpenTelemetryQueryAnalyzer.class);
    }
}
