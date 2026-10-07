/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_micrometer_tracing_test;

import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_boot_micrometer_tracing_testTest {

    private static final String FACTORY_CLASS_NAME =
                    "org.springframework.boot.micrometer.tracing.test.autoconfigure.TracingContextCustomizerFactory";

    @Test
    void autoConfigureTracingImportsObservationAndTracingInfrastructure() {
        assertThat(ImportCandidates.load(AutoConfigureTracing.class, getClass().getClassLoader()).getCandidates())
                        .containsExactly(
                                        "org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration",
                                        "org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration",
                                        "org.springframework.boot.micrometer.tracing.autoconfigure.NoopTracerAutoConfiguration");
    }

    @Test
    void testContextFactoryDisablesTracingExportByDefault() {
        try (ConfigurableApplicationContext context = loadContext(UnannotatedTest.class, null)) {
            assertThat(tracingExportProperty(context)).isEqualTo("false");
        }
    }

    @Test
    void globalTestPropertyCanEnableTracingExport() {
        try (ConfigurableApplicationContext context = loadContext(UnannotatedTest.class, true)) {
            assertThat(tracingExportProperty(context)).isNull();
        }
    }

    @Test
    void annotationCanDisableTracingExportDespiteGlobalProperty() {
        try (ConfigurableApplicationContext context = loadContext(AnnotationDisabledTest.class, true)) {
            assertThat(tracingExportProperty(context)).isEqualTo("false");
        }
    }

    @Test
    void annotationCanEnableTracingExportDespiteGlobalProperty() {
        try (ConfigurableApplicationContext context = loadContext(AnnotationEnabledTest.class, false)) {
            assertThat(tracingExportProperty(context)).isNull();
        }
    }

    @Test
    void inheritedAnnotationOverridesGlobalProperty() {
        try (ConfigurableApplicationContext context = loadContext(InheritedTracingTest.class, true)) {
            assertThat(tracingExportProperty(context)).isEqualTo("false");
        }
    }

    @Test
    void tracingConfigurationParticipatesInTestContextCaching() {
        ContextCustomizer firstDisabled = createCustomizer(FirstDisabledTracingTest.class);
        ContextCustomizer secondDisabled = createCustomizer(SecondDisabledTracingTest.class);
        ContextCustomizer enabled = createCustomizer(EnabledTracingTest.class);

        assertThat(secondDisabled).isEqualTo(firstDisabled);
        assertThat(enabled).isNotEqualTo(firstDisabled);
    }

    private static ConfigurableApplicationContext loadContext(Class<?> testClass, Boolean tracingExport) {
        GenericApplicationContext context = new GenericApplicationContext();
        if (tracingExport != null) {
            context.getEnvironment().getPropertySources().addFirst(
                            new MapPropertySource("test",
                                            new HashMap<>(java.util.Map.of("spring.test.tracing.export", tracingExport))));
        }
        createCustomizer(testClass).customizeContext(context, null);
        return context;
    }

    private static ContextCustomizer createCustomizer(Class<?> testClass) {
        ContextCustomizerFactory factory = SpringFactoriesLoader.loadFactories(
                        ContextCustomizerFactory.class, testClass.getClassLoader()).stream()
                        .filter(candidate -> candidate.getClass().getName().equals(FACTORY_CLASS_NAME))
                        .findFirst()
                        .orElseThrow();
        return factory.createContextCustomizer(testClass, List.of());
    }

    private static String tracingExportProperty(ConfigurableApplicationContext context) {
        return context.getEnvironment().getProperty("management.tracing.export.enabled");
    }

    @AutoConfigureTracing
    static class TracingEnabledTest {

    }

    static class UnannotatedTest {

    }

    @AutoConfigureTracing(export = false)
    static class AnnotationDisabledTest {

    }

    @AutoConfigureTracing(export = true)
    static class AnnotationEnabledTest {

    }

    @AutoConfigureTracing(export = false)
    static class TracingBaseTest {

    }

    static class InheritedTracingTest extends TracingBaseTest {

    }

    @AutoConfigureTracing(export = false)
    static class FirstDisabledTracingTest {

    }

    @AutoConfigureTracing(export = false)
    static class SecondDisabledTracingTest {

    }

    @AutoConfigureTracing(export = true)
    static class EnabledTracingTest {

    }
}
