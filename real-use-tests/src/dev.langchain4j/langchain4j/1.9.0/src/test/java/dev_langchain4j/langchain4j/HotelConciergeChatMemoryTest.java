/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_langchain4j.langchain4j;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dev.langchain4j.data.message.ChatMessageType.AI;
import static dev.langchain4j.data.message.ChatMessageType.SYSTEM;
import static dev.langchain4j.data.message.ChatMessageType.USER;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps one conversation per hotel guest through langchain4j chat memory: a `ChatMemoryProvider`
 * that hands each `@MemoryId` its own message-window memory, a file-backed `ChatMemoryStore` that
 * survives a restart, and `ChatMemoryAccess` to inspect, evict, and clear a guest's history.
 *
 * The concierge remembers what each guest said, but only the last few messages, and writes every
 * conversation to a JSON file per guest so a restarted service picks the conversation up again.
 * The test plays the Ollama server: it answers "What is my name?" from the name the guest gave
 * earlier in the same request, so every answer shows which history the model was sent.
 */
class HotelConciergeChatMemoryTest {

    private static final Pattern USER_MESSAGE = Pattern.compile("\\{[^{}]*\"role\"\\s*:\\s*\"user\"[^{}]*}");

    private static final Pattern CONTENT = Pattern.compile("\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private static final Pattern GIVEN_NAME = Pattern.compile("My name is (\\w+)\\.");

    private static final String NAME_QUESTION = "What is my name?";

    private final List<String> chatRequests = new CopyOnWriteArrayList<>();

    @TempDir
    Path conversationsDir;

    private HttpServer ollama;

    private ChatModel model;

    interface Concierge extends ChatMemoryAccess {

        @SystemMessage("You are the concierge of Hotel Aurora. Keep answers short.")
        String chat(@MemoryId String guestId, @UserMessage String message);
    }

    /** Keeps each guest's conversation as a JSON file named after the guest. */
    static final class JsonFileChatMemoryStore implements ChatMemoryStore {

        private final Path directory;

        JsonFileChatMemoryStore(Path directory) {
            this.directory = directory;
        }

        Path fileOf(Object guestId) {
            return directory.resolve(guestId + ".json");
        }

        @Override
        public List<ChatMessage> getMessages(Object guestId) {
            Path file = fileOf(guestId);
            if (!Files.exists(file)) {
                return List.of();
            }
            try {
                return ChatMessageDeserializer.messagesFromJson(Files.readString(file));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void updateMessages(Object guestId, List<ChatMessage> messages) {
            try {
                Files.writeString(fileOf(guestId), ChatMessageSerializer.messagesToJson(messages));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void deleteMessages(Object guestId) {
            try {
                Files.deleteIfExists(fileOf(guestId));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @BeforeEach
    void startModelServer() throws IOException {
        ollama = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        ollama.createContext("/api/chat", this::answerChat);
        ollama.start();

        model = OllamaChatModel.builder()
                .baseUrl("http://localhost:" + ollama.getAddress().getPort())
                .modelName("llama3.1")
                .temperature(0.0)
                .timeout(Duration.ofSeconds(30))
                .maxRetries(0)
                .build();
    }

    @AfterEach
    void stopModelServer() {
        ollama.stop(0);
    }

    @Test
    void keepsWindowedPerGuestConversationsAcrossARestart() {
        JsonFileChatMemoryStore store = new JsonFileChatMemoryStore(conversationsDir);
        Concierge concierge = startConcierge(store);

        assertThat(concierge.chat("ana", "My name is Ana.")).isEqualTo("Noted: My name is Ana.");
        assertThat(concierge.chat("bo", "My name is Bo.")).isEqualTo("Noted: My name is Bo.");
        assertThat(concierge.chat("ana", NAME_QUESTION)).isEqualTo("Your name is Ana.");
        assertThat(lastRequest()).contains("Hotel Aurora", "My name is Ana.").doesNotContain("Bo");

        assertThat(concierge.getChatMemory("ana").messages())
                .extracting(ChatMessage::type)
                .containsExactly(SYSTEM, USER, AI, USER, AI);
        assertThat(aiAnswers(concierge, "ana")).containsExactly("Noted: My name is Ana.", "Your name is Ana.");
        assertThat(store.fileOf("ana")).content().contains("My name is Ana.").doesNotContain("Bo");
        assertThat(store.fileOf("bo")).content().contains("My name is Bo.").doesNotContain("Ana");

        // The window holds seven messages and always keeps the system message, so Ana's oldest turn goes.
        assertThat(concierge.chat("ana", "Book a table for two at eight.")).isEqualTo("Noted: Book a table for two at eight.");
        assertThat(concierge.chat("ana", "Wake me at seven.")).isEqualTo("Noted: Wake me at seven.");
        assertThat(concierge.getChatMemory("ana").messages())
                .extracting(ChatMessage::type)
                .containsExactly(SYSTEM, USER, AI, USER, AI, USER, AI);
        assertThat(aiAnswers(concierge, "ana")).containsExactly(
                "Your name is Ana.", "Noted: Book a table for two at eight.", "Noted: Wake me at seven.");
        assertThat(store.getMessages("ana").get(0).type()).isEqualTo(SYSTEM);
        assertThat(store.fileOf("ana")).content().contains("Wake me at seven.").doesNotContain("My name is Ana.");

        // A restarted service builds memories lazily and reloads each one from the guest's file.
        JsonFileChatMemoryStore reopenedStore = new JsonFileChatMemoryStore(conversationsDir);
        Concierge restarted = startConcierge(reopenedStore);
        assertThat(restarted.getChatMemory("bo")).isNull();
        assertThat(restarted.chat("bo", NAME_QUESTION)).isEqualTo("Your name is Bo.");
        assertThat(lastRequest()).contains("My name is Bo.").doesNotContain("Ana");
        assertThat(restarted.chat("ana", NAME_QUESTION)).isEqualTo("I do not know your name yet.");

        // Evicting drops only the cached memory; the stored conversation comes back on the next call.
        assertThat(restarted.evictChatMemory("bo")).isTrue();
        assertThat(restarted.evictChatMemory("bo")).isFalse();
        assertThat(restarted.getChatMemory("bo")).isNull();
        assertThat(store.fileOf("bo")).exists();
        assertThat(restarted.chat("bo", NAME_QUESTION)).isEqualTo("Your name is Bo.");

        // Clearing a memory deletes the guest's stored conversation for good.
        restarted.getChatMemory("bo").clear();
        assertThat(store.fileOf("bo")).doesNotExist();
        assertThat(restarted.getChatMemory("bo").messages()).isEmpty();
        assertThat(restarted.chat("bo", NAME_QUESTION)).isEqualTo("I do not know your name yet.");
        assertThat(store.fileOf("ana")).exists();

        assertThat(chatRequests).hasSize(9);
    }

    private Concierge startConcierge(ChatMemoryStore store) {
        ChatMemoryProvider memoryPerGuest = guestId -> MessageWindowChatMemory.builder()
                .id(guestId)
                .maxMessages(7)
                .chatMemoryStore(store)
                .build();
        return AiServices.builder(Concierge.class)
                .chatModel(model)
                .chatMemoryProvider(memoryPerGuest)
                .build();
    }

    private String lastRequest() {
        return chatRequests.get(chatRequests.size() - 1);
    }

    private static List<String> aiAnswers(Concierge concierge, String guestId) {
        return concierge.getChatMemory(guestId).messages().stream()
                .filter(AiMessage.class::isInstance)
                .map(message -> ((AiMessage) message).text())
                .toList();
    }

    private void answerChat(HttpExchange exchange) throws IOException {
        String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        chatRequests.add(request);

        List<String> userMessages = USER_MESSAGE.matcher(request).results()
                .map(message -> CONTENT.matcher(message.group()))
                .filter(Matcher::find)
                .map(content -> content.group(1))
                .toList();
        assertThat(userMessages).as("user messages of chat request %s", request).isNotEmpty();
        String question = userMessages.get(userMessages.size() - 1);

        String answer;
        if (question.equals(NAME_QUESTION)) {
            answer = userMessages.stream()
                    .map(GIVEN_NAME::matcher)
                    .filter(Matcher::find)
                    .map(name -> "Your name is " + name.group(1) + ".")
                    .reduce((first, latest) -> latest)
                    .orElse("I do not know your name yet.");
        } else {
            answer = "Noted: " + question;
        }
        String response = """
                {"model": "llama3.1", "created_at": "2025-11-04T10:15:30Z",
                 "message": {"role": "assistant", "content": "%s"},
                 "done": true, "done_reason": "stop", "prompt_eval_count": 48, "eval_count": 9}
                """.formatted(answer);

        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(bytes);
        }
    }
}
