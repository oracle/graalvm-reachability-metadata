/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_openai.openai_java_core

import com.openai.azure.credential.AzureApiKeyCredential
import com.openai.client.OpenAIClientImpl
import com.openai.core.ClientOptions
import com.openai.core.JsonValue
import com.openai.core.RequestOptions
import com.openai.core.Timeout
import com.openai.core.http.Headers
import com.openai.core.http.HttpClient
import com.openai.core.http.HttpRequest
import com.openai.core.http.HttpResponse
import com.openai.core.http.QueryParams
import com.openai.credential.BearerTokenCredential
import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.embeddings.EmbeddingCreateParams
import com.openai.models.embeddings.EmbeddingModel
import com.openai.models.files.FileCreateParams
import com.openai.models.files.FileObject
import com.openai.models.files.FilePurpose
import com.openai.models.moderations.ModerationCreateParams
import com.openai.models.moderations.ModerationModel
import com.openai.models.responses.ResponseCreateParams
import com.openai.models.responses.ResponseStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

public class Openai_java_coreTest {
    @Test
    fun clientOptionsTimeoutsCredentialsAndMultimapBuildersRemainUsable() {
        val timeout: Timeout = Timeout.builder()
            .connect(Duration.ofSeconds(10))
            .read(Duration.ofSeconds(10))
            .write(Duration.ofSeconds(10))
            .request(Duration.ofSeconds(10))
            .build()
        val headers: Headers = Headers.builder()
            .put("X-Test", "one")
            .put("x-test", "two")
            .replace("X-Replace", listOf("final"))
            .build()
        val queryParams: QueryParams = QueryParams.builder()
            .put("include", listOf("usage", "messages"))
            .replace("limit", "2")
            .build()
        val bearer = BearerTokenCredential.create { "dynamic-token" } as BearerTokenCredential
        val azure = AzureApiKeyCredential.create("initial-key") as AzureApiKeyCredential

        val options: ClientOptions = ClientOptions.builder()
            .baseUrl("https://example.invalid/v1")
            .apiKey("sk-test")
            .adminApiKey("admin-test")
            .organization("org-test")
            .project("project-test")
            .webhookSecret("whsec-test")
            .httpClient(RecordingHttpClient(emptyMap()))
            .timeout(timeout)
            .credential(bearer)
            .headers(headers)
            .queryParams(queryParams)
            .maxRetries(0)
            .responseValidation(true)
            .build()

        assertThat(options.baseUrl()).isEqualTo("https://example.invalid/v1")
        assertThat(options.apiKey()).isEmpty()
        assertThat(options.adminApiKey()).contains("admin-test")
        assertThat(options.organization()).contains("org-test")
        assertThat(options.project()).contains("project-test")
        assertThat(options.webhookSecret()).contains("whsec-test")
        assertThat(options.timeout).isEqualTo(timeout)
        assertThat(options.headers.values("X-Test")).containsExactly("one", "two")
        assertThat(options.queryParams.values("include")).containsExactly("usage", "messages")
        assertThat(options.toBuilder().credential(BearerTokenCredential.create("static-token")).build().apiKey()).isEmpty()
        assertThat(bearer.token()).isEqualTo("dynamic-token")
        assertThat(azure.update("updated-key").apiKey()).isEqualTo("updated-key")
    }

    @Test
    fun jsonValuesAndGeneratedParameterBuildersPreserveKnownAndUnknownData() {
        val jsonObject = JsonValue.from(
            mapOf(
                "enabled" to true,
                "threshold" to 7,
                "labels" to listOf("safe", "review"),
            ),
        )
        @Suppress("UNCHECKED_CAST")
        val converted: Map<String, Any?> = jsonObject.convert(Map::class.java) as Map<String, Any?>
        val customModel: ChatModel = ChatModel.of("custom-chat-model")
        val responseStatus: ResponseStatus = ResponseStatus.of("completed")

        val responseParams: ResponseCreateParams = ResponseCreateParams.builder()
            .model(customModel)
            .input("Summarize this in one sentence")
            .instructions("Be concise")
            .maxOutputTokens(64)
            .parallelToolCalls(false)
            .temperature(0.2)
            .putAdditionalBodyProperty("metadata", jsonObject)
            .putAdditionalHeader("X-Trace", "trace-1")
            .putAdditionalQueryParam("debug", "true")
            .build()
        val moderationParams: ModerationCreateParams = ModerationCreateParams.builder()
            .inputOfStrings(listOf("hello", "world"))
            .model(ModerationModel.OMNI_MODERATION_LATEST)
            .putAdditionalBodyProperty("policy", JsonValue.from("strict"))
            .build()

        assertThat(converted).containsEntry("enabled", true)
        assertThat(customModel.asString()).isEqualTo("custom-chat-model")
        assertThat(responseStatus.asString()).isEqualTo("completed")
        assertThat(responseStatus.isValid()).isTrue()
        assertThat(responseParams.model()).isPresent
        assertThat(responseParams.input().get().asText()).contains("Summarize this in one sentence")
        assertThat(responseParams.instructions()).contains("Be concise")
        assertThat(responseParams.parallelToolCalls()).contains(false)
        assertThat(responseParams._additionalBodyProperties()).containsKey("metadata")
        assertThat(responseParams._headers().values("X-Trace")).containsExactly("trace-1")
        assertThat(responseParams._queryParams().values("debug")).containsExactly("true")
        assertThat(moderationParams.input().asStrings()).containsExactly("hello", "world")
        assertThat(moderationParams.model()).contains(ModerationModel.OMNI_MODERATION_LATEST)
    }

