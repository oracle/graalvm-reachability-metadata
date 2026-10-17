/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_okhttp_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.forms.ChannelProvider
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.parameters
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.runBlocking
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import okhttp3.Interceptor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

/**
 * HTML-style form submissions that the OkHttp engine sends: URL-encoded forms in the body and in
 * the query string, and `multipart/form-data` uploads mixing text fields with file parts.
 *
 * An insurance claims portal searches open claims with a GET form, files a claim with a
 * URL-encoded POST form, and then attaches evidence to the claim as a multipart upload: a note
 * field, a photo given as bytes and a scanned receipt streamed from a channel. A Ktor CIO service
 * parses the forms the way a web backend would. The test checks that repeated fields and
 * reserved characters survive encoding, that every multipart part arrives with its name, file
 * name, content type and exact bytes, and that the requests went out with the form content types.
 */
class ClaimsPortalFormSubmissionTest {

    @Serializable
    data class ClaimSearch(val holder: String, val statuses: List<String>)

    @Serializable
    data class FiledClaim(val id: String, val holder: String, val description: String, val tags: List<String>)

    @Serializable
    data class ReceivedPart(
        val name: String,
        val kind: String,
        val fileName: String?,
        val contentType: String?,
        val size: Int,
        val sha256: String,
        val text: String?,
    )

    @Serializable
    data class EvidenceReceipt(val claimId: String, val parts: List<ReceivedPart>)

    /** One request as seen on the wire by a network interceptor. */
    data class WireRequest(val method: String, val path: String, val query: String?, val contentType: String?)

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0

