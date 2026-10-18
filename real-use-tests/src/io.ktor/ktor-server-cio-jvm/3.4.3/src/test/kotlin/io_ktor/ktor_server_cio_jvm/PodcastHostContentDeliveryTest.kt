/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_cio_jvm

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.toHttpDate
import io.ktor.server.application.install
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.compression.matchContentType
import io.ktor.server.plugins.compression.minimumSize
import io.ktor.server.plugins.conditionalheaders.ConditionalHeaders
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

/**
 * The CIO engine's HTTP content delivery: gzip response and request encoding, byte-range
 * partial content, conditional GET on entity tags and modification dates, and automatic
 * HEAD responses.
 *
 * A podcast host serves episode audio and a JSON catalog from an embedded CIO server.
 * Listeners' apps fetch the catalog gzip-compressed, players seek inside an episode with
 * single and multiple byte ranges, caching proxies revalidate with `If-None-Match`,
 * `If-Modified-Since` and `If-Range`, feed checkers probe the episode with `HEAD`, and the
 * production desk uploads a gzip-encoded transcript. The test checks every status code,
 * the delivery headers, and that each decoded body matches what the host published.
 */
class PodcastHostContentDeliveryTest {

    /** An episode file of known length, the kind of body that byte ranges can slice. */
    class EpisodeAudio(private val bytes: ByteArray) : OutgoingContent.ReadChannelContent() {
        override val contentType: ContentType = ContentType.Audio.MPEG
        override val contentLength: Long = bytes.size.toLong()

        override fun readFrom(): ByteReadChannel = ByteReadChannel(bytes)
    }

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0
    private val audio: ByteArray = ByteArray(AUDIO_SIZE) { index: Int -> ((index * 31 + 7) % 251).toByte() }
    private val published: GMTDate = GMTDate(PUBLISHED_MILLIS)
    private val transcripts: MutableMap<String, String> = ConcurrentHashMap()

