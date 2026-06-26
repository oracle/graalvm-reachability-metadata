/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_ai.spring_ai_openai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.openai.models.audio.AudioResponseFormat;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.moderation.ModerationPrompt;
import org.springframework.ai.moderation.ModerationResponse;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.ai.openai.OpenAiModerationModel;
import org.springframework.ai.openai.OpenAiModerationOptions;
import org.springframework.core.io.ByteArrayResource;

public class Spring_ai_openaiTest {

    private static final Duration CLIENT_TIMEOUT = Duration.ofSeconds(10);
    private static final String API_KEY = "test-api-key";

    @Test
    void chatModelUsesConfiguredLocalBaseUrlAndMapsAssistantMessage() throws IOException {
        try (OpenAiStubServer server = new OpenAiStubServer()) {
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .baseUrl(server.baseUrl())
                    .apiKey(API_KEY)
                    .model("gpt-4o-mini")
                    .temperature(0.0)
                    .maxTokens(32)
                    .timeout(CLIENT_TIMEOUT)
                    .maxRetries(0)
                    .build();
            OpenAiChatModel model = OpenAiChatModel.builder().options(options).build();

            ChatResponse response = model.call(new Prompt("Say hello from the stub"));

            assertThat(response.getResult().getOutput().getText()).isEqualTo("hello from local OpenAI stub");
            StubRequest request = server.singleRequest();
            assertThat(request.path()).isEqualTo("/v1/chat/completions");
            assertThat(request.authorization()).isEqualTo("Bearer " + API_KEY);
            assertThat(request.body()).contains("gpt-4o-mini", "Say hello from the stub");
        }
    }

    @Test
    void chatModelStreamsAssistantMessageChunksFromLocalEndpoint() throws IOException {
        try (OpenAiStubServer server = new OpenAiStubServer()) {
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .baseUrl(server.baseUrl())
                    .apiKey(API_KEY)
                    .model("gpt-4o-mini")
                    .temperature(0.0)
                    .maxTokens(32)
                    .timeout(CLIENT_TIMEOUT)
                    .maxRetries(0)
                    .build();
            OpenAiChatModel model = OpenAiChatModel.builder().options(options).build();

            List<ChatResponse> responses = model.stream(new Prompt("Stream a greeting"))
                    .collectList()
                    .block(CLIENT_TIMEOUT);

            assertThat(responses).isNotNull();
            StringBuilder streamedText = new StringBuilder();
            for (ChatResponse response : responses) {
                if (response.getResult() != null && response.getResult().getOutput() != null
                        && response.getResult().getOutput().getText() != null) {
                    streamedText.append(response.getResult().getOutput().getText());
                }
            }
            assertThat(streamedText.toString()).isEqualTo("hello streaming client");
            StubRequest request = server.singleRequest();
            assertThat(request.path()).isEqualTo("/v1/chat/completions");
            assertThat(request.authorization()).isEqualTo("Bearer " + API_KEY);
            assertThat(request.body()).contains("gpt-4o-mini", "Stream a greeting", "stream");
        }
    }

    @Test
    void embeddingModelPostsInputAndReturnsFloatingPointVector() throws IOException {
        try (OpenAiStubServer server = new OpenAiStubServer()) {
            OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                    .baseUrl(server.baseUrl())
                    .apiKey(API_KEY)
                    .model("text-embedding-3-small")
                    .dimensions(3)
                    .timeout(CLIENT_TIMEOUT)
                    .maxRetries(0)
                    .build();
            OpenAiEmbeddingModel model = OpenAiEmbeddingModel.builder().options(options).build();

            EmbeddingResponse response = model.call(new EmbeddingRequest(List.of("native image metadata"), options));

            assertThat(response.getResult().getOutput()).containsExactly(0.125f, 0.25f, 0.5f);
            StubRequest request = server.singleRequest();
            assertThat(request.path()).isEqualTo("/v1/embeddings");
            assertThat(request.authorization()).isEqualTo("Bearer " + API_KEY);
            assertThat(request.body()).contains("text-embedding-3-small", "native image metadata", "dimensions");
        }
    }

