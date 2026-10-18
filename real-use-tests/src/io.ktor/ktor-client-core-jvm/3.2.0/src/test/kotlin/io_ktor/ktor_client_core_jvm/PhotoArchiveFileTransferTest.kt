/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_core_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.body
import io.ktor.client.call.save
import io.ktor.client.content.LocalFileContent
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import io.ktor.server.cio.CIO as ServerCIO

/**
 * JVM file and stream bodies: `LocalFileContent` uploads, `InputStream` request and response bodies,
 * and calls saved with `HttpClientCall.save()`.
 *
 * A photo backup tool pushes pictures from a local album to an archive service and later restores
 * them. The test checks that a file sent as `LocalFileContent` arrives byte for byte with the content
 * type guessed from its extension and its length, that an `InputStream` body is uploaded with the
 * content type the request declares, that a download received as an `InputStream` restores an
 * identical file on disk, and that a call saved inside `execute { }` can still be read, twice, after
 * the streaming block has returned.
 */
class PhotoArchiveFileTransferTest {

    /** What the archive service remembers about one stored photo. */
    data class StoredPhoto(val bytes: ByteArray, val contentType: String, val declaredLength: String?)

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0
    private val archive: ConcurrentHashMap<String, StoredPhoto> = ConcurrentHashMap()

    @TempDir
    lateinit var workDir: File

    @BeforeEach
    fun start() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                put("/photos/{name}") {
                    val name: String = call.parameters["name"]!!
                    val bytes: ByteArray = call.receive<ByteArray>()
                    archive[name] = StoredPhoto(
                        bytes,
                        call.request.headers[HttpHeaders.ContentType] ?: "none",
                        call.request.headers[HttpHeaders.ContentLength],
                    )
                    call.respondText(sha256(bytes), status = HttpStatusCode.Created)
                }
                get("/photos/{name}") {
                    val photo: StoredPhoto = archive.getValue(call.parameters["name"]!!)
                    call.respondBytes(photo.bytes, ContentType.parse(photo.contentType))
                }
                get("/photos") {
                    val index: String = archive.entries.sortedBy { it.key }
                        .joinToString("\n") { "${it.key} ${it.value.bytes.size}" }
                    call.respondText(index)
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
    fun albumIsBackedUpAndRestoredThroughFilesAndStreams() {
        val base: String = "http://127.0.0.1:$port"
        val album: File = File(workDir, "album")
        val harbor: File = File(album, "2026/harbor.jpg")
        harbor.parentFile.mkdirs()
        harbor.writeBytes(photoBytes(HARBOR_SIZE, seed = 7))
        val lighthouse: ByteArray = photoBytes(LIGHTHOUSE_SIZE, seed = 11)

        HttpClient(CIO).use { client: HttpClient ->
            runBlocking {
                val fileUpload: LocalFileContent = LocalFileContent(album, "2026/harbor.jpg")
                assertEquals(ContentType.Image.JPEG, fileUpload.contentType)
                assertEquals(HARBOR_SIZE.toLong(), fileUpload.contentLength)

                val harborStored: HttpResponse = client.put("$base/photos/harbor.jpg") {
                    setBody(fileUpload)
                }
                assertEquals(HttpStatusCode.Created, harborStored.status)
                assertEquals(sha256(harbor.readBytes()), harborStored.bodyAsText())
                val harborRecord: StoredPhoto = archive.getValue("harbor.jpg")
                assertEquals(ContentType.Image.JPEG, ContentType.parse(harborRecord.contentType))
                assertEquals(HARBOR_SIZE.toString(), harborRecord.declaredLength)

                val streamStored: HttpResponse = client.put("$base/photos/lighthouse.png") {
                    contentType(ContentType.Image.PNG)
                    setBody(ByteArrayInputStream(lighthouse))
                }
                assertEquals(HttpStatusCode.Created, streamStored.status)
                assertEquals(sha256(lighthouse), streamStored.bodyAsText())
                assertArrayEquals(lighthouse, archive.getValue("lighthouse.png").bytes)
                assertEquals(ContentType.Image.PNG, ContentType.parse(archive.getValue("lighthouse.png").contentType))

                val restored: File = File(workDir, "restore/harbor.jpg")
                restored.parentFile.mkdirs()
                val download: HttpResponse = client.get("$base/photos/harbor.jpg")
                assertEquals(ContentType.Image.JPEG, download.contentType())
                download.body<InputStream>().use { input: InputStream ->
                    restored.outputStream().use { output -> input.copyTo(output) }
                }
                assertArrayEquals(harbor.readBytes(), restored.readBytes())

                val lighthouseBack: ByteArray = client.get("$base/photos/lighthouse.png").readRawBytes()
                assertArrayEquals(lighthouse, lighthouseBack)

                val savedIndex: HttpClientCall = client.prepareGet("$base/photos").execute { response: HttpResponse ->
                    response.call.save()
                }
                val expectedIndex: String = "harbor.jpg $HARBOR_SIZE\nlighthouse.png $LIGHTHOUSE_SIZE"
                assertEquals(HttpStatusCode.OK, savedIndex.response.status)
                assertEquals(expectedIndex, savedIndex.response.bodyAsText())
                assertEquals(expectedIndex, savedIndex.body<String>())
            }
        }
    }

    private companion object {
        const val HARBOR_SIZE: Int = 196_613
        const val LIGHTHOUSE_SIZE: Int = 48_271

        /** Produces reproducible binary content that spans several network chunks. */
        fun photoBytes(size: Int, seed: Int): ByteArray {
            var state: Int = seed
            return ByteArray(size) {
                state = state * 1_103_515_245 + 12_345
                (state ushr 16).toByte()
            }
        }

        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
