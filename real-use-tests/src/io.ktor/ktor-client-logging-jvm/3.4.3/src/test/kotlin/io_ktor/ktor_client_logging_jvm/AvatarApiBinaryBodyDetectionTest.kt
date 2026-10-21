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
import io.ktor.client.plugins.logging.LoggingFormat
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receive
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Collections
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Binary body detection by the default body filter of the `Logging` client plugin.
 *
 * A profile client uploads an avatar image, reads back the stored profile as JSON and
 * downloads the generated thumbnail from an embedded CIO server. Its wire log runs at the
 * `BODY` level without a body filter of its own, so the plugin must itself tell the image
 * bytes from text: the JSON profile is logged verbatim, while both image transfers are
 * omitted as binary with their sizes, and the server and client still exchange the real bytes.
 */
class AvatarApiBinaryBodyDetectionTest {

    /** Keeps every message the plugin emits, as an application wire log would. */
    class WireLog : Logger {
        private val entries: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override fun log(message: String) {
            entries += message
        }

        fun messages(): List<String> = entries.toList()
    }

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0
    private val storedAvatars: MutableList<ByteArray> = Collections.synchronizedList(mutableListOf())

    /** A PNG signature followed by pixel bytes, as an uploaded image starts. */
    private val avatar: ByteArray = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    ) + ByteArray(1016) { (it * 7 % 256).toByte() }

    /** The thumbnail the server renders: the first quarter of the stored image. */
    private fun thumbnailOf(image: ByteArray): ByteArray = image.copyOf(image.size / 4)

    @BeforeEach
    fun startServer() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                put("/profiles/alice/avatar") {
                    storedAvatars += call.receive<ByteArray>()
                    call.respondText(
                        """{"user":"alice","avatarBytes":${storedAvatars.last().size}}""",
                        ContentType.Application.Json,
                    )
                }
                get("/profiles/alice/thumbnail") {
                    call.respondBytes(thumbnailOf(storedAvatars.last()), ContentType.Image.PNG)
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
    fun wireLogOmitsImageBytesAndKeepsJson() {
        val wireLog = WireLog()
        val client = HttpClient(ClientCIO) {
            install(Logging) {
                logger = wireLog
                level = LogLevel.BODY
                format = LoggingFormat.OkHttp
            }
        }
        val profile = """{"user":"alice","avatarBytes":${avatar.size}}"""
        val thumbnail: ByteArray = thumbnailOf(avatar)

        client.use {
            runBlocking {
                val upload = it.put("http://127.0.0.1:$port/profiles/alice/avatar") {
                    contentType(ContentType.Image.PNG)
                    setBody(avatar)
                }
                assertEquals(HttpStatusCode.OK, upload.status)
                assertEquals(profile, upload.bodyAsText())

                val download = it.get("http://127.0.0.1:$port/profiles/alice/thumbnail")
                assertEquals(HttpStatusCode.OK, download.status)
                assertArrayEquals(thumbnail, download.bodyAsBytes())
            }
        }

        assertEquals(1, storedAvatars.size)
        assertArrayEquals(avatar, storedAvatars.first())

        val messages: List<String> = wireLog.messages()
        val everything: String = messages.joinToString("\n----\n")
        assertEquals(4, messages.size, everything)
        assertFalse(everything.contains("PNG"), "no image bytes may reach the log:\n$everything")
        val (uploadRequest, uploadResponse, downloadRequest, downloadResponse) = messages.map { it.lines() }

        assertEquals("--> PUT http://127.0.0.1:$port/profiles/alice/avatar", uploadRequest.first(), everything)
        assertTrue(uploadRequest.contains("Content-Type: image/png"), everything)
        assertTrue(uploadRequest.contains("Content-Length: ${avatar.size}"), everything)
        assertEquals(
            listOf("", "--> END PUT (binary ${avatar.size}-byte body omitted)"),
            uploadRequest.takeLast(2),
            "the avatar upload is omitted as binary with its size:\n$everything",
        )

        assertTrue(uploadResponse.first().startsWith("<-- 200 OK "), everything)
        assertTrue(uploadResponse.contains("Content-Type: application/json"), everything)
        assertEquals(profile, uploadResponse[uploadResponse.size - 2], "the JSON profile is logged:\n$everything")
        assertTrue(
            Regex("""<-- END HTTP \(\d+ms, ${profile.length}-byte body\)""").matches(uploadResponse.last()),
            everything,
        )

        assertEquals(
            listOf("--> GET http://127.0.0.1:$port/profiles/alice/thumbnail", "Accept: */*", "--> END GET"),
            downloadRequest,
            everything,
        )

        assertTrue(downloadResponse.first().startsWith("<-- 200 OK "), everything)
        assertTrue(downloadResponse.contains("Content-Type: image/png"), everything)
        assertTrue(downloadResponse.contains("Content-Length: ${thumbnail.size}"), everything)
        assertTrue(
            Regex("""<-- END HTTP \(\d+ms, binary ${thumbnail.size}-byte body omitted\)""")
                .matches(downloadResponse.last()),
            "the thumbnail download is omitted as binary with its size:\n$everything",
        )
    }
}