    @Test
    void imageModelReturnsGeneratedImageUrlFromLocalEndpoint() throws IOException {
        try (OpenAiStubServer server = new OpenAiStubServer()) {
            OpenAiImageOptions options = OpenAiImageOptions.builder()
                    .baseUrl(server.baseUrl())
                    .apiKey(API_KEY)
                    .model("dall-e-3")
                    .size("1024x1024")
                    .quality("standard")
                    .n(1)
                    .timeout(CLIENT_TIMEOUT)
                    .maxRetries(0)
                    .build();
            OpenAiImageModel model = OpenAiImageModel.builder().options(options).build();

            ImageResponse response = model.call(new ImagePrompt("a tiny test robot", options));

            assertThat(response.getResult().getOutput().getUrl()).isEqualTo("https://example.test/generated.png");
            StubRequest request = server.singleRequest();
            assertThat(request.path()).isEqualTo("/v1/images/generations");
            assertThat(request.authorization()).isEqualTo("Bearer " + API_KEY);
            assertThat(request.body()).contains("dall-e-3", "a tiny test robot", "1024x1024");
        }
    }

    @Test
    void audioSpeechModelReturnsBinaryAudioBytes() throws IOException {
        try (OpenAiStubServer server = new OpenAiStubServer()) {
            OpenAiAudioSpeechOptions options = OpenAiAudioSpeechOptions.builder()
                    .baseUrl(server.baseUrl())
                    .apiKey(API_KEY)
                    .model("tts-1")
                    .voice(OpenAiAudioSpeechOptions.Voice.ALLOY)
                    .responseFormat(OpenAiAudioSpeechOptions.AudioResponseFormat.MP3)
                    .speed(1.0)
                    .timeout(CLIENT_TIMEOUT)
                    .maxRetries(0)
                    .build();
            OpenAiAudioSpeechModel model = OpenAiAudioSpeechModel.builder().options(options).build();

            byte[] audio = model.call("Generate a short audio sample");
            TextToSpeechPrompt prompt = new TextToSpeechPrompt("Generate a second audio sample", options);
            TextToSpeechResponse response = model.call(prompt);

            assertThat(audio).containsExactly((byte) 1, (byte) 3, (byte) 3, (byte) 7);
            assertThat(response.getResult().getOutput()).containsExactly((byte) 1, (byte) 3, (byte) 3, (byte) 7);
            assertThat(server.requests()).hasSize(2);
            assertThat(server.requests()).allSatisfy(request -> {
                assertThat(request.path()).isEqualTo("/v1/audio/speech");
                assertThat(request.authorization()).isEqualTo("Bearer " + API_KEY);
            });
            assertThat(server.requests().get(0).body()).contains("tts-1", "alloy", "Generate a short audio sample");
            assertThat(server.requests().get(1).body()).contains("Generate a second audio sample");
        }
    }

    @Test
    void audioTranscriptionModelPostsMultipartResourceAndMapsText() throws IOException {
        try (OpenAiStubServer server = new OpenAiStubServer()) {
            OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
                    .baseUrl(server.baseUrl())
                    .apiKey(API_KEY)
                    .model("whisper-1")
                    .language("en")
                    .prompt("Use concise English")
                    .responseFormat(AudioResponseFormat.JSON)
                    .temperature(0.0f)
                    .timeout(CLIENT_TIMEOUT)
                    .maxRetries(0)
                    .build();
            OpenAiAudioTranscriptionModel model = OpenAiAudioTranscriptionModel.builder().options(options).build();
            ByteArrayResource audioResource = new NamedByteArrayResource(new byte[] { 0x52, 0x49, 0x46, 0x46 },
                    "sample.wav");

            AudioTranscriptionResponse response = model.call(new AudioTranscriptionPrompt(audioResource, options));

            assertThat(response.getResult().getOutput()).isEqualTo("transcribed by local stub");
            StubRequest request = server.singleRequest();
            assertThat(request.path()).isEqualTo("/v1/audio/transcriptions");
            assertThat(request.authorization()).isEqualTo("Bearer " + API_KEY);
            assertThat(request.contentType()).contains("multipart/form-data");
            assertThat(request.body()).contains("whisper-1", "Use concise English", "sample.wav");
        }
    }

    @Test
    void moderationModelMapsFlaggedResultCategoriesAndScores() throws IOException {
        try (OpenAiStubServer server = new OpenAiStubServer()) {
            OpenAiModerationOptions options = OpenAiModerationOptions.builder()
                    .baseUrl(server.baseUrl())
                    .apiKey(API_KEY)
                    .model("omni-moderation-latest")
                    .timeout(CLIENT_TIMEOUT)
                    .maxRetries(0)
                    .build();
            OpenAiModerationModel model = OpenAiModerationModel.builder().options(options).build();

            ModerationPrompt prompt = new ModerationPrompt("Please moderate this harmless text", options);
            ModerationResponse response = model.call(prompt);

            assertThat(response.getResult().getOutput().getId()).isEqualTo("modr-local-1");
            assertThat(response.getResult().getOutput().getModel()).isEqualTo("omni-moderation-latest");
            assertThat(response.getResult().getOutput().getResults()).hasSize(1);
            assertThat(response.getResult().getOutput().getResults().get(0).isFlagged()).isFalse();
            assertThat(response.getResult().getOutput().getResults().get(0).getCategories().isViolence()).isFalse();
            assertThat(response.getResult().getOutput().getResults().get(0).getCategoryScores().getViolence())
                    .isEqualTo(0.01d);
            StubRequest request = server.singleRequest();
            assertThat(request.path()).isEqualTo("/v1/moderations");
            assertThat(request.authorization()).isEqualTo("Bearer " + API_KEY);
            assertThat(request.body()).contains("omni-moderation-latest", "Please moderate this harmless text");
        }
    }

