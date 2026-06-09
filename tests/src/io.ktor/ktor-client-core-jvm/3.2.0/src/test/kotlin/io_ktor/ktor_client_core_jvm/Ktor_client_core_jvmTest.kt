/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_core_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.cookies.cookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.timeout
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareGet
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.http.withCharset
import io.ktor.utils.io.ByteReadChannel
import java.nio.charset.StandardCharsets
import java.util.function.Consumer
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

public class Ktor_client_core_jvmTest {
    @Test
    public fun `default request and user agent plugins enrich outgoing requests`(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val engine = MockEngine { request ->
                assertThat(request.url.protocol.name).isEqualTo("https")
                assertThat(request.url.host).isEqualTo("api.example.test")
                assertThat(request.url.encodedPath).isEqualTo("/v1/resources")
                assertThat(request.url.parameters["id"]).isEqualTo("42")
                assertThat(request.url.parameters["default"]).isEqualTo("yes")
                assertThat(request.headers[HttpHeaders.UserAgent]).isEqualTo("ktor-core-test-agent")
                assertThat(request.headers["X-Default"]).isEqualTo("from-default-request")
                assertThat(request.headers["X-Call"]).isEqualTo("from-call")
                respond(content = "configured", status = HttpStatusCode.OK)
            }
            val client = HttpClient(engine) {
                defaultRequest {
                    url("https://api.example.test/v1/")
                    header("X-Default", "from-default-request")
                    url.parameters.append("default", "yes")
                }
                install(UserAgent) {
                    agent = "ktor-core-test-agent"
                }
            }

            try {
                val body: String = client.get("resources") {
                    header("X-Call", "from-call")
                    parameter("id", "42")
                }.bodyAsText()

                assertThat(body).isEqualTo("configured")
                assertThat(engine.requestHistory).hasSize(1)
            } finally {
                client.close()
            }
        }
    }

    @Test
    public fun `http cookies plugin stores response cookies and sends matching request cookies`(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val engine = MockEngine { request ->
                when (request.url.encodedPath) {
                    "/login" -> {
                        assertThat(request.headers[HttpHeaders.Cookie]).isNull()
                        respond(
                            content = "logged-in",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.SetCookie, "session=alpha; Path=/; HttpOnly"),
                        )
                    }

                    "/account" -> {
                        assertThat(request.headers[HttpHeaders.Cookie]).contains("session=alpha")
                        respond(content = "account", status = HttpStatusCode.OK)
                    }

                    else -> error("Unexpected request path: ${request.url.encodedPath}")
                }
            }
            val client = HttpClient(engine) {
                install(HttpCookies)
            }

            try {
                assertThat(client.get("https://example.test/login").bodyAsText()).isEqualTo("logged-in")
                assertThat(client.get("https://example.test/account").bodyAsText()).isEqualTo("account")

                val storedCookies = client.cookies("https://example.test/account")
                assertThat(storedCookies.map { it.name to it.value }).contains("session" to "alpha")
                assertThat(engine.requestHistory).hasSize(2)
            } finally {
                client.close()
            }
        }
    }

    @Test
    public fun `redirect plugin follows location header to the target resource`(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val engine = MockEngine { request ->
                when (request.url.encodedPath) {
                    "/start" -> {
                        assertThat(request.method).isEqualTo(HttpMethod.Get)
                        assertThat(request.headers["X-Trace"]).isEqualTo("redirect-flow")
                        respond(
                            content = "redirecting",
                            status = HttpStatusCode.Found,
                            headers = headersOf(HttpHeaders.Location, "/target?from=redirect"),
                        )
                    }

                    "/target" -> {
                        assertThat(request.url.parameters["from"]).isEqualTo("redirect")
                        respond(content = "redirected", status = HttpStatusCode.OK)
                    }

                    else -> error("Unexpected request path: ${request.url.encodedPath}")
                }
            }
            val client = HttpClient(engine) {
                install(HttpRedirect)
            }

            try {
                val body: String = client.get("https://example.test/start") {
                    header("X-Trace", "redirect-flow")
                }.bodyAsText()

                assertThat(body).isEqualTo("redirected")
                assertThat(engine.requestHistory).hasSize(2)
                assertThat(engine.requestHistory.last().url.toString())
                    .isEqualTo("https://example.test/target?from=redirect")
            } finally {
                client.close()
            }
        }
    }

    @Test
    public fun `retry plugin resends server failures and can modify retry requests`(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            var attempt: Int = 0
            val engine = MockEngine { request ->
                attempt += 1
                if (attempt == 1) {
                    assertThat(request.headers["X-Retry-Count"]).isNull()
                    respond(content = "try-again", status = HttpStatusCode.InternalServerError)
                } else {
                    assertThat(request.headers["X-Retry-Count"]).isEqualTo("1")
                    respond(content = "recovered", status = HttpStatusCode.OK)
                }
            }
            val client = HttpClient(engine) {
                install(HttpRequestRetry) {
                    retryOnServerErrors(maxRetries = 1)
                    constantDelay(1L, 0L, false)
                    modifyRequest { request ->
                        request.headers.append("X-Retry-Count", retryCount.toString())
                    }
                }
            }

            try {
                val body: String = client.get("https://example.test/retry").bodyAsText()

                assertThat(body).isEqualTo("recovered")
                assertThat(attempt).isEqualTo(2)
                assertThat(engine.requestHistory).hasSize(2)
            } finally {
                client.close()
            }
        }
    }

    @Test
    public fun `expect success maps unsuccessful responses to typed response exceptions`(): Unit {
        val engine = MockEngine {
            respond(content = "missing", status = HttpStatusCode.NotFound)
        }
        val client = HttpClient(engine) {
            expectSuccess = true
        }

        try {
            assertThatThrownBy {
                runBlocking {
                    withTimeout(TEST_TIMEOUT_MILLIS) {
                        client.get("https://example.test/missing")
                    }
                }
            }
                .isInstanceOf(ClientRequestException::class.java)
                .satisfies(Consumer { error: Throwable ->
                    val exception = error as ClientRequestException
                    assertThat(exception.response.status).isEqualTo(HttpStatusCode.NotFound)
                })
        } finally {
            client.close()
        }
    }

    @Test
    public fun `plain text transformers encode request text and decode response charset`(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val responseBytes: ByteArray = "Grüße vom Server".toByteArray(StandardCharsets.UTF_16BE)
            val engine = MockEngine { request ->
                assertThat(request.method).isEqualTo(HttpMethod.Post)
                assertThat(request.body.contentType?.withoutParameters()).isEqualTo(ContentType.Text.Plain)
                assertThat(request.body.readText()).isEqualTo("client says héllo")

                respond(
                    content = ByteReadChannel(responseBytes),
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentType,
                        ContentType.Text.Plain.withCharset(StandardCharsets.UTF_16BE).toString(),
                    ),
                )
            }
            val client = HttpClient(engine)

            try {
                val responseText: String = client.post("https://example.test/text") {
                    setBody(
                        TextContent(
                            text = "client says héllo",
                            contentType = ContentType.Text.Plain.withCharset(StandardCharsets.UTF_8),
                        ),
                    )
                }.bodyAsText()

                assertThat(responseText).isEqualTo("Grüße vom Server")
            } finally {
                client.close()
            }
        }
    }

    @Test
    public fun `timeout plugin supplies request scoped timeout capability to the engine`(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val engine = MockEngine { request ->
                val timeout = request.getCapabilityOrNull(HttpTimeoutCapability)
                assertThat(timeout).isNotNull()
                assertThat(timeout?.requestTimeoutMillis).isEqualTo(250L)
                assertThat(timeout?.connectTimeoutMillis).isEqualTo(500L)
                assertThat(timeout?.socketTimeoutMillis).isEqualTo(750L)
                respond(content = "timeout-configured", status = HttpStatusCode.OK)
            }
            val client = HttpClient(engine) {
                install(HttpTimeout) {
                    requestTimeoutMillis = 1_000L
                    connectTimeoutMillis = 500L
                    socketTimeoutMillis = 750L
                }
            }

            try {
                val body: String = client.get("https://example.test/timeout") {
                    timeout {
                        requestTimeoutMillis = 250L
                    }
                }.bodyAsText()

                assertThat(body).isEqualTo("timeout-configured")
            } finally {
                client.close()
            }
        }
    }

    @Test
    public fun `prepared requests execute with a scoped response block`(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val engine = MockEngine { request ->
                assertThat(request.url.toString()).isEqualTo("https://example.test/prepared?mode=streaming")
                respond(content = "prepared-body", status = HttpStatusCode.OK)
            }
            val client = HttpClient(engine)

            try {
                val responseSummary: String = client.prepareGet("https://example.test/prepared") {
                    parameter("mode", "streaming")
                }.execute { response ->
                    "${response.status.value}:${response.bodyAsText()}"
                }

                assertThat(responseSummary).isEqualTo("200:prepared-body")
            } finally {
                client.close()
            }
        }
    }

    @Test
    public fun `submit form sends url encoded parameters using post by default`(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val engine = MockEngine { request ->
                assertThat(request.method).isEqualTo(HttpMethod.Post)
                assertThat(request.body.contentType?.withoutParameters()).isEqualTo(ContentType.Application.FormUrlEncoded)

                val formBody: String = request.body.readText()
                assertThat(formBody).contains("first=one")
                assertThat(formBody).contains("space=a+b")
                respond(content = "accepted", status = HttpStatusCode.Accepted)
            }
            val client = HttpClient(engine)

            try {
                val responseText: String = client.submitForm(
                    url = "https://example.test/form",
                    formParameters = Parameters.build {
                        append("first", "one")
                        append("space", "a b")
                    },
                ).bodyAsText()

                assertThat(responseText).isEqualTo("accepted")
            } finally {
                client.close()
            }
        }
    }

    private companion object {
        private const val TEST_TIMEOUT_MILLIS: Long = 5_000L
    }
}

private suspend fun OutgoingContent.readText(): String = String(toByteArray(), StandardCharsets.UTF_8)
