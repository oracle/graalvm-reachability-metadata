/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ai.spring_ai_autoconfigure_model_tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.micrometer.common.KeyValues;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
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
import org.springframework.ai.tool.observation.ToolCallingContentObservationFilter;
import org.springframework.ai.tool.observation.ToolCallingObservationContext;
import org.springframework.ai.tool.observation.ToolCallingObservationConvention;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class Spring_ai_autoconfigure_model_toolTest {
    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {}
            }
            """;

    @Test
    void autoConfigurationCreatesDefaultInfrastructureAndBindsProperties() {
        try (AnnotationConfigApplicationContext context = applicationContext(Map.of(), ctx -> {
        })) {
            ToolCallingProperties properties = context.getBean(ToolCallingProperties.class);

            assertThat(context.getBean(ToolCallbackResolver.class)).isNotNull();
            assertThat(context.getBean(ToolCallingManager.class)).isNotNull();
            assertThat(properties.isThrowExceptionOnError()).isFalse();
            assertThat(properties.getObservations().isIncludeContent()).isFalse();
            assertThat(context.getBeansOfType(ToolCallingContentObservationFilter.class)).isEmpty();
        }
    }

    @Test
    void customToolCallbackResolverBacksOffDefaultResolverAndIsUsedByManager() {
        ContextAwareToolCallback resolverTool = new ContextAwareToolCallback("resolverTool", "resolver-result");
        ContextAwareToolCallback beanTool = new ContextAwareToolCallback("beanTool", "bean-result");
        ToolCallbackResolver customResolver = name -> "resolverTool".equals(name) ? resolverTool : null;

        try (AnnotationConfigApplicationContext context = applicationContext(Map.of(), ctx -> {
            ctx.registerBean("customResolver", ToolCallbackResolver.class, () -> customResolver);
            ctx.registerBean("beanTool", ToolCallback.class, () -> beanTool);
        })) {
            ToolCallbackResolver resolver = context.getBean(ToolCallbackResolver.class);
            ToolCallingManager manager = context.getBean(ToolCallingManager.class);

            assertThat(resolver).isSameAs(customResolver);
            assertThat(resolver.resolve("resolverTool")).isSameAs(resolverTool);
            assertThat(resolver.resolve("beanTool")).isNull();

            ToolExecutionResult result = manager.executeToolCalls(
                    promptWithToolContext("requestId", "resolver-123"),
                    responseWithToolCall("tool-call-custom", "resolverTool", "{}"));

            ToolResponseMessage responseMessage = (ToolResponseMessage) result.conversationHistory().get(2);
            assertThat(responseMessage.getResponses()).singleElement().satisfies(response -> {
                assertThat(response.id()).isEqualTo("tool-call-custom");
                assertThat(response.name()).isEqualTo("resolverTool");
                assertThat(response.responseData()).isEqualTo("resolver-result:resolver-123:{}");
            });
        }
    }

    @Test
    void resolverCombinesToolCallbackBeansAndToolCallbackProvidersForManagerExecution() {
        ContextAwareToolCallback beanTool = new ContextAwareToolCallback("beanTool", "bean-result");
        ContextAwareToolCallback providerTool = new ContextAwareToolCallback("providerTool", "provider-result");

        try (AnnotationConfigApplicationContext context = applicationContext(Map.of(), ctx -> {
            ctx.registerBean("beanTool", ToolCallback.class, () -> beanTool);
            ctx.registerBean("provider", ToolCallbackProvider.class, () -> ToolCallbackProvider.from(providerTool));
        })) {
            ToolCallbackResolver resolver = context.getBean(ToolCallbackResolver.class);
            ToolCallingManager manager = context.getBean(ToolCallingManager.class);

            assertThat(resolver.resolve("beanTool")).isSameAs(beanTool);
            assertThat(resolver.resolve("providerTool")).isSameAs(providerTool);
            assertThat(resolver.resolve("missingTool")).isNull();

            ToolExecutionResult result = manager.executeToolCalls(
                    promptWithToolContext("requestId", "abc-123"),
                    responseWithToolCall("tool-call-1", "providerTool", "{\"city\":\"Prague\"}"));

            assertThat(result.returnDirect()).isFalse();
            assertThat(result.conversationHistory()).hasSize(3);
            Message lastMessage = result.conversationHistory().get(2);
            assertThat(lastMessage).isInstanceOf(ToolResponseMessage.class);
            ToolResponseMessage responseMessage = (ToolResponseMessage) lastMessage;
            assertThat(responseMessage.getResponses()).singleElement().satisfies(response -> {
                assertThat(response.id()).isEqualTo("tool-call-1");
                assertThat(response.name()).isEqualTo("providerTool");
                assertThat(response.responseData()).isEqualTo("provider-result:abc-123:{\"city\":\"Prague\"}");
            });
        }
    }

    @Test
    void includeContentPropertyEnablesObservationContentFilter() {
        try (AnnotationConfigApplicationContext context = applicationContext(
                Map.of("spring.ai.tools.observations.include-content", "true"), ctx -> {
                })) {
            ToolCallingProperties properties = context.getBean(ToolCallingProperties.class);

            assertThat(properties.getObservations().isIncludeContent()).isTrue();
            assertThat(context.getBean(ToolCallingContentObservationFilter.class)).isNotNull();
        }
    }

    @Test
    void customObservationRegistryAndConventionAreUsedForToolExecution() {
        ObservationRegistry observationRegistry = ObservationRegistry.create();
        CapturingObservationHandler observationHandler = new CapturingObservationHandler();
        observationRegistry.observationConfig().observationHandler(observationHandler);

        ContextAwareToolCallback observationTool = new ContextAwareToolCallback("observationTool", "observed-result");
        ToolCallingObservationConvention observationConvention = new CustomToolCallingObservationConvention();

        try (AnnotationConfigApplicationContext context = applicationContext(Map.of(), ctx -> {
            ctx.registerBean("observationRegistry", ObservationRegistry.class, () -> observationRegistry);
            ctx.registerBean("observationConvention", ToolCallingObservationConvention.class,
                    () -> observationConvention);
            ctx.registerBean("observationTool", ToolCallback.class, () -> observationTool);
        })) {
            ToolExecutionResult result = context.getBean(ToolCallingManager.class)
                .executeToolCalls(promptWithToolContext("requestId", "observed-456"),
                        responseWithToolCall("tool-call-observed", "observationTool", "{\"value\":42}"));

            ToolResponseMessage responseMessage = (ToolResponseMessage) result.conversationHistory().get(2);
            assertThat(responseMessage.getResponses()).singleElement().satisfies(response -> {
                assertThat(response.id()).isEqualTo("tool-call-observed");
                assertThat(response.name()).isEqualTo("observationTool");
                assertThat(response.responseData()).isEqualTo("observed-result:observed-456:{\"value\":42}");
            });

            ToolCallingObservationContext observationContext = observationHandler.singleStoppedContext();
            assertThat(observationContext.getName()).isEqualTo("custom.tool.observation");
            assertThat(observationContext.getContextualName()).isEqualTo("custom observationTool tool call");
            assertThat(observationContext.getToolDefinition().name()).isEqualTo("observationTool");
            assertThat(observationContext.getToolCallId()).isEqualTo("tool-call-observed");
            assertThat(observationContext.getToolCallArguments()).isEqualTo("{\"value\":42}");
            assertThat(observationContext.getToolCallResult()).isEqualTo("observed-result:observed-456:{\"value\":42}");
            assertThat(observationContext.getLowCardinalityKeyValue("custom.tool.name").getValue())
                .isEqualTo("observationTool");
            assertThat(observationContext.getHighCardinalityKeyValue("custom.tool.call.id").getValue())
                .isEqualTo("tool-call-observed");
        }
    }

    @Test
    void throwExceptionOnErrorPropertyControlsToolExecutionExceptionHandling() {
        try (AnnotationConfigApplicationContext context = applicationContext(Map.of(), ctx -> ctx.registerBean(
                "failingTool", ToolCallback.class, () -> new FailingToolCallback("failingTool")))) {
            ToolExecutionResult result = context.getBean(ToolCallingManager.class)
                .executeToolCalls(new Prompt("call the failing tool"),
                        responseWithToolCall("tool-call-2", "failingTool", "{}"));

            ToolResponseMessage responseMessage = (ToolResponseMessage) result.conversationHistory().get(2);
            assertThat(responseMessage.getResponses()).singleElement().satisfies(response -> {
                assertThat(response.name()).isEqualTo("failingTool");
                assertThat(response.responseData()).isEqualTo("boom from failingTool");
            });
        }

        try (AnnotationConfigApplicationContext context = applicationContext(
                Map.of("spring.ai.tools.throw-exception-on-error", "true"), ctx -> ctx.registerBean("failingTool",
                        ToolCallback.class, () -> new FailingToolCallback("failingTool")))) {
            ToolCallingManager manager = context.getBean(ToolCallingManager.class);
            ChatResponse response = responseWithToolCall("tool-call-3", "failingTool", "{}");

            assertThatExceptionOfType(ToolExecutionException.class)
                .isThrownBy(() -> manager.executeToolCalls(new Prompt("call the failing tool"), response))
                .withMessage("boom from failingTool")
                .withCauseInstanceOf(IllegalArgumentException.class);
        }
    }

    private static AnnotationConfigApplicationContext applicationContext(Map<String, Object> properties,
            Consumer<AnnotationConfigApplicationContext> customizer) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        if (!properties.isEmpty()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
        }
        customizer.accept(context);
        context.register(ToolCallingAutoConfiguration.class);
        context.refresh();
        return context;
    }

    private static Prompt promptWithToolContext(String key, Object value) {
        ToolCallingChatOptions options = ToolCallingChatOptions.builder().toolContext(key, value).build();
        return new Prompt("call the provider tool", options);
    }

    private static ChatResponse responseWithToolCall(String id, String name, String arguments) {
        AssistantMessage assistantMessage = AssistantMessage.builder()
            .content("")
            .toolCalls(List.of(new AssistantMessage.ToolCall(id, "function", name, arguments)))
            .build();
        return new ChatResponse(List.of(new Generation(assistantMessage)));
    }

    private static ToolDefinition toolDefinition(String name) {
        return ToolDefinition.builder().name(name).description("Test tool " + name).inputSchema(INPUT_SCHEMA).build();
    }

    private static final class ContextAwareToolCallback implements ToolCallback {
        private final ToolDefinition toolDefinition;

        private final String resultPrefix;

        private ContextAwareToolCallback(String name, String resultPrefix) {
            this.toolDefinition = toolDefinition(name);
            this.resultPrefix = resultPrefix;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return this.toolDefinition;
        }

        @Override
        public String call(String toolInput) {
            return this.resultPrefix + ":no-context:" + toolInput;
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            Object requestId = toolContext.getContext().get("requestId");
            return this.resultPrefix + ":" + requestId + ":" + toolInput;
        }
    }

    private static final class FailingToolCallback implements ToolCallback {
        private final ToolDefinition toolDefinition;

        private FailingToolCallback(String name) {
            this.toolDefinition = toolDefinition(name);
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return this.toolDefinition;
        }

        @Override
        public String call(String toolInput) {
            throw new ToolExecutionException(this.toolDefinition,
                    new IllegalArgumentException("boom from " + this.toolDefinition.name()));
        }
    }

    private static final class CustomToolCallingObservationConvention implements ToolCallingObservationConvention {
        @Override
        public String getName() {
            return "custom.tool.observation";
        }

        @Override
        public String getContextualName(ToolCallingObservationContext context) {
            return "custom " + context.getToolDefinition().name() + " tool call";
        }

        @Override
        public KeyValues getLowCardinalityKeyValues(ToolCallingObservationContext context) {
            return KeyValues.of("custom.tool.name", context.getToolDefinition().name());
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(ToolCallingObservationContext context) {
            return KeyValues.of("custom.tool.call.id", context.getToolCallId());
        }
    }

    private static final class CapturingObservationHandler
            implements ObservationHandler<ToolCallingObservationContext> {
        private final List<ToolCallingObservationContext> stoppedContexts = new ArrayList<>();

        @Override
        public void onStop(ToolCallingObservationContext context) {
            this.stoppedContexts.add(context);
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof ToolCallingObservationContext;
        }

        private ToolCallingObservationContext singleStoppedContext() {
            assertThat(this.stoppedContexts).singleElement();
            return this.stoppedContexts.get(0);
        }
    }
}
