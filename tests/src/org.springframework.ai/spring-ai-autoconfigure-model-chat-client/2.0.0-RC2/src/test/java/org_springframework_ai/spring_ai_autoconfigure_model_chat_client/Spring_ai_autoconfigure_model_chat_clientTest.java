/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ai.spring_ai_autoconfigure_model_chat_client;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientBuilderProperties;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityChecker;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_ai_autoconfigure_model_chat_clientTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ChatClientAutoConfiguration.class))
            .withUserConfiguration(ChatModelConfiguration.class);

    @Test
    void createsChatClientBuilderAndBindsToolCallingProperties() {
        this.contextRunner.withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class))
                .withPropertyValues("spring.ai.chat.client.tool-calling.advisor-order=42",
                        "spring.ai.chat.client.tool-calling.enabled=false",
                        "spring.ai.chat.client.tool-calling.stream-tool-call-responses=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ChatClient.Builder.class);
                    assertThat(context).hasSingleBean(ChatClientBuilderProperties.class);
                    assertThat(context).hasSingleBean(ToolCallingManager.class);
                    assertThat(context).hasSingleBean(ToolCallingAdvisor.Builder.class);

                    ChatClientBuilderProperties properties = context.getBean(ChatClientBuilderProperties.class);
                    assertThat(properties.getToolCalling().isEnabled()).isFalse();
                    assertThat(properties.getToolCalling().getAdvisorOrder()).isEqualTo(42);
                    assertThat(properties.getToolCalling().isStreamToolCallResponses()).isTrue();

                    ToolCallingAdvisor advisor = context.getBean(ToolCallingAdvisor.Builder.class).build();
                    assertThat(advisor.getOrder()).isEqualTo(42);
                });
    }

    @Test
    void toolCallingStreamResponsesPropertyChangesAdvisorStreamingBehavior() {
        ApplicationContextRunner runner = this.contextRunner
                .withBean(ToolCallingManager.class, RecordingToolCallingManager::new)
                .withBean(ToolExecutionEligibilityChecker.class,
                        () -> response -> "model requested a tool".equals(responseText(response)));

        runner.withPropertyValues("spring.ai.chat.client.tool-calling.stream-tool-call-responses=true")
                .run(context -> {
                    ToolCallingAdvisor advisor = context.getBean(ToolCallingAdvisor.Builder.class).build();
                    RecordingToolCallingManager toolCallingManager = (RecordingToolCallingManager) context
                            .getBean(ToolCallingManager.class);

                    List<String> responses = advisor.adviseStream(toolRequest(), new SingleResponseStreamAdvisorChain())
                            .map(response -> response.chatResponse().getResult().getOutput().getText())
                            .collectList()
                            .block(Duration.ofSeconds(10));

                    assertThat(responses).containsExactly("model requested a tool", "tool result");
                    assertThat(toolCallingManager.executions).hasValue(1);
                });

        runner.withPropertyValues("spring.ai.chat.client.tool-calling.stream-tool-call-responses=false")
                .run(context -> {
                    ToolCallingAdvisor advisor = context.getBean(ToolCallingAdvisor.Builder.class).build();

                    List<String> responses = advisor.adviseStream(toolRequest(), new SingleResponseStreamAdvisorChain())
                            .map(response -> response.chatResponse().getResult().getOutput().getText())
                            .collectList()
                            .block(Duration.ofSeconds(10));

                    assertThat(responses).containsExactly("tool result");
                });
    }

    @Test
    void backsOffToolCallingAdvisorBuilderWhenNoToolCallingManagerIsAvailable() {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ChatClient.Builder.class);
            assertThat(context).doesNotHaveBean(ToolCallingManager.class);
            assertThat(context).doesNotHaveBean(ToolCallingAdvisor.Builder.class);
        });
    }

    @Test
    void usesUserProvidedToolCallingAdvisorBuilderInsteadOfCreatingAnotherOne() {
        ToolCallingAdvisor.Builder<?> customBuilder = ToolCallingAdvisor.builder().advisorOrder(77);

        this.contextRunner.withBean(ToolCallingAdvisor.Builder.class, () -> customBuilder).run(context -> {
            assertThat(context).hasSingleBean(ToolCallingAdvisor.Builder.class);
            assertThat(context.getBean(ToolCallingAdvisor.Builder.class)).isSameAs(customBuilder);
            assertThat(context.getBean(ToolCallingAdvisor.Builder.class).build().getOrder()).isEqualTo(77);
            assertThat(context).hasSingleBean(ChatClient.Builder.class);
        });
    }

    private static ChatClientRequest toolRequest() {
        return ChatClientRequest.builder()
                .prompt(new Prompt("run the local tool", ToolCallingChatOptions.builder().build()))
                .context(Map.of())
                .build();
    }

    private static ChatClientResponse response(String content) {
        return ChatClientResponse.builder()
                .chatResponse(new ChatResponse(List.of(new Generation(new AssistantMessage(content)))))
                .context(Map.of())
                .build();
    }

    private static String responseText(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
    }

    public static class ChatModelConfiguration {
        @Bean
        public ChatModel chatModel() {
            return prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))));
        }
    }

    public static class RecordingToolCallingManager implements ToolCallingManager {
        private final AtomicInteger executions = new AtomicInteger();

        @Override
        public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions options) {
            return List.of();
        }

        @Override
        public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
            this.executions.incrementAndGet();
            return ToolExecutionResult.builder()
                    .conversationHistory(List.of(ToolResponseMessage.builder()
                            .responses(List.of(
                                    new ToolResponseMessage.ToolResponse("tool-id", "tool-name", "tool result")))
                            .build()))
                    .returnDirect(true)
                    .build();
        }
    }

    public static class SingleResponseStreamAdvisorChain implements StreamAdvisorChain {
        @Override
        public Flux<ChatClientResponse> nextStream(ChatClientRequest chatClientRequest) {
            return Flux.just(response("model requested a tool"));
        }

        @Override
        public List<StreamAdvisor> getStreamAdvisors() {
            return List.of();
        }

        @Override
        public StreamAdvisorChain copy(StreamAdvisor advisor) {
            return this;
        }
    }
}
