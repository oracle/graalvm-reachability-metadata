/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_langchain4j.langchain4j;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.tool.ToolErrorContext;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Controls households' devices through langchain4j dynamic tools: a `ToolProvider` that builds
 * tool specifications and executors per request from the household behind the `@MemoryId`,
 * with argument errors, execution failures and hallucinated tool names turned into tool results
 * the model reads instead of exceptions the caller sees.
 *
 * A smart home hub serves several households from one AI service. Each household owns a
 * different set of rooms with lights and may own a thermostat, so the hub only advertises the
 * tools and room names that household can use. When the model names a room that does not exist,
 * a thermostat that is offline, or a tool nobody offers, the hub reports the problem back to the
 * model so it can explain it to the resident. The test plays the Ollama server with scripted
 * assistant messages and inspects every chat request the hub sends.
 */
class SmartHomeDynamicToolsTest {

    private static final Pattern STRING_ARGUMENT = Pattern.compile("\"(\\w+)\"\\s*:\\s*\"([^\"]*)\"");

    private static final Pattern LITERAL_ARGUMENT = Pattern.compile("\"(\\w+)\"\\s*:\\s*(true|false|-?\\d+)");

    private final List<String> chatRequests = new CopyOnWriteArrayList<>();

    private final Deque<String> scriptedMessages = new ArrayDeque<>();

    private final Map<String, Household> households = new LinkedHashMap<>();

    private final SmartHomeHub hub = new SmartHomeHub(households);

    private HttpServer ollama;

    private HomeAssistant assistant;

    /** Devices one household owns, as the hub's device registry tracks them. */
    static final class Household {

        private final Map<String, Boolean> lights = new LinkedHashMap<>();

        private final boolean hasThermostat;

        private boolean thermostatOnline = true;

        private Integer thermostatCelsius;

        Household(List<String> rooms, boolean hasThermostat) {
            rooms.forEach(room -> lights.put(room, false));
            this.hasThermostat = hasThermostat;
        }
    }

    /** Supplies each household's tools on every request and records the errors it reports. */
    static final class SmartHomeHub implements ToolProvider {

        private final Map<String, Household> households;

        private final List<String> toolRequests = new CopyOnWriteArrayList<>();

        private final List<String> reportedErrors = new CopyOnWriteArrayList<>();

        SmartHomeHub(Map<String, Household> households) {
            this.households = households;
        }

        @Override
        public ToolProviderResult provideTools(ToolProviderRequest request) {
            String householdId = String.valueOf(request.chatMemoryId());
            toolRequests.add(householdId + ": " + request.userMessage().singleText());
            Household household = households.get(householdId);

            ToolProviderResult.Builder tools = ToolProviderResult.builder();
            ToolSpecification switchLight = ToolSpecification.builder()
                    .name("switchLight")
                    .description("Turns the light in one room of the home on or off")
                    .parameters(JsonObjectSchema.builder()
                            .addEnumProperty("room", List.copyOf(household.lights.keySet()), "the room to light")
                            .addBooleanProperty("on", "true to turn the light on, false to turn it off")
                            .required("room", "on")
                            .build())
                    .build();
            tools.add(switchLight, (toolRequest, memoryId) -> {
                Map<String, String> arguments = arguments(toolRequest);
                String room = arguments.get("room");
                if (!household.lights.containsKey(room)) {
                    throw new ToolArgumentsException("There is no light in room '" + room + "'");
                }
                boolean on = Boolean.parseBoolean(arguments.get("on"));
                household.lights.put(room, on);
                return room + " light is now " + (on ? "on" : "off");
            });

            if (household.hasThermostat) {
                ToolSpecification setThermostat = ToolSpecification.builder()
                        .name("setThermostat")
                        .description("Sets the target temperature of the home thermostat")
                        .parameters(JsonObjectSchema.builder()
                                .addIntegerProperty("celsius", "the target temperature in degrees Celsius")
                                .required("celsius")
                                .build())
                        .build();
                tools.add(setThermostat, (toolRequest, memoryId) -> {
                    if (!household.thermostatOnline) {
                        throw new IllegalStateException("The thermostat is offline");
                    }
                    household.thermostatCelsius = Integer.valueOf(arguments(toolRequest).get("celsius"));
                    return "thermostat set to " + household.thermostatCelsius + " C";
                });
            }
            return tools.build();
        }

        ToolErrorHandlerResult invalidArguments(Throwable error, ToolErrorContext context) {
            reportedErrors.add(context.memoryId() + " arguments " + context.toolExecutionRequest().name());
            return ToolErrorHandlerResult.text("Invalid arguments: " + error.getMessage());
        }

        ToolErrorHandlerResult deviceFailure(Throwable error, ToolErrorContext context) {
            reportedErrors.add(context.memoryId() + " execution " + context.toolExecutionRequest().name());
            return ToolErrorHandlerResult.text("Device error: " + error.getMessage());
        }

        ToolExecutionResultMessage unknownTool(ToolExecutionRequest request) {
            reportedErrors.add("unknown tool " + request.name());
            return ToolExecutionResultMessage.from(request, "No tool named " + request.name() + " in this home");
        }

        /// Reads the flat string, boolean and integer arguments of a tool call.
        private static Map<String, String> arguments(ToolExecutionRequest request) {
            Map<String, String> arguments = new LinkedHashMap<>();
            for (Pattern pattern : List.of(STRING_ARGUMENT, LITERAL_ARGUMENT)) {
                Matcher argument = pattern.matcher(request.arguments());
                while (argument.find()) {
                    arguments.put(argument.group(1), argument.group(2));
                }
            }
            return arguments;
        }
    }

    interface HomeAssistant {

