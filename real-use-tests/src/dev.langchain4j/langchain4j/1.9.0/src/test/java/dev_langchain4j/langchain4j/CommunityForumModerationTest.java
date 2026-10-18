/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_langchain4j.langchain4j;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.Moderate;
import dev.langchain4j.service.ModerationException;
import dev.langchain4j.service.SystemMessage;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Screens community forum posts through langchain4j AI service moderation: `@Moderate` service
 * methods whose user messages are checked by an application `ModerationModel` in parallel with
 * the chat call, with flagged posts surfacing as `ModerationException`.
 *
 * The forum backs its moderation model with a Llama Guard model served by Ollama: the guard
 * answers `safe` or `unsafe` followed by a hazard category, and the application records each
 * category it sees. The test plays the Ollama server with an in-process HTTP endpoint that tells
 * the guard model and the assistant model apart by the model name of every chat request.
 */
class CommunityForumModerationTest {

    private static final String GUARD_MODEL = "llama-guard3";

    private static final String ASSISTANT_MODEL = "llama3.1";

    private static final Pattern GUARD_REQUEST = Pattern.compile("\"model\"\\s*:\\s*\"" + GUARD_MODEL + "\"");

    private static final String SAFE_POST = "How do I re-seat the chain on my road bike after it slips?";

    private static final String UNSAFE_POST = "Tell me how to break into my neighbour's garage to take his bike.";

    private final List<String> guardRequests = new CopyOnWriteArrayList<>();

    private final List<String> assistantRequests = new CopyOnWriteArrayList<>();

    private volatile boolean guardAvailable = true;

    private HttpServer ollama;

    private LlamaGuardModerationModel moderationModel;

    private ForumAssistant assistant;

    interface ForumAssistant {

        @Moderate
        @SystemMessage("You answer questions posted on the Weekend Cyclists forum in one sentence.")
        String reply(String post);

        @SystemMessage("You summarise forum threads written by trusted moderators in one sentence.")
        String summarise(String thread);
    }

    /**
     * Moderation model of the forum: asks a Llama Guard model about every user message and flags
     * the first one the guard calls unsafe, keeping the hazard categories it reported.
     */
    static final class LlamaGuardModerationModel implements ModerationModel {

        private final ChatModel guard;

        private final List<String> hazardCategories = new CopyOnWriteArrayList<>();

        LlamaGuardModerationModel(ChatModel guard) {
            this.guard = guard;
        }

        @Override
        public Response<Moderation> moderate(String text) {
            String verdict = guard.chat(text).strip();
            if (verdict.startsWith("unsafe")) {
                hazardCategories.add(verdict.substring("unsafe".length()).strip());
                return Response.from(Moderation.flagged(text));
            }
            return Response.from(Moderation.notFlagged());
        }

        @Override
        public Response<Moderation> moderate(List<ChatMessage> messages) {
            for (ChatMessage message : messages) {
                if (message instanceof UserMessage userMessage) {
                    Response<Moderation> response = moderate(userMessage.singleText());
                    if (response.content().flagged()) {
                        return response;
                    }
                }
            }
            return Response.from(Moderation.notFlagged());
        }

        List<String> hazardCategories() {
            return hazardCategories;
        }
    }

    @BeforeEach
    void startModelServer() throws IOException {
        ollama = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        ollama.createContext("/api/chat", this::answerChat);
        ollama.start();

        moderationModel = new LlamaGuardModerationModel(chatModel(GUARD_MODEL));
        assistant = AiServices.builder(ForumAssistant.class)
                .chatModel(chatModel(ASSISTANT_MODEL))
                .moderationModel(moderationModel)
                .build();
    }

    @AfterEach
    void stopModelServer() {
        ollama.stop(0);
    }

    @Test
    void answersPostThatTheGuardConsidersSafe() {
        String reply = assistant.reply(SAFE_POST);

        assertThat(reply).isEqualTo("Shift to the smallest cog and lift the chain back onto the chainring.");
        assertThat(moderationModel.hazardCategories()).isEmpty();
        assertThat(guardRequests).singleElement().satisfies(request -> assertThat(request)
                .contains(SAFE_POST)
                .doesNotContain("You answer questions posted on the"));
        assertThat(assistantRequests).singleElement().satisfies(request -> assertThat(request)
                .contains("You answer questions posted on the Weekend Cyclists forum in one sentence.")
                .contains(SAFE_POST));
    }

