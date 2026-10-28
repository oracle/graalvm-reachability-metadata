/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_core

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.readRemaining
import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.readByteArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import io.ktor.server.cio.CIO as ServerCIO

/** Exercises multipart form uploads by attaching a binary receipt to an expense claim. */
class ExpenseMultipartUploadTest {
    @Test
    @Timeout(60)
    fun uploadsClaimFieldsAndReceiptWithoutChangingBinaryContent(): Unit = runBlocking {
        withTimeout(45_000) {
            val server = embeddedServer(ServerCIO, host = "127.0.0.1", port = 0) {
                routing {
                    post("/expenses") {
                        var claimId: String = ""
                        var fileName: String? = null
                        var mediaType: ContentType? = null
                        var receipt: ByteArray = byteArrayOf()
                        call.receiveMultipart().forEachPart { part: PartData ->
                            try {
                                when (part) {
                                    is PartData.FormItem -> if (part.name == "claimId") claimId = part.value
                                    is PartData.FileItem -> if (part.name == "receipt") {
                                        fileName = part.originalFileName
                                        mediaType = part.contentType
                                        receipt = part.provider().readRemaining().readByteArray()
                                    }
                                    else -> Unit
                                }
                            } finally {
                                part.dispose()
                            }
                        }
                        call.respondText(
                            "Stored $claimId/$fileName: $mediaType; ${Base64.getEncoder().encodeToString(receipt)}",
                            status = HttpStatusCode.Created
                        )
                    }
                }
            }
            try {
                server.start(wait = false)
                val port: Int = server.engine.resolvedConnectors().single().port
                HttpClient(CIO) {
                    install(HttpTimeout) {
                        requestTimeoutMillis = 15_000
                        connectTimeoutMillis = 15_000
                        socketTimeoutMillis = 15_000
                    }
                }.use { client: HttpClient ->
                    val uploaded: HttpResponse = client.submitFormWithBinaryData(
                        url = "http://127.0.0.1:$port/expenses",
                        formData = formData {
                            append("claimId", "expense-42")
                            append("receipt", byteArrayOf(0, 1, 2, 3, 127, -128, -1), Headers.build {
                                append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                                append(HttpHeaders.ContentDisposition, "filename=\"receipt.bin\"")
                            })
                        }
                    )
                    assertEquals(HttpStatusCode.Created, uploaded.status)
                    assertEquals(
                        "Stored expense-42/receipt.bin: application/octet-stream; AAECA3+A/w==",
                        uploaded.bodyAsText()
                    )
                }
            } finally {
                server.stop(0, 10_000)
            }
        }
    }
}
