/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_core_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.forms.ChannelProvider
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentDisposition
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
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Client form submission through `submitForm` and multipart `formData` bodies.
 *
 * An expense app files a claim: it first posts the claim header as a URL-encoded form, then
 * uploads the claim with two receipt attachments as `multipart/form-data`, one from a byte
 * array and one streamed from a channel. The server echoes what it parsed. The test checks
 * that field values with spaces and symbols survive URL encoding, that every multipart part
 * arrives with its name, file name, content type and exact bytes, and that a prebuilt
 * `MultiPartFormDataContent` with a custom boundary is sent with that boundary.
 */
class ExpenseReceiptFormUploadTest {

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0

    @BeforeEach
    fun start() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                post("/claims") {
                    val form: Parameters = call.receiveParameters()
                    val summary: String = listOf("employee", "purpose", "amount", "tag")
                        .joinToString(";") { name -> "$name=${form.getAll(name)?.joinToString(",")}" }
                    call.respondText(summary, status = HttpStatusCode.Created)
                }
                post("/claims/{id}/receipts") {
                    val boundary: String = call.request.contentType().parameter("boundary") ?: "none"
                    val lines: MutableList<String> = mutableListOf("boundary=$boundary")
                    call.receiveMultipart().forEachPart { part: PartData ->
                        when (part) {
                            is PartData.FormItem -> lines += "field ${part.name}=${part.value}"
                            is PartData.FileItem -> {
                                val bytes: ByteArray = part.provider().toByteArray()
                                lines += "file ${part.name} ${part.originalFileName} ${part.contentType} " +
                                    "${bytes.size} ${String(bytes, Charsets.UTF_8)}"
                            }
                            else -> lines += "other ${part.name}"
                        }
                        part.dispose()
                    }
                    call.respondText(lines.joinToString("\n"))
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
    fun claimHeaderAndReceiptsAreSubmittedAsForms() {
        val base: String = "http://127.0.0.1:$port"
        val client = HttpClient(CIO)

        client.use {
            runBlocking {
                val created: HttpResponse = it.submitForm(
                    url = "$base/claims",
                    formParameters = parameters {
                        append("employee", "Ana Kovac")
                        append("purpose", "Client visit & dinner")
                        append("amount", "84.50")
                        append("tag", "travel")
                        append("tag", "meals")
                    },
                )
                assertEquals(HttpStatusCode.Created, created.status)
                assertEquals(
                    "employee=Ana Kovac;purpose=Client visit & dinner;amount=84.50;tag=travel,meals",
                    created.bodyAsText(),
                )

                val taxiReceipt: ByteArray = "TAXI 23.00 EUR".toByteArray(Charsets.UTF_8)
                val dinnerReceipt: String = "DINNER 61.50 EUR"
                val uploaded: HttpResponse = it.submitFormWithBinaryData(
                    url = "$base/claims/C-17/receipts",
                    formData = formData {
                        append("claimId", "C-17")
                        append("currency", "EUR")
                        append(
                            "receipt",
                            taxiReceipt,
                            Headers.build {
                                append(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
                                append(HttpHeaders.ContentDisposition, "filename=\"taxi.txt\"")
                            },
                        )
                        append(
                            "receipt",
                            ChannelProvider(size = dinnerReceipt.length.toLong()) {
                                ByteReadChannel(dinnerReceipt)
                            },
                            Headers.build {
                                append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                                append(
                                    HttpHeaders.ContentDisposition,
                                    ContentDisposition.Attachment
                                        .withParameter(ContentDisposition.Parameters.FileName, "dinner.bin")
                                        .toString(),
                                )
                            },
                        )
                    },
                )
                assertEquals(HttpStatusCode.OK, uploaded.status)
                val parsed: List<String> = uploaded.bodyAsText().lines()
                assertEquals(
                    listOf(
                        "field claimId=C-17",
                        "field currency=EUR",
                        "file receipt taxi.txt text/plain 14 TAXI 23.00 EUR",
                        "file receipt dinner.bin application/octet-stream 16 DINNER 61.50 EUR",
                    ),
                    parsed.drop(1),
                )

                val prebuilt = MultiPartFormDataContent(
                    parts = formData { append("note", "Approved by Marko") },
                    boundary = CUSTOM_BOUNDARY,
                )
                val noted: HttpResponse = it.post("$base/claims/C-17/receipts") { setBody(prebuilt) }
                assertEquals(
                    listOf("boundary=$CUSTOM_BOUNDARY", "field note=Approved by Marko"),
                    noted.bodyAsText().lines(),
                )

                val encoded = FormDataContent(parameters { append("purpose", "a+b=c & d") })
                assertEquals(ContentType.Application.FormUrlEncoded, encoded.contentType.withoutParameters())
                assertEquals("UTF-8", encoded.contentType.parameter("charset"))
                assertEquals("purpose=a%2Bb%3Dc+%26+d", String(encoded.bytes(), Charsets.UTF_8))
            }
        }
    }

    private companion object {
        const val CUSTOM_BOUNDARY: String = "expense-claim-boundary-42"
    }
}
