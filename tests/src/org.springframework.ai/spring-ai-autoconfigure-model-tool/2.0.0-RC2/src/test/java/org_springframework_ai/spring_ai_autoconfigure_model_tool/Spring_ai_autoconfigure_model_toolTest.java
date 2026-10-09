/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ai.spring_ai_autoconfigure_model_tool;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingProperties;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.ai.tool.observation.ToolCallingContentObservationFilter;
import org.springframework.ai.tool.observation.ToolCallingObservationContext;
import org.springframework.ai.tool.observation.ToolCallingObservationConvention;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public class Spring_ai_autoconfigure_model_toolTest {

    @Test
    void createsToolCallingInfrastructureAndBindsProperties() {
        try (AnnotationConfigApplicationContext context = openContext(
                Map.of("spring.ai.tools.throw-exception-on-error", "true"))) {
            assertThat(context.getBean(ToolCallingManager.class)).isNotNull();
            assertThat(context.getBean(ToolCallbackResolver.class)).isNotNull();
            assertThat(context.getBean(ToolExecutionExceptionProcessor.class)).isNotNull();

            ToolCallingProperties properties = context.getBean(ToolCallingProperties.class);
            assertThat(properties.isThrowExceptionOnError()).isTrue();
            assertThat(properties.getObservations().isIncludeContent()).isFalse();
            assertThat(context.getBeansOfType(ToolCallingContentObservationFilter.class)).isEmpty();
        }
    }

    @Test
    void registersContentObservationFilterWhenContentInclusionIsEnabled() {
        try (AnnotationConfigApplicationContext context = openContext(
                Map.of("spring.ai.tools.observations.include-content", "true"))) {
            assertThat(context.getBean(ToolCallingContentObservationFilter.class)).isNotNull();
            assertThat(context.getBean(ToolCallingProperties.class).getObservations().isIncludeContent()).isTrue();
        }
    }

    @Test
    void resolvesCallbacksFromBeansAndProviderListsWhenExecutingToolCalls() {
        try (AnnotationConfigApplicationContext context = openContext(Map.of(), CallbackConfiguration.class)) {
            ToolCallingManager manager = context.getBean(ToolCallingManager.class);
            AssistantMessage assistantMessage = AssistantMessage.builder()
                    .content("")
                    .toolCalls(List.of(new AssistantMessage.ToolCall("direct-id", "function", "direct-tool",
                            "{\"value\":\"one\"}"),
                            new AssistantMessage.ToolCall("provider-id", "function", "provider-tool",
                                    "{\"value\":\"two\"}")))
                    .build();
            Prompt prompt = new Prompt(List.of(new UserMessage("Use both tools")),
                    ToolCallingChatOptions.builder()
                            .toolContext("requestId", "request-42")
                            .build());

            ToolExecutionResult result = manager.executeToolCalls(prompt,
                    new ChatResponse(List.of(new Generation(assistantMessage))));

            assertThat(result.conversationHistory()).hasSize(3);
            assertThat(result.conversationHistory().get(2)).isInstanceOf(ToolResponseMessage.class);
            ToolResponseMessage toolResponses = (ToolResponseMessage) result.conversationHistory().get(2);
            assertThat(toolResponses.getResponses()).hasSize(2);
            assertThat(toolResponses.getResponses()).anySatisfy(response -> {
                assertThat(response.name()).isEqualTo("direct-tool");
                assertThat(response.responseData()).isEqualTo("direct-tool:{\"value\":\"one\"}:request-42");
            });
            assertThat(toolResponses.getResponses()).anySatisfy(response -> {
                assertThat(response.name()).isEqualTo("provider-tool");
                assertThat(response.responseData()).isEqualTo("provider-tool:{\"value\":\"two\"}:request-42");
            });
        }
    }

    @Test
    void resolvesCallbacksFromIndividualProviderBeansWhenExecutingToolCalls() {
        try (AnnotationConfigApplicationContext context = openContext(Map.of(), DirectProviderConfiguration.class)) {
            AssistantMessage assistantMessage = AssistantMessage.builder()
                    .content("")
                    .toolCalls(List.of(new AssistantMessage.ToolCall("standalone-provider-id", "function",
                            "standalone-provider-tool", "{\"value\":\"three\"}")))
                    .build();
            Prompt prompt = new Prompt(new UserMessage("Use the standalone provider tool"),
                    ToolCallingChatOptions.builder()
                            .toolContext("requestId", "request-44")
                            .build());

            ToolExecutionResult result = context.getBean(ToolCallingManager.class)
                    .executeToolCalls(prompt, new ChatResponse(List.of(new Generation(assistantMessage))));

            ToolResponseMessage toolResponses = (ToolResponseMessage) result.conversationHistory().get(2);
            assertThat(toolResponses.getResponses()).singleElement().satisfies(response -> {
                assertThat(response.name()).isEqualTo("standalone-provider-tool");
                assertThat(response.responseData())
                        .isEqualTo("standalone-provider-tool:{\"value\":\"three\"}:request-44");
            });
        }
    }

    @Test
    void convertsToolErrorsToToolResponsesByDefault() {
        try (AnnotationConfigApplicationContext context = openContext(Map.of(), FailingToolConfiguration.class)) {
            ToolExecutionResult result = executeFailingTool(context.getBean(ToolCallingManager.class));

            ToolResponseMessage toolResponses = (ToolResponseMessage) result.conversationHistory().get(2);
            assertThat(toolResponses.getResponses()).singleElement().satisfies(response -> {
                assertThat(response.name()).isEqualTo("failing-tool");
                assertThat(response.responseData()).contains("intentional tool error");
            });
        }
    }

    @Test
    void throwsToolErrorsWhenConfiguredToDoSo() {
        try (AnnotationConfigApplicationContext context = openContext(
                Map.of("spring.ai.tools.throw-exception-on-error", "true"), FailingToolConfiguration.class)) {
            Throwable thrown = catchThrowable(() -> executeFailingTool(context.getBean(ToolCallingManager.class)));
            assertThat(thrown).isInstanceOf(ToolExecutionException.class);
            ToolExecutionException exception = (ToolExecutionException) thrown;
            assertThat(exception.getToolDefinition().name()).isEqualTo("failing-tool");
        }
    }

    @Test
    void appliesApplicationObservationConventionToToolCalls() {
        try (AnnotationConfigApplicationContext context = openContext(Map.of(), ObservationConfiguration.class)) {
            AssistantMessage assistantMessage = AssistantMessage.builder()
                    .content("")
                    .toolCalls(List.of(new AssistantMessage.ToolCall("observed-id", "function", "observed-tool",
                            "{\"value\":\"three\"}")))
                    .build();
            Prompt prompt = new Prompt(new UserMessage("Use the observed tool"),
                    ToolCallingChatOptions.builder()
                            .toolContext("requestId", "request-43")
                            .build());

            ToolExecutionResult result = context.getBean(ToolCallingManager.class)
                    .executeToolCalls(prompt, new ChatResponse(List.of(new Generation(assistantMessage))));

            RecordingToolCallingObservationHandler handler = context
                    .getBean(RecordingToolCallingObservationHandler.class);
            assertThat(result.conversationHistory()).hasSize(3);
            assertThat(handler.observationContext).isNotNull();
            assertThat(handler.observationContext.getName()).isEqualTo("test.tool.call");
            assertThat(handler.observationContext.getToolDefinition().name()).isEqualTo("observed-tool");
            assertThat(handler.observationContext.getToolCallArguments()).isEqualTo("{\"value\":\"three\"}");
            assertThat(handler.observationContext.getToolCallResult())
                    .isEqualTo("observed-tool:{\"value\":\"three\"}:request-43");
            assertThat(context.getBean(RecordingToolCallingObservationConvention.class).contextualNames)
                    .hasValue(1);
        }
    }

    @Test
    void backsOffWhenTheApplicationProvidesToolCallingManager() {
        ToolCallingManager customManager = new ToolCallingManager() {
            @Override
            public List<ToolDefinition> resolveToolDefinitions(
                    ToolCallingChatOptions options) {
                return List.of();
            }

            @Override
            public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
                return ToolExecutionResult.builder().conversationHistory(List.of()).build();
            }
        };

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ToolCallingManager.class, () -> customManager);
            context.register(ToolCallingAutoConfiguration.class);
            context.refresh();

            assertThat(context.getBean(ToolCallingManager.class)).isSameAs(customManager);
            assertThat(context.getBeansOfType(ToolExecutionExceptionProcessor.class)).hasSize(1);
        }
    }

    private static AnnotationConfigApplicationContext openContext(Map<String, Object> properties,
            Class<?>... configurations) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test-properties", properties));
        context.register(ToolCallingAutoConfiguration.class);
        if (configurations.length > 0) {
            context.register(configurations);
        }
        context.refresh();
        return context;
    }

    private static ToolExecutionResult executeFailingTool(ToolCallingManager manager) {
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("failure-id", "function", "failing-tool", "{}")))
                .build();
        Prompt prompt = new Prompt(new UserMessage("Use the failing tool"), ToolCallingChatOptions.builder().build());
        return manager.executeToolCalls(prompt, new ChatResponse(List.of(new Generation(assistantMessage))));
    }

    @Configuration(proxyBeanMethods = false)
    public static class CallbackConfiguration {

        @Bean
        public ToolCallback directTool() {
            return new ContextAwareToolCallback("direct-tool");
        }

        @Bean
        public List<ToolCallbackProvider> callbackProviders() {
            return List.of(ToolCallbackProvider.from(new ContextAwareToolCallback("provider-tool")));
        }

    }

    @Configuration(proxyBeanMethods = false)
    public static class DirectProviderConfiguration {

        @Bean
        public ToolCallbackProvider standaloneCallbackProvider() {
            return ToolCallbackProvider.from(new ContextAwareToolCallback("standalone-provider-tool"));
        }

    }

    @Configuration(proxyBeanMethods = false)
    public static class ObservationConfiguration {

        @Bean
        public ToolCallback observedTool() {
            return new ContextAwareToolCallback("observed-tool");
        }

        @Bean
        public RecordingToolCallingObservationHandler observationHandler() {
            return new RecordingToolCallingObservationHandler();
        }

        @Bean
        public ObservationRegistry observationRegistry(RecordingToolCallingObservationHandler handler) {
            ObservationRegistry registry = ObservationRegistry.create();
            registry.observationConfig().observationHandler(handler);
            return registry;
        }

        @Bean
        public RecordingToolCallingObservationConvention toolCallingObservationConvention() {
            return new RecordingToolCallingObservationConvention();
        }

    }

    @Configuration(proxyBeanMethods = false)
    public static class FailingToolConfiguration {

        @Bean
        public ToolCallback failingTool() {
            return new FailingToolCallback();
        }

    }

    public static final class ContextAwareToolCallback implements ToolCallback {

        private final String name;

        public ContextAwareToolCallback(String name) {
            this.name = name;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return ToolDefinition.builder()
                    .name(this.name)
                    .description("A deterministic test tool")
                    .inputSchema("{\"type\":\"object\"}")
                    .build();
        }

        @Override
        public String call(String toolInput) {
            return this.name + ":" + toolInput;
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            return this.name + ":" + toolInput + ":" + toolContext.getContext().get("requestId");
        }

    }

    public static final class RecordingToolCallingObservationHandler
            implements ObservationHandler<ToolCallingObservationContext> {

        private ToolCallingObservationContext observationContext;

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof ToolCallingObservationContext;
        }

        @Override
        public void onStop(ToolCallingObservationContext context) {
            this.observationContext = context;
        }

    }

    public static final class RecordingToolCallingObservationConvention implements ToolCallingObservationConvention {

        private final AtomicInteger contextualNames = new AtomicInteger();

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof ToolCallingObservationContext;
        }

        @Override
        public String getName() {
            return "test.tool.call";
        }

        @Override
        public String getContextualName(ToolCallingObservationContext context) {
            this.contextualNames.incrementAndGet();
            return context.getToolDefinition().name();
        }

    }

    public static final class FailingToolCallback implements ToolCallback {

        @Override
        public ToolDefinition getToolDefinition() {
            return ToolDefinition.builder()
                    .name("failing-tool")
                    .description("A tool that reports an execution error")
                    .inputSchema("{\"type\":\"object\"}")
                    .build();
        }

        @Override
        public String call(String toolInput) {
            throw new ToolExecutionException(getToolDefinition(),
                    new IllegalArgumentException("intentional tool error"));
        }

    }

}
