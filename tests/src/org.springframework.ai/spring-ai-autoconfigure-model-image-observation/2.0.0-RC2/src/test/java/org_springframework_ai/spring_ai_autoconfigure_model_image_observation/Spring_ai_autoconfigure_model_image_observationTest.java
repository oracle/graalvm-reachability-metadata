/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ai.spring_ai_autoconfigure_model_image_observation;

import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.image.observation.ImageModelObservationContext;
import org.springframework.ai.image.observation.ImageModelPromptContentObservationHandler;
import org.springframework.ai.model.image.observation.autoconfigure.ImageObservationAutoConfiguration;
import org.springframework.ai.model.image.observation.autoconfigure.ImageObservationProperties;
import org.springframework.ai.observation.TracingAwareLoggingObservationHandler;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_ai_autoconfigure_model_image_observationTest {

    private static final String LOG_PROMPT_PROPERTY = "spring.ai.image.observations.log-prompt";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ImageObservationAutoConfiguration.class));

    @Test
    void propertiesExposeDocumentedPrefixAndDefaultLogPromptValue() {
        ImageObservationProperties properties = new ImageObservationProperties();

        assertThat(ImageObservationProperties.CONFIG_PREFIX).isEqualTo("spring.ai.image.observations");
        assertThat(properties.isLogPrompt()).isFalse();

        properties.setLogPrompt(true);

        assertThat(properties.isLogPrompt()).isTrue();
    }

    @Test
    void autoConfigurationRegistersPropertiesWithDefaultValues() {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ImageObservationProperties.class)
                    .doesNotHaveBean(ImageModelPromptContentObservationHandler.class)
                    .doesNotHaveBean(TracingAwareLoggingObservationHandler.class);
            assertThat(context.getBean(ImageObservationProperties.class).isLogPrompt()).isFalse();
        });
    }

    @Test
    void propertyBindingEnablesPromptLoggingFlagWithoutEagerlyCreatingHandler() {
        this.contextRunner.withPropertyValues(LOG_PROMPT_PROPERTY + "=true").run(context -> {
            assertThat(context).hasSingleBean(ImageObservationProperties.class)
                    .doesNotHaveBean(ImageModelPromptContentObservationHandler.class)
                    .doesNotHaveBean(TracingAwareLoggingObservationHandler.class);
            assertThat(context.getBean(ImageObservationProperties.class).isLogPrompt()).isTrue();
        });
    }

    @Test
    void createsPlainPromptContentHandlerWhenPromptLoggingIsEnabledAndTracerIsUnavailable() {
        this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
                .withPropertyValues(LOG_PROMPT_PROPERTY + "=true")
                .run(context -> assertThat(context).hasSingleBean(ImageModelPromptContentObservationHandler.class)
                        .doesNotHaveBean(TracingAwareLoggingObservationHandler.class)
                        .hasBean("imageModelPromptContentObservationHandler"));
    }

    @Test
    void createsTracingAwarePromptContentHandlerWhenPromptLoggingIsEnabledAndTracerBeanIsAvailable() {
        this.contextRunner.withUserConfiguration(TracerConfiguration.class)
                .withPropertyValues(LOG_PROMPT_PROPERTY + "=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ImageModelPromptContentObservationHandler.class)
                            .hasSingleBean(TracingAwareLoggingObservationHandler.class)
                            .hasBean("imageModelPromptContentObservationHandler");
                    assertThat(context.getBean("imageModelPromptContentObservationHandler"))
                            .isInstanceOf(TracingAwareLoggingObservationHandler.class);
                });
    }

    @Test
    void disabledPromptLoggingDoesNotCreateHandlersWithOrWithoutTracer() {
        this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
                .withPropertyValues(LOG_PROMPT_PROPERTY + "=false")
                .run(context -> assertThat(context).doesNotHaveBean(ImageModelPromptContentObservationHandler.class)
                        .doesNotHaveBean(TracingAwareLoggingObservationHandler.class));

        this.contextRunner.withUserConfiguration(TracerConfiguration.class)
                .withPropertyValues(LOG_PROMPT_PROPERTY + "=false")
                .run(context -> assertThat(context).doesNotHaveBean(ImageModelPromptContentObservationHandler.class)
                        .doesNotHaveBean(TracingAwareLoggingObservationHandler.class));
    }

    @Test
    void backsOffWhenCustomPlainPromptContentHandlerExists() {
        this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
                .withUserConfiguration(CustomPlainHandlerConfiguration.class)
                .withPropertyValues(LOG_PROMPT_PROPERTY + "=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ImageModelPromptContentObservationHandler.class)
                            .hasBean("customImageModelPromptContentObservationHandler")
                            .doesNotHaveBean(TracingAwareLoggingObservationHandler.class);
                    assertThat(context.getBean(ImageModelPromptContentObservationHandler.class))
                            .isSameAs(CustomPlainHandlerConfiguration.HANDLER);
                });
    }

    @Test
    void backsOffWhenCustomTracingAwareHandlerUsesAutoConfiguredBeanName() {
        this.contextRunner
                .withUserConfiguration(TracerConfiguration.class, CustomTracingAwareHandlerConfiguration.class)
                .withPropertyValues(LOG_PROMPT_PROPERTY + "=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ImageModelPromptContentObservationHandler.class)
                            .hasSingleBean(TracingAwareLoggingObservationHandler.class)
                            .hasBean("imageModelPromptContentObservationHandler");
                    assertThat(context.getBean(TracingAwareLoggingObservationHandler.class))
                            .isSameAs(CustomTracingAwareHandlerConfiguration.HANDLER);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class TracerConfiguration {

        @Bean
        Tracer tracer() {
            return Tracer.NOOP;
        }

    }

    @Configuration(proxyBeanMethods = false)
    static class CustomPlainHandlerConfiguration {

        static final ImageModelPromptContentObservationHandler HANDLER =
                new ImageModelPromptContentObservationHandler();

        @Bean
        ImageModelPromptContentObservationHandler customImageModelPromptContentObservationHandler() {
            return HANDLER;
        }

    }

    @Configuration(proxyBeanMethods = false)
    static class CustomTracingAwareHandlerConfiguration {

        static final TracingAwareLoggingObservationHandler<ImageModelObservationContext> HANDLER =
                new TracingAwareLoggingObservationHandler<>(new ImageModelPromptContentObservationHandler(),
                        Tracer.NOOP);

        @Bean("imageModelPromptContentObservationHandler")
        TracingAwareLoggingObservationHandler<ImageModelObservationContext> customTracingAwareHandler() {
            return HANDLER;
        }

    }

}
