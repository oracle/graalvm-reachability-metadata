/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_micrometer_tracing_test;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;

import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_micrometer_tracing_testTest {

    @Test
    void autoConfigureTracingImportsUsableObservationAndTracingInfrastructure() {
        try (ConfigurableApplicationContext context = loadContext(TracingEnabledTest.class)) {
            Tracer tracer = context.getBean(Tracer.class);
            ObservationRegistry registry = context.getBean(ObservationRegistry.class);

            assertThat(tracer).isSameAs(Tracer.NOOP);
            Span span = tracer.nextSpan().name("checkout").start();
            assertThat(span.isNoop()).isTrue();
            span.end();

            assertThat(registry).isNotSameAs(ObservationRegistry.NOOP);
            Observation observation = Observation.start("checkout", registry);
            try (Observation.Scope ignored = observation.openScope()) {
                assertThat(registry.getCurrentObservation()).isSameAs(observation);
            } finally {
                observation.stop();
            }
            assertThat(registry.getCurrentObservation()).isNull();
            assertThat(context.getEnvironment().getProperty("management.tracing.export.enabled")).isNull();
        }
    }

    @Test
    void testContextFactoryDisablesTracingExportByDefault() {
        try (ConfigurableApplicationContext context = loadContext(UnannotatedTest.class)) {
            assertThat(tracingExportProperty(context.getEnvironment())).isEqualTo("false");
        }
    }

    @Test
    void globalTestPropertyCanEnableTracingExport() {
        try (ConfigurableApplicationContext context = loadContext(PropertyEnabledTest.class)) {
            assertThat(tracingExportProperty(context.getEnvironment())).isNull();
        }
    }

    @Test
    void annotationCanDisableTracingExportDespiteGlobalProperty() {
        try (ConfigurableApplicationContext context = loadContext(AnnotationDisabledTest.class)) {
            assertThat(tracingExportProperty(context.getEnvironment())).isEqualTo("false");
        }
    }

    @Test
    void annotationCanEnableTracingExportDespiteGlobalProperty() {
        try (ConfigurableApplicationContext context = loadContext(AnnotationEnabledTest.class)) {
            assertThat(tracingExportProperty(context.getEnvironment())).isNull();
        }
    }

    @Test
    void inheritedAnnotationConfiguresTracingAndOverridesGlobalProperty() {
        try (ConfigurableApplicationContext context = loadContext(InheritedTracingTest.class)) {
            assertThat(context.getBean(Tracer.class)).isSameAs(Tracer.NOOP);
            assertThat(context.getBean(ObservationRegistry.class)).isNotSameAs(ObservationRegistry.NOOP);
            assertThat(tracingExportProperty(context.getEnvironment())).isEqualTo("false");
        }
    }

    @Test
    void tracingConfigurationParticipatesInTestContextCaching() {
        ConfigurableApplicationContext firstDisabledContext = loadContext(FirstDisabledTracingTest.class);
        ConfigurableApplicationContext secondDisabledContext = loadContext(SecondDisabledTracingTest.class);
        ConfigurableApplicationContext enabledContext = loadContext(EnabledTracingTest.class);
        try {
            assertThat(secondDisabledContext).isSameAs(firstDisabledContext);
            assertThat(enabledContext).isNotSameAs(firstDisabledContext);
        } finally {
            firstDisabledContext.close();
            if (secondDisabledContext != firstDisabledContext) {
                secondDisabledContext.close();
            }
            enabledContext.close();
        }
    }

    private static ConfigurableApplicationContext loadContext(Class<?> testClass) {
        TestContextManager manager = new TestContextManager(testClass);
        return (ConfigurableApplicationContext) manager.getTestContext().getApplicationContext();
    }

    private static String tracingExportProperty(Environment environment) {
        return environment.getProperty("management.tracing.export.enabled");
    }

    @Configuration(proxyBeanMethods = false)
    static class EmptyConfiguration {

    }

    @Configuration(proxyBeanMethods = false)
    static class InheritedTracingConfiguration {

    }

    @Configuration(proxyBeanMethods = false)
    static class CachingConfiguration {

    }

    @AutoConfigureTracing
    @ContextConfiguration(classes = EmptyConfiguration.class)
    static class TracingEnabledTest {

    }

    @ContextConfiguration(classes = EmptyConfiguration.class)
    static class UnannotatedTest {

    }

    @TestPropertySource(properties = "spring.test.tracing.export=true")
    @ContextConfiguration(classes = EmptyConfiguration.class)
    static class PropertyEnabledTest {

    }

    @AutoConfigureTracing(export = false)
    @TestPropertySource(properties = "spring.test.tracing.export=true")
    @ContextConfiguration(classes = EmptyConfiguration.class)
    static class AnnotationDisabledTest {

    }

    @AutoConfigureTracing(export = true)
    @TestPropertySource(properties = "spring.test.tracing.export=false")
    @ContextConfiguration(classes = EmptyConfiguration.class)
    static class AnnotationEnabledTest {

    }

    @AutoConfigureTracing(export = false)
    static class TracingBaseTest {

    }

    @TestPropertySource(properties = "spring.test.tracing.export=true")
    @ContextConfiguration(classes = InheritedTracingConfiguration.class)
    static class InheritedTracingTest extends TracingBaseTest {

    }

    @AutoConfigureTracing(export = false)
    @ContextConfiguration(classes = CachingConfiguration.class)
    static class FirstDisabledTracingTest {

    }

    @AutoConfigureTracing(export = false)
    @ContextConfiguration(classes = CachingConfiguration.class)
    static class SecondDisabledTracingTest {

    }

    @AutoConfigureTracing(export = true)
    @ContextConfiguration(classes = CachingConfiguration.class)
    static class EnabledTracingTest {

    }
}
