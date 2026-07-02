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
import io.ktor.server.application.call
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.http.content.staticFiles
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.slf4j.MDC
import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.helpers.LegacyAbstractLogger
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.writeText

public class KtorServerCallLoggingJvmTest {
    @Test
    fun defaultFormatterLogsCompletedSuccessfulCallsWithDeterministicProcessingTime(): Unit = testApplication {
        val logger = RecordingLogger()
        install(CallLogging) {
            this.logger = logger
            level = Level.INFO
            disableDefaultColors()
            clock(deterministicClock(1_000L, 1_042L))
        }
        routing {
            get("/hello") {
                call.respondText("hello from route")
            }
        }

        val response = client.get("/hello")
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.bodyAsText()).isEqualTo("hello from route")

        val events: List<LogEvent> = logger.events()
        assertThat(events).hasSize(1)
        assertThat(events[0].level).isEqualTo(Level.INFO)
        assertThat(events[0].message).isEqualTo("200 OK: GET - /hello in 42ms")
    }

    @Test
    fun defaultFormatterIncludesRedirectLocationForFoundResponses(): Unit = testApplication {
        val logger = RecordingLogger()
        install(CallLogging) {
            this.logger = logger
            level = Level.INFO
            disableDefaultColors()
            clock(deterministicClock(200L, 207L))
        }
        routing {
            get("/redirect") {
                call.respondRedirect("/target")
            }
            get("/target") {
                call.respondText("target")
            }
        }

        val redirectClient = createClient {
            followRedirects = false
        }
        try {
            val response = redirectClient.get("/redirect")
            assertThat(response.status).isEqualTo(HttpStatusCode.Found)
            assertThat(response.headers[HttpHeaders.Location]).isEqualTo("/target")

            val events: List<LogEvent> = logger.events()
            assertThat(events).hasSize(1)
            assertThat(events[0].message).isEqualTo("302 Found: GET - /redirect in 7ms -> /target")
        } finally {
            redirectClient.close()
        }
    }

    @Test
    fun filtersCustomFormatterAndLogLevelSelectOnlyMatchingCalls(): Unit = testApplication {
        val logger = RecordingLogger()
        install(CallLogging) {
            this.logger = logger
            level = Level.WARN
            filter { call -> call.request.path().startsWith("/audit") }
            format { call ->
                "filtered:${call.request.httpMethod.value}:${call.request.path()}:${call.response.status()?.value}"
            }
        }
        routing {
            get("/ignored") {
                call.respondText("ignored")
            }
            get("/audit/event") {
                call.respondText("audited")
            }
        }

        val ignoredResponse = client.get("/ignored")
        assertThat(ignoredResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(ignoredResponse.bodyAsText()).isEqualTo("ignored")

        val auditResponse = client.get("/audit/event")
        assertThat(auditResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(auditResponse.bodyAsText()).isEqualTo("audited")

        val events: List<LogEvent> = logger.events()
        assertThat(events).hasSize(1)
        assertThat(events[0].level).isEqualTo(Level.WARN)
        assertThat(events[0].message).isEqualTo("filtered:GET:/audit/event:200")
    }

    @Test
    fun failedCallsAreLoggedAfterKtorMapsUnhandledExceptionsToServerErrors(): Unit = testApplication {
        val logger = RecordingLogger()
        environment {
            config = MapApplicationConfig("ktor.test.throwOnException" to "false")
        }
        install(CallLogging) {
            this.logger = logger
            level = Level.ERROR
            format { call -> "failure:${call.request.path()}:${call.response.status()?.value}" }
        }
        routing {
            get("/boom") {
                throw IllegalStateException("route failed")
            }
        }

        val response = client.get("/boom")
        assertThat(response.status).isEqualTo(HttpStatusCode.InternalServerError)
        assertThat(response.bodyAsText()).isEqualTo("route failed")

        val events: List<LogEvent> = logger.events()
        assertThat(events).hasSize(1)
        assertThat(events[0].level).isEqualTo(Level.ERROR)
        assertThat(events[0].message).isEqualTo("failure:/boom:500")
    }

    @Test
    fun disableForStaticContentSkipsStaticResponsesButKeepsLoggingApplicationRoutes(
        @TempDir staticDirectory: Path
    ): Unit = testApplication {
        staticDirectory.resolve("app.js").writeText("console.log('static asset')")
        val logger = RecordingLogger()
        install(CallLogging) {
            this.logger = logger
            level = Level.INFO
            disableForStaticContent()
            format { call -> "logged:${call.request.path()}" }
        }
        routing {
            staticFiles("/assets", staticDirectory.toFile())
            get("/dynamic") {
                call.respondText("dynamic route")
            }
        }

        val staticResponse = client.get("/assets/app.js")
        assertThat(staticResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(staticResponse.bodyAsText()).isEqualTo("console.log('static asset')")

        val dynamicResponse = client.get("/dynamic")
        assertThat(dynamicResponse.status).isEqualTo(HttpStatusCode.OK)
        assertThat(dynamicResponse.bodyAsText()).isEqualTo("dynamic route")

        val events: List<LogEvent> = logger.events()
        assertThat(events).hasSize(1)
        assertThat(events[0].level).isEqualTo(Level.INFO)
        assertThat(events[0].message).isEqualTo("logged:/dynamic")
    }

    @Test
    fun disabledSlf4jLevelSuppressesConfiguredCallLogMessage(): Unit = testApplication {
        val logger = RecordingLogger(enabledLevels = setOf(Level.WARN))
        install(CallLogging) {
            this.logger = logger
            level = Level.INFO
            format { call -> "suppressed:${call.request.path()}" }
        }
        routing {
            get("/suppressed") {
                call.respondText("suppressed route")
            }
        }

        val response = client.get("/suppressed")
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.bodyAsText()).isEqualTo("suppressed route")

        assertThat(logger.events()).isEmpty()
    }

    @Test
    fun mdcProvidersAreEvaluatedOncePerCallAndLoggingStillUsesConfiguredLogger(): Unit = testApplication {
        val logger = RecordingLogger()
        val pathProviderCalls = AtomicInteger()
        val agentProviderCalls = AtomicInteger()
        install(CallLogging) {
            this.logger = logger
            level = Level.DEBUG
            mdc("requestPath") { call ->
                pathProviderCalls.incrementAndGet()
                call.request.path()
            }
            mdc("agent") { call ->
                agentProviderCalls.incrementAndGet()
                call.request.headers[HttpHeaders.UserAgent] ?: "missing-agent"
            }
            format { call -> "mdc-enabled:${call.request.path()}" }
        }
        routing {
            get("/mdc") {
                call.respondText("mdc route")
            }
        }

        val response = client.get("/mdc") {
            header(HttpHeaders.UserAgent, "call-logging-test-client")
        }
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.bodyAsText()).isEqualTo("mdc route")

        assertThat(pathProviderCalls.get()).isEqualTo(1)
        assertThat(agentProviderCalls.get()).isEqualTo(1)
        val events: List<LogEvent> = logger.events()
        assertThat(events).hasSize(1)
        assertThat(events[0].level).isEqualTo(Level.DEBUG)
        assertThat(events[0].message).isEqualTo("mdc-enabled:/mdc")
    }

    private fun deterministicClock(vararg values: Long): () -> Long {
        val index = AtomicInteger()
        return {
            val selectedIndex: Int = index.getAndIncrement()
            if (selectedIndex < values.size) values[selectedIndex] else values.last()
        }
    }

    private data class LogEvent(
        val level: Level,
        val message: String,
        val mdc: Map<String, String>,
    )

    private class RecordingLogger(
        private val enabledLevels: Set<Level> = setOf(Level.TRACE, Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR),
    ) : LegacyAbstractLogger() {
        private val recordedEvents: CopyOnWriteArrayList<LogEvent> = CopyOnWriteArrayList()

        init {
            name = "recording-call-logger"
        }

        fun events(): List<LogEvent> = recordedEvents.toList()

        override fun isTraceEnabled(): Boolean = Level.TRACE in enabledLevels

        override fun isDebugEnabled(): Boolean = Level.DEBUG in enabledLevels

        override fun isInfoEnabled(): Boolean = Level.INFO in enabledLevels

        override fun isWarnEnabled(): Boolean = Level.WARN in enabledLevels

        override fun isErrorEnabled(): Boolean = Level.ERROR in enabledLevels

        override fun getFullyQualifiedCallerName(): String = RecordingLogger::class.java.name

        override fun handleNormalizedLoggingCall(
            level: Level,
            marker: Marker?,
            messagePattern: String?,
            arguments: Array<out Any?>?,
            throwable: Throwable?
        ) {
            recordedEvents += LogEvent(
                level = level,
                message = messagePattern.orEmpty(),
                mdc = MDC.getCopyOfContextMap().orEmpty(),
            )
        }
    }
}
