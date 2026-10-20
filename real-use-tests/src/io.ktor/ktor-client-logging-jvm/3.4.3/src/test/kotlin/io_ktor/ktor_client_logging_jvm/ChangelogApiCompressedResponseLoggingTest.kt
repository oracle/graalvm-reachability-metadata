/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_logging_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.LoggingFormat
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.compression.minimumSize
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
import java.io.ByteArrayInputStream
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Logging of gzip-compressed (`Content-Encoding: gzip`) responses in the `Logging` client plugin.
 *
 * A release-notes service serves its changelog gzip-compressed through the server
 * `Compression` plugin. A reader client that installs `ContentEncoding` sees the changelog
 * decoded in its call log, with the decoded byte count and without the encoding header. A
 * mirroring client asks for gzip itself so it can store the compressed bytes untouched; its
 * call log keeps the `Content-Encoding` header and omits the encoded body instead of printing
 * compressed bytes, while the mirror still gets a payload that inflates to the changelog.
 */
class ChangelogApiCompressedResponseLoggingTest {

    /** A log sink that keeps every call-log message, as an application log appender would. */
    class CallLogSink : Logger {
        private val entries: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override fun log(message: String) {
            entries += message
        }

        /** Polls until a message contains [marker], bounded by [timeoutMillis], and returns all messages. */
        fun awaitMessage(marker: String, timeoutMillis: Long): List<String> {
            val deadline: Long = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
            var current: List<String> = entries.toList()
            while (current.none { it.contains(marker) } && System.nanoTime() < deadline) {
                Thread.onSpinWait()
                current = entries.toList()
            }
            return current
        }
    }

    private val changelog: String = (1..40).joinToString(separator = "\n", postfix = "\n") {
        "v1.%d.0 - fixed issue #%04d in the release pipeline".format(it, it * 13)
    }

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private lateinit var changelogUrl: String

    @BeforeEach
    fun startServer() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(Compression) {
                gzip {
                    minimumSize(1)
                }
            }
            routing {
                get("/releases/changelog") {
                    call.respondText(changelog, ContentType.Text.Plain, HttpStatusCode.OK)
                }
            }
        }.start(wait = false)
        val port: Int = runBlocking { server.engine.resolvedConnectors().first().port }
        changelogUrl = "http://127.0.0.1:$port/releases/changelog"
    }

    @AfterEach
    fun stopServer() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    @Test
    fun decodingClientLogsTheDecompressedChangelog() {
        val sink = CallLogSink()
        val client = HttpClient(ClientCIO) {
            install(ContentEncoding) {
                gzip()
            }
            install(Logging) {
                logger = sink
                level = LogLevel.BODY
                format = LoggingFormat.OkHttp
            }
        }

        val received: String = client.use {
            runBlocking {
                val response = it.get(changelogUrl)
                assertEquals(HttpStatusCode.OK, response.status)
                response.bodyAsText()
            }
        }
        assertEquals(changelog, received)

        val messages: List<String> = sink.awaitMessage("<-- END HTTP", timeoutMillis = 10_000)
        val everything: String = messages.joinToString("\n----\n")
        val request: List<String> = messages.first { it.startsWith("--> GET") }.lines()
        assertEquals("--> GET $changelogUrl", request.first(), everything)
        assertTrue(request.contains("Accept-Encoding: gzip"), "the client advertises gzip:\n$everything")

        val response: List<String> = messages.first { it.startsWith("<-- 200") }.lines()
        assertTrue(Regex("""<-- 200 OK \Q$changelogUrl\E \(\d+ms\)""").matches(response.first()), everything)
        assertTrue(response.contains("Vary: Accept-Encoding"), "the server negotiated an encoding:\n$everything")
        assertFalse(
            response.any { it.startsWith("Content-Encoding") },
            "the decoded response no longer carries its encoding:\n$everything",
        )
        assertTrue(messages.first { it.startsWith("<-- 200") }.contains(changelog), "the log holds plain text:\n$everything")
        assertTrue(
            Regex("""<-- END HTTP \(\d+ms, ${changelog.toByteArray().size}-byte body\)""").matches(response.last()),
            "the log counts decoded bytes:\n$everything",
        )
    }

    @Test
    fun mirroringClientLogsThatTheEncodedBodyIsOmitted() {
        val sink = CallLogSink()
        val client = HttpClient(ClientCIO) {
            install(Logging) {
                logger = sink
                level = LogLevel.BODY
                format = LoggingFormat.OkHttp
            }
        }

        val stored: ByteArray = client.use {
            runBlocking {
                val response = it.get(changelogUrl) {
                    header(HttpHeaders.AcceptEncoding, "gzip")
                }
                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("gzip", response.headers[HttpHeaders.ContentEncoding])
                response.body<ByteArray>()
            }
        }
        val inflated: String = GZIPInputStream(ByteArrayInputStream(stored)).use { it.readBytes().decodeToString() }
        assertEquals(changelog, inflated, "the mirror stores a payload that inflates to the changelog")
        assertTrue(stored.size < changelog.length, "the stored payload is the compressed form")

        val messages: List<String> = sink.awaitMessage("<-- END HTTP", timeoutMillis = 10_000)
        val everything: String = messages.joinToString("\n----\n")
        val responseMessage: String = messages.first { it.startsWith("<-- 200") }
        val response: List<String> = responseMessage.lines()
        assertTrue(Regex("""<-- 200 OK \Q$changelogUrl\E \(\d+ms\)""").matches(response.first()), everything)
        assertTrue(response.contains("Content-Encoding: gzip"), "the raw response keeps its encoding:\n$everything")
        assertTrue(
            Regex("""<-- END HTTP \(\d+ms, encoded body omitted\)""").matches(response.last()),
            "the encoded body is omitted from the log:\n$everything",
        )
        assertFalse(responseMessage.contains("release pipeline"), "no plain changelog text is logged:\n$everything")
    }
}
