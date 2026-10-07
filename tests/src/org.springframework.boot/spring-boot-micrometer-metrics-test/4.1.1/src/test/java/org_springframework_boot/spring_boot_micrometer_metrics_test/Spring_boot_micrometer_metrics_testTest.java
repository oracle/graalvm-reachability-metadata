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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_micrometer_metrics_testTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MetricsTestConfiguration.class);

    @Test
    void autoConfigureMetricsProvidesRegistriesAndRecordsMetrics() {
        this.contextRunner.run((context) -> {
            assertThat(context).hasSingleBean(MeterRegistry.class);
            assertThat(context).hasSingleBean(ObservationRegistry.class);
            assertThat(context.getBean(MeterRegistry.class)).isInstanceOf(SimpleMeterRegistry.class);

            MeterRegistry meterRegistry = context.getBean(MeterRegistry.class);
            Counter counter = meterRegistry.counter("application.requests", "status", "ok");
            counter.increment(3.0);

            assertThat(counter.count()).isEqualTo(3.0);
        });
    }

    @Test
    void autoConfigureMetricsImportsMetricsAutoConfiguration() {
        new ApplicationContextRunner()
                .withUserConfiguration(AutoConfigureMetricsOnlyConfiguration.class)
                .run((context) -> {
                    assertThat(context).hasSingleBean(MeterRegistry.class);
                    assertThat(context).hasSingleBean(ObservationRegistry.class);
                    assertThat(context.getBean(MeterRegistry.class)).isInstanceOf(SimpleMeterRegistry.class);
                });
    }

    @Test
    void metricsExportIsDisabledByDefaultWithoutAnnotation() {
        contextRunnerFor(NoMetricsAnnotation.class).run((context) -> {
            assertThat(context.getEnvironment().getProperty("management.defaults.metrics.export.enabled"))
                .isEqualTo("false");
            assertThat(context.getEnvironment().getProperty("management.simple.metrics.export.enabled"))
                .isEqualTo("true");
            assertThat(context.getBean(MeterRegistry.class)).isInstanceOf(SimpleMeterRegistry.class);

            Counter counter = context.getBean(MeterRegistry.class)
                .counter("application.requests", "status", "defaulted");
            counter.increment(5.0);

            assertThat(counter.count()).isEqualTo(5.0);
        });
    }

    @Test
    void annotationDisablesExternalExportAndEnablesSimpleMetrics() {
        contextRunnerFor(MetricsExportDisabled.class).run((context) -> {
            assertThat(context.getBean(MeterRegistry.class)).isInstanceOf(SimpleMeterRegistry.class);
            assertThat(context).hasSingleBean(ObservationRegistry.class);
            assertThat(context.getEnvironment().getProperty("management.defaults.metrics.export.enabled"))
                .isEqualTo("false");
            assertThat(context.getEnvironment().getProperty("management.simple.metrics.export.enabled"))
                .isEqualTo("true");

            Counter counter = context.getBean(MeterRegistry.class)
                .counter("application.requests", "status", "accepted");
            counter.increment();

            assertThat(counter.count()).isEqualTo(1.0);
        });
    }

    @Test
    void propertyEnablesMetricsExportWhenAnnotationIsAbsent() {
        new ApplicationContextRunner()
                .withPropertyValues("spring.test.metrics.export=true")
                .withInitializer((context) -> metricsContextCustomizer(NoMetricsAnnotation.class)
                    .customizeContext(context, null))
                .withUserConfiguration(MetricsTestConfiguration.class)
                .run((context) -> {
                    assertThat(context.getEnvironment()
                        .containsProperty("management.defaults.metrics.export.enabled")).isFalse();
                    assertThat(context.getEnvironment()
                        .containsProperty("management.simple.metrics.export.enabled")).isFalse();

                    Counter counter = context.getBean(MeterRegistry.class)
                        .counter("application.requests", "status", "configured");
                    counter.increment(2.0);

                    assertThat(counter.count()).isEqualTo(2.0);
                });
    }

    @Test
    void propertyDisablesMetricsExportWhenAnnotationIsAbsent() {
        new ApplicationContextRunner()
                .withPropertyValues("spring.test.metrics.export=false")
                .withInitializer((context) -> metricsContextCustomizer(NoMetricsAnnotation.class)
                    .customizeContext(context, null))
                .withUserConfiguration(MetricsTestConfiguration.class)
                .run((context) -> {
                    assertThat(context.getEnvironment()
                        .getProperty("management.defaults.metrics.export.enabled")).isEqualTo("false");
                    assertThat(context.getEnvironment()
                        .getProperty("management.simple.metrics.export.enabled")).isEqualTo("true");
                    assertThat(context.getBean(MeterRegistry.class)).isInstanceOf(SimpleMeterRegistry.class);

                    Counter counter = context.getBean(MeterRegistry.class)
                        .counter("application.requests", "status", "property-override");
                    counter.increment(7.0);

                    assertThat(counter.count()).isEqualTo(7.0);
                });
    }

    @Test
    void annotationEnablesMetricsExportWhenPropertyDisablesIt() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            TestPropertyValues.of("spring.test.metrics.export=false").applyTo(context);
            metricsContextCustomizer(MetricsExportEnabled.class).customizeContext(context, null);
            context.register(MetricsTestConfiguration.class);
            context.refresh();

            assertThat(context.getEnvironment()
                .containsProperty("management.defaults.metrics.export.enabled")).isFalse();
            assertThat(context.getEnvironment()
                .containsProperty("management.simple.metrics.export.enabled")).isFalse();

            Counter counter = context.getBean(MeterRegistry.class)
                .counter("application.requests", "status", "annotation-override");
            counter.increment(4.0);

            assertThat(counter.count()).isEqualTo(4.0);
        }
    }

    @Test
    void annotationDisablesMetricsExportWhenPropertyEnablesIt() {
        new ApplicationContextRunner()
                .withPropertyValues("spring.test.metrics.export=true")
                .withInitializer((context) -> metricsContextCustomizer(MetricsExportDisabled.class)
                    .customizeContext(context, null))
                .withUserConfiguration(MetricsTestConfiguration.class)
                .run((context) -> {
                    assertThat(context.getEnvironment()
                        .getProperty("management.defaults.metrics.export.enabled")).isEqualTo("false");
                    assertThat(context.getEnvironment()
                        .getProperty("management.simple.metrics.export.enabled")).isEqualTo("true");
                    assertThat(context.getBean(MeterRegistry.class)).isInstanceOf(SimpleMeterRegistry.class);

                    Counter counter = context.getBean(MeterRegistry.class)
                        .counter("application.requests", "status", "annotation-override");
                    counter.increment(6.0);

                    assertThat(counter.count()).isEqualTo(6.0);
                });
    }

    private static ApplicationContextRunner contextRunnerFor(Class<?> testClass) {
        return new ApplicationContextRunner()
                .withInitializer((context) -> metricsContextCustomizer(testClass).customizeContext(context, null))
                .withUserConfiguration(MetricsTestConfiguration.class);
    }

    private static ContextCustomizer metricsContextCustomizer(Class<?> testClass) {
        return SpringFactoriesLoader.loadFactories(ContextCustomizerFactory.class,
                Spring_boot_micrometer_metrics_testTest.class.getClassLoader())
            .stream()
            .filter((factory) -> factory.getClass().getName().equals(
                    "org.springframework.boot.micrometer.metrics.test.autoconfigure.MetricsContextCustomizerFactory"))
            .findFirst()
            .orElseThrow()
            .createContextCustomizer(testClass, List.of());
    }

    @Configuration(proxyBeanMethods = false)
    @Import({MetricsAutoConfiguration.class, SimpleMetricsExportAutoConfiguration.class,
            CompositeMeterRegistryAutoConfiguration.class, ObservationAutoConfiguration.class})
    @AutoConfigureMetrics
    static class MetricsTestConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigureMetrics
    static class AutoConfigureMetricsOnlyConfiguration {
    }

    @AutoConfigureMetrics(export = false)
    static class MetricsExportDisabled {
    }

    @AutoConfigureMetrics
    static class MetricsExportEnabled {
    }

    static class NoMetricsAnnotation {
    }

}
