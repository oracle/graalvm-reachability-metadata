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
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.tool.ToolExecution;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Answers warehouse questions through langchain4j tool calling: `@Tool`-annotated application
 * methods that an AI service advertises to the model, executes when the model requests them, and
 * feeds back into the conversation before the final answer.
 *
 * The warehouse registers a `StockTools` object with `AiServices`. The test plays the Ollama
 * server: on the first chat round it asks for tool calls, and once the request carries the tool
 * results it answers in prose built from them. The scenario checks the advertised tool schema,
 * the arguments the application method received, and the tool executions the result reports.
 */
class WarehouseAssistantToolsTest {

    private static final Pattern TOOL_RESULT_MESSAGE = Pattern.compile("\"role\"\\s*:\\s*\"tool\"", Pattern.CASE_INSENSITIVE);

    private final List<String> chatRequests = new CopyOnWriteArrayList<>();

    private final StockTools stockTools = new StockTools();

    private HttpServer ollama;

    private WarehouseAssistant assistant;

    /** Inventory operations the application exposes to the model. */
    static final class StockTools {

        private final Map<String, Integer> stock = new ConcurrentHashMap<>(Map.of("SKU-100", 12, "SKU-200", 3));

        private final List<String> calls = new CopyOnWriteArrayList<>();

        @Tool("Returns how many units of a product are on hand")
        int unitsOnHand(@P("the product SKU, for example SKU-100") String sku) {
            calls.add("unitsOnHand(" + sku + ")");
            return stock.getOrDefault(sku, 0);
        }

        @Tool("Reserves units of a product for an order and returns the units left")
        int reserve(@P("the product SKU") String sku, @P("how many units to reserve") int quantity) {
            calls.add("reserve(" + sku + ", " + quantity + ")");
            return stock.merge(sku, -quantity, Integer::sum);
        }
    }

    interface WarehouseAssistant {

        @SystemMessage("You are the warehouse assistant. Use the tools to check and reserve stock.")
        Result<String> chat(String question);
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
        assistant = AiServices.builder(WarehouseAssistant.class)
                .chatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
                .tools(stockTools)
                .build();
    }

    @AfterEach
    void stopModelServer() {
        ollama.stop(0);
    }

    @Test
    void executesRequestedToolsAndAnswersFromTheirResults() {
        Result<String> answer = assistant.chat("Reserve 5 units of SKU-100 if we have enough.");

        assertThat(answer.content()).isEqualTo("Reserved 5 units of SKU-100; 7 remain in stock.");
        assertThat(stockTools.calls).containsExactly("unitsOnHand(SKU-100)", "reserve(SKU-100, 5)");
        assertThat(answer.toolExecutions())
                .extracting(execution -> execution.request().name(), ToolExecution::result)
                .containsExactly(
                        tuple("unitsOnHand", "12"),
                        tuple("reserve", "7"));

        assertThat(chatRequests).hasSize(2);
        assertThat(chatRequests.get(0))
                .contains("You are the warehouse assistant.")
                .contains("unitsOnHand", "Returns how many units of a product are on hand")
                .contains("reserve", "how many units to reserve", "\"integer\"");
        assertThat(chatRequests.get(1)).containsPattern(TOOL_RESULT_MESSAGE).contains("12", "7");
    }

    private void answerChat(HttpExchange exchange) throws IOException {
        String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        chatRequests.add(request);

        String message;
        if (!TOOL_RESULT_MESSAGE.matcher(request).find()) {
            // Like a real model, fill the arguments under the parameter names the tool schema advertises.
            List<String> skuParameters = requiredParameters(request, "unitsOnHand");
            List<String> reserveParameters = requiredParameters(request, "reserve");
            message = """
                    {"role": "assistant", "content": "", "tool_calls": [
                      {"function": {"name": "unitsOnHand", "arguments": {"%s": "SKU-100"}}},
                      {"function": {"name": "reserve", "arguments": {"%s": "SKU-100", "%s": 5}}}
                    ]}""".formatted(skuParameters.get(0), reserveParameters.get(0), reserveParameters.get(1));
        } else {
            message = """
                    {"role": "assistant", "content": "Reserved 5 units of SKU-100; 7 remain in stock."}""";
        }
        String response = """
                {"model": "llama3.1", "created_at": "2025-11-04T10:15:30Z", "message": %s,
                 "done": true, "done_reason": "stop", "prompt_eval_count": 64, "eval_count": 21}
                """.formatted(message);

        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(bytes);
        }
    }

    /// Reads the required parameter names, in declaration order, of the named tool in a chat request.
    private static List<String> requiredParameters(String request, String toolName) {
        Matcher tool = Pattern.compile(
                "\"name\"\\s*:\\s*\"" + toolName + "\".*?\"required\"\\s*:\\s*\\[([^\\]]*)]", Pattern.DOTALL)
                .matcher(request);
        assertThat(tool.find()).as("schema of tool %s", toolName).isTrue();
        return Pattern.compile("\"([^\"]+)\"").matcher(tool.group(1)).results()
                .map(name -> name.group(1))
                .toList();
    }
}
