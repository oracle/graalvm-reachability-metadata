/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_langchain4j.langchain4j;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.router.LanguageModelQueryRouter;
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
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

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Answers employee questions through langchain4j retrieval-augmented generation: a
 * `DefaultRetrievalAugmentor` that an AI service runs before every call, compressing follow-up
 * questions with the chat history, routing each query to a content retriever chosen by the model,
 * and injecting the retrieved excerpts and their sources into the user message.
 *
 * The HR team indexes its handbook in an in-memory embedding store, tagging every excerpt with
 * the country it applies to, and keeps salary dates in a payroll calendar the application looks
 * up itself. The embedding retriever narrows each search to the asking employee's country. The
 * test plays the Ollama server: embeddings are small topic vectors and chat answers are chosen by
 * the prompt the augmentor rendered, so every retrieval and route is deterministic.
 */
class EmployeeHandbookRetrievalAugmentationTest {

    private static final Pattern INPUT_ARRAY = Pattern.compile("\"input\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);

    private static final Pattern JSON_STRING = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    private static final Pattern TRAILING_QUESTION = Pattern.compile("Question: ((?:[^\"\\\\]|\\\\.)*)\"");

    private static final Pattern OPTION_NUMBER = Pattern.compile("(\\d+): (Employee handbook|Payroll calendar)");

    private static final String CARRY_OVER_QUESTION = "How many unused vacation days can I carry over to next year?";

    /// Word stems of the two topics the mock embedding model places on its first two axes.
    private static final List<List<String>> TOPICS = List.of(
            List.of("vacation", "holiday", "leave", "carry"),
            List.of("expense", "receipt", "travel", "reimburse"));

    private static final Map<String, String> EMPLOYEE_COUNTRIES = Map.of("anna", "DE", "ben", "US");

    private final List<String> embedRequests = new CopyOnWriteArrayList<>();

    private final List<String> chatRequests = new CopyOnWriteArrayList<>();

    private HttpServer ollama;

    private HandbookAssistant assistant;

    interface HandbookAssistant {

        @SystemMessage("You answer questions from Acme employees.")
        Result<String> ask(@MemoryId String employee, @UserMessage String question);
    }

    @BeforeEach
    void startAssistant() throws IOException {
        ollama = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        ollama.createContext("/api/embed", this::answerEmbed);
        ollama.createContext("/api/chat", this::answerChat);
        ollama.start();
        String baseUrl = "http://localhost:" + ollama.getAddress().getPort();

        ChatModel chatModel = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName("llama3.1")
                .temperature(0.0)
                .timeout(Duration.ofSeconds(30))
                .maxRetries(0)
                .build();
        EmbeddingModel embeddingModel = OllamaEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .modelName("nomic-embed-text")
                .timeout(Duration.ofSeconds(30))
                .maxRetries(0)
                .build();

        InMemoryEmbeddingStore<TextSegment> handbook = new InMemoryEmbeddingStore<>();
        List<TextSegment> excerpts = List.of(
                excerpt("Employees in Germany receive 30 vacation days per year.", "DE", "vacation-de.md"),
                excerpt("Up to 5 unused vacation days carry over to the next year.", "DE", "vacation-de.md"),
                excerpt("Employees in the United States receive 20 vacation days per year.", "US", "vacation-us.md"),
                excerpt("Travel expenses are reimbursed within 14 days of uploading the receipt.", "ALL", "expenses.md"));
        handbook.addAll(embeddingModel.embedAll(excerpts).content(), excerpts);

        ContentRetriever handbookRetriever = EmbeddingStoreContentRetriever.builder()
                .displayName("handbook")
                .embeddingStore(handbook)
                .embeddingModel(embeddingModel)
                .maxResults(3)
                .minScore(0.75)
                .dynamicFilter(query -> metadataKey("country")
                        .isIn(EMPLOYEE_COUNTRIES.get((String) query.metadata().chatMemoryId()), "ALL"))
                .build();
        ContentRetriever payrollRetriever = query -> List.of(Content.from(
                "The next payday for " + query.metadata().chatMemoryId() + " is November 28."));

        Map<ContentRetriever, String> retrievers = new LinkedHashMap<>();
        retrievers.put(handbookRetriever, "Employee handbook with vacation and expense policies");
        retrievers.put(payrollRetriever, "Payroll calendar with salary dates");

        RetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
                .queryTransformer(CompressingQueryTransformer.builder()
                        .chatModel(chatModel)
                        .promptTemplate(PromptTemplate.from("""
                                REWRITE the last question so it stands alone.
                                Conversation:
                                {{chatMemory}}
                                Question: {{query}}"""))
                        .build())
                .queryRouter(LanguageModelQueryRouter.builder()
                        .chatModel(chatModel)
                        .retrieverToDescription(retrievers)
                        .promptTemplate(PromptTemplate.from("""
                                ROUTE the question to the numbers of the sources that can answer it.
                                {{options}}
                                Question: {{query}}"""))
                        .build())
                .contentInjector(DefaultContentInjector.builder()
                        .promptTemplate(PromptTemplate.from("""
                                {{userMessage}}

                                Answer using only these excerpts:
                                {{contents}}"""))
                        .metadataKeysToInclude(List.of("source"))
                        .build())
                .build();

        assistant = AiServices.builder(HandbookAssistant.class)
                .chatModel(chatModel)
                .chatMemoryProvider(employee -> MessageWindowChatMemory.withMaxMessages(10))
                .retrievalAugmentor(augmentor)
                .build();
        embedRequests.clear();
    }

    @AfterEach
    void stopModelServer() {
        ollama.stop(0);
    }

    @Test
    void answersFromTheEmployeesOwnCountryAndCompressesFollowUps() {
        Result<String> allowance = assistant.ask("anna", "How many vacation days do I get?");

        assertThat(allowance.content()).isEqualTo("You get 30 vacation days per year.");
        assertThat(sourceTexts(allowance)).containsExactly(
                "Employees in Germany receive 30 vacation days per year.",
                "Up to 5 unused vacation days carry over to the next year.");
        assertThat(allowance.sources()).allSatisfy(content -> assertThat(
                content.textSegment().metadata().getString("source")).isEqualTo("vacation-de.md"));
        assertThat(chatRequests).hasSize(2);
        assertThat(chatRequests.get(0)).contains("ROUTE", "Question: How many vacation days do I get?");
        assertThat(chatRequests.get(1))
                .contains("You answer questions from Acme employees.")
                .contains("How many vacation days do I get?", "Answer using only these excerpts:")
                .contains("Employees in Germany receive 30 vacation days per year.", "vacation-de.md")
                .doesNotContain("United States", "Travel expenses");

        chatRequests.clear();
        Result<String> carryOver = assistant.ask("anna", "And how many of those can I keep?");

        assertThat(carryOver.content()).isEqualTo("You can carry over up to 5 vacation days.");
        assertThat(chatRequests).hasSize(3);
        assertThat(chatRequests.get(0))
                .contains("REWRITE", "How many vacation days do I get?", "You get 30 vacation days per year.")
                .contains("Question: And how many of those can I keep?");
        assertThat(chatRequests.get(1)).contains("ROUTE", "Question: " + CARRY_OVER_QUESTION);
        assertThat(chatRequests.get(2))
                .contains("And how many of those can I keep?")
                .contains("Up to 5 unused vacation days carry over to the next year.");
        assertThat(embedRequests).hasSize(2);
        assertThat(embedRequests.get(1)).contains(CARRY_OVER_QUESTION);
    }

    @Test
    void routesSalaryQuestionsToThePayrollCalendarAndFiltersByCountry() {
        Result<String> payday = assistant.ask("ben", "When is my next payday?");

        assertThat(payday.content()).isEqualTo("Your next salary arrives on November 28.");
        assertThat(sourceTexts(payday)).containsExactly("The next payday for ben is November 28.");
        assertThat(embedRequests).isEmpty();
        assertThat(chatRequests.get(chatRequests.size() - 1))
                .contains("The next payday for ben is November 28.")
                .doesNotContain("vacation days per year");

        chatRequests.clear();
        Result<String> allowance = assistant.ask("ben", "How many vacation days do I get?");

        assertThat(allowance.content()).isEqualTo("You get 20 vacation days per year.");
        assertThat(sourceTexts(allowance))
                .containsExactly("Employees in the United States receive 20 vacation days per year.");
        assertThat(allowance.sources().get(0).textSegment().metadata().getString("source"))
                .isEqualTo("vacation-us.md");
        assertThat(chatRequests.get(0)).contains("REWRITE", "When is my next payday?");
        assertThat(chatRequests.get(chatRequests.size() - 1))
                .contains("vacation-us.md")
                .doesNotContain("Germany", "carry over");
    }

    private static TextSegment excerpt(String text, String country, String source) {
        return TextSegment.from(text, new Metadata().put("country", country).put("source", source));
    }

    private static List<String> sourceTexts(Result<String> result) {
        return result.sources().stream()
                .map(content -> content.textSegment().text())
                .toList();
    }

    private void answerChat(HttpExchange exchange) throws IOException {
        String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        chatRequests.add(request);

        String answer;
        if (request.contains("REWRITE the last question")) {
            String question = trailingQuestion(request);
            answer = question.contains("those") ? CARRY_OVER_QUESTION : question;
        } else if (request.contains("ROUTE the question")) {
            String wanted = trailingQuestion(request).contains("payday") ? "Payroll calendar" : "Employee handbook";
            answer = OPTION_NUMBER.matcher(request).results()
                    .filter(option -> option.group(2).equals(wanted))
                    .map(option -> option.group(1))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No option for " + wanted + " in " + request));
        } else if (request.contains("Up to 5 unused") && request.contains("of those")) {
            answer = "You can carry over up to 5 vacation days.";
        } else if (request.contains("receive 30 vacation days")) {
            answer = "You get 30 vacation days per year.";
        } else if (request.contains("receive 20 vacation days")) {
            answer = "You get 20 vacation days per year.";
        } else if (request.contains("next payday for")) {
            answer = "Your next salary arrives on November 28.";
        } else {
            answer = "I cannot find that in the handbook.";
        }
        respond(exchange, """
                {"model": "llama3.1", "created_at": "2025-11-04T10:15:30Z",
                 "message": {"role": "assistant", "content": "%s"},
                 "done": true, "done_reason": "stop", "prompt_eval_count": 64, "eval_count": 12}
                """.formatted(answer.replace("\\", "\\\\").replace("\"", "\\\"")));
    }

    private static String trailingQuestion(String request) {
        Matcher question = TRAILING_QUESTION.matcher(request);
        assertThat(question.find()).as("question in chat request %s", request).isTrue();
        return question.group(1);
    }

    private void answerEmbed(HttpExchange exchange) throws IOException {
        String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        embedRequests.add(request);

        Matcher input = INPUT_ARRAY.matcher(request);
        assertThat(input.find()).as("input of embed request %s", request).isTrue();
        String embeddings = JSON_STRING.matcher(input.group(1)).results()
                .map(text -> topicVector(text.group(1)))
                .collect(Collectors.joining(", ", "[", "]"));
        respond(exchange, """
                {"model": "nomic-embed-text", "embeddings": %s}
                """.formatted(embeddings));
    }

    /// Places a text on the axis of every topic it mentions, plus a small constant third axis so
    /// that texts sharing no topic still have a non-zero vector.
    private static String topicVector(String text) {
        String lowerCase = text.toLowerCase(Locale.ROOT);
        double[] vector = new double[TOPICS.size() + 1];
        for (int axis = 0; axis < TOPICS.size(); axis++) {
            for (String stem : TOPICS.get(axis)) {
                if (lowerCase.contains(stem)) {
                    vector[axis] += 1.0;
                }
            }
        }
        vector[TOPICS.size()] = 0.1;
        return Arrays.stream(vector)
                .mapToObj(Double::toString)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static void respond(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(bytes);
        }
    }
}
