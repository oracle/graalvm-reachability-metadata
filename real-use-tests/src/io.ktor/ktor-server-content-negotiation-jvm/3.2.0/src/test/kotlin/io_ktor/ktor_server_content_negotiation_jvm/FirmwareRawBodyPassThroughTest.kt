/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_content_negotiation_jvm

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Raw bodies the plugin passes through untouched by default, and the application-wide ignore list.
 *
 * A firmware-distribution service describes each release with a JSON manifest but serves and
 * stores the firmware image itself as raw bytes, from a byte array, an input stream or a byte
 * channel, while its JSON converter stays installed for the whole application. A deployment
 * webhook reads its JSON body as raw bytes first, so it can check the sender's HMAC signature
 * over exactly the bytes that arrived before decoding them. A legacy telemetry service takes
 * byte arrays off the ignore list so its browser clients get them as JSON number arrays. The
 * test checks that byte arrays, streams, channels, status codes and strings skip the JSON
 * converter on the way out, that byte arrays are received as the bytes sent even under a JSON
 * content type, and that removing a type from the ignore list hands it to the converter.
 */
class FirmwareRawBodyPassThroughTest {

    @Serializable
    data class Manifest(val device: String, val version: String, val sizeBytes: Int)

    @Serializable
    data class DeployEvent(val device: String, val version: String, val ring: String)

    private val images: ConcurrentHashMap<String, ByteArray> = ConcurrentHashMap()
    private val deployments: ConcurrentLinkedQueue<DeployEvent> = ConcurrentLinkedQueue()