    @Test
    fun blockingChatCompletionServiceBuildsRequestAndParsesTypedResponse() {
        val httpClient = RecordingHttpClient(
            mapOf(
                "/chat/completions" to """
                    {
                      "id": "chatcmpl-test",
                      "object": "chat.completion",
                      "created": 1710000000,
                      "model": "gpt-4o-mini",
                      "choices": [
                        {
                          "index": 0,
                          "message": {"role": "assistant", "content": "pong"},
                          "finish_reason": "stop"
                        }
                      ],
                      "usage": {"prompt_tokens": 3, "completion_tokens": 1, "total_tokens": 4}
                    }
                """.trimIndent(),
            ),
        )
        val client = OpenAIClientImpl(testClientOptions(httpClient))

        try {
            val params: ChatCompletionCreateParams = ChatCompletionCreateParams.builder()
                .model(ChatModel.GPT_4O_MINI)
                .addSystemMessage("Answer with a single token")
                .addUserMessage("ping")
                .maxCompletionTokens(8)
                .temperature(0.0)
                .putAdditionalHeader("X-Request-Feature", "chat-test")
                .putAdditionalQueryParam("beta", "true")
                .putAdditionalBodyProperty("custom_flag", JsonValue.from(true))
                .build()

            val completion = client.chat().completions().create(params, requestOptions())

            assertThat(completion.id()).isEqualTo("chatcmpl-test")
            assertThat(completion.model()).isEqualTo("gpt-4o-mini")
            assertThat(completion.choices()).hasSize(1)
            assertThat(completion.choices()[0].message().content()).contains("pong")
            assertThat(completion.usage().get().totalTokens()).isEqualTo(4)
        } finally {
            client.close()
        }

        val call = httpClient.singleCall()
        assertThat(call.request.method.name).isEqualTo("POST")
        assertThat(call.request.pathSegments).containsExactly("chat", "completions")
        assertThat(call.request.url()).startsWith("https://example.invalid/v1/chat/completions")
        assertThat(call.request.headers.values("Authorization")).containsExactly("Bearer sk-test")
        assertThat(call.request.headers.values("OpenAI-Organization")).containsExactly("org-test")
        assertThat(call.request.headers.values("OpenAI-Project")).containsExactly("project-test")
        assertThat(call.request.headers.values("X-Request-Feature")).containsExactly("chat-test")
        assertThat(call.request.queryParams.values("client-query")).containsExactly("present")
        assertThat(call.request.queryParams.values("beta")).containsExactly("true")
        assertThat(call.body).contains("\"model\":\"gpt-4o-mini\"")
        assertThat(call.body).contains("\"role\":\"system\"")
        assertThat(call.body).contains("\"content\":\"ping\"")
        assertThat(call.body).contains("\"custom_flag\":true")
        assertThat(call.options.timeout).isEqualTo(requestOptions().timeout)
    }

