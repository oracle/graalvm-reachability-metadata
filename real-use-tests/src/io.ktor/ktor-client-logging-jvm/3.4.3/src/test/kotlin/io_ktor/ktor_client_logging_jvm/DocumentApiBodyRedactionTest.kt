/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_logging_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.logging.BodyFilterResult
import io.ktor.client.plugins.logging.LogBodyFilter
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.LoggingFormat
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.toByteArray
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
 * Body redaction through a custom `LogBodyFilter` in the `Logging` client plugin.
 *
 * A document client signs in with a password and then uploads a scanned document to an
 * embedded CIO server. Its wire log runs at the `BODY` level with a body filter the
 * application owns: credentials and access tokens in JSON bodies are masked before they
 * reach the log, and document uploads are left out with a reason, while the server still
 * receives, and the client still reads, the real bodies.
 */
class DocumentApiBodyRedactionTest {

    /** Keeps every message the plugin emits, as an application wire log would. */
    class WireLog : Logger {
        private val entries: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override fun log(message: String) {
            entries += message
        }

        fun messages(): List<String> = entries.toList()
    }

    /** Masks secrets in JSON bodies and omits uploaded documents from the wire log. */
    class RedactingBodyFilter : LogBodyFilter {
        private val secretField: Regex = Regex(""""(password|accessToken)"\s*:\s*"[^"]*"""")

        override suspend fun filterRequest(
            url: Url,
            contentLength: Long?,
            contentType: ContentType?,
            headers: Headers,
            body: ByteReadChannel,
        ): BodyFilterResult {
            if (url.encodedPath.startsWith("/documents")) {
                return BodyFilterResult.Skip("scanned document", contentLength)
            }
            return redact(contentType, body)
        }

        override suspend fun filterResponse(
            url: Url,
            contentLength: Long?,
            contentType: ContentType?,
            headers: Headers,
            body: ByteReadChannel,
        ): BodyFilterResult = redact(contentType, body)

        private suspend fun redact(contentType: ContentType?, body: ByteReadChannel): BodyFilterResult {
            val bytes: ByteArray = body.toByteArray()
            if (bytes.isEmpty()) {
                return BodyFilterResult.Empty
            }
            if (contentType == null || !contentType.match(ContentType.Application.Json)) {
                return BodyFilterResult.Skip("not JSON", bytes.size.toLong())
            }
            val masked: String = secretField.replace(bytes.decodeToString()) { "\"${it.groupValues[1]}\":\"***\"" }
            return object : BodyFilterResult.Content {
                override val byteSize: Long = bytes.size.toLong()

                override fun read(): String = masked
            }
        }
    }

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0
    private val receivedLogins: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val receivedDocumentSizes: MutableList<Int> = Collections.synchronizedList(mutableListOf())

    @BeforeEach
    fun startServer() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                post("/sessions") {
                    receivedLogins += call.receiveText()
                    call.respondText(
                        """{"user":"alice","accessToken":"tok-5f2a9c"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.Created,
                    )
                }
                post("/documents") {
                    receivedDocumentSizes += call.receive<ByteArray>().size
                    call.respondText("""{"documentId":"doc-9","pages":2}""", ContentType.Application.Json)
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
    fun wireLogMasksSecretsAndOmitsDocuments() {
        val wireLog = WireLog()
        val client = HttpClient(ClientCIO) {
            install(Logging) {
                logger = wireLog
                level = LogLevel.BODY
                format = LoggingFormat.OkHttp
                bodyFilter = RedactingBodyFilter()
            }
        }
        val login = """{"user":"alice","password":"hunter2"}"""
        val sessionReply = """{"user":"alice","accessToken":"tok-5f2a9c"}"""
        val scan: ByteArray = ByteArray(2048) { (it % 251).toByte() }

        client.use {
            runBlocking {
                val session = it.post("http://127.0.0.1:$port/sessions") {
                    contentType(ContentType.Application.Json)
                    setBody(login)
                }
                assertEquals(HttpStatusCode.Created, session.status)
                assertEquals(sessionReply, session.bodyAsText())

                val upload = it.post("http://127.0.0.1:$port/documents") {
                    contentType(ContentType.Application.OctetStream)
                    setBody(scan)
                }
                assertEquals(HttpStatusCode.OK, upload.status)
                assertEquals("""{"documentId":"doc-9","pages":2}""", upload.bodyAsText())
            }
        }

        assertEquals(listOf(login), receivedLogins.toList())
        assertEquals(listOf(scan.size), receivedDocumentSizes.toList())

        val messages: List<String> = wireLog.messages()
        val everything: String = messages.joinToString("\n----\n")
        assertEquals(4, messages.size, everything)
        assertFalse(everything.contains("hunter2"), "the password must not reach the log:\n$everything")
        assertFalse(everything.contains("tok-5f2a9c"), "the access token must not reach the log:\n$everything")
        val (loginRequest, loginResponse, uploadRequest, uploadResponse) = messages.map { it.lines() }

        assertEquals("--> POST http://127.0.0.1:$port/sessions", loginRequest.first(), everything)
        assertEquals(
            listOf("", """{"user":"alice","password":"***"}""", "--> END POST (${login.length}-byte body)"),
            loginRequest.takeLast(3),
            "the masked login is logged with the size of the real body:\n$everything",
        )

        assertTrue(loginResponse.first().startsWith("<-- 201 Created "), everything)
        assertEquals("""{"user":"alice","accessToken":"***"}""", loginResponse[loginResponse.size - 2], everything)
        assertTrue(
            Regex("""<-- END HTTP \(\d+ms, ${sessionReply.length}-byte body\)""").matches(loginResponse.last()),
            everything,
        )

        assertEquals("--> POST http://127.0.0.1:$port/documents", uploadRequest.first(), everything)
        assertTrue(uploadRequest.contains("Content-Length: ${scan.size}"), everything)
        assertEquals(
            listOf("", "--> END POST (scanned document ${scan.size}-byte body omitted)"),
            uploadRequest.takeLast(2),
            "the document upload is omitted with the filter's reason:\n$everything",
        )

        assertTrue(uploadResponse.first().startsWith("<-- 200 OK "), everything)
        assertEquals("""{"documentId":"doc-9","pages":2}""", uploadResponse[uploadResponse.size - 2], everything)
    }
}
