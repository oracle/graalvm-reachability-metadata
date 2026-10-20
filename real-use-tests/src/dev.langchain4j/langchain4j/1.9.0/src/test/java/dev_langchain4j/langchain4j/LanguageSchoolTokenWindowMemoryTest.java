/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_langchain4j.langchain4j;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dev.langchain4j.data.message.ChatMessageType.AI;
import static dev.langchain4j.data.message.ChatMessageType.SYSTEM;
import static dev.langchain4j.data.message.ChatMessageType.TOOL_EXECUTION_RESULT;
import static dev.langchain4j.data.message.ChatMessageType.USER;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bounds each student's tutoring conversation through langchain4j token-window chat memory: a
 * `TokenWindowChatMemory` per `@MemoryId` whose token budget follows the student's plan, which
 * evicts the oldest messages once an application `TokenCountEstimator` counts more tokens than
 * the budget allows, while keeping the system message and evicting tool calls with their results.
 *
 * The language school sells a free and a premium plan; the budget is looked up on every message,
 * so a student who upgrades mid-conversation stops losing history. The estimator counts words.
 * The test plays the Ollama server: it answers "What did I ask first?" from the oldest user
 * message it was sent and calls the conjugation tool when asked to conjugate, so every answer
 * shows which part of the conversation still fits the budget.
 */
class LanguageSchoolTokenWindowMemoryTest {

    private static final Pattern ROLE = Pattern.compile("\"role\"\\s*:\\s*\"(\\w+)\"");

    private static final Pattern USER_MESSAGE = Pattern.compile("\\{[^{}]*\"role\"\\s*:\\s*\"user\"[^{}]*}");

    private static final Pattern TOOL_MESSAGE = Pattern.compile("\\{[^{}]*\"role\"\\s*:\\s*\"tool\"[^{}]*}");

    private static final Pattern TOOL_CALL = Pattern.compile("\"tool_calls\"\\s*:\\s*\\[\\s*\\{");

    private static final Pattern CONTENT = Pattern.compile("\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private static final String FIRST_QUESTION = "What did I ask first?";

    private static final int FREE_PLAN_TOKENS = 32;

    private static final int PREMIUM_PLAN_TOKENS = 120;

    private final List<String> chatRequests = new CopyOnWriteArrayList<>();

    private final Map<String, Integer> planTokens = new ConcurrentHashMap<>();

    private final InMemoryChatMemoryStore store = new InMemoryChatMemoryStore();

    private final WordCountEstimator estimator = new WordCountEstimator();

    private final ConjugationTools conjugationTools = new ConjugationTools();

    private HttpServer ollama;

    private Tutor tutor;

    interface Tutor extends ChatMemoryAccess {

        String chat(@MemoryId String student, @dev.langchain4j.service.UserMessage String message);
    }

    /// Counts one token per word, one per message, and a flat five per requested tool call.
    static final class WordCountEstimator implements TokenCountEstimator {

        @Override
        public int estimateTokenCountInText(String text) {
            return text == null || text.isBlank() ? 0 : text.trim().split("\\s+").length;
        }

        @Override
        public int estimateTokenCountInMessage(ChatMessage message) {
            int words = switch (message) {
                case SystemMessage system -> estimateTokenCountInText(system.text());
                case UserMessage user -> estimateTokenCountInText(user.singleText());
                case AiMessage ai -> estimateTokenCountInText(ai.text()) + 5 * ai.toolExecutionRequests().size();
                case ToolExecutionResultMessage result -> estimateTokenCountInText(result.text());
                default -> throw new IllegalArgumentException("Unexpected message " + message);
            };
            return 1 + words;
        }

        @Override
        public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
            int tokens = 0;
            for (ChatMessage message : messages) {
                tokens += estimateTokenCountInMessage(message);
            }
            return tokens;
        }
    }

    /** Grammar helpers the tutor offers to the model. */
    static final class ConjugationTools {

        private final List<String> calls = new CopyOnWriteArrayList<>();