    @Test
    void optionBuildersExposeProviderConfigurationAndMutation() {
        OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
                .baseUrl("http://127.0.0.1:65535/v1")
                .apiKey(API_KEY)
                .model("gpt-4o-mini")
                .organizationId("org-local")
                .customHeaders(Collections.singletonMap("X-Test", "enabled"))
                .timeout(CLIENT_TIMEOUT)
                .maxRetries(0)
                .build();

        OpenAiChatOptions mutated = chatOptions.mutate().model("gpt-4.1-mini").maxCompletionTokens(64).build();

        assertThat(mutated.getBaseUrl()).isEqualTo("http://127.0.0.1:65535/v1");
        assertThat(mutated.getApiKey()).isEqualTo(API_KEY);
        assertThat(mutated.getOrganizationId()).isEqualTo("org-local");
        assertThat(mutated.getCustomHeaders()).containsEntry("X-Test", "enabled");
        assertThat(mutated.getModel()).isEqualTo("gpt-4.1-mini");
        assertThat(mutated.getMaxCompletionTokens()).isEqualTo(64);
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {

        private final String filename;

        private NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return this.filename;
        }
    }

    private static final class OpenAiStubServer implements AutoCloseable {

        private final HttpServer server;
        private final ExecutorService executor;
        private final List<StubRequest> requests = Collections.synchronizedList(new ArrayList<>());

        private OpenAiStubServer() throws IOException {
            this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            this.server.createContext("/", new OpenAiHandler(this.requests));
            this.executor = Executors.newSingleThreadExecutor();
            this.server.setExecutor(this.executor);
            this.server.start();
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + this.server.getAddress().getPort() + "/v1";
        }

        private StubRequest singleRequest() {
            assertThat(this.requests).hasSize(1);
            return this.requests.get(0);
        }

        private List<StubRequest> requests() {
            return List.copyOf(this.requests);
        }

        @Override
        public void close() {
            this.server.stop(0);
            this.executor.shutdownNow();
        }
    }

    private static final class OpenAiHandler implements HttpHandler {

        private final List<StubRequest> requests;

        private OpenAiHandler(List<StubRequest> requests) {
            this.requests = requests;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] requestBytes = exchange.getRequestBody().readAllBytes();
            String body = new String(requestBytes, StandardCharsets.UTF_8);
            String path = exchange.getRequestURI().getPath();
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            this.requests.add(new StubRequest(path, authorization, contentType, body));

