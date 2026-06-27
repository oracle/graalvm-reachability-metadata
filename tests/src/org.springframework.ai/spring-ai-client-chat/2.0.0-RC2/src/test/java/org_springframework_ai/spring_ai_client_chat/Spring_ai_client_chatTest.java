/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ai.spring_ai_client_chat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.ParameterizedTypeReference;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_ai_client_chatTest {

    @Test
    void createClientCallsFakeModelWithPromptText() {
        RecordingChatModel chatModel = new RecordingChatModel("reply");
        ChatClient chatClient = ChatClient.create(chatModel);

        String content = chatClient.prompt("What is Spring AI?").call().content();

        assertThat(content).isEqualTo("reply: What is Spring AI?");
        assertThat(chatModel.prompts()).hasSize(1);
        Prompt capturedPrompt = chatModel.lastPrompt();
        assertThat(capturedPrompt.getUserMessage().getText()).isEqualTo("What is Spring AI?");
        assertThat(capturedPrompt.getUserMessage().getMessageType()).isEqualTo(MessageType.USER);
    }

    @Test
    void builderAppliesDefaultSystemTemplateAndRequestUserTemplate() {
        RecordingChatModel chatModel = new RecordingChatModel("templated");
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultSystem(system -> system.text("Answer as a {role}").param("role", "librarian"))
                .build();

        String content = chatClient.prompt()
                .user(user -> user.text("Find material about {topic}").param("topic", "native images"))
                .call()
                .content();

        assertThat(content).isEqualTo("templated: Find material about native images");
        Prompt capturedPrompt = chatModel.lastPrompt();
        assertThat(capturedPrompt.getSystemMessage().getText()).isEqualTo("Answer as a librarian");
        assertThat(capturedPrompt.getUserMessage().getText()).isEqualTo("Find material about native images");
    }

    @Test
    void promptMessagesAndOptionsReachTheChatModel() {
        RecordingChatModel chatModel = new RecordingChatModel("options");
        ChatClient chatClient = ChatClient.builder(chatModel).build();
        ChatOptions options = ChatOptions.builder()
                .model("in-test-model")
                .temperature(0.2)
                .maxTokens(64)
                .build();

        ChatResponse response = chatClient.prompt()
                .messages(new UserMessage("Return a short deterministic answer"))
                .options(options.mutate())
                .call()
                .chatResponse();

        assertThat(response.getResult().getOutput().getText())
                .isEqualTo("options: Return a short deterministic answer");
        Prompt capturedPrompt = chatModel.lastPrompt();
        assertThat(capturedPrompt.getOptions().getModel()).isEqualTo("in-test-model");
        assertThat(capturedPrompt.getOptions().getTemperature()).isEqualTo(0.2);
        assertThat(capturedPrompt.getOptions().getMaxTokens()).isEqualTo(64);
    }

    @Test
    void mutateCreatesClientWithAdditionalDefaultsWithoutChangingOriginal() {
        RecordingChatModel chatModel = new RecordingChatModel("mutated");
        ChatClient original = ChatClient.create(chatModel);
        ChatClient withDefaultSystem = original.mutate()
                .defaultSystem("Always mention reachability metadata")
                .build();

        original.prompt("original request").call().content();
        withDefaultSystem.prompt("mutated request").call().content();

        assertThat(chatModel.prompts()).hasSize(2);
        assertThat(chatModel.prompts().get(0).getSystemMessage().getText()).isEmpty();
        assertThat(chatModel.prompts().get(1).getSystemMessage().getText())
                .isEqualTo("Always mention reachability metadata");
    }

    @Test
    void callAdvisorCanTransformRequestAndResponseContext() {
        RecordingChatModel chatModel = new RecordingChatModel("advised");
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(new AppendingCallAdvisor())
                .build();

        ChatClientResponse response = chatClient.prompt()
                .advisors(advisor -> advisor.param("conversation", "test"))
                .user("base request")
                .call()
                .chatClientResponse();

        assertThat(response.chatResponse().getResult().getOutput().getText())
                .isEqualTo("advised: base request | advised");
        assertThat(response.context())
                .containsEntry("advisor.after", "appending-call-advisor")
                .containsEntry("conversation", "test");
        assertThat(chatModel.lastPrompt().getUserMessage().getText()).isEqualTo("base request | advised");
    }

    @Test
    void streamContentReturnsChunksFromStreamingChatModel() {
        RecordingChatModel chatModel = new RecordingChatModel("streamed");
        ChatClient chatClient = ChatClient.create(chatModel);

        List<String> chunks = chatClient.prompt("stream request")
                .stream()
                .content()
                .collectList()
                .block(Duration.ofSeconds(10));

        assertThat(chunks).containsExactly("streamed chunk 1", "streamed chunk 2");
        assertThat(chatModel.lastPrompt().getUserMessage().getText()).isEqualTo("stream request");
    }

    @Test
    void requestAndResponseBuildersSupportCopyAndMutation() {
        Prompt prompt = new Prompt("builder request");
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(prompt)
                .context("request.id", "r-1")
                .build();
        ChatClientRequest mutatedRequest = request.copy()
                .mutate()
                .context("request.stage", "copied")
                .build();
        ChatResponse chatResponse = response("builder response");
        ChatClientResponse response = ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .context(Map.of("response.id", "c-1"))
                .build();
        ChatClientResponse mutatedResponse = response.copy()
                .mutate()
                .context("response.stage", "copied")
                .build();

        assertThat(mutatedRequest.prompt()).isEqualTo(prompt);
        assertThat(mutatedRequest.context())
                .containsEntry("request.id", "r-1")
                .containsEntry("request.stage", "copied");
        assertThat(mutatedResponse.chatResponse()).isEqualTo(chatResponse);
        assertThat(mutatedResponse.context())
                .containsEntry("response.id", "c-1")
                .containsEntry("response.stage", "copied");
    }

    @Test
    void chatResponseMetadataAndGenerationMetadataRemainAvailableThroughClient() {
        RecordingChatModel chatModel = new RecordingChatModel("metadata");
        ChatClient chatClient = ChatClient.create(chatModel);

        ChatResponse response = chatClient.prompt("include metadata").call().chatResponse();

        assertThat(response.getMetadata().getId()).isEqualTo("response-metadata-id");
        assertThat(response.getMetadata().getModel()).isEqualTo("recording-chat-model");
        assertThat(response.getResult().getMetadata().getFinishReason()).isEqualTo("stop");
        assertThat(response.hasFinishReasons(Set.of("stop"))).isTrue();
    }

    @Test
    void callEntityConvertsStructuredJsonResponseIntoParameterizedCollection() {
        FixedResponseChatModel chatModel = new FixedResponseChatModel("""
                ["metadata", "native-image"]
                """);
        ChatClient chatClient = ChatClient.create(chatModel);
        ParameterizedTypeReference<List<String>> listOfStrings = new ParameterizedTypeReference<>() {
        };

        List<String> entity = chatClient.prompt("Return two tags")
                .call()
                .entity(listOfStrings);
        ResponseEntity<ChatResponse, List<String>> responseEntity = chatClient.prompt("Return two more tags")
                .call()
                .responseEntity(listOfStrings);

        assertThat(entity).containsExactly("metadata", "native-image");
        assertThat(responseEntity.getEntity()).containsExactly("metadata", "native-image");
        assertThat(responseEntity.getResponse().getResult().getOutput().getText())
                .contains("metadata", "native-image");
        assertThat(chatModel.prompts()).hasSize(2);
        assertThat(chatModel.prompts().get(0).getUserMessage().getText())
                .contains("Return two tags", "JSON");
        assertThat(chatModel.prompts().get(1).getUserMessage().getText())
                .contains("Return two more tags", "JSON");
    }

    private static ChatResponse response(String content) {
        ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.builder()
                .finishReason("stop")
                .metadata("source", "fake-model")
                .build();
        ChatResponseMetadata responseMetadata = ChatResponseMetadata.builder()
                .id("response-metadata-id")
                .model("recording-chat-model")
                .keyValue("test", "spring-ai-client-chat")
                .build();
        Generation generation = new Generation(new AssistantMessage(content), generationMetadata);
        return new ChatResponse(List.of(generation), responseMetadata);
    }

    private static final class RecordingChatModel implements ChatModel {

        private final String prefix;

        private final List<Prompt> prompts = new ArrayList<>();

        private RecordingChatModel(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            this.prompts.add(prompt);
            return response(this.prefix + ": " + lastUserText(prompt));
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            this.prompts.add(prompt);
            return Flux.just(response(this.prefix + " chunk 1"), response(this.prefix + " chunk 2"));
        }

        private List<Prompt> prompts() {
            return this.prompts;
        }

        private Prompt lastPrompt() {
            assertThat(this.prompts).isNotEmpty();
            return this.prompts.get(this.prompts.size() - 1);
        }

        private static String lastUserText(Prompt prompt) {
            UserMessage userMessage = prompt.getUserMessage();
            if (userMessage != null) {
                return userMessage.getText();
            }
            List<Message> instructions = prompt.getInstructions();
            assertThat(instructions).isNotEmpty();
            return instructions.get(instructions.size() - 1).getText();
        }
    }

    private static final class FixedResponseChatModel implements ChatModel {

        private final String content;

        private final List<Prompt> prompts = new ArrayList<>();

        private FixedResponseChatModel(String content) {
            this.content = content;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            this.prompts.add(prompt);
            return response(this.content);
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            this.prompts.add(prompt);
            return Flux.just(response(this.content));
        }

        private List<Prompt> prompts() {
            return this.prompts;
        }
    }

    private static final class AppendingCallAdvisor implements CallAdvisor {

        @Override
        public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
            Prompt advisedPrompt = request.prompt().augmentUserMessage(user -> UserMessage.builder()
                    .text(user.getText() + " | advised")
                    .metadata(user.getMetadata())
                    .build());
            Map<String, Object> requestContext = new LinkedHashMap<>(request.context());
            requestContext.put("advisor.before", getName());
            ChatClientRequest advisedRequest = request.mutate()
                    .prompt(advisedPrompt)
                    .context(requestContext)
                    .build();
            ChatClientResponse response = chain.nextCall(advisedRequest);
            Map<String, Object> responseContext = new LinkedHashMap<>(advisedRequest.context());
            responseContext.putAll(response.context());
            responseContext.put("advisor.after", getName());
            return response.mutate().context(responseContext).build();
        }

        @Override
        public String getName() {
            return "appending-call-advisor";
        }

        @Override
        public int getOrder() {
            return 0;
        }
    }
}
