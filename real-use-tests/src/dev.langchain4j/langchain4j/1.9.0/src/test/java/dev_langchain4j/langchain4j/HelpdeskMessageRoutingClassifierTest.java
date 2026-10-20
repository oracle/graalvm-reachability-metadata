/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_langchain4j.langchain4j;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.classification.ClassificationResult;
import dev.langchain4j.classification.EmbeddingModelTextClassifier;
import dev.langchain4j.classification.ScoredLabel;
import dev.langchain4j.classification.TextClassifier;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Routes helpdesk messages to departments with the langchain4j text classifier: an
 * `EmbeddingModelTextClassifier` that learns each label from example sentences and scores new
 * texts, documents and segments against them by embedding similarity.
 *
 * The helpdesk describes each department with a few typical requests and embeds them through an
 * Ollama embedding model. Incoming messages go to the best matching department, and a triage
 * view lists every department above a confidence threshold for messages that touch several of
 * them. The test plays the Ollama server with a small topic vector per text, so every score is a
 * deterministic function of the words in the message.
 */
class HelpdeskMessageRoutingClassifierTest {

    private static final Pattern INPUT_ARRAY = Pattern.compile("\"input\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);

    private static final Pattern JSON_STRING = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    /// Word stems of the three topics the mock embedding model places on its three axes.
    private static final List<List<String>> TOPICS = List.of(
            List.of("invoice", "refund", "charge", "payment"),
            List.of("parcel", "deliver", "courier", "tracking"),
            List.of("password", "login", "crash", "app"));

    private final List<String> embedRequests = new CopyOnWriteArrayList<>();

    private HttpServer ollama;

    private EmbeddingModel embeddingModel;

    enum Department {
        BILLING, SHIPPING, TECHNICAL
    }

    @BeforeEach
    void startModelServer() throws IOException {
        ollama = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        ollama.createContext("/api/embed", this::answerEmbed);
        ollama.start();

        embeddingModel = OllamaEmbeddingModel.builder()
                .baseUrl("http://localhost:" + ollama.getAddress().getPort())
                .modelName("nomic-embed-text")
                .timeout(Duration.ofSeconds(30))
                .maxRetries(0)
                .build();
    }

    @AfterEach
    void stopModelServer() {
        ollama.stop(0);
    }

    @Test
    void routesEachMessageToItsBestDepartment() {
        TextClassifier<Department> router = new EmbeddingModelTextClassifier<>(embeddingModel, examples());

        assertThat(String.join("\n", embedRequests))
                .contains("nomic-embed-text", "I was charged twice on my invoice", "The app crashes on start");
        int exampleRequests = embedRequests.size();

        assertThat(router.classify("My parcel is with the courier but tracking is stuck"))
                .containsExactly(Department.SHIPPING);

        Document emailTicket = Document.from(
                "Please refund the payment on invoice 1042",
                Metadata.from("ticket", "T-1042"));
        assertThat(router.classify(emailTicket)).containsExactly(Department.BILLING);

        TextSegment chatLine = TextSegment.from("My login fails after I changed my password");
        ClassificationResult<Department> technical = router.classifyWithScores(chatLine);
        assertThat(technical.scoredLabels()).singleElement().satisfies(label -> {
            assertThat(label.label()).isEqualTo(Department.TECHNICAL);
            assertThat(label.score()).isCloseTo(1.0, within(0.001));
        });

        assertThat(embedRequests).hasSize(exampleRequests + 3);
        assertThat(embedRequests.subList(exampleRequests, embedRequests.size()))
                .satisfiesExactly(
                        request -> assertThat(request).contains("tracking is stuck"),
                        request -> assertThat(request).contains("invoice 1042"),
                        request -> assertThat(request).contains("changed my password"));
    }

    @Test
    void listsEveryDepartmentAboveTheConfidenceThreshold() {
        TextClassifier<Department> triage = new EmbeddingModelTextClassifier<>(
                embeddingModel, examples(), Department.values().length, 0.75, 0.5);

        ClassificationResult<Department> mixed = triage.classifyWithScores(
                "Refund my payment because the courier lost the parcel and tracking stopped");

        List<ScoredLabel<Department>> labels = mixed.scoredLabels();
        assertThat(labels).extracting(ScoredLabel::label)
                .containsExactly(Department.SHIPPING, Department.BILLING);
        assertThat(labels.get(0).score()).isCloseTo(0.917, within(0.005));
        assertThat(labels.get(1).score()).isCloseTo(0.780, within(0.005));
        assertThat(triage.classify(
                "Refund my payment because the courier lost the parcel and tracking stopped"))
                .containsExactly(Department.SHIPPING, Department.BILLING);

        ClassificationResult<Department> mostlyTechnical = triage.classifyWithScores(
                "The app crashes whenever I open my invoice");
        assertThat(mostlyTechnical.scoredLabels()).singleElement().satisfies(label -> {
            assertThat(label.label()).isEqualTo(Department.TECHNICAL);
            assertThat(label.score()).isCloseTo(0.948, within(0.005));
        });
    }

    /// Typical requests per department; each names two words of its own topic and none of the others.
    private static Map<Department, List<String>> examples() {
        Map<Department, List<String>> examples = new LinkedHashMap<>();
        examples.put(Department.BILLING, List.of(
                "I was charged twice on my invoice",
                "Please refund my payment"));
        examples.put(Department.SHIPPING, List.of(
                "The courier lost my parcel",
                "Where is the tracking for my delivery"));
        examples.put(Department.TECHNICAL, List.of(
                "The app crashes on start",
                "I cannot reset my password at login"));
        return examples;
    }

    private void answerEmbed(HttpExchange exchange) throws IOException {
        String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        embedRequests.add(request);

        Matcher input = INPUT_ARRAY.matcher(request);
        assertThat(input.find()).as("input of embed request %s", request).isTrue();
        String embeddings = JSON_STRING.matcher(input.group(1)).results()
                .map(text -> topicVector(text.group(1)))
                .collect(Collectors.joining(", ", "[", "]"));
        String response = """
                {"model": "nomic-embed-text", "embeddings": %s}
                """.formatted(embeddings);

        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(bytes);
        }
    }

    /// Counts the topic words of a text on each axis, over a small floor so no vector is zero.
    private static String topicVector(String text) {
        List<String> words = Arrays.asList(text.toLowerCase(Locale.ROOT).split("\\W+"));
        return TOPICS.stream()
                .map(stems -> words.stream().filter(word -> stems.stream().anyMatch(word::startsWith)).count())
                .map(count -> String.valueOf(count + 0.01))
                .collect(Collectors.joining(", ", "[", "]"));
    }
}
