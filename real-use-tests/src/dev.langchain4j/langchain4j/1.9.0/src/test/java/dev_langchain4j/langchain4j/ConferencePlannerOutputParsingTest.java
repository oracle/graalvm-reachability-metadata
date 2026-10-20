/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_langchain4j.langchain4j;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.output.OutputParsingException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plans a developer conference through langchain4j AI service output parsing: service methods
 * whose declared return types are string lists, enum sets, record lists, dates, timestamps,
 * decimals, integers and booleans, each parsed from the model answer.
 *
 * The organizers declare a `ConferencePlanner` interface and let `AiServices` implement it on
 * top of an Ollama chat model that advertises JSON schema response formats, which record lists
 * require. The test plays the Ollama server with an in-process HTTP endpoint that records each
 * chat request and answers like a model would: collections arrive either as a JSON `values`
 * wrapper or as one value per line, scalars arrive as plain text. The scenario checks the typed
 * values the application receives, the format instructions or schemas the service added to each
 * request, and that an unparseable answer surfaces as an `OutputParsingException`.
 */
class ConferencePlannerOutputParsingTest {

    private static final String CALL_FOR_PAPERS = """
            JVM Days 2026 takes place in Belgrade. Submissions close on 14 March 2026 and the
            keynote opens the event on 2 June 2026 at 09:30. Early bird tickets cost 249.50 EUR
            and the main hall seats 680 people.""";

    private final List<String> chatRequests = new CopyOnWriteArrayList<>();

    private HttpServer ollama;

    private ConferencePlanner planner;

    enum Track {
        CLOUD, NATIVE, SECURITY, TOOLING, LANGUAGES
    }

    record Session(String title, String room, int minutes) {
    }

    interface ConferencePlanner {

        @SystemMessage("TITLES: you suggest talk titles.")
        @UserMessage("Suggest three talk titles about {{it}}")
        List<String> suggestTitles(String topic);

        @SystemMessage("SPEAKERS: you list confirmed speakers.")
        List<String> confirmedSpeakers(@UserMessage String announcement);

        @SystemMessage("TRACKS: you assign conference tracks to abstracts.")
        @UserMessage("Assign tracks to this abstract: {{it}}")
        Set<Track> assignTracks(String talkAbstract);

        @SystemMessage("AGENDA: you draft session agendas.")
        @UserMessage("Draft the morning agenda for: {{it}}")
        List<Session> draftMorningAgenda(String theme);

        @SystemMessage("DEADLINE: you read submission deadlines.")
        LocalDate submissionDeadline(@UserMessage String callForPapers);

        @SystemMessage("KEYNOTE: you read keynote start times.")
        LocalDateTime keynoteStart(@UserMessage String callForPapers);

        @SystemMessage("PRICE: you read early bird ticket prices in EUR.")
        BigDecimal earlyBirdPrice(@UserMessage String callForPapers);

        @SystemMessage("CAPACITY: you read the main hall capacity.")
        int mainHallCapacity(@UserMessage String callForPapers);

        @SystemMessage("VISA: you tell whether a speaker needs a visa.")
        @UserMessage("Does a speaker travelling from {{it}} need a visa for Serbia?")
        boolean speakerNeedsVisa(String country);
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
                .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
                .build();
        planner = AiServices.builder(ConferencePlanner.class)
                .chatModel(model)
                .build();
    }

    @AfterEach
    void stopModelServer() {
        ollama.stop(0);
    }

    @Test
    void parsesStringListsFromJsonWrapperAndFromLines() {
        List<String> titles = planner.suggestTitles("native images");
        List<String> speakers = planner.confirmedSpeakers("Speakers so far: Ana, Marko and Jelena.");

        assertThat(titles).containsExactly(
                "Startup in Milliseconds",
                "Reachability Metadata Explained",
                "Profiling Native Executables");
        assertThat(speakers).containsExactly("Ana Petrovic", "Marko Ilic", "Jelena Savic");
        assertThat(chatRequests).hasSize(2);
        assertThat(chatRequests.get(0)).contains("Suggest three talk titles about native images");
        assertThat(chatRequests.get(1)).contains("Speakers so far: Ana, Marko and Jelena.");
    }

