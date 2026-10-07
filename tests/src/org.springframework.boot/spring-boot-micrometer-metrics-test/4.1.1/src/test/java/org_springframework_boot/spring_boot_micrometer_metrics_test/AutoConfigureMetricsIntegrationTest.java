/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_micrometer_metrics_test;

import java.util.List;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

public class AutoConfigureMetricsIntegrationTest {

    private static final String METRICS_CONTEXT_CUSTOMIZER_FACTORY =
            "org.springframework.boot.micrometer.metrics.test.autoconfigure.MetricsContextCustomizerFactory";

    @Test
    void annotationPublishesItsMetricsAndObservationAutoConfigurationImports() {
        List<String> candidates = ImportCandidates
            .load(AutoConfigureMetrics.class, AutoConfigureMetricsIntegrationTest.class.getClassLoader())
            .getCandidates();

        assertThat(candidates)
                .containsExactly(
                        "org.springframework.boot.micrometer.metrics.autoconfigure."
                                + "CompositeMeterRegistryAutoConfiguration",
                        "org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration",
                        "org.springframework.boot.micrometer.metrics.autoconfigure.export.simple."
                                + "SimpleMetricsExportAutoConfiguration",
                        "org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration");
    }

    @Test
    void publishedAutoConfigurationsProvideAnEnabledSimpleRegistry() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            TestPropertyValues.of("management.simple.metrics.export.enabled=true").applyTo(context);
            context.register(MetricsAutoConfiguration.class, SimpleMetricsExportAutoConfiguration.class,
                    ObservationAutoConfiguration.class);
            context.refresh();

            MeterRegistry registry = context.getBean(MeterRegistry.class);
            ObservationRegistry observationRegistry = context.getBean(ObservationRegistry.class);
            Counter counter = registry.counter("test.requests", "outcome", "accepted");
            counter.increment(2.0);

            assertThat(registry.find("test.requests").tag("outcome", "accepted").counter()).isSameAs(counter);
            assertThat(counter.count()).isEqualTo(2.0);
            assertThat(observationRegistry).isNotSameAs(ObservationRegistry.NOOP);
        }
    }

    @Test
    void absentAnnotationDisablesExternalExportAndRetainsSimpleMetrics() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            customizeContext(UnannotatedConfiguration.class, context);

            assertThat(context.getEnvironment().getProperty("management.defaults.metrics.export.enabled"))
                    .isEqualTo("false");
            assertThat(context.getEnvironment().getProperty("management.simple.metrics.export.enabled"))
                    .isEqualTo("true");
        }
    }

    @Test
    void globalPropertyEnablesMetricsForAnUnannotatedTest() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            TestPropertyValues.of("spring.test.metrics.export=true").applyTo(context);
            customizeContext(UnannotatedConfiguration.class, context);

            assertThat(context.getEnvironment().getProperty("spring.test.metrics.export")).isEqualTo("true");
            assertThat(context.getEnvironment().getProperty("management.defaults.metrics.export.enabled")).isNull();
            assertThat(context.getEnvironment().getProperty("management.simple.metrics.export.enabled")).isNull();
        }
    }

    @Test
    void enabledAnnotationOverridesTheGlobalDisabledProperty() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            TestPropertyValues.of("spring.test.metrics.export=false").applyTo(context);
            customizeContext(MetricsEnabledConfiguration.class, context);

            assertThat(context.getEnvironment().getProperty("spring.test.metrics.export")).isEqualTo("false");
            assertThat(context.getEnvironment().getProperty("management.defaults.metrics.export.enabled")).isNull();
            assertThat(context.getEnvironment().getProperty("management.simple.metrics.export.enabled")).isNull();
        }
    }

    @Test
    void disabledAnnotationOverridesTheGlobalEnabledProperty() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            TestPropertyValues.of("spring.test.metrics.export=true").applyTo(context);
            customizeContext(MetricsDisabledConfiguration.class, context);

            assertThat(context.getEnvironment().getProperty("spring.test.metrics.export")).isEqualTo("true");
            assertThat(context.getEnvironment().getProperty("management.defaults.metrics.export.enabled"))
                    .isEqualTo("false");
            assertThat(context.getEnvironment().getProperty("management.simple.metrics.export.enabled"))
                    .isEqualTo("true");
        }
    }

    private static void customizeContext(Class<?> testClass, GenericApplicationContext context) {
        ContextCustomizer customizer = metricsContextCustomizerFactory().createContextCustomizer(testClass, List.of());
        assertThat(customizer).isNotNull();
        customizer.customizeContext(context, new MergedContextConfiguration(testClass, null, null, null, null));
    }

    private static ContextCustomizerFactory metricsContextCustomizerFactory() {
        return SpringFactoriesLoader
            .loadFactories(ContextCustomizerFactory.class, AutoConfigureMetricsIntegrationTest.class.getClassLoader())
            .stream()
            .filter((factory) -> factory.getClass().getName().equals(METRICS_CONTEXT_CUSTOMIZER_FACTORY))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Metrics context customizer factory was not loaded"));
    }

    static class UnannotatedConfiguration {
    }

    @AutoConfigureMetrics
    static class MetricsEnabledConfiguration {
    }

    @AutoConfigureMetrics(export = false)
    static class MetricsDisabledConfiguration {
    }
}