    @Test
    fun blockingResponsesServiceBuildsRequestAndParsesOutputMessage() {
        val httpClient = RecordingHttpClient(
            mapOf(
                "/responses" to """
                    {
                      "id": "resp-test",
                      "object": "response",
                      "created_at": 1710000000,
                      "model": "gpt-4o-mini",
                      "output": [
                        {
                          "id": "msg-test",
                          "type": "message",
                          "role": "assistant",
                          "status": "completed",
                          "content": [
                            {"type": "output_text", "text": "A concise response.", "annotations": []}
                          ]
                        }
                      ],
                      "parallel_tool_calls": false,
                      "tool_choice": "auto",
                      "tools": [],
                      "status": "completed",
                      "usage": {
                        "input_tokens": 5,
                        "input_tokens_details": {"cached_tokens": 1},
                        "output_tokens": 4,
                        "output_tokens_details": {"reasoning_tokens": 0},
                        "total_tokens": 9
                      }
                    }
                """.trimIndent(),
            ),
        )
        val client = OpenAIClientImpl(testClientOptions(httpClient))

        try {
            val params: ResponseCreateParams = ResponseCreateParams.builder()
                .model(ChatModel.GPT_4O_MINI)
                .input("Explain native image metadata in one sentence")
                .instructions("Keep the reply short")
                .maxOutputTokens(16)
                .parallelToolCalls(false)
                .putAdditionalHeader("X-Request-Feature", "responses-test")
                .putAdditionalBodyProperty("trace", JsonValue.from("responses-test"))
                .build()

            val response = client.responses().create(params, requestOptions())
            val outputItem = response.output().single()
            val message = outputItem.asMessage()
            val content = message.content().single().asOutputText()

            assertThat(response.id()).isEqualTo("resp-test")
            assertThat(response.model().isChat()).isTrue()
            assertThat(response.model().asChat()).isEqualTo(ChatModel.GPT_4O_MINI)
            assertThat(response.status()).contains(ResponseStatus.COMPLETED)
            assertThat(response.parallelToolCalls()).isFalse()
            assertThat(response.usage().get().totalTokens()).isEqualTo(9)
            assertThat(outputItem.isMessage()).isTrue()
            assertThat(message.id()).isEqualTo("msg-test")
            assertThat(content.text()).isEqualTo("A concise response.")
            assertThat(content.annotations()).isEmpty()
        } finally {
            client.close()
        }

        val call = httpClient.singleCall()
        assertThat(call.request.method.name).isEqualTo("POST")
        assertThat(call.request.pathSegments).containsExactly("responses")
        assertThat(call.request.headers.values("X-Request-Feature")).containsExactly("responses-test")
        assertThat(call.body).contains("\"model\":\"gpt-4o-mini\"")
        assertThat(call.body).contains("\"input\":\"Explain native image metadata in one sentence\"")
        assertThat(call.body).contains("\"instructions\":\"Keep the reply short\"")
        assertThat(call.body).contains("\"max_output_tokens\":16")
        assertThat(call.body).contains("\"parallel_tool_calls\":false")
        assertThat(call.body).contains("\"trace\":\"responses-test\"")
    }

    @Test
    fun fileUploadServiceBuildsMultipartRequestAndParsesFileObject() {
        val httpClient = RecordingHttpClient(
            mapOf(
                "/files" to """
                    {
                      "id": "file-test",
                      "object": "file",
                      "bytes": 18,
                      "created_at": 1710000000,
                      "filename": "training.jsonl",
                      "purpose": "assistants",
                      "status": "processed",
                      "expires_at": 1710086400
                    }
                """.trimIndent(),
            ),
        )
        val client = OpenAIClientImpl(testClientOptions(httpClient))

        try {
            val params: FileCreateParams = FileCreateParams.builder()
                .file("{\"prompt\":\"ping\"}\n".toByteArray(StandardCharsets.UTF_8))
                .purpose(FilePurpose.ASSISTANTS)
                .expiresAfter(
                    FileCreateParams.ExpiresAfter.builder()
                        .seconds(86_400)
                        .build(),
                )
                .build()

            val file: FileObject = client.files().create(params, requestOptions())

            assertThat(file.id()).isEqualTo("file-test")
            assertThat(file.bytes()).isEqualTo(18)
            assertThat(file.filename()).isEqualTo("training.jsonl")
            assertThat(file.purpose()).isEqualTo(FileObject.Purpose.ASSISTANTS)
            assertThat(file.expiresAt()).contains(1710086400L)
            assertThat(file.isValid()).isTrue()
        } finally {
            client.close()
        }

        val call = httpClient.singleCall()
        assertThat(call.request.method.name).isEqualTo("POST")
        assertThat(call.request.pathSegments).containsExactly("files")
        assertThat(call.body).contains("Content-Disposition: form-data; name=\"file\"")
        assertThat(call.body).contains("{\"prompt\":\"ping\"}")
        assertThat(call.body).contains("Content-Disposition: form-data; name=\"purpose\"")
        assertThat(call.body).contains("assistants")
        assertThat(call.body).contains("Content-Disposition: form-data; name=\"expires_after[anchor]\"")
        assertThat(call.body).contains("created_at")
        assertThat(call.body).contains("Content-Disposition: form-data; name=\"expires_after[seconds]\"")
        assertThat(call.body).contains("86400")
    }

