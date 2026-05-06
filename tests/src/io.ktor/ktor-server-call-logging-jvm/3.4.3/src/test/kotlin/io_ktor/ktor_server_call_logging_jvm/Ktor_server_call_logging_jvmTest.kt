/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_call_logging_jvm

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.http.content.staticFiles
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.calllogging.processingTimeMillis
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.helpers.AbstractLogger
import org.slf4j.helpers.MessageFormatter

public class Ktor_server_call_logging_jvmTest {
    @Test
    fun customFormatterFilterLevelAndClockAreApplied(): Unit = testApplication {
        val logger = RecordingLogger()
        val clock = AtomicLong(1_000L)

        application {
            install(CallLogging) {
                this.logger = logger
                level = Level.WARN
                clock { clock.getAndAdd(40L) }
                filter { call -> call.request.path().startsWith("/api/") }
                format { call ->
                    "${call.request.httpMethod.value} ${call.request.path()} -> " +
                        "${call.response.status()?.value} in ${call.processingTimeMillis { clock.get() }}ms"
                }
            }
            routing {
                get("/api/success") {
                    call.respondText("ok", status = HttpStatusCode.OK)
                }
                get("/health") {
                    call.respondText("healthy", status = HttpStatusCode.OK)
                }
            }
        }

        val successfulResponse = client.get("/api/success")
        val ignoredResponse = client.get("/health")

        assertThat(successfulResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(successfulResponse.bodyAsText()).isEqualTo("ok")
        assertThat(ignoredResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(logger.events).containsExactly(
            LoggedEvent(Level.WARN, "GET /api/success -> 200 in 40ms", emptyMap())
        )
    }

    @Test
    fun defaultFormatterLogsStatusRedirectTargetsAndNotFoundResponses(): Unit = testApplication {
        val logger = RecordingLogger()
        val clock = AtomicLong(10L)

        application {
            install(CallLogging) {
                this.logger = logger
                disableDefaultColors()
                clock { clock.getAndAdd(3L) }
            }
            routing {
                get("/accepted") {
                    call.respond(HttpStatusCode.Accepted, "queued")
                }
                get("/redirect") {
                    call.respondRedirect("/accepted")
                }
            }
        }

        val acceptedResponse = client.get("/accepted")
        val noRedirectClient = createClient {
            followRedirects = false
        }
        val redirectResponse = noRedirectClient.get("/redirect")
        val missingResponse = client.get("/missing")

        assertThat(acceptedResponse.status).isEqualTo(HttpStatusCode.Accepted)
        assertThat(acceptedResponse.bodyAsText()).isEqualTo("queued")
        assertThat(redirectResponse.status).isEqualTo(HttpStatusCode.Found)
        assertThat(redirectResponse.headers[HttpHeaders.Location]).isEqualTo("/accepted")
        assertThat(missingResponse.status).isEqualTo(HttpStatusCode.NotFound)

        assertThat(logger.messages()).anySatisfy { message ->
            assertThat(message).isEqualTo("202 Accepted: GET - /accepted in 3ms")
        }
        assertThat(logger.messages()).anySatisfy { message ->
            assertThat(message).isEqualTo("302 Found: GET - /redirect in 3ms -> /accepted")
        }
        assertThat(logger.messages()).anySatisfy { message ->
            assertThat(message).startsWith("404 Not Found: GET - /missing in ").endsWith("ms")
        }
    }

    @Test
    fun mdcProviderIsEvaluatedForLoggedCallsThenCleanedUp(): Unit = testApplication {
        val logger = RecordingLogger()
        val providerCalls = AtomicInteger()

        application {
            install(CallLogging) {
                this.logger = logger
                mdc("call-id") { call ->
                    providerCalls.incrementAndGet()
                    call.request.headers["X-Call-Id"]
                }
                format {
                    "logged call-id=${MDC.get("call-id") ?: "missing"}"
                }
            }
            routing {
                get("/mdc") {
                    call.respondText("handled")
                }
            }
        }

        val response = client.get("/mdc") {
            header("X-Call-Id", "abc-123")
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.bodyAsText()).isEqualTo("handled")
        assertThat(providerCalls.get()).isEqualTo(1)
        assertThat(logger.events).containsExactly(
            LoggedEvent(Level.INFO, "logged call-id=missing", emptyMap())
        )
        assertThat(MDC.get("call-id")).isNull()
    }

    @Test
    fun staticContentCanBeExcludedWhileDynamicCallsAreLogged() {
        val staticDirectory = Files.createTempDirectory("ktor-call-logging-static").toFile()
        try {
            staticDirectory.resolve("asset.txt").writeText("static asset")
            val logger = RecordingLogger()

            testApplication {
                application {
                    install(CallLogging) {
                        this.logger = logger
                        disableForStaticContent()
                        format { call -> "logged:${call.request.path()}" }
                    }
                    routing {
                        staticFiles("/assets", staticDirectory)
                        get("/dynamic") {
                            call.respondText("dynamic response")
                        }
                    }
                }

                val staticResponse = client.get("/assets/asset.txt")
                val dynamicResponse = client.get("/dynamic")

                assertThat(staticResponse.status).isEqualTo(HttpStatusCode.OK)
                assertThat(staticResponse.bodyAsText()).isEqualTo("static asset")
                assertThat(dynamicResponse.status).isEqualTo(HttpStatusCode.OK)
                assertThat(dynamicResponse.bodyAsText()).isEqualTo("dynamic response")
                assertThat(logger.messages()).containsExactly("logged:/dynamic")
            }
        } finally {
            staticDirectory.deleteRecursively()
        }
    }
}

private data class LoggedEvent(
    val level: Level,
    val message: String,
    val mdc: Map<String, String>
)

private class RecordingLogger : AbstractLogger() {
    val events: MutableList<LoggedEvent> = CopyOnWriteArrayList()

    init {
        name = "recording-call-logging-test"
    }

    override fun isTraceEnabled(): Boolean = true

    override fun isTraceEnabled(marker: Marker?): Boolean = true

    override fun isDebugEnabled(): Boolean = true

    override fun isDebugEnabled(marker: Marker?): Boolean = true

    override fun isInfoEnabled(): Boolean = true

    override fun isInfoEnabled(marker: Marker?): Boolean = true

    override fun isWarnEnabled(): Boolean = true

    override fun isWarnEnabled(marker: Marker?): Boolean = true

    override fun isErrorEnabled(): Boolean = true

    override fun isErrorEnabled(marker: Marker?): Boolean = true

    override fun getFullyQualifiedCallerName(): String = RecordingLogger::class.java.name

    override fun handleNormalizedLoggingCall(
        level: Level,
        marker: Marker?,
        messagePattern: String,
        arguments: Array<Any>?,
        throwable: Throwable?
    ) {
        val message = if (arguments.isNullOrEmpty()) {
            messagePattern
        } else {
            MessageFormatter.arrayFormat(messagePattern, arguments).message
        }
        events += LoggedEvent(level, message, MDC.getCopyOfContextMap().orEmpty())
    }

    fun messages(): List<String> = events.map { it.message }
}