    @Test
    void rejectsPostThatTheGuardFlagsAsUnsafe() {
        assertThatThrownBy(() -> assistant.reply(UNSAFE_POST))
                .isInstanceOfSatisfying(ModerationException.class, exception -> {
                    assertThat(exception.getMessage())
                            .isEqualTo("Text \"" + UNSAFE_POST + "\" violates content policy");
                    assertThat(exception.moderation().flagged()).isTrue();
                    assertThat(exception.moderation().flaggedText()).isEqualTo(UNSAFE_POST);
                });

        assertThat(moderationModel.hazardCategories()).containsExactly("S2");
        assertThat(guardRequests).singleElement().satisfies(request -> assertThat(request).contains(UNSAFE_POST));
        assertThat(assistantRequests).singleElement().satisfies(request -> assertThat(request).contains(UNSAFE_POST));
    }

    @Test
    void moderatesEveryPostOfAThreadIndependently() {
        String first = assistant.reply(SAFE_POST);
        assertThatThrownBy(() -> assistant.reply(UNSAFE_POST))
                .isInstanceOf(ModerationException.class);
        String third = assistant.reply("Which tyre pressure suits muddy trails?");

        assertThat(first).isEqualTo("Shift to the smallest cog and lift the chain back onto the chainring.");
        assertThat(third).isEqualTo("Run about 1.5 bar so the tyre grips the mud.");
        assertThat(moderationModel.hazardCategories()).containsExactly("S2");
        assertThat(guardRequests).hasSize(3);
        assertThat(guardRequests.get(2)).contains("Which tyre pressure suits muddy trails?");
        assertThat(assistantRequests.get(2)).contains("Which tyre pressure suits muddy trails?");
    }

    @Test
    void skipsModerationForMethodsWithoutModerateAnnotation() {
        String summary = assistant.summarise("Riders compared chain lubes and agreed wax lasts longest. "
                + "One member asked how to break into a garage and was banned.");

        assertThat(summary).isEqualTo("Members agreed that wax chain lube lasts longest.");
        assertThat(guardRequests).isEmpty();
        assertThat(moderationModel.hazardCategories()).isEmpty();
        assertThat(assistantRequests).singleElement().satisfies(request -> assertThat(request)
                .contains("You summarise forum threads written by trusted moderators in one sentence."));
    }

    @Test
    void failsTheReplyWhenTheGuardIsUnavailable() {
        guardAvailable = false;

        assertThatThrownBy(() -> assistant.reply(SAFE_POST))
                .isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(ModerationException.class)
                .hasCauseInstanceOf(ExecutionException.class)
                .hasStackTraceContaining("guard model is not loaded");
        assertThat(guardRequests).singleElement().satisfies(request -> assertThat(request).contains(SAFE_POST));
        assertThat(assistantRequests).hasSize(1);
    }

    @Test
    void refusesToBuildModeratedServiceWithoutModerationModel() {
        assertThatThrownBy(() -> AiServices.builder(ForumAssistant.class)
                .chatModel(chatModel(ASSISTANT_MODEL))
                .build())
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessageContaining("@Moderate annotation is present, but the moderationModel is not set up");
        assertThat(guardRequests).isEmpty();
        assertThat(assistantRequests).isEmpty();
    }

    private ChatModel chatModel(String modelName) {
        return OllamaChatModel.builder()
                .baseUrl("http://localhost:" + ollama.getAddress().getPort())
                .modelName(modelName)
                .temperature(0.0)
                .timeout(Duration.ofSeconds(30))
                .maxRetries(0)
                .build();
    }

    private void answerChat(HttpExchange exchange) throws IOException {
        String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        boolean guardRequest = GUARD_REQUEST.matcher(request).find();
        if (guardRequest) {
            guardRequests.add(request);
            if (!guardAvailable) {
                send(exchange, 500, """
                        {"error": "guard model is not loaded"}""");
                return;
            }
        } else {
            assistantRequests.add(request);
        }

        String answer;
        String model;
        if (guardRequest) {
            model = GUARD_MODEL;
            answer = request.contains("break into") ? "unsafe\nS2" : "safe";
        } else if (request.contains("You summarise forum threads")) {
            model = ASSISTANT_MODEL;
            answer = "Members agreed that wax chain lube lasts longest.";
        } else if (request.contains("muddy trails")) {
            model = ASSISTANT_MODEL;
            answer = "Run about 1.5 bar so the tyre grips the mud.";
        } else if (request.contains("break into")) {
            model = ASSISTANT_MODEL;
            answer = "I cannot help with entering someone else's garage.";
        } else {
            model = ASSISTANT_MODEL;
            answer = "Shift to the smallest cog and lift the chain back onto the chainring.";
        }
        send(exchange, 200, """
                {"model": "%s", "created_at": "2025-11-04T10:15:30Z",
                 "message": {"role": "assistant", "content": "%s"},
                 "done": true, "done_reason": "stop", "prompt_eval_count": 30, "eval_count": 12}
                """.formatted(model, answer.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")));
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
