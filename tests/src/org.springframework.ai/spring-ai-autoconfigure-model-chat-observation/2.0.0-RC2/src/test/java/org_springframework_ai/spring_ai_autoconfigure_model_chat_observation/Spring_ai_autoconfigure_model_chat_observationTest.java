/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ai.spring_ai_autoconfigure_model_chat_observation;

import java.util.List;
import java.util.Map;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.observation.ChatModelMeterObservationHandler;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelPromptContentObservationHandler;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.chat.observation.autoconfigure.ChatObservationAutoConfiguration;
import org.springframework.ai.model.chat.observation.autoconfigure.ChatObservationProperties;
import org.springframework.ai.model.observation.ErrorLoggingObservationHandler;
import org.springframework.ai.observation.TracingAwareLoggingObservationHandler;
import org.springframework.ai.observation.conventions.AiObservationMetricAttributes;
import org.springframework.ai.observation.conventions.AiObservationMetricNames;
import org.springframework.ai.observation.conventions.AiTokenType;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_ai_autoconfigure_model_chat_observationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ChatObservationAutoConfiguration.class))
            .withBean(ChatModel.class, () -> prompt -> chatResponse("ack: " + prompt.getContents(), 1, 2))
            .withBean(ObservationRegistry.class, ObservationRegistry::create)
            .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new);

    @Test
    void autoConfigurationIsRegisteredForSpringBootDiscovery() {
        ClassLoader classLoader = Spring_ai_autoconfigure_model_chat_observationTest.class
                .getClassLoader();
        List<String> candidates = ImportCandidates.load(AutoConfiguration.class, classLoader).getCandidates();

        assertThat(candidates).contains(ChatObservationAutoConfiguration.class.getName());
    }

    @Test
    void defaultPropertiesCreateOnlyTheMeterHandler() {
        this.contextRunner.run(context -> {
            ChatObservationProperties properties = context.getBean(ChatObservationProperties.class);

            assertThat(properties.isLogPrompt()).isFalse();
            assertThat(properties.isLogCompletion()).isFalse();
            assertThat(properties.isIncludeErrorLogging()).isFalse();
            assertThat(context.getBeansOfType(ChatModelMeterObservationHandler.class)).hasSize(1);
            assertThat(context.getBeansOfType(TracingAwareLoggingObservationHandler.class)).isEmpty();
            assertThat(context.getBeansOfType(ErrorLoggingObservationHandler.class)).isEmpty();
            assertThat(context.getBean(ObservationRegistry.class)).isNotNull();
            assertThat(context.getBean(ChatModel.class).call("hello")).isEqualTo("ack: hello");
        });
    }

    @Test
    void enabledPropertiesCreateTracingAndErrorHandlers() {
        this.contextRunner.withBean(Tracer.class, () -> Tracer.NOOP)
                .withPropertyValues("spring.ai.chat.observations.log-prompt=true",
                        "spring.ai.chat.observations.log-completion=true",
                        "spring.ai.chat.observations.include-error-logging=true")
                .run(context -> {
                    ChatObservationProperties properties = context.getBean(ChatObservationProperties.class);
                    Map<String, TracingAwareLoggingObservationHandler> loggingHandlers = context
                            .getBeansOfType(TracingAwareLoggingObservationHandler.class);
                    ErrorLoggingObservationHandler errorHandler = context
                            .getBean(ErrorLoggingObservationHandler.class);
                    ChatModelObservationContext observationContext = chatObservationContext();

                    assertThat(properties.isLogPrompt()).isTrue();
                    assertThat(properties.isLogCompletion()).isTrue();
                    assertThat(properties.isIncludeErrorLogging()).isTrue();
                    assertThat(loggingHandlers).containsOnlyKeys("chatModelPromptContentObservationHandler",
                            "chatModelCompletionObservationHandler");
                    assertThat(loggingHandlers.values())
                            .allSatisfy(handler -> assertThat(handler.supportsContext(observationContext)).isTrue());
                    assertThat(loggingHandlers.values())
                            .allSatisfy(handler -> assertThat(handler.supportsContext(new Observation.Context()))
                                    .isFalse());
                    assertThat(errorHandler.supportsContext(observationContext)).isTrue();
                    assertThat(errorHandler.supportsContext(new Observation.Context())).isFalse();
                });
    }

    @Test
    void meterHandlerRecordsTokenUsageFromAChatResponse() {
        this.contextRunner.run(context -> {
            SimpleMeterRegistry meterRegistry = context.getBean(SimpleMeterRegistry.class);
            ChatModelMeterObservationHandler handler = context.getBean(ChatModelMeterObservationHandler.class);
            ChatModelObservationContext observationContext = chatObservationContext();
            observationContext.setResponse(chatResponse("answer", 4, 6));

            assertThat(handler.supportsContext(observationContext)).isTrue();
            assertThat(handler.supportsContext(new Observation.Context())).isFalse();

            handler.onStop(observationContext);

            assertTokenCounter(meterRegistry, AiTokenType.INPUT, 4.0);
            assertTokenCounter(meterRegistry, AiTokenType.OUTPUT, 6.0);
            assertTokenCounter(meterRegistry, AiTokenType.TOTAL, 10.0);
        });
    }

    @Test
    void loggingPropertiesDoNotCreateTracingHandlersWithoutATracerBean() {
        this.contextRunner
                .withPropertyValues("spring.ai.chat.observations.log-prompt=true",
                        "spring.ai.chat.observations.log-completion=true",
                        "spring.ai.chat.observations.include-error-logging=true")
                .run(context -> {
                    assertThat(context.getBeansOfType(TracingAwareLoggingObservationHandler.class)).isEmpty();
                    assertThat(context.getBeansOfType(ChatModelPromptContentObservationHandler.class)).isEmpty();
                    assertThat(context.getBeansOfType(ErrorLoggingObservationHandler.class)).isEmpty();
                });
    }

    @Test
    void autoConfigurationBacksOffForAUserPromptHandler() {
        this.contextRunner.withBean(Tracer.class, () -> Tracer.NOOP)
                .withBean("userPromptHandler", ChatModelPromptContentObservationHandler.class,
                        ChatModelPromptContentObservationHandler::new)
                .withPropertyValues("spring.ai.chat.observations.log-prompt=true")
                .run(context -> {
                    Map<String, ChatModelPromptContentObservationHandler> handlers = context
                            .getBeansOfType(ChatModelPromptContentObservationHandler.class);

                    assertThat(handlers).containsOnlyKeys("userPromptHandler");
                    assertThat(context.getBeansOfType(TracingAwareLoggingObservationHandler.class)).isEmpty();
                });
    }

    private static ChatModelObservationContext chatObservationContext() {
        return ChatModelObservationContext.builder()
                .prompt(new Prompt("How are observations configured?"))
                .provider("test-provider")
                .streaming(false)
                .build();
    }

    private static ChatResponse chatResponse(String content, int promptTokens, int completionTokens) {
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .model("test-chat-model")
                .usage(new DefaultUsage(promptTokens, completionTokens))
                .build();
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))), metadata);
    }

    private static void assertTokenCounter(SimpleMeterRegistry meterRegistry, AiTokenType tokenType,
            double expectedCount) {
        Counter counter = meterRegistry.find(AiObservationMetricNames.TOKEN_USAGE.value())
                .tag(AiObservationMetricAttributes.TOKEN_TYPE.value(), tokenType.value())
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(expectedCount);
    }

}