    @Test
    fun asyncEmbeddingServiceBuildsRequestAndParsesTypedResponse() {
        val httpClient = RecordingHttpClient(
            mapOf(
                "/embeddings" to """
                    {
                      "object": "list",
                      "data": [
                        {"object": "embedding", "index": 0, "embedding": [0.1, 0.2, 0.3]}
                      ],
                      "model": "text-embedding-3-small",
                      "usage": {"prompt_tokens": 2, "total_tokens": 2}
                    }
                """.trimIndent(),
            ),
        )
        val client = OpenAIClientImpl(testClientOptions(httpClient))

        try {
            val params: EmbeddingCreateParams = EmbeddingCreateParams.builder()
                .model(EmbeddingModel.TEXT_EMBEDDING_3_SMALL)
                .inputOfArrayOfStrings(listOf("alpha", "beta"))
                .dimensions(3)
                .user("user-123")
                .putAdditionalBodyProperty("trace", JsonValue.from("embedding-test"))
                .build()

            val response = client.async().embeddings().create(params, requestOptions()).get(10, TimeUnit.SECONDS)

            assertThat(response.model()).isEqualTo("text-embedding-3-small")
            assertThat(response.data()).hasSize(1)
            assertThat(response.data()[0].index()).isEqualTo(0)
            assertThat(response.data()[0].embedding()).containsExactly(0.1f, 0.2f, 0.3f)
            assertThat(response.usage().totalTokens()).isEqualTo(2)
        } finally {
            client.close()
        }

        val call = httpClient.singleCall()
        assertThat(call.request.method.name).isEqualTo("POST")
        assertThat(call.request.pathSegments).containsExactly("embeddings")
        assertThat(call.body).contains("\"model\":\"text-embedding-3-small\"")
        assertThat(call.body).contains("\"input\":[\"alpha\",\"beta\"]")
        assertThat(call.body).contains("\"dimensions\":3")
        assertThat(call.body).contains("\"user\":\"user-123\"")
        assertThat(call.body).contains("\"trace\":\"embedding-test\"")
    }

    private fun testClientOptions(httpClient: HttpClient): ClientOptions = ClientOptions.builder()
        .httpClient(httpClient)
        .baseUrl("https://example.invalid/v1")
        .apiKey("sk-test")
        .organization("org-test")
        .project("project-test")
        .putHeader("X-Client", "core-test")
        .putQueryParam("client-query", "present")
        .timeout(Duration.ofSeconds(10))
        .maxRetries(0)
        .responseValidation(true)
        .build()

    private fun requestOptions(): RequestOptions = RequestOptions.builder()
        .timeout(Duration.ofSeconds(10))
        .responseValidation(true)
        .build()

    private data class RecordedCall(
        val request: HttpRequest,
        val options: RequestOptions,
        val body: String,
    )

    private class RecordingHttpClient(
        private val responseBodiesByPath: Map<String, String>,
    ) : HttpClient {
        private val calls: MutableList<RecordedCall> = mutableListOf()
        override fun execute(request: HttpRequest, requestOptions: RequestOptions): HttpResponse {
            val requestBody = request.body ?: error("Expected request body")
            val output = ByteArrayOutputStream()
            try {
                requestBody.writeTo(output)
            } finally {
                requestBody.close()
            }
            val bodyText = output.toString(StandardCharsets.UTF_8)
            calls += RecordedCall(request, requestOptions, bodyText)
            val path = "/" + request.pathSegments.joinToString("/")
            val responseBody = responseBodiesByPath[path] ?: error("No test response configured for $path")
            return StringHttpResponse(200, responseBody, Headers.builder().put("X-Request-Id", "req-test").build())
        }

        override fun executeAsync(request: HttpRequest, requestOptions: RequestOptions): CompletableFuture<HttpResponse> =
            CompletableFuture.completedFuture(execute(request, requestOptions))

        override fun close() = Unit

        fun singleCall(): RecordedCall {
            assertThat(calls).hasSize(1)
            return calls.single()
        }
    }

    private class StringHttpResponse(
        private val statusCode: Int,
        responseBody: String,
        private val headers: Headers,
    ) : HttpResponse {
        private val bytes: ByteArray = responseBody.toByteArray(StandardCharsets.UTF_8)

        override fun statusCode(): Int = statusCode

        override fun headers(): Headers = headers

        override fun body(): InputStream = ByteArrayInputStream(bytes)

        override fun close() = Unit
    }
}
