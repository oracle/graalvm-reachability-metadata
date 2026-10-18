/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_langchain4j.langchain4j;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.chain.Chain;
import dev.langchain4j.chain.ConversationalChain;
import dev.langchain4j.chain.ConversationalRetrievalChain;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs a bicycle workshop's service desk through langchain4j chains: a `ConversationalChain` that
 * replays a windowed chat memory to the model on every turn, and a `ConversationalRetrievalChain`
 * that augments each question with workshop notes before it enters the conversation.
 *
 * The desk keeps one chat memory per rider and answers repair questions from its maintenance
 * notes. The test plays the Ollama server with an in-process HTTP endpoint that records every chat
 * request and answers according to the latest message, so the scenario can check which history
 * and which notes each turn sent to the model.
 */
class BicycleWorkshopConversationalChainTest {

    private static final Pattern MESSAGE_CONTENT = Pattern.compile("\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private static final String DESK_PERSONA = "You are the service desk of Spoke and Chain bicycles.";

    /// Answers of the mock model, keyed by a phrase of the latest message they reply to.
    private static final Map<String, String> ANSWERS = orderedAnswers();

    /// Maintenance notes of the workshop, keyed by the word that makes a question match them.
    private static final Map<String, String> NOTES = Map.of(
            "hub", "Internal hub gears need a fresh oil bath every 5000 km.",
            "chain", "Waxed chains should be rewaxed every 300 km.",
            "tubeless", "Tubeless sealant dries out and must be topped up every three months.");

    private final List<String> chatRequests = new CopyOnWriteArrayList<>();

    private final List<Query> retrievedQueries = new CopyOnWriteArrayList<>();

    private HttpServer ollama;

    private ChatModel model;

    private ContentRetriever workshopNotes;

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
        workshopNotes = query -> {
            retrievedQueries.add(query);
            String question = query.text().toLowerCase(Locale.ROOT);
            return NOTES.entrySet().stream()
                    .filter(note -> question.contains(note.getKey()))
                    .map(note -> Content.from(TextSegment.from(note.getValue(),
                            new Metadata().put("source", "workshop-manual-" + note.getKey()))))
                    .toList();
        };
    }

    @AfterEach
    void stopModelServer() {
        ollama.stop(0);
    }

