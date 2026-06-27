/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ai.spring_ai_autoconfigure_model_chat_observation;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.observation.ChatModelCompletionObservationHandler;
import org.springframework.ai.chat.observation.ChatModelMeterObservationHandler;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelPromptContentObservationHandler;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.chat.observation.autoconfigure.ChatObservationAutoConfiguration;
import org.springframework.ai.model.chat.observation.autoconfigure.ChatObservationProperties;
import org.springframework.ai.model.observation.ErrorLoggingObservationHandler;
import org.springframework.ai.observation.TracingAwareLoggingObservationHandler;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_ai_autoconfigure_model_chat_observationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ChatObservationAutoConfiguration.class));

    @Test
    void meterRegistryEnablesMeterHandlerAndDefaultPropertiesRemainDisabled() {
        this.contextRunner.withUserConfiguration(MeterRegistryConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(ChatObservationProperties.class);
            ChatObservationProperties properties = context.getBean(ChatObservationProperties.class);
            assertThat(properties.isLogPrompt()).isFalse();
            assertThat(properties.isLogCompletion()).isFalse();
            assertThat(properties.isIncludeErrorLogging()).isFalse();

            assertThat(context).hasSingleBean(ChatModelMeterObservationHandler.class);
            assertThat(context).doesNotHaveBean("chatModelPromptContentObservationHandler");
            assertThat(context).doesNotHaveBean("chatModelCompletionObservationHandler");
            assertThat(context).doesNotHaveBean(ErrorLoggingObservationHandler.class);
        });
    }

    @Test
    void loggingPropertiesWithoutTracerDoNotEnableTracingAwareHandlers() {
        this.contextRunner
                .withUserConfiguration(MeterRegistryConfiguration.class)
                .withPropertyValues(
                        "spring.ai.chat.observations.log-prompt=true",
                        "spring.ai.chat.observations.log-completion=true",
                        "spring.ai.chat.observations.include-error-logging=true")
                .run(context -> {
                    ChatObservationProperties properties = context.getBean(ChatObservationProperties.class);
                    assertThat(properties.isLogPrompt()).isTrue();
                    assertThat(properties.isLogCompletion()).isTrue();
                    assertThat(properties.isIncludeErrorLogging()).isTrue();

                    assertThat(context).hasSingleBean(ChatModelMeterObservationHandler.class);
                    assertThat(context).doesNotHaveBean("chatModelPromptContentObservationHandler");
                    assertThat(context).doesNotHaveBean("chatModelCompletionObservationHandler");
                    assertThat(context).doesNotHaveBean(ErrorLoggingObservationHandler.class);
                });
    }

    @Test
    void loggingPropertiesAndTracerEnableTracingAwareHandlers() {
        this.contextRunner
                .withUserConfiguration(MeterRegistryConfiguration.class, TracerConfiguration.class)
                .withPropertyValues(
                        "spring.ai.chat.observations.log-prompt=true",
                        "spring.ai.chat.observations.log-completion=true",
                        "spring.ai.chat.observations.include-error-logging=true")
                .run(context -> {
                    ChatObservationProperties properties = context.getBean(ChatObservationProperties.class);
                    assertThat(properties.isLogPrompt()).isTrue();
                    assertThat(properties.isLogCompletion()).isTrue();
                    assertThat(properties.isIncludeErrorLogging()).isTrue();

                    assertThat(context).hasSingleBean(ChatModelMeterObservationHandler.class);
                    assertThat(context).hasSingleBean(ErrorLoggingObservationHandler.class);
                    assertThat(context).hasBean("chatModelPromptContentObservationHandler");
                    assertThat(context).hasBean("chatModelCompletionObservationHandler");
                    assertThat(context).doesNotHaveBean(ChatModelPromptContentObservationHandler.class);
                    assertThat(context).doesNotHaveBean(ChatModelCompletionObservationHandler.class);

                    ObservationHandler<?> promptHandler = context.getBean(
                            "chatModelPromptContentObservationHandler", ObservationHandler.class);
                    ObservationHandler<?> completionHandler = context.getBean(
                            "chatModelCompletionObservationHandler", ObservationHandler.class);
                    ErrorLoggingObservationHandler errorHandler = context.getBean(ErrorLoggingObservationHandler.class);
                    ChatModelMeterObservationHandler meterHandler = context.getBean(
                            ChatModelMeterObservationHandler.class);
                    ChatModelObservationContext chatContext = chatObservationContext();
                    Observation.Context plainContext = new Observation.Context();

                    assertThat(promptHandler).isInstanceOf(TracingAwareLoggingObservationHandler.class);
                    assertThat(completionHandler).isInstanceOf(TracingAwareLoggingObservationHandler.class);
                    assertThat(promptHandler.supportsContext(chatContext)).isTrue();
                    assertThat(completionHandler.supportsContext(chatContext)).isTrue();
                    assertThat(errorHandler.supportsContext(chatContext)).isTrue();
                    assertThat(meterHandler.supportsContext(chatContext)).isTrue();
                    assertThat(promptHandler.supportsContext(plainContext)).isFalse();
                    assertThat(completionHandler.supportsContext(plainContext)).isFalse();
                    assertThat(errorHandler.supportsContext(plainContext)).isFalse();
                    assertThat(meterHandler.supportsContext(plainContext)).isFalse();
                });
    }

    @Test
    void userProvidedPromptHandlerMakesPromptAutoConfigurationBackOff() {
        this.contextRunner
                .withUserConfiguration(MeterRegistryConfiguration.class, TracerConfiguration.class,
                        CustomPromptHandlerConfiguration.class)
                .withPropertyValues(
                        "spring.ai.chat.observations.log-prompt=true",
                        "spring.ai.chat.observations.log-completion=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ChatModelPromptContentObservationHandler.class);
                    assertThat(context.getBean("chatModelPromptContentObservationHandler"))
                            .isInstanceOf(ChatModelPromptContentObservationHandler.class);
                    assertThat(context.getBean("chatModelCompletionObservationHandler"))
                            .isInstanceOf(TracingAwareLoggingObservationHandler.class);
                    assertThat(context).doesNotHaveBean(ErrorLoggingObservationHandler.class);
                });
    }

    private static ChatModelObservationContext chatObservationContext() {
        return ChatModelObservationContext.builder()
                .prompt(new Prompt("Explain observation auto-configuration."))
                .provider("test-provider")
                .streaming(false)
                .build();
    }

    @Configuration(proxyBeanMethods = false)
    static class MeterRegistryConfiguration {

        @Bean
        SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TracerConfiguration {

        @Bean
        Tracer tracer() {
            return Tracer.NOOP;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomPromptHandlerConfiguration {

        @Bean
        ChatModelPromptContentObservationHandler chatModelPromptContentObservationHandler() {
            return new ChatModelPromptContentObservationHandler();
        }
    }
}
