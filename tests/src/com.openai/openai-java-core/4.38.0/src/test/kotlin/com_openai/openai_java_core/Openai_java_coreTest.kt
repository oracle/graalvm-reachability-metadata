/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_openai.openai_java_core

import com.fasterxml.jackson.databind.json.JsonMapper
import com.openai.client.OpenAIClientImpl
import com.openai.core.ClientOptions
import com.openai.core.RequestOptions
import com.openai.core.http.Headers
import com.openai.core.http.HttpClient
import com.openai.core.http.HttpRequest
import com.openai.core.http.HttpResponse
import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.embeddings.EmbeddingCreateParams
import com.openai.models.embeddings.EmbeddingModel
import com.openai.models.responses.ResponseCreateParams
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class Openai_java_coreTest {
    @Test
    fun serializesChatRequestsAndParsesSynchronousAndAsynchronousResponses(): Unit {
        val chatResponse = """
            {
              "id": "chatcmpl-fixture",
              "object": "chat.completion",
              "created": 1700000000,
              "model": "gpt-4o-mini",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "Fixture response"
                  },
                  "finish_reason": "stop"
                }
              ],
              "usage": {
                "prompt_tokens": 5,
                "completion_tokens": 2,
                "total_tokens": 7
              }
            }
        """.trimIndent()
        val httpClient = RecordingHttpClient(listOf(chatResponse, chatResponse))
        val options = clientOptions(httpClient)
        val client = OpenAIClientImpl(options)

        try {
            val params = ChatCompletionCreateParams.builder()
                .addSystemMessage("Answer concisely.")
                .addUserMessage("What is in this fixture?")
                .model(ChatModel.GPT_4O_MINI)
                .temperature(0.0)
                .maxCompletionTokens(32L)
                .build()
            val mapper: JsonMapper = options.jsonMapper
            val serialized = mapper.writeValueAsString(params._body())
            val serializedJson = mapper.readTree(serialized)

            assertThat(serializedJson.path("messages").size()).isEqualTo(2)
            assertThat(serializedJson.path("messages").get(0).path("role").asText())
                .isEqualTo("system")
            assertThat(serializedJson.path("messages").get(0).path("content").asText())
                .isEqualTo("Answer concisely.")
            assertThat(serializedJson.path("messages").get(1).path("role").asText())
                .isEqualTo("user")
            assertThat(serializedJson.path("messages").get(1).path("content").asText())
                .isEqualTo("What is in this fixture?")
            assertThat(serializedJson.path("model").asText()).isEqualTo(ChatModel.GPT_4O_MINI.asString())

            val synchronous = client.chat().completions().create(params)
            val asynchronous = client.async().chat().completions().create(params).get(30, TimeUnit.SECONDS)

            assertThat(synchronous.id()).isEqualTo("chatcmpl-fixture")
            val synchronousChoice = synchronous.choices().single()
            assertThat(synchronousChoice.message().content().get()).isEqualTo("Fixture response")
            assertThat(synchronousChoice.finishReason().asString()).isEqualTo("stop")
            assertThat(asynchronous.choices()).hasSize(1)
            assertThat(asynchronous.choices()[0].message().content().get()).isEqualTo("Fixture response")
            assertThat(httpClient.requests).hasSize(2)
            assertThat(httpClient.requests.map { request -> request.url() })
                .containsOnly("https://example.test/chat/completions")
            assertThat(httpClient.requestBodies).allSatisfy { body ->
                assertThat(body).contains("\"messages\"", "\"model\"", "What is in this fixture?")
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun serializesEmbeddingRequestsAndParsesEmbeddingResponses(): Unit {
        val response = """
            {
              "object": "list",
              "data": [
                {
                  "object": "embedding",
                  "embedding": [0.25, -0.5],
                  "index": 0
                }
              ],
              "model": "text-embedding-3-small",
              "usage": {
                "prompt_tokens": 3,
                "total_tokens": 3
              }
            }
        """.trimIndent()
        val httpClient = RecordingHttpClient(listOf(response))
        val options = clientOptions(httpClient)
        val client = OpenAIClientImpl(options)

        try {
            val params = EmbeddingCreateParams.builder()
                .input("A deterministic embedding fixture")
                .model(EmbeddingModel.TEXT_EMBEDDING_3_SMALL)
                .dimensions(2L)
                .user("test-user")
                .build()
            val mapper = options.jsonMapper
            val serializedJson = mapper.readTree(mapper.writeValueAsString(params._body()))
            val embedding = client.embeddings().create(params)

            assertThat(serializedJson.path("input").asText()).isEqualTo("A deterministic embedding fixture")
            assertThat(serializedJson.path("model").asText())
                .isEqualTo(EmbeddingModel.TEXT_EMBEDDING_3_SMALL.asString())
            assertThat(serializedJson.path("dimensions").asLong()).isEqualTo(2L)
            assertThat(embedding.model()).isEqualTo("text-embedding-3-small")
            val embeddingValue = embedding.data().single()
            assertThat(embeddingValue.index()).isZero()
            assertThat(embeddingValue.embedding()).containsExactly(0.25f, -0.5f)
            assertThat(httpClient.requests.single().url()).isEqualTo("https://example.test/embeddings")
            assertThat(httpClient.requestBodies.single()).contains("\"input\"", "\"dimensions\":2")
        } finally {
            client.close()
        }
    }

    @Test
    fun serializesResponsesRequestUnionsAndAdditionalParameters(): Unit {
        val httpClient = RecordingHttpClient(emptyList())
        val options = clientOptions(httpClient)
        val params = ResponseCreateParams.builder()
            .model("gpt-4o-mini")
            .input("Summarize this fixture")
            .instructions("Return one sentence.")
            .temperature(0.0)
            .maxOutputTokens(32L)
            .putAdditionalBodyProperty("fixture_flag", com.openai.core.JsonValue.from(true))
            .build()

        try {
            val serialized = options.jsonMapper.writeValueAsString(params._body())
            val serializedJson = options.jsonMapper.readTree(serialized)

            assertThat(serializedJson.path("model").asText()).isEqualTo("gpt-4o-mini")
            assertThat(serializedJson.path("input").asText()).isEqualTo("Summarize this fixture")
            assertThat(serializedJson.path("instructions").asText()).isEqualTo("Return one sentence.")
            assertThat(serializedJson.path("max_output_tokens").asLong()).isEqualTo(32L)
            assertThat(serializedJson.path("fixture_flag").asBoolean()).isTrue()
        } finally {
            options.close()
        }
    }

    @Test
    fun streamsChatCompletionChunksUntilDone(): Unit {
        val streamResponse = """
            data: {"id":"chatcmpl-stream","object":"chat.completion.chunk","created":1700000000,"model":"gpt-4o-mini","choices":[{"index":0,"delta":{"role":"assistant","content":"Fixture"},"finish_reason":null}]}

            data: {"id":"chatcmpl-stream","object":"chat.completion.chunk","created":1700000000,"model":"gpt-4o-mini","choices":[{"index":0,"delta":{"content":" response"},"finish_reason":"stop"}]}

            data: [DONE]

        """.trimIndent()
        val httpClient = RecordingHttpClient(listOf(streamResponse))
        val client = OpenAIClientImpl(clientOptions(httpClient))
        val params = ChatCompletionCreateParams.builder()
            .addUserMessage("Stream this fixture")
            .model(ChatModel.GPT_4O_MINI)
            .build()

        try {
            client.chat().completions().createStreaming(params).use { response ->
                val chunks = response.stream().toList()

                assertThat(chunks).hasSize(2)
                assertThat(chunks.map { chunk -> chunk.id() }).containsOnly("chatcmpl-stream")
                assertThat(chunks.map { chunk -> chunk.choices().single().delta().content().get() })
                    .containsExactly("Fixture", " response")
                assertThat(chunks.last().choices().single().finishReason().get().asString())
                    .isEqualTo("stop")
            }
            assertThat(httpClient.requests.single().url())
                .isEqualTo("https://example.test/chat/completions")
            assertThat(httpClient.requests.single().headers.values("Accept"))
                .containsExactly("text/event-stream")
            assertThat(httpClient.requestBodies.single()).contains("\"stream\":true")
        } finally {
            client.close()
        }
    }

    private fun clientOptions(httpClient: HttpClient): ClientOptions =
        ClientOptions.builder()
            .httpClient(httpClient)
            .apiKey("test-key")
            .baseUrl("https://example.test")
            .build()

    private class RecordingHttpClient(responses: List<String>) : HttpClient {
        private val responses = ArrayDeque(responses)
        val requests = mutableListOf<HttpRequest>()
        val requestBodies = mutableListOf<String>()

        override fun execute(request: HttpRequest, requestOptions: RequestOptions): HttpResponse {
            requests += request
            val body = request.body
            if (body != null) {
                val output = ByteArrayOutputStream()
                body.writeTo(output)
                body.close()
                requestBodies += String(output.toByteArray(), StandardCharsets.UTF_8)
            }
            return FixtureHttpResponse(responses.removeFirst())
        }

        override fun executeAsync(
            request: HttpRequest,
            requestOptions: RequestOptions,
        ): CompletableFuture<HttpResponse> = CompletableFuture.completedFuture(execute(request, requestOptions))

        override fun close(): Unit = Unit
    }

    private class FixtureHttpResponse(private val response: String) : HttpResponse {
        override fun statusCode(): Int = 200

        override fun headers(): Headers = Headers.builder()
            .put("x-request-id", "fixture-request")
            .build()

        override fun body(): InputStream = ByteArrayInputStream(response.toByteArray(StandardCharsets.UTF_8))

        override fun close(): Unit = Unit
    }
}