    @BeforeEach
    fun start() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(ServerContentNegotiation) { json() }
            routing {
                get("/claims/search") {
                    val query: Parameters = call.request.queryParameters
                    call.respond(ClaimSearch(query["holder"].orEmpty(), query.getAll("status").orEmpty()))
                }
                post("/claims") {
                    val form: Parameters = call.receiveParameters()
                    val claim = FiledClaim(
                        id = "CLM-${form["policy"]}",
                        holder = form["holder"].orEmpty(),
                        description = form["description"].orEmpty(),
                        tags = form.getAll("tag").orEmpty(),
                    )
                    call.respond(HttpStatusCode.Created, claim)
                }
                post("/claims/{id}/evidence") {
                    val received: MutableList<ReceivedPart> = ArrayList()
                    call.receiveMultipart().forEachPart { part: PartData ->
                        when (part) {
                            is PartData.FormItem -> {
                                val bytes: ByteArray = part.value.encodeToByteArray()
                                received += ReceivedPart(
                                    part.name.orEmpty(), "field", null, null, bytes.size, sha256(bytes), part.value,
                                )
                            }
                            is PartData.FileItem -> {
                                val bytes: ByteArray = part.provider().readRemaining().readByteArray()
                                received += ReceivedPart(
                                    part.name.orEmpty(),
                                    "file",
                                    part.originalFileName,
                                    part.contentType?.toString(),
                                    bytes.size,
                                    sha256(bytes),
                                    null,
                                )
                            }
                            else -> received += ReceivedPart(part.name.orEmpty(), "other", null, null, 0, "", null)
                        }
                        part.dispose()
                    }
                    call.respond(EvidenceReceipt(call.parameters["id"].orEmpty(), received))
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
    fun portalSearchesFilesAndAttachesEvidenceThroughForms() {
        val wire: MutableList<WireRequest> = CopyOnWriteArrayList()
        val recordWire = Interceptor { chain: Interceptor.Chain ->
            val request = chain.request()
            wire += WireRequest(
                request.method,
                request.url.encodedPath,
                request.url.encodedQuery,
                request.header("Content-Type"),
            )
            chain.proceed(request)
        }

        val photo: ByteArray = ByteArray(48_000) { index: Int -> ((index * 31) xor (index ushr 7)).toByte() }
        val receipt: ByteArray = "RECEIPT\nGarage Nord\nBumper repair 1240.00 EUR\n".repeat(200).encodeToByteArray()

        val client = HttpClient(OkHttp) {
            engine { addNetworkInterceptor(recordWire) }
            install(ClientContentNegotiation) { json() }
        }

        client.use {
            runBlocking {
                val search: ClaimSearch = it.submitForm(
                    url = "http://127.0.0.1:$port/claims/search",
                    formParameters = parameters {
                        append("holder", "Ana Kovac & Sons")
                        append("status", "open")
                        append("status", "under review")
                    },
                    encodeInQuery = true,
                ).body()
                assertEquals(ClaimSearch("Ana Kovac & Sons", listOf("open", "under review")), search)

                val filedResponse = it.submitForm(
                    url = "http://127.0.0.1:$port/claims",
                    formParameters = parameters {
                        append("policy", "7731")
                        append("holder", "Ana Kovac & Sons")
                        append("description", "Rear bumper dented in car park; repair quote = 1240 EUR")
                        append("tag", "vehicle")
                        append("tag", "collision")
                    },
                )
                assertEquals(HttpStatusCode.Created, filedResponse.status)
                val filed: FiledClaim = filedResponse.body()
                assertEquals(
                    FiledClaim(
                        "CLM-7731",
                        "Ana Kovac & Sons",
                        "Rear bumper dented in car park; repair quote = 1240 EUR",
                        listOf("vehicle", "collision"),
                    ),
                    filed,
                )

                val evidence: EvidenceReceipt = it.submitFormWithBinaryData(
                    url = "http://127.0.0.1:$port/claims/${filed.id}/evidence",
                    formData = formData {
                        append("note", "Photos taken on site; receipt from the garage.")
                        append(
                            "photo",
                            photo,
                            Headers.build {
                                append(HttpHeaders.ContentType, "image/jpeg")
                                append(HttpHeaders.ContentDisposition, "filename=\"bumper.jpg\"")
                            },
                        )
                        append(
                            "receipt",
                            ChannelProvider(receipt.size.toLong()) { ByteReadChannel(receipt) },
                            Headers.build {
                                append(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
                                append(HttpHeaders.ContentDisposition, "filename=\"receipt.txt\"")
                            },
                        )
                    },
                ).body()

                assertEquals("CLM-7731", evidence.claimId)
                assertEquals(listOf("note", "photo", "receipt"), evidence.parts.map { part -> part.name })

                val note: ReceivedPart = evidence.parts[0]
                assertEquals("field", note.kind)
                assertEquals("Photos taken on site; receipt from the garage.", note.text)

                val photoPart: ReceivedPart = evidence.parts[1]
                assertEquals("file", photoPart.kind)
                assertEquals("bumper.jpg", photoPart.fileName)
                assertEquals("image/jpeg", photoPart.contentType)
                assertEquals(photo.size, photoPart.size)
                assertEquals(sha256(photo), photoPart.sha256)

                val receiptPart: ReceivedPart = evidence.parts[2]
                assertEquals("file", receiptPart.kind)
                assertEquals("receipt.txt", receiptPart.fileName)
                assertTrue(receiptPart.contentType.orEmpty().startsWith("text/plain"), "${receiptPart.contentType}")
                assertEquals(receipt.size, receiptPart.size)
                assertEquals(sha256(receipt), receiptPart.sha256)
            }
        }

        assertEquals(
            listOf("GET /claims/search", "POST /claims", "POST /claims/CLM-7731/evidence"),
            wire.map { request: WireRequest -> "${request.method} ${request.path}" },
        )
        val searchQuery: String = wire[0].query.orEmpty()
        assertTrue(searchQuery.contains("status=open"), searchQuery)
        assertTrue(searchQuery.contains("status=under"), searchQuery)
        assertTrue(searchQuery.contains("%26"), "the ampersand in the holder name is escaped: $searchQuery")
        assertTrue(
            wire[1].contentType.orEmpty().startsWith("application/x-www-form-urlencoded"),
            "${wire[1].contentType}",
        )
        assertTrue(wire[2].contentType.orEmpty().startsWith("multipart/form-data"), "${wire[2].contentType}")
        assertTrue(wire[2].contentType.orEmpty().contains("boundary="), "${wire[2].contentType}")
    }

    private companion object {
        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte: Byte -> "%02x".format(byte) }
    }
}