        @Tool("Conjugates a regular Spanish -ar verb in the first person singular present tense")
        String firstPersonPresent(@P("the verb in the infinitive, for example hablar") String verb) {
            calls.add(verb);
            return verb.substring(0, verb.length() - 2) + "o";
        }
    }

    @BeforeEach
    void startModelServer() throws IOException {
        ollama = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        ollama.createContext("/api/chat", this::answerChat);
        ollama.start();

        ChatModel model = OllamaChatModel.builder()
                .baseUrl("http://localhost:" + ollama.getAddress().getPort())
                .modelName("llama3.1")
                .temperature(0.0)
                .timeout(Duration.ofSeconds(30))
                .maxRetries(0)
                .build();
        ChatMemoryProvider memoryPerStudent = student -> TokenWindowChatMemory.builder()
                .id(student)
                .dynamicMaxTokens(id -> planTokens.get(id), estimator)
                .chatMemoryStore(store)
                .build();
        tutor = AiServices.builder(Tutor.class)
                .chatModel(model)
                .systemMessageProvider(student -> "You are a patient Spanish tutor.")
                .chatMemoryProvider(memoryPerStudent)
                .tools(conjugationTools)
                .build();
    }

    @AfterEach
    void stopModelServer() {
        ollama.stop(0);
    }

    @Test
    void keepsEachStudentWithinTheTokenBudgetOfTheirPlan() {
        planTokens.put("lucia", FREE_PLAN_TOKENS);
        planTokens.put("marco", PREMIUM_PLAN_TOKENS);

        for (String student : List.of("lucia", "marco")) {
            assertThat(tutor.chat(student, "I want to practice the past tense.")).isEqualTo("Got it.");
            assertThat(tutor.chat(student, "Yesterday I visited my grandmother in Seville.")).isEqualTo("Got it.");
        }

        // Lucia's third message overflows 32 tokens, so her first question is gone before the model sees it.
        assertThat(tutor.chat("lucia", FIRST_QUESTION))
                .isEqualTo("Your first question was: Yesterday I visited my grandmother in Seville.");
        assertThat(lastRequest()).contains("patient Spanish tutor").doesNotContain("past tense");
        assertThat(tutor.chat("marco", FIRST_QUESTION))
                .isEqualTo("Your first question was: I want to practice the past tense.");

        // The long answer evicts again; the system message always stays first.
        ChatMemory lucia = tutor.getChatMemory("lucia");
        assertThat(lucia.messages()).extracting(ChatMessage::type).containsExactly(SYSTEM, AI, USER, AI);
        assertThat(estimator.estimateTokenCountInMessages(lucia.messages())).isEqualTo(28);
        assertThat(store.getMessages("lucia")).isEqualTo(lucia.messages());

        ChatMemory marco = tutor.getChatMemory("marco");
        assertThat(marco.messages()).extracting(ChatMessage::type)
                .containsExactly(SYSTEM, USER, AI, USER, AI, USER, AI);
        assertThat(estimator.estimateTokenCountInMessages(marco.messages())).isEqualTo(47);

        // After the upgrade the budget read on the next message is the premium one, so nothing is evicted.
        planTokens.put("lucia", PREMIUM_PLAN_TOKENS);
        assertThat(tutor.chat("lucia", "Now let us try the future tense.")).isEqualTo("Got it.");
        assertThat(tutor.chat("lucia", FIRST_QUESTION)).isEqualTo("Your first question was: What did I ask first?");
        assertThat(lucia.messages()).extracting(ChatMessage::type)
                .containsExactly(SYSTEM, AI, USER, AI, USER, AI, USER, AI);
        assertThat(store.getMessages("lucia")).isEqualTo(lucia.messages());

        assertThat(chatRequests).hasSize(8);
    }

