/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_java_jvm

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger as LogbackLogger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.LoggingFormat
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList

/**
 * HTTP traffic logging through the client Logging plugin over the Java engine, as a checkout
 * service audits its calls to a payment gateway without leaking card tokens or API keys.
 */
public class PaymentGatewayLoggingTest {

    /** The payment gateway: authorizes charges and reports its health. */
    class PaymentGateway : AutoCloseable {
        private val server: EmbeddedServer<*, *> = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            routing {
                post("/charges") {
                    val charge: String = call.receiveText()
                    call.response.header("X-Gateway-Session", "gw-session-secret")
                    call.respondText(
                        """{"status":"authorized","echo":$charge}""",
                        ContentType.Application.Json,
                        HttpStatusCode.Created,
                    )
                }
                get("/health") {
                    call.respondText("gateway up")
                }
                get("/charges/{id}") {
                    call.respondText(
                        "charge ${call.parameters["id"]} not found",
                        status = HttpStatusCode.NotFound,
                    )
                }
            }
        }

        val port: Int

        init {
            server.start(wait = false)
            port = runBlocking { server.engine.resolvedConnectors().first().port }
        }

        override fun close() {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 2_000)
        }
    }

    /** An audit log that keeps every message the Logging plugin emits. */
    class AuditLogger : Logger {
        val messages: MutableList<String> = CopyOnWriteArrayList()

        override fun log(message: String) {
            messages += message
        }

        /** All logged text joined, waiting until [expected] appears so asynchronous body logs land. */
        suspend fun awaitText(expected: String): String = withTimeout(10_000) {
            while (messages.none { it.contains(expected) }) {
                delay(10)
            }
            messages.joinToString("\n")
        }
    }

    @Test
    fun logsFullExchangesWithSensitiveHeadersMasked(): Unit = runBlocking {
        PaymentGateway().use { gateway: PaymentGateway ->
            val audit: AuditLogger = AuditLogger()
            val client: HttpClient = HttpClient(Java) {
                defaultRequest { url("http://127.0.0.1:${gateway.port}/") }
                install(Logging) {
                    logger = audit
                    level = LogLevel.ALL
                    sanitizeHeader { name: String -> name.equals(HttpHeaders.Authorization, ignoreCase = true) }
                    // The Java engine reports response header names in lower case.
                    sanitizeHeader(placeholder = "<session>") { name: String ->
                        name.equals("X-Gateway-Session", ignoreCase = true)
                    }
                    filter { request -> request.url.pathSegments.lastOrNull() != "health" }
                }
            }
            client.use {
                val charge: HttpResponse = client.post("charges") {
                    header(HttpHeaders.Authorization, "Bearer sk_live_51Hx")
                    contentType(ContentType.Application.Json)
                    setBody("""{"amount":4200,"currency":"EUR"}""")
                }
                assertThat(charge.status).isEqualTo(HttpStatusCode.Created)
                assertThat(charge.headers["X-Gateway-Session"]).isEqualTo("gw-session-secret")
                assertThat(charge.bodyAsText()).contains("\"status\":\"authorized\"")

                val log: String = audit.awaitText("\"status\":\"authorized\"")
                assertThat(log)
                    .contains("REQUEST: http://127.0.0.1:${gateway.port}/charges")
                    .contains("METHOD: POST")
                    .contains("""{"amount":4200,"currency":"EUR"}""")
                    .contains("RESPONSE: 201 Created")
                    .contains("-> Authorization: ***")
                    .containsIgnoringCase("-> X-Gateway-Session: <session>")
                    .doesNotContain("sk_live_51Hx")
                    .doesNotContain("gw-session-secret")

                // Health probes are filtered out of the audit log entirely.
                val logged: Int = audit.messages.size
                val health: HttpResponse = client.get("health")
                assertThat(health.bodyAsText()).isEqualTo("gateway up")
                assertThat(audit.messages).hasSize(logged)
                assertThat(audit.messages.joinToString("\n")).doesNotContain("/health")
            }
        }
    }

    @Test
    fun writesOneLinePerExchangeInTheOkHttpFormat(): Unit = runBlocking {
        PaymentGateway().use { gateway: PaymentGateway ->
            val audit: AuditLogger = AuditLogger()
            val client: HttpClient = HttpClient(Java) {
                defaultRequest { url("http://127.0.0.1:${gateway.port}/") }
                install(Logging) {
                    logger = audit
                    level = LogLevel.INFO
                    format = LoggingFormat.OkHttp
                }
            }
            client.use {
                val missing: HttpResponse = client.get("charges/ch_404")
                assertThat(missing.status).isEqualTo(HttpStatusCode.NotFound)
                assertThat(missing.bodyAsText()).isEqualTo("charge ch_404 not found")

                val log: String = audit.awaitText("<-- 404")
                assertThat(log)
                    .contains("--> GET http://127.0.0.1:${gateway.port}/charges/ch_404")
                    .contains("<-- 404 Not Found http://127.0.0.1:${gateway.port}/charges/ch_404")
                    .doesNotContain("charge ch_404 not found")
            }
        }
    }

    @Test
    fun routesHeaderLogsToSlf4jThroughTheDefaultLogger(): Unit = runBlocking {
        val context: LoggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
        val clientLog: LogbackLogger = context.getLogger(HttpClient::class.java)
        val appender: ListAppender<ILoggingEvent> = ListAppender<ILoggingEvent>()
        appender.context = context
        appender.start()
        val previousLevel: Level? = clientLog.level
        clientLog.level = Level.INFO
        clientLog.addAppender(appender)
        try {
            PaymentGateway().use { gateway: PaymentGateway ->
                val client: HttpClient = HttpClient(Java) {
                    defaultRequest { url("http://127.0.0.1:${gateway.port}/") }
                    install(Logging) {
                        logger = Logger.DEFAULT
                        level = LogLevel.HEADERS
                    }
                }
                client.use {
                    val health: HttpResponse = client.get("health") {
                        header("X-Request-Id", "req-7781")
                    }
                    assertThat(health.bodyAsText()).isEqualTo("gateway up")
                }
            }
            val log: String = withTimeout(10_000) {
                while (appender.list.none { it.formattedMessage.contains("RESPONSE: 200 OK") }) {
                    delay(10)
                }
                appender.list.joinToString("\n") { it.formattedMessage }
            }
            assertThat(log)
                .contains("REQUEST: http://127.0.0.1:")
                .contains("X-Request-Id: req-7781")
                .contains("RESPONSE: 200 OK")
                .doesNotContain("gateway up")
            assertThat(appender.list.map { it.loggerName }.toSet()).containsExactly(HttpClient::class.java.name)
        } finally {
            clientLog.detachAppender(appender)
            clientLog.level = previousLevel
            appender.stop()
        }
    }
}