    private fun runFirmwareService(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) {
                json()
            }
            routing {
                get("/firmware/{device}/manifest") {
                    val device: String = call.parameters["device"]!!
                    call.respond(Manifest(device, VERSION, IMAGE.size))
                }
                get("/firmware/{device}/image") {
                    call.respond(IMAGE)
                }
                get("/firmware/{device}/image/stream") {
                    val stream: InputStream = ByteArrayInputStream(IMAGE)
                    call.respond(stream)
                }
                get("/firmware/{device}/image/channel") {
                    call.respond(ByteReadChannel(IMAGE))
                }
                get("/firmware/{device}/notes") {
                    call.respond(NOTES)
                }
                put("/firmware/{device}/image") {
                    val device: String = call.parameters["device"]!!
                    val image: ByteArray = call.receive()
                    images[device] = image
                    call.respond(HttpStatusCode.Created)
                }
                delete("/firmware/{device}/image") {
                    val device: String = call.parameters["device"]!!
                    images.remove(device)
                    call.respond(HttpStatusCode.NoContent)
                }
                post("/webhooks/deploy") {
                    val raw: ByteArray = call.receive()
                    if (call.request.headers[SIGNATURE_HEADER] != sign(raw)) {
                        call.respond(HttpStatusCode.Unauthorized)
                        return@post
                    }
                    val event: DeployEvent = Json.decodeFromString(String(raw, Charsets.UTF_8))
                    deployments.add(event)
                    call.respond(HttpStatusCode.Accepted)
                }
            }
        }
        block()
    }

    @Test
    fun manifestIsEncodedAsJsonWhileImageBytesPassThroughUnchanged() = runFirmwareService {
        val manifest: HttpResponse = client.get("/firmware/gw-7/manifest") {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }
        assertEquals(HttpStatusCode.OK, manifest.status)
        assertEquals(ContentType.Application.Json, manifest.contentType()!!.withoutParameters())
        assertEquals(Manifest("gw-7", VERSION, IMAGE.size), Json.decodeFromString<Manifest>(manifest.bodyAsText()))

        for (path: String in listOf("/firmware/gw-7/image", "/firmware/gw-7/image/stream", "/firmware/gw-7/image/channel")) {
            val image: HttpResponse = client.get(path) {
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }
            assertEquals(HttpStatusCode.OK, image.status, path)
            // No converter ran, so the plugin labels nothing as JSON and the bytes arrive as written.
            assertNull(image.headers[HttpHeaders.ContentType], path)
            assertArrayEquals(IMAGE, image.bodyAsBytes(), path)
        }

        val notes: HttpResponse = client.get("/firmware/gw-7/notes") {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }
        assertEquals(HttpStatusCode.OK, notes.status)
        assertEquals(ContentType.Text.Plain, notes.contentType()!!.withoutParameters())
        assertEquals(NOTES, notes.bodyAsText())
    }

    @Test
    fun uploadedImageIsStoredAsReceivedAndRemovedWithABareStatus() = runFirmwareService {
        val upload: HttpResponse = client.put("/firmware/gw-7/image") {
            header(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
            setBody(IMAGE)
        }
        assertEquals(HttpStatusCode.Created, upload.status)
        assertArrayEquals(IMAGE, images["gw-7"])

        val removal: HttpResponse = client.delete("/firmware/gw-7/image")
        assertEquals(HttpStatusCode.NoContent, removal.status)
        assertEquals("", removal.bodyAsText())
        assertEquals(emptySet<String>(), images.keys)
    }

    @Test
    fun webhookVerifiesSignatureOverTheExactJsonBytesBeforeDecoding() = runFirmwareService {
        val payload: ByteArray = WEBHOOK_BODY.toByteArray(Charsets.UTF_8)

        val signed: HttpResponse = client.post("/webhooks/deploy") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            header(SIGNATURE_HEADER, sign(payload))
            setBody(payload)
        }
        assertEquals(HttpStatusCode.Accepted, signed.status)
        assertEquals(listOf(DeployEvent("gw-7", VERSION, "canary")), deployments.toList())

        // The same event re-encoded compactly is a different byte sequence, so the old signature fails.
        val reencoded: ByteArray = Json.encodeToString(DeployEvent("gw-7", VERSION, "canary")).toByteArray(Charsets.UTF_8)
        val tampered: HttpResponse = client.post("/webhooks/deploy") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            header(SIGNATURE_HEADER, sign(payload))
            setBody(reencoded)
        }
        assertEquals(HttpStatusCode.Unauthorized, tampered.status)
        assertEquals(1, deployments.size)
    }

    @Test
    fun byteArraysRemovedFromTheIgnoreListAreEncodedByTheConverter() = testApplication {
        application {
            install(ContentNegotiation) {
                json()
                removeIgnoredType<ByteArray>()
            }
            routing {
                get("/telemetry/{device}/frame") {
                    call.respond(IMAGE)
                }
                get("/telemetry/{device}/status") {
                    call.respond(NOTES)
                }
            }
        }

        val frame: HttpResponse = client.get("/telemetry/gw-7/frame") {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }
        assertEquals(HttpStatusCode.OK, frame.status)
        assertEquals(ContentType.Application.Json, frame.contentType()!!.withoutParameters())
        assertEquals("[127,69,76,70,0,-1,16,-128]", frame.bodyAsText())
        assertArrayEquals(IMAGE, Json.decodeFromString<ByteArray>(frame.bodyAsText()))

        val status: HttpResponse = client.get("/telemetry/gw-7/status") {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }
        assertEquals(ContentType.Text.Plain, status.contentType()!!.withoutParameters())
        assertEquals(NOTES, status.bodyAsText())
    }

    private companion object {
        const val VERSION: String = "4.2.1"
        const val NOTES: String = "Fixes watchdog reset on cold boot."
        const val SIGNATURE_HEADER: String = "X-Deploy-Signature"
        val IMAGE: ByteArray = byteArrayOf(0x7f, 0x45, 0x4c, 0x46, 0x00, 0xff.toByte(), 0x10, 0x80.toByte())
        val WEBHOOK_BODY: String = """
            {
              "device": "gw-7",
              "version": "4.2.1",
              "ring": "canary"
            }
        """.trimIndent()
        val SECRET: ByteArray = "deploy-hook-secret".toByteArray(Charsets.UTF_8)

        /** Hex-encoded HMAC-SHA256 of [body] under the shared webhook secret. */
        fun sign(body: ByteArray): String {
            val mac: Mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(SECRET, "HmacSHA256"))
            return mac.doFinal(body).joinToString("") { "%02x".format(it) }
        }
    }
}
