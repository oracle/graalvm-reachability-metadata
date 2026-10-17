/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_apache5_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
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
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.runBlocking
import kotlinx.io.readByteArray
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.zip.CRC32
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Multipart form-data and URL-encoded form submission on the `Apache5` engine.
 *
 * An expense tool files claims with scanned receipts attached as `multipart/form-data`, logs in
 * to the finance portal with a URL-encoded form post, and searches past claims with a form
 * encoded into the query string. The service parses each part and echoes what it received, so
 * the application checks field values, file names, part content types, and receipt checksums.
 */
class ExpenseClaimFormSubmissionTest {

    /** Deterministic receipt bytes and their checksum. */
    object Receipt {
        fun bytes(size: Int, seed: Int): ByteArray = ByteArray(size) { index -> ((index * 31 + seed) % 251).toByte() }

        fun checksum(bytes: ByteArray): Long = CRC32().apply { update(bytes) }.value
    }

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                post("/claims") {
                    val summary: MutableList<String> = mutableListOf()
                    call.receiveMultipart().forEachPart { part ->
                        when (part) {
                            is PartData.FormItem -> summary += "field ${part.name}=${part.value}"
                            is PartData.FileItem -> {
                                val bytes: ByteArray = part.provider().readRemaining().readByteArray()
                                summary += "file ${part.name} ${part.originalFileName} ${part.contentType} " +
                                    "${bytes.size} ${Receipt.checksum(bytes)}"
                            }
                            else -> summary += "other ${part.name}"
                        }
                        part.dispose()
                    }
                    call.respondText(summary.joinToString("\n"))
                }
                post("/login") {
                    val form: Parameters = call.receiveParameters()
                    val type: String = call.request.contentType().withoutParameters().toString()
                    if (form["password"] == "s3cret & safe") {
                        call.respondText("welcome ${form["username"]} via $type remember=${form["remember"]}")
                    } else {
                        call.respondText("denied", status = HttpStatusCode.Unauthorized)
                    }
                }
                get("/claims/search") {
                    val query: Parameters = call.request.queryParameters
                    val categories: List<String> = query.getAll("category")!!
                    call.respondText("submitter=${query["submitter"]} categories=${categories.joinToString("|")}")
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stopServer() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    private fun expenseClient(): HttpClient = HttpClient(Apache5) {
        engine {
            socketTimeout = 30_000
        }
    }

    @Test
    fun filesClaimWithReceiptAttachments() {
        val hotel: ByteArray = Receipt.bytes(48_000, 7)
        val taxi: ByteArray = Receipt.bytes(1_200, 13)

        expenseClient().use { client ->
            runBlocking {
                val response: HttpResponse = client.submitFormWithBinaryData(
                    url = "http://127.0.0.1:$port/claims",
                    formData = formData {
                        append("employee", "E-1042")
                        append("purpose", "Customer visit, Zurich")
                        append("amount", 412.5)
                        append(
                            "receipt",
                            hotel,
                            Headers.build {
                                append(HttpHeaders.ContentType, "application/pdf")
                                append(HttpHeaders.ContentDisposition, "filename=\"hotel.pdf\"")
                            },
                        )
                        append(
                            "receipt",
                            taxi,
                            Headers.build {
                                append(HttpHeaders.ContentType, "image/png")
                                append(HttpHeaders.ContentDisposition, "filename=\"taxi.png\"")
                            },
                        )
                    },
                )

                assertEquals(HttpStatusCode.OK, response.status)
                val expected: String = listOf(
                    "field employee=E-1042",
                    "field purpose=Customer visit, Zurich",
                    "field amount=412.5",
                    "file receipt hotel.pdf application/pdf ${hotel.size} ${Receipt.checksum(hotel)}",
                    "file receipt taxi.png image/png ${taxi.size} ${Receipt.checksum(taxi)}",
                ).joinToString("\n")
                assertEquals(expected, response.bodyAsText())
            }
        }
    }

    @Test
    fun logsInWithUrlEncodedForm() {
        expenseClient().use { client ->
            runBlocking {
                val accepted: HttpResponse = client.submitForm(
                    url = "http://127.0.0.1:$port/login",
                    formParameters = parameters {
                        append("username", "ana.petrovic@example.com")
                        append("password", "s3cret & safe")
                        append("remember", "true")
                    },
                )
                assertEquals(HttpStatusCode.OK, accepted.status)
                assertEquals(
                    "welcome ana.petrovic@example.com via ${ContentType.Application.FormUrlEncoded} remember=true",
                    accepted.bodyAsText(),
                )

                val rejected: HttpResponse = client.submitForm(
                    url = "http://127.0.0.1:$port/login",
                    formParameters = parameters {
                        append("username", "ana.petrovic@example.com")
                        append("password", "wrong")
                    },
                )
                assertEquals(HttpStatusCode.Unauthorized, rejected.status)
                assertEquals("denied", rejected.bodyAsText())
            }
        }
    }

    @Test
    fun searchesClaimsWithQueryEncodedForm() {
        expenseClient().use { client ->
            runBlocking {
                val response: HttpResponse = client.submitForm(
                    url = "http://127.0.0.1:$port/claims/search",
                    formParameters = parameters {
                        append("submitter", "E-1042 / Finance")
                        appendAll("category", listOf("travel", "meals & drinks"))
                    },
                    encodeInQuery = true,
                )

                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals("submitter=E-1042 / Finance categories=travel|meals & drinks", response.bodyAsText())
            }
        }
    }
}
