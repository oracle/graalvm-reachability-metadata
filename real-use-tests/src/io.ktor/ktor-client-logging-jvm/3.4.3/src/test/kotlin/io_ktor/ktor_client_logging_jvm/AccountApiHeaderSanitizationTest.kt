/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_logging_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Collections
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Header logging with sensitive-header sanitization in the `Logging` client plugin.
 *
 * An account service client authenticates with a bearer token and receives a session cookie
 * from an embedded CIO server. The client logs every request and response at the `HEADERS`
 * level into the application's audit log; the token and the cookie must appear there only as
 * placeholders, while ordinary headers such as the correlation id stay readable and the server
 * still receives the real credentials.
 */
class AccountApiHeaderSanitizationTest {

    /** Collects every message the plugin emits, as an application audit log would. */
    class AuditLog : Logger {
        private val entries: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override fun log(message: String) {
            entries += message
        }

        fun lines(): List<String> = entries.toList().flatMap { it.lines() }.map { it.trim() }
    }

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0

    @Volatile
    private var receivedAuthorization: String? = null

    @BeforeEach
    fun startServer() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/accounts/me") {
                    receivedAuthorization = call.request.headers[HttpHeaders.Authorization]
                    call.response.header(HttpHeaders.SetCookie, "SESSION=s3cr3t-session; HttpOnly")
                    call.response.header("X-Request-Id", call.request.headers["X-Request-Id"] ?: "none")
                    call.respondText("""{"id":"acc-42","owner":"Ada"}""")
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stopServer() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    @Test
    fun auditLogShowsHeadersButHidesCredentials() {
        val auditLog = AuditLog()
        val client = HttpClient(ClientCIO) {
            install(Logging) {
                logger = auditLog
                level = LogLevel.HEADERS
                sanitizeHeader { header -> header == HttpHeaders.Authorization }
                sanitizeHeader(placeholder = "<session>") { header ->
                    header.equals(HttpHeaders.SetCookie, ignoreCase = true)
                }
            }
        }

        val body = client.use {
            runBlocking {
                val response = it.get("http://127.0.0.1:$port/accounts/me") {
                    bearerAuth("tok-123456")
                    header("X-Request-Id", "corr-7")
                }
                assertEquals(HttpStatusCode.OK, response.status)
                response.bodyAsText()
            }
        }

        assertEquals("""{"id":"acc-42","owner":"Ada"}""", body)
        assertEquals("Bearer tok-123456", receivedAuthorization)

        val lines = auditLog.lines()
        assertTrue(lines.contains("REQUEST: http://127.0.0.1:$port/accounts/me"), lines.joinToString("\n"))
        assertTrue(lines.contains("METHOD: GET"), lines.joinToString("\n"))
        assertTrue(lines.contains("-> Authorization: ***"), lines.joinToString("\n"))
        assertTrue(lines.contains("RESPONSE: 200 OK"), lines.joinToString("\n"))
        assertTrue(
            lines.any { it.equals("-> Set-Cookie: <session>", ignoreCase = true) },
            lines.joinToString("\n"),
        )
        assertEquals(
            2,
            lines.count { it.equals("-> X-Request-Id: corr-7", ignoreCase = true) },
            "the correlation id is logged on the request and echoed on the response:\n" +
                lines.joinToString("\n"),
        )
        val everything = lines.joinToString("\n")
        assertFalse(everything.contains("tok-123456"), everything)
        assertFalse(everything.contains("s3cr3t-session"), everything)
        assertFalse(everything.contains("acc-42"), "HEADERS level must not log bodies:\n$everything")
    }
}