        @SystemMessage("You control the resident's home. Use only the tools you are given.")
        Result<String> chat(@MemoryId String householdId, @UserMessage String command);
    }

    @BeforeEach
    void startHub() throws IOException {
        households.put("lakeview", new Household(List.of("kitchen", "porch"), true));
        households.put("loft", new Household(List.of("studio"), false));

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
        assistant = AiServices.builder(HomeAssistant.class)
                .chatModel(model)
                .chatMemoryProvider(householdId -> MessageWindowChatMemory.withMaxMessages(20))
                .toolProvider(hub)
                .toolArgumentsErrorHandler(hub::invalidArguments)
                .toolExecutionErrorHandler(hub::deviceFailure)
                .hallucinatedToolNameStrategy(hub::unknownTool)
                .build();
    }

    @AfterEach
    void stopHub() {
        ollama.stop(0);
    }

    @Test
    void advertisesAndRunsOnlyTheToolsEachHouseholdOwns() {
        scriptedMessages.add(toolCalls("""
                {"function": {"name": "switchLight", "arguments": {"room": "studio", "on": true}}}"""));
        scriptedMessages.add(answer("The studio light is on."));
        Result<String> loft = assistant.chat("loft", "Turn on the studio light");

        assertThat(loft.content()).isEqualTo("The studio light is on.");
        assertThat(households.get("loft").lights).containsEntry("studio", true);
        assertThat(loft.toolExecutions())
                .extracting(execution -> execution.request().name(), ToolExecution::result, ToolExecution::hasFailed)
                .containsExactly(tuple("switchLight", "studio light is now on", false));
        assertThat(chatRequests.get(0))
                .contains("You control the resident's home.", "switchLight", "\"studio\"")
                .doesNotContain("setThermostat", "kitchen");
        assertThat(chatRequests.get(1)).contains("studio light is now on");

        scriptedMessages.add(toolCalls("""
                {"function": {"name": "setThermostat", "arguments": {"celsius": 21}}},
                {"function": {"name": "switchLight", "arguments": {"room": "porch", "on": true}}}"""));
        scriptedMessages.add(answer("It will be 21 C inside and the porch light is on."));
        Result<String> lakeview = assistant.chat("lakeview", "Warm the house to 21 and light the porch");

        assertThat(lakeview.content()).isEqualTo("It will be 21 C inside and the porch light is on.");
        Household lakeviewHome = households.get("lakeview");
        assertThat(lakeviewHome.thermostatCelsius).isEqualTo(21);
        assertThat(lakeviewHome.lights).containsEntry("porch", true).containsEntry("kitchen", false);
        assertThat(lakeview.toolExecutions())
                .extracting(execution -> execution.request().name(), ToolExecution::result)
                .containsExactly(
                        tuple("setThermostat", "thermostat set to 21 C"),
                        tuple("switchLight", "porch light is now on"));
        assertThat(chatRequests.get(2))
                .contains("setThermostat", "the target temperature in degrees Celsius", "\"kitchen\"", "\"porch\"")
                .doesNotContain("studio", "Turn on the studio light");

        assertThat(hub.toolRequests).containsExactly(
                "loft: Turn on the studio light",
                "lakeview: Warm the house to 21 and light the porch");
        assertThat(hub.reportedErrors).isEmpty();
        assertThat(chatRequests).hasSize(4);
    }

    @Test
    void reportsBadArgumentsFailedDevicesAndUnknownToolsToTheModel() {
        Household lakeviewHome = households.get("lakeview");
        lakeviewHome.thermostatOnline = false;

        scriptedMessages.add(toolCalls("""
                {"function": {"name": "switchLight", "arguments": {"room": "garage", "on": true}}},
                {"function": {"name": "setThermostat", "arguments": {"celsius": 19}}},
                {"function": {"name": "unlockFrontDoor", "arguments": {}}}"""));
        scriptedMessages.add(answer("There is no garage light, the thermostat is offline, and I cannot unlock doors."));
        Result<String> reply = assistant.chat("lakeview", "Light the garage, cool to 19 and unlock the front door");

        assertThat(reply.content())
                .isEqualTo("There is no garage light, the thermostat is offline, and I cannot unlock doors.");
        assertThat(lakeviewHome.lights).containsOnly(Map.entry("kitchen", false), Map.entry("porch", false));
        assertThat(lakeviewHome.thermostatCelsius).isNull();

        assertThat(hub.reportedErrors).containsExactlyInAnyOrder(
                "lakeview arguments switchLight",
                "lakeview execution setThermostat",
                "unknown tool unlockFrontDoor");
        List<String> failed = new ArrayList<>();
        reply.toolExecutions().stream()
                .filter(ToolExecution::hasFailed)
                .forEach(execution -> failed.add(execution.request().name() + ": " + execution.result()));
        assertThat(failed).containsExactly(
                "switchLight: Invalid arguments: There is no light in room 'garage'",
                "setThermostat: Device error: The thermostat is offline");

        assertThat(chatRequests).hasSize(2);
        assertThat(chatRequests.get(1)).contains(
                "Invalid arguments: There is no light in room 'garage'",
                "Device error: The thermostat is offline",
                "No tool named unlockFrontDoor in this home");
    }

    private static String toolCalls(String calls) {
        return """
                {"role": "assistant", "content": "", "tool_calls": [%s]}""".formatted(calls);
    }

    private static String answer(String text) {
        return """
                {"role": "assistant", "content": "%s"}""".formatted(text);
    }

    private void answerChat(HttpExchange exchange) throws IOException {
        String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        chatRequests.add(request);

        String message;
        synchronized (scriptedMessages) {
            message = scriptedMessages.poll();
        }
        assertThat(message).as("scripted reply for chat request %s", request).isNotNull();
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
}