    @Test
    void evictsAToolCallTogetherWithItsResult() {
        planTokens.put("noah", FREE_PLAN_TOKENS);

        assertThat(tutor.chat("noah", "Conjugate hablar.")).isEqualTo("The first person is hablo.");
        assertThat(conjugationTools.calls).containsExactly("hablar");
        ChatMemory noah = tutor.getChatMemory("noah");
        assertThat(noah.messages()).extracting(ChatMessage::type)
                .containsExactly(SYSTEM, USER, AI, TOOL_EXECUTION_RESULT, AI);
        assertThat(estimator.estimateTokenCountInMessages(noah.messages())).isEqualTo(24);

        // The second answer overflows the budget: only the question goes, the tool call and its result stay.
        assertThat(tutor.chat("noah", "Give me an example sentence with it.")).isEqualTo("Got it.");
        assertThat(noah.messages()).extracting(ChatMessage::type)
                .containsExactly(SYSTEM, AI, TOOL_EXECUTION_RESULT, AI, USER, AI);
        assertThat(((AiMessage) noah.messages().get(1)).toolExecutionRequests())
                .extracting(ToolExecutionRequest::name)
                .containsExactly("firstPersonPresent");

        // Evicting the tool call takes its result along, so the model never sees an orphaned tool message.
        assertThat(tutor.chat("noah", "And the past tense?")).isEqualTo("Got it.");
        assertThat(lastRequest()).doesNotContainPattern(TOOL_MESSAGE).doesNotContainPattern(TOOL_CALL);
        assertThat(noah.messages()).extracting(ChatMessage::type)
                .containsExactly(SYSTEM, AI, USER, AI, USER, AI);
        assertThat(((AiMessage) noah.messages().get(1)).text()).isEqualTo("The first person is hablo.");
        assertThat(estimator.estimateTokenCountInMessages(noah.messages())).isLessThanOrEqualTo(FREE_PLAN_TOKENS);
        assertThat(store.getMessages("noah")).isEqualTo(noah.messages());

        assertThat(chatRequests).hasSize(4);
    }

    private String lastRequest() {
        return chatRequests.get(chatRequests.size() - 1);
    }

    private void answerChat(HttpExchange exchange) throws IOException {
        String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        chatRequests.add(request);

        List<String> roles = ROLE.matcher(request).results().map(role -> role.group(1)).toList();
        List<String> userMessages = contents(USER_MESSAGE, request);
        assertThat(userMessages).as("user messages of chat request %s", request).isNotEmpty();
        String question = userMessages.get(userMessages.size() - 1);

        String message;
        if (roles.get(roles.size() - 1).equals("tool")) {
            List<String> toolResults = contents(TOOL_MESSAGE, request);
            message = assistant("The first person is " + toolResults.get(toolResults.size() - 1) + ".");
        } else if (question.startsWith("Conjugate ")) {
            String verb = question.substring("Conjugate ".length(), question.length() - 1);
            message = """
                    {"role": "assistant", "content": "", "tool_calls": [
                      {"function": {"name": "firstPersonPresent", "arguments": {"%s": "%s"}}}
                    ]}""".formatted(verbParameter(request), verb);
        } else if (question.equals(FIRST_QUESTION)) {
            message = assistant("Your first question was: " + userMessages.get(0));
        } else {
            message = assistant("Got it.");
        }
        String response = """
                {"model": "llama3.1", "created_at": "2025-11-04T10:15:30Z", "message": %s,
                 "done": true, "done_reason": "stop", "prompt_eval_count": 40, "eval_count": 8}
                """.formatted(message);

        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(bytes);
        }
    }

    private static String assistant(String text) {
        return """
                {"role": "assistant", "content": "%s"}""".formatted(text);
    }

    /// Reads the contents of the flat messages the pattern matches, in request order.
    private static List<String> contents(Pattern messagePattern, String request) {
        return messagePattern.matcher(request).results()
                .map(message -> CONTENT.matcher(message.group()))
                .filter(Matcher::find)
                .map(content -> content.group(1))
                .toList();
    }

    /// Reads the parameter name the conjugation tool schema advertises, as a real model would.
    private static String verbParameter(String request) {
        Matcher tool = Pattern.compile(
                "\"name\"\\s*:\\s*\"firstPersonPresent\".*?\"required\"\\s*:\\s*\\[\\s*\"([^\"]+)\"", Pattern.DOTALL)
                .matcher(request);
        assertThat(tool.find()).as("schema of the conjugation tool in %s", request).isTrue();
        return tool.group(1);
    }
}
