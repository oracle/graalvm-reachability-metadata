/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_cio_jvm

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.parameters
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.util.getOrFail
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.CRC32
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

/**
 * The CIO engine's form decoding: multipart/form-data uploads with binary file parts and
 * URL-encoded form posts.
 *
 * An insurance claims office runs on an embedded CIO server. An adjuster files a claim as
 * a multipart form that carries text fields and two photos, one of them large enough to
 * span many read buffers and seeded with CRLF and dash runs that resemble a part boundary.
 * A supervisor later settles the claim with a URL-encoded form whose values need percent
 * decoding. The test checks the fields, file names, content types, sizes, and checksums
 * the server decoded, and the settled claim it reports back.
 */
class ClaimsOfficeFormUploadTest {

    data class Photo(val field: String, val fileName: String, val contentType: String, val bytes: ByteArray)

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0
    private val claims: MutableMap<String, String> = ConcurrentHashMap()
    private val requestTypes: MutableList<String> = CopyOnWriteArrayList()

    @BeforeEach
    fun start() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                post("/claims") {
                    requestTypes += call.request.contentType().withoutParameters().toString()
                    val form: MultiPartData = call.receiveMultipart(formFieldLimit = 4L * 1024 * 1024)
                    val fields: MutableMap<String, String> = sortedMapOf()
                    val files: MutableList<String> = mutableListOf()
                    form.forEachPart { part: PartData ->
                        when (part) {
                            is PartData.FormItem -> fields[part.name!!] = part.value
                            is PartData.FileItem -> {
                                val bytes: ByteArray = part.provider().toByteArray()
                                files += "${part.name}=${part.originalFileName};${part.contentType};" +
                                    "${bytes.size};${crc(bytes)}"
                            }
                            else -> Unit
                        }
                        part.dispose()
                    }
                    val claimId = "C-${fields.getValue("policy").takeLast(4)}"
                    claims[claimId] = "open"
                    call.respondText(
                        (listOf("claim $claimId") + fields.map { "${it.key}=${it.value}" } + files).joinToString("\n"),
                        status = HttpStatusCode.Created,
                    )
                }
                post("/claims/settle") {
                    requestTypes += call.request.contentType().withoutParameters().toString()
                    val form: Parameters = call.receiveParameters()
                    val claimId: String = form.getOrFail("claim")
                    val previous: String = claims.replace(claimId, "settled") ?: "unknown"
                    call.respondText(
                        "$claimId $previous->settled payout=${form["payout"]} note=${form["note"]} " +
                            "approvers=${form.getAll("approver")}",
                    )
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
    fun claimsAreFiledAndSettledThroughCioFormDecoding() {
        val front = Photo("photo", "front bumper.jpg", "image/jpeg", photoBytes(180_000, seed = 7))
        val plate = Photo("photo", "plate.png", "image/png", photoBytes(2_048, seed = 31))

        HttpClient(ClientCIO).use { client: HttpClient ->
            runBlocking {
                val filed: HttpResponse = client.submitFormWithBinaryData(
                    url = "http://127.0.0.1:$port/claims",
                    formData = formData {
                        append("policy", "POL-2024-8812")
                        append("description", "Rear-ended at a light,\r\nbumper and plate damaged")
                        listOf(front, plate).forEach { photo: Photo ->
                            append(
                                photo.field,
                                photo.bytes,
                                Headers.build {
                                    append(HttpHeaders.ContentType, photo.contentType)
                                    append(HttpHeaders.ContentDisposition, "filename=\"${photo.fileName}\"")
                                },
                            )
                        }
                    },
                )
                assertEquals(HttpStatusCode.Created, filed.status)
                assertEquals(
                    listOf(
                        "claim C-8812",
                        "description=Rear-ended at a light,\r\nbumper and plate damaged",
                        "policy=POL-2024-8812",
                        "photo=front bumper.jpg;image/jpeg;180000;${crc(front.bytes)}",
                        "photo=plate.png;image/png;2048;${crc(plate.bytes)}",
                    ).joinToString("\n"),
                    filed.bodyAsText(),
                )

                val settled: HttpResponse = client.submitForm(
                    url = "http://127.0.0.1:$port/claims/settle",
                    formParameters = parameters {
                        append("claim", "C-8812")
                        append("payout", "1250.00 EUR")
                        append("note", "parts & labour = 100%")
                        append("approver", "m.ito")
                        append("approver", "r.diaz")
                    },
                )
                assertEquals(HttpStatusCode.OK, settled.status)
                assertEquals(
                    "C-8812 open->settled payout=1250.00 EUR note=parts & labour = 100% approvers=[m.ito, r.diaz]",
                    settled.bodyAsText(),
                )
            }
        }

        assertEquals(mapOf("C-8812" to "settled"), claims.toMap())
        assertEquals(
            listOf(ContentType.MultiPart.FormData.toString(), ContentType.Application.FormUrlEncoded.toString()),
            requestTypes,
        )
    }

    private fun crc(bytes: ByteArray): Long = CRC32().apply { update(bytes) }.value

    /** Pseudo-random image bytes with boundary-like `\r\n--` runs scattered through them. */
    private fun photoBytes(size: Int, seed: Int): ByteArray {
        var state: Int = seed
        val bytes = ByteArray(size) {
            state = state * 1_103_515_245 + 12_345
            (state ushr 16).toByte()
        }
        val decoy: ByteArray = "\r\n--".toByteArray()
        for (offset: Int in 0 until size - decoy.size step 997) {
            decoy.copyInto(bytes, offset)
        }
        return bytes
    }
}