    @Test
    void conversationalChainReplaysWindowedHistoryAndKeepsTheSystemMessage() {
        ChatMemory memory = MessageWindowChatMemory.builder()
                .id("rider-17")
                .maxMessages(4)
                .build();
        memory.add(SystemMessage.from(DESK_PERSONA));
        Chain<String, String> desk = ConversationalChain.builder()
                .chatModel(model)
                .chatMemory(memory)
                .build();

        assertThat(desk.execute("My rear brake squeals when it rains."))
                .isEqualTo("Wet rims squeal, so clean them and check the pads.");
        assertThat(desk.execute("How much does a pad replacement cost?"))
                .isEqualTo("A brake pad replacement costs 25 euros.");
        assertThat(chatRequests.get(1))
                .contains(DESK_PERSONA)
                .contains("My rear brake squeals when it rains.")
                .contains("Wet rims squeal, so clean them and check the pads.")
                .contains("How much does a pad replacement cost?");

        assertThat(desk.execute("Can you do it today?")).isEqualTo("Yes, drop the bike off before 4 pm.");
        assertThat(chatRequests).hasSize(3);
        assertThat(chatRequests.get(2))
                .contains(DESK_PERSONA)
                .contains("How much does a pad replacement cost?", "A brake pad replacement costs 25 euros.")
                .contains("Can you do it today?")
                .doesNotContain("squeals");
        assertThat(memory.messages()).extracting(BicycleWorkshopConversationalChainTest::text).containsExactly(
                DESK_PERSONA,
                "A brake pad replacement costs 25 euros.",
                "Can you do it today?",
                "Yes, drop the bike off before 4 pm.");

        assertThatThrownBy(() -> desk.execute("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userMessage");
        assertThat(chatRequests).hasSize(3);
    }

    @Test
    void retrievalChainInjectsMatchingNotesIntoEveryQuestionItRemembers() {
        ConversationalRetrievalChain desk = ConversationalRetrievalChain.builder()
                .chatModel(model)
                .contentRetriever(workshopNotes)
                .build();

        assertThat(desk.execute("How often should I service my hub gears?"))
                .isEqualTo("Change the hub oil every 5000 km.");
        assertThat(chatRequests.get(0))
                .contains("How often should I service my hub gears?")
                .contains("Answer using the following information:")
                .contains("Internal hub gears need a fresh oil bath every 5000 km.")
                .doesNotContain("rewaxed");

        assertThat(desk.execute("And what about the chain?")).isEqualTo("Rewax the chain every 300 km.");
        assertThat(chatRequests.get(1))
                .contains("Internal hub gears need a fresh oil bath every 5000 km.")
                .contains("Change the hub oil every 5000 km.")
                .contains("And what about the chain?", "Waxed chains should be rewaxed every 300 km.");

        assertThat(desk.execute("Are you open on Sunday?")).isEqualTo("The workshop is closed on Sundays.");
        assertThat(chatRequests).hasSize(3);
        assertThat(chatRequests.get(2).split("Answer using the following information:", -1)).hasSize(3);

        assertThat(retrievedQueries).extracting(Query::text).containsExactly(
                "How often should I service my hub gears?",
                "And what about the chain?",
                "Are you open on Sunday?");
        assertThat(retrievedQueries).extracting(query -> query.metadata().chatMemory().size())
                .containsExactly(0, 2, 4);
    }

    @Test
    void retrievalChainRunsTheApplicationsAugmentorAgainstTheRidersMemory() {
        ChatMemory memory = MessageWindowChatMemory.builder()
                .id("rider-42")
                .maxMessages(10)
                .build();
        memory.add(SystemMessage.from(DESK_PERSONA));
        ConversationalRetrievalChain desk = ConversationalRetrievalChain.builder()
                .chatModel(model)
                .chatMemory(memory)
                .retrievalAugmentor(DefaultRetrievalAugmentor.builder()
                        .contentRetriever(workshopNotes)
                        .contentInjector(DefaultContentInjector.builder()
                                .promptTemplate(PromptTemplate.from("""
                                        Rider question: {{userMessage}}

                                        Workshop notes:
                                        {{contents}}"""))
                                .metadataKeysToInclude(List.of("source"))
                                .build())
                        .build())
                .build();

        assertThat(desk.execute("How often do I refill my tubeless tyres?"))
                .isEqualTo("Top up the sealant every three months.");

        assertThat(retrievedQueries).singleElement().satisfies(query -> {
            assertThat(query.text()).isEqualTo("How often do I refill my tubeless tyres?");
            assertThat(query.metadata().chatMemoryId()).isEqualTo("rider-42");
            assertThat(query.metadata().chatMemory()).extracting(BicycleWorkshopConversationalChainTest::text)
                    .containsExactly(DESK_PERSONA);
        });
        assertThat(chatRequests).singleElement().satisfies(request -> assertThat(request)
                .contains(DESK_PERSONA)
                .contains("Rider question: How often do I refill my tubeless tyres?")
                .contains("Workshop notes:", "Tubeless sealant dries out and must be topped up every three months.")
                .contains("workshop-manual-tubeless")
                .doesNotContain("Answer using the following information:"));
        assertThat(memory.messages()).hasSize(3);
        assertThat(memory.messages().get(1)).isInstanceOfSatisfying(UserMessage.class, question -> assertThat(
                question.singleText()).startsWith("Rider question: How often do I refill my tubeless tyres?")
                .contains("workshop-manual-tubeless"));
        assertThat(memory.messages().get(2)).isInstanceOfSatisfying(AiMessage.class, answer -> assertThat(
                answer.text()).isEqualTo("Top up the sealant every three months."));
    }

    private static String text(ChatMessage message) {
        return switch (message) {
            case SystemMessage system -> system.text();
            case UserMessage user -> user.singleText();
            case AiMessage ai -> ai.text();
            default -> throw new AssertionError("Unexpected message " + message);
        };
    }

    private static Map<String, String> orderedAnswers() {
        Map<String, String> answers = new LinkedHashMap<>();
        answers.put("squeals", "Wet rims squeal, so clean them and check the pads.");
        answers.put("pad replacement", "A brake pad replacement costs 25 euros.");
        answers.put("today", "Yes, drop the bike off before 4 pm.");
        answers.put("hub gears", "Change the hub oil every 5000 km.");
        answers.put("the chain", "Rewax the chain every 300 km.");
        answers.put("tubeless", "Top up the sealant every three months.");
        answers.put("Sunday", "The workshop is closed on Sundays.");
        return answers;
    }

    private void answerChat(HttpExchange exchange) throws IOException {
        String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        chatRequests.add(request);

        String latestMessage = MESSAGE_CONTENT.matcher(request).results()
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("No message in chat request " + request))
                .group(1);
        String answer = ANSWERS.entrySet().stream()
                .filter(entry -> latestMessage.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("Please call the workshop for that.");
        String response = """
                {"model": "llama3.1", "created_at": "2025-11-04T10:15:30Z",
                 "message": {"role": "assistant", "content": "%s"},
                 "done": true, "done_reason": "stop", "prompt_eval_count": 48, "eval_count": 11}
                """.formatted(answer.replace("\\", "\\\\").replace("\"", "\\\""));

        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(bytes);
        }
    }
}
