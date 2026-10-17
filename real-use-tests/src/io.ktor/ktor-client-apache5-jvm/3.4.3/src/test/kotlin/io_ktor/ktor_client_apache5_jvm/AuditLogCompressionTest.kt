/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_apache5_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.compression.ContentEncodingConfig
import io.ktor.client.plugins.compression.appliedDecoders
import io.ktor.client.plugins.compression.compress
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.install
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.CompressionConfig
import io.ktor.server.plugins.compression.appliedDecoders
import io.ktor.server.plugins.compression.deflate
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.compression.minimumSize
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.ktor.server.cio.CIO as ServerCIO

/**
 * HTTP content encoding (gzip and deflate) of request and response bodies on the `Apache5` engine.
 *
 * An audit console downloads large plain-text audit logs that the service compresses, and ships
 * batches of audit events back with a gzip-compressed request body. The client negotiates the
 * encoding through `Accept-Encoding` quality values, the compressed bytes travel through the
 * engine, and the application checks the decoded text and which decoders were applied, both for
 * compressed logs and for short replies the service leaves uncompressed.
 */
class AuditLogCompressionTest {

    /** Deterministic audit-log text, large enough for the service to compress it. */
    object AuditLog {
        fun lines(count: Int): String = buildString {
            repeat(count) { index ->
                append("2026-10-17T08:")
                append((index % 60).toString().padStart(2, '0'))
                append(":00Z user=operator-")
                append(index % 7)
                append(" action=")
                append(if (index % 3 == 0) "LOGIN" else "UPDATE_RECORD")
                append(" record=")
                append(1000 + index)
                append('\n')
            }
        }
    }

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(Compression) {
                mode = CompressionConfig.Mode.All
                gzip {
                    priority = 1.0
                    minimumSize(512)
                }
                deflate {
                    priority = 0.9
                    minimumSize(512)
                }
            }
            routing {
                get("/audit/logs/{lines}") {
                    val lines: Int = call.parameters["lines"]!!.toInt()
                    call.response.header("X-Requested-Encodings", call.request.headers[HttpHeaders.AcceptEncoding] ?: "")
                    call.respondText(AuditLog.lines(lines), ContentType.Text.Plain)
                }
                post("/audit/events") {
                    val events: List<String> = call.receiveText().lines().filter { it.isNotEmpty() }
                    val decoders: String = call.request.appliedDecoders.joinToString(",")
                    call.respondText("accepted=${events.size} decoders=$decoders last=${events.last()}")
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stopServer() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    private fun auditClient(configure: ContentEncodingConfig.() -> Unit): HttpClient = HttpClient(Apache5) {
        expectSuccess = true
        install(ContentEncoding) {
            mode = ContentEncodingConfig.Mode.All
            configure()
        }
        engine {
            socketTimeout = 30_000
        }
    }

    @Test
    fun downloadsGzipCompressedAuditLog() {
        val expected: String = AuditLog.lines(2_000)

        auditClient { gzip(); deflate() }.use { client ->
            runBlocking {
                val response: HttpResponse = client.get("http://127.0.0.1:$port/audit/logs/2000")

                assertEquals(HttpStatusCode.OK, response.status)
                val requested: String = response.headers["X-Requested-Encodings"]!!
                assertTrue(requested.contains("gzip"), requested)
                assertTrue(requested.contains("deflate"), requested)
                assertEquals(listOf("gzip"), response.appliedDecoders)
                assertEquals(expected, response.bodyAsText())
            }
        }
    }

    @Test
    fun prefersDeflateWhenClientRanksItHigher() {
        val expected: String = AuditLog.lines(1_500)

        auditClient { deflate(1.0f); gzip(0.2f) }.use { client ->
            runBlocking {
                val response: HttpResponse = client.get("http://127.0.0.1:$port/audit/logs/1500")

                assertEquals(HttpStatusCode.OK, response.status)
                assertTrue(response.headers["X-Requested-Encodings"]!!.contains("gzip;q=0.2"))
                assertEquals(listOf("deflate"), response.appliedDecoders)
                assertEquals(expected, response.bodyAsText())
            }
        }
    }

    @Test
    fun leavesShortLogUncompressed() {
        auditClient { gzip(); deflate() }.use { client ->
            runBlocking {
                val response: HttpResponse = client.get("http://127.0.0.1:$port/audit/logs/2")

                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals(emptyList<String>(), response.appliedDecoders)
                assertEquals(AuditLog.lines(2), response.bodyAsText())
            }
        }
    }

    @Test
    fun uploadsGzipCompressedEventBatch() {
        val batch: String = AuditLog.lines(800)

        auditClient { gzip() }.use { client ->
            runBlocking {
                val response: HttpResponse = client.post("http://127.0.0.1:$port/audit/events") {
                    compress("gzip")
                    contentType(ContentType.Text.Plain)
                    setBody(batch)
                }

                assertEquals(HttpStatusCode.OK, response.status)
                val lastEvent: String = batch.lines().last { it.isNotEmpty() }
                assertEquals("accepted=800 decoders=gzip last=$lastEvent", response.bodyAsText())
            }
        }
    }
}