    @BeforeEach
    fun start() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(Compression) {
                gzip {
                    matchContentType(ContentType.Application.Json, ContentType.Text.Plain)
                    minimumSize(256)
                }
            }
            install(ConditionalHeaders)
            install(PartialContent) { maxRangeCount = 4 }
            install(AutoHeadResponse)
            routing {
                get("/catalog.json") {
                    call.respondText(catalogJson(), ContentType.Application.Json)
                }
                get("/status.txt") {
                    call.respondText("on air", ContentType.Text.Plain)
                }
                get("/episodes/{id}/audio") {
                    call.response.header(HttpHeaders.ETag, "\"ep-${call.parameters["id"]}-v2\"")
                    call.response.header(HttpHeaders.LastModified, published.toHttpDate())
                    call.respond(EpisodeAudio(audio))
                }
                post("/episodes/{id}/transcript") {
                    val id: String = call.parameters["id"]!!
                    val text: String = call.receiveText()
                    transcripts[id] = text
                    call.respondText("${text.split(' ').size} words", ContentType.Text.Plain)
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stop() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    @Test
    fun listenersReceiveCompressedRangedAndRevalidatedContent() {
        HttpClient(ClientCIO).use { client: HttpClient ->
            runBlocking {
                val base = "http://127.0.0.1:$port"

                val compressed: HttpResponse = client.get("$base/catalog.json") {
                    header(HttpHeaders.AcceptEncoding, "gzip")
                }
                assertEquals(HttpStatusCode.OK, compressed.status)
                assertEquals("gzip", compressed.headers[HttpHeaders.ContentEncoding])
                assertTrue(compressed.headers.getAll(HttpHeaders.Vary).orEmpty().any { it.contains("Accept-Encoding") })
                assertEquals(catalogJson(), gunzip(compressed.bodyAsBytes()))

                val identity: HttpResponse = client.get("$base/catalog.json")
                assertNull(identity.headers[HttpHeaders.ContentEncoding])
                assertEquals(catalogJson(), identity.bodyAsText())

                val small: HttpResponse = client.get("$base/status.txt") {
                    header(HttpHeaders.AcceptEncoding, "gzip")
                }
                assertNull(small.headers[HttpHeaders.ContentEncoding])
                assertEquals("on air", small.bodyAsText())

                val full: HttpResponse = client.get("$base/episodes/7/audio") {
                    header(HttpHeaders.AcceptEncoding, "gzip")
                }
                assertEquals(HttpStatusCode.OK, full.status)
                assertNull(full.headers[HttpHeaders.ContentEncoding])
                assertEquals("bytes", full.headers[HttpHeaders.AcceptRanges])
                assertEquals("\"ep-7-v2\"", full.headers[HttpHeaders.ETag])
                assertEquals(published.toHttpDate(), full.headers[HttpHeaders.LastModified])
                assertArrayEquals(audio, full.bodyAsBytes())

                val seek: HttpResponse = client.get("$base/episodes/7/audio") {
                    header(HttpHeaders.Range, "bytes=1000-1099")
                }
                assertEquals(HttpStatusCode.PartialContent, seek.status)
                assertEquals("bytes 1000-1099/$AUDIO_SIZE", seek.headers[HttpHeaders.ContentRange])
                assertArrayEquals(audio.copyOfRange(1000, 1100), seek.bodyAsBytes())

                val tail: HttpResponse = client.get("$base/episodes/7/audio") {
                    header(HttpHeaders.Range, "bytes=-16")
                }
                assertEquals(HttpStatusCode.PartialContent, tail.status)
                assertEquals("bytes ${AUDIO_SIZE - 16}-${AUDIO_SIZE - 1}/$AUDIO_SIZE", tail.headers[HttpHeaders.ContentRange])
                assertArrayEquals(audio.copyOfRange(AUDIO_SIZE - 16, AUDIO_SIZE), tail.bodyAsBytes())

                val chapters: HttpResponse = client.get("$base/episodes/7/audio") {
                    header(HttpHeaders.Range, "bytes=0-9,2048-2057")
                }
                assertEquals(HttpStatusCode.PartialContent, chapters.status)
                assertTrue(
                    chapters.contentType()?.match(ContentType.MultiPart.ByteRanges) == true,
                    "${chapters.contentType()}",
                )
                val parts: String = String(chapters.bodyAsBytes(), Charsets.ISO_8859_1)
                assertTrue(parts.contains("Content-Range: bytes 0-9/$AUDIO_SIZE"), parts)
                assertTrue(parts.contains("Content-Range: bytes 2048-2057/$AUDIO_SIZE"), parts)
                assertTrue(parts.contains(String(audio.copyOfRange(2048, 2058), Charsets.ISO_8859_1)), parts)

                val beyond: HttpResponse = client.get("$base/episodes/7/audio") {
                    header(HttpHeaders.Range, "bytes=${AUDIO_SIZE + 10}-${AUDIO_SIZE + 20}")
                }
                assertEquals(HttpStatusCode.RequestedRangeNotSatisfiable, beyond.status)
                assertEquals("bytes */$AUDIO_SIZE", beyond.headers[HttpHeaders.ContentRange])

                val unchanged: HttpResponse = client.get("$base/episodes/7/audio") {
                    header(HttpHeaders.IfNoneMatch, "\"ep-7-v2\"")
                }
                assertEquals(HttpStatusCode.NotModified, unchanged.status)
                assertEquals(0, unchanged.bodyAsBytes().size)

                val notSince: HttpResponse = client.get("$base/episodes/7/audio") {
                    header(HttpHeaders.IfModifiedSince, GMTDate(PUBLISHED_MILLIS + 3_600_000L).toHttpDate())
                }
                assertEquals(HttpStatusCode.NotModified, notSince.status)

                val stale: HttpResponse = client.get("$base/episodes/7/audio") {
                    header(HttpHeaders.IfNoneMatch, "\"ep-7-v1\"")
                }
                assertEquals(HttpStatusCode.OK, stale.status)
                assertArrayEquals(audio, stale.bodyAsBytes())

                val resumed: HttpResponse = client.get("$base/episodes/7/audio") {
                    header(HttpHeaders.Range, "bytes=100-199")
                    header(HttpHeaders.IfRange, "\"ep-7-v2\"")
                }
                assertEquals(HttpStatusCode.PartialContent, resumed.status)
                assertArrayEquals(audio.copyOfRange(100, 200), resumed.bodyAsBytes())

                val restarted: HttpResponse = client.get("$base/episodes/7/audio") {
                    header(HttpHeaders.Range, "bytes=100-199")
                    header(HttpHeaders.IfRange, "\"ep-7-v1\"")
                }
                assertEquals(HttpStatusCode.OK, restarted.status)
                assertArrayEquals(audio, restarted.bodyAsBytes())

                val probe: HttpResponse = client.head("$base/episodes/7/audio")
                assertEquals(HttpStatusCode.OK, probe.status)
                assertEquals(AUDIO_SIZE.toString(), probe.headers[HttpHeaders.ContentLength])
                assertEquals("\"ep-7-v2\"", probe.headers[HttpHeaders.ETag])
                assertEquals(0, probe.bodyAsBytes().size)

                val transcript: String = List(120) { index: Int -> "segment$index" }.joinToString(" ")
                val upload: HttpResponse = client.post("$base/episodes/7/transcript") {
                    header(HttpHeaders.ContentEncoding, "gzip")
                    contentType(ContentType.Text.Plain)
                    setBody(gzip(transcript))
                }
                assertEquals(HttpStatusCode.OK, upload.status)
                assertEquals("120 words", upload.bodyAsText())
            }
        }

        assertEquals(mapOf("7" to List(120) { index: Int -> "segment$index" }.joinToString(" ")), transcripts.toMap())
    }

    private fun catalogJson(): String = (1..12).joinToString(",", "[", "]") { number: Int ->
        """{"episode":$number,"title":"Night shift chronicles part $number","minutes":${30 + number}}"""
    }

    private fun gunzip(bytes: ByteArray): String =
        GZIPInputStream(bytes.inputStream()).use { String(it.readBytes(), Charsets.UTF_8) }

    private fun gzip(text: String): ByteArray {
        val buffer = ByteArrayOutputStream()
        GZIPOutputStream(buffer).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return buffer.toByteArray()
    }

    private companion object {
        const val AUDIO_SIZE: Int = 4096
        const val PUBLISHED_MILLIS: Long = 1_760_000_000_000L
    }
}