            switch (path) {
                case "/v1/chat/completions" -> {
                    if (isStreamingChatRequest(body)) {
                        sendServerSentJsonEvents(exchange, """
                                {
                                  "id": "chatcmpl-stream-local-1",
                                  "object": "chat.completion.chunk",
                                  "created": 1710000002,
                                  "model": "gpt-4o-mini",
                                  "choices": [
                                    {
                                      "index": 0,
                                      "delta": {
                                        "role": "assistant",
                                        "content": "hello "
                                      },
                                      "finish_reason": null
                                    }
                                  ]
                                }
                                """, """
                                {
                                  "id": "chatcmpl-stream-local-1",
                                  "object": "chat.completion.chunk",
                                  "created": 1710000002,
                                  "model": "gpt-4o-mini",
                                  "choices": [
                                    {
                                      "index": 0,
                                      "delta": {
                                        "content": "streaming client"
                                      },
                                      "finish_reason": null
                                    }
                                  ]
                                }
                                """, """
                                {
                                  "id": "chatcmpl-stream-local-1",
                                  "object": "chat.completion.chunk",
                                  "created": 1710000002,
                                  "model": "gpt-4o-mini",
                                  "choices": [
                                    {
                                      "index": 0,
                                      "delta": {},
                                      "finish_reason": "stop"
                                    }
                                  ]
                                }
                                """);
                    }
                    else {
                        sendJson(exchange, """
                                {
                                  "id": "chatcmpl-local-1",
                                  "object": "chat.completion",
                                  "created": 1710000000,
                                  "model": "gpt-4o-mini",
                                  "choices": [
                                    {
                                      "index": 0,
                                      "message": {
                                        "role": "assistant",
                                        "content": "hello from local OpenAI stub"
                                      },
                                      "finish_reason": "stop"
                                    }
                                  ],
                                  "usage": {
                                    "prompt_tokens": 4,
                                    "completion_tokens": 5,
                                    "total_tokens": 9
                                  }
                                }
                                """);
                    }
                }
                case "/v1/embeddings" -> sendJson(exchange, """
                        {
                          "object": "list",
                          "model": "text-embedding-3-small",
                          "data": [
                            {
                              "object": "embedding",
                              "index": 0,
                              "embedding": [0.125, 0.25, 0.5]
                            }
                          ],
                          "usage": {
                            "prompt_tokens": 3,
                            "total_tokens": 3
                          }
                        }
                        """);
                case "/v1/images/generations" -> sendJson(exchange, """
                        {
                          "created": 1710000001,
                          "data": [
                            {
                              "url": "https://example.test/generated.png",
                              "revised_prompt": "a tiny test robot"
                            }
                          ]
                        }
                        """);
                case "/v1/audio/speech" -> sendBytes(exchange, "audio/mpeg", new byte[] { 1, 3, 3, 7 });
                case "/v1/audio/transcriptions" -> sendJson(exchange, """
                        {
                          "text": "transcribed by local stub"
                        }
                        """);
                case "/v1/moderations" -> sendJson(exchange, """
                        {
                          "id": "modr-local-1",
                          "model": "omni-moderation-latest",
                          "results": [
                            {
                              "flagged": false,
                              "categories": {
                                "sexual": false,
                                "hate": false,
                                "harassment": false,
                                "self-harm": false,
                                "sexual/minors": false,
                                "hate/threatening": false,
                                "violence/graphic": false,
                                "self-harm/intent": false,
                                "self-harm/instructions": false,
                                "harassment/threatening": false,
                                "violence": false,
                                "illicit": false,
                                "illicit/violent": false
                              },
                              "category_scores": {
                                "sexual": 0.0,
                                "hate": 0.0,
                                "harassment": 0.0,
                                "self-harm": 0.0,
                                "sexual/minors": 0.0,
                                "hate/threatening": 0.0,
                                "violence/graphic": 0.0,
                                "self-harm/intent": 0.0,
                                "self-harm/instructions": 0.0,
                                "harassment/threatening": 0.0,
                                "violence": 0.01,
                                "illicit": 0.0,
                                "illicit/violent": 0.0
                              },
                              "category_applied_input_types": {
                                "sexual": [],
                                "hate": [],
                                "harassment": [],
                                "self-harm": [],
                                "sexual/minors": [],
                                "hate/threatening": [],
                                "violence/graphic": [],
                                "self-harm/intent": [],
                                "self-harm/instructions": [],
                                "harassment/threatening": [],
                                "violence": [],
                                "illicit": [],
                                "illicit/violent": []
                              }
                            }
                          ]
                        }
                        """);
                default -> sendJson(exchange, 404, """
                        { "error": { "message": "unexpected test endpoint" } }
                        """);
            }
        }

        private static boolean isStreamingChatRequest(String body) {
            String compactBody = body.replace(" ", "")
                    .replace("\n", "")
                    .replace("\r", "")
                    .replace("\t", "");
            return compactBody.contains("\"stream\":true") || body.contains("Stream a greeting");
        }

        private static void sendJson(HttpExchange exchange, String body) throws IOException {
            sendJson(exchange, 200, body);
        }

        private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
            sendBytes(exchange, status, "application/json", body.getBytes(StandardCharsets.UTF_8));
        }

        private static void sendServerSentJsonEvents(HttpExchange exchange, String... jsonEvents)
                throws IOException {
            StringBuilder body = new StringBuilder();
            for (String jsonEvent : jsonEvents) {
                body.append("data: ").append(jsonEvent.replace("\n", "").trim()).append("\n\n");
            }
            body.append("data: [DONE]\n\n");
            byte[] eventBytes = body.toString().getBytes(StandardCharsets.UTF_8);
            sendBytes(exchange, "text/event-stream", eventBytes);
        }

        private static void sendBytes(HttpExchange exchange, String contentType, byte[] body) throws IOException {
            sendBytes(exchange, 200, contentType, body);
        }

        private static void sendBytes(HttpExchange exchange, int status, String contentType, byte[] body)
                throws IOException {
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        }
    }

    private record StubRequest(String path, String authorization, String contentType, String body) {
    }
}