    @Test
    void parsesEnumSetAndRecordList() {
        Set<Track> tracks = planner.assignTracks("Hardening native executables built in CI pipelines");
        List<Session> agenda = planner.draftMorningAgenda("cloud native Java");

        assertThat(tracks).containsExactlyInAnyOrder(Track.NATIVE, Track.SECURITY, Track.TOOLING);
        assertThat(agenda).containsExactly(
                new Session("Opening Keynote", "Main Hall", 45),
                new Session("Serverless Without Cold Starts", "Room A", 40),
                new Session("Kubernetes Operators in Java", "Room B", 40));
        assertThat(chatRequests.get(0)).contains("CLOUD", "NATIVE", "SECURITY", "TOOLING", "LANGUAGES");
        assertThat(chatRequests.get(1)).contains("title", "room", "minutes");
    }

    @Test
    void parsesDatesTimestampsAndNumbers() {
        LocalDate deadline = planner.submissionDeadline(CALL_FOR_PAPERS);
        LocalDateTime keynote = planner.keynoteStart(CALL_FOR_PAPERS);
        BigDecimal price = planner.earlyBirdPrice(CALL_FOR_PAPERS);
        int capacity = planner.mainHallCapacity(CALL_FOR_PAPERS);

        assertThat(deadline).isEqualTo(LocalDate.of(2026, 3, 14));
        assertThat(keynote).isEqualTo(LocalDateTime.of(2026, 6, 2, 9, 30));
        assertThat(price).isEqualByComparingTo("249.50");
        assertThat(capacity).isEqualTo(680);
        assertThat(chatRequests).hasSize(4);
        assertThat(chatRequests.get(0)).contains("yyyy-MM-dd");
        assertThat(chatRequests.get(1)).contains("yyyy-MM-ddTHH:mm:ss");
    }

    @Test
    void parsesBooleanAndRejectsUnparseableAnswer() {
        assertThat(planner.speakerNeedsVisa("Germany")).isFalse();
        assertThat(planner.speakerNeedsVisa("India")).isTrue();

        assertThatThrownBy(() -> planner.speakerNeedsVisa("Atlantis"))
                .isInstanceOf(OutputParsingException.class)
                .hasMessageContaining("depends on the passport");
        assertThat(chatRequests).hasSize(3);
        assertThat(chatRequests.get(2)).contains("travelling from Atlantis");
    }

    private void answerChat(HttpExchange exchange) throws IOException {
        String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        chatRequests.add(request);

        String answer = answerFor(request);
        String response = """
                {"model": "llama3.1", "created_at": "2026-01-12T08:00:00Z",
                 "message": {"role": "assistant", "content": "%s"},
                 "done": true, "done_reason": "stop", "prompt_eval_count": 64, "eval_count": 12}
                """.formatted(answer
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n"));

        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(bytes);
        }
    }

    private static String answerFor(String request) {
        if (request.contains("TITLES:")) {
            return """
                    {"values": ["Startup in Milliseconds", "Reachability Metadata Explained",
                    "Profiling Native Executables"]}""";
        }
        if (request.contains("SPEAKERS:")) {
            return "Ana Petrovic\nMarko Ilic\n\nJelena Savic\n";
        }
        if (request.contains("TRACKS:")) {
            return """
                    {"values": ["NATIVE", "SECURITY", "TOOLING"]}""";
        }
        if (request.contains("AGENDA:")) {
            return """
                    {"values": [
                      {"title": "Opening Keynote", "room": "Main Hall", "minutes": 45},
                      {"title": "Serverless Without Cold Starts", "room": "Room A", "minutes": 40},
                      {"title": "Kubernetes Operators in Java", "room": "Room B", "minutes": 40}
                    ]}""";
        }
        if (request.contains("DEADLINE:")) {
            return "2026-03-14";
        }
        if (request.contains("KEYNOTE:")) {
            return "2026-06-02T09:30:00";
        }
        if (request.contains("PRICE:")) {
            return "249.50";
        }
        if (request.contains("CAPACITY:")) {
            return "680";
        }
        if (request.contains("travelling from Germany")) {
            return "false";
        }
        if (request.contains("travelling from India")) {
            return "TRUE";
        }
        return "It depends on the passport";
    }
}
