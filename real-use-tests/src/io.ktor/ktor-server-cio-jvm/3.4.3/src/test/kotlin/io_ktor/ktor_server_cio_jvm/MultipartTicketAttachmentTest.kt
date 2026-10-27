/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_cio_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO as ClientCIO
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
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.readRemaining
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.readByteArray
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir

/**
 * Exercises CIO multipart form uploads through a support ticket with a persisted binary attachment.
 */
class MultipartTicketAttachmentTest {
    @Test
    @Timeout(60)
    fun `accepts ticket fields and stores the attachment without changing its bytes`(@TempDir directory: Path): Unit =
        runBlocking {
            val storedAttachment: Path = directory.resolve("ticket-attachment.bin")
            val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
                routing {
                    post("/tickets") {
                        var subject: String = ""
                        var category: String = ""
                        var filename: String = ""
                        var size: Int = 0
                        call.receiveMultipart().forEachPart { part: PartData ->
                            try {
                                when (part) {
                                    is PartData.FormItem -> when (part.name) {
                                        "subject" -> subject = part.value
                                        "category" -> category = part.value
                                    }
                                    is PartData.FileItem -> if (part.name == "attachment") {
                                        filename = part.originalFileName.orEmpty()
                                        val bytes: ByteArray = part.provider().readRemaining().readByteArray()
                                        Files.write(storedAttachment, bytes)
                                        size = bytes.size
                                    }
                                    else -> Unit
                                }
                            } finally {
                                part.dispose()
                            }
                        }
                        call.respondText(
                            "ticket:$category:$subject:$filename:$size",
                            ContentType.Text.Plain,
                            HttpStatusCode.Created,
                        )
                    }
                }
            }
            try {
                withTimeout(40_000) {
                    server.start(wait = false)
                    val port: Int = server.engine.resolvedConnectors().single().port
                    HttpClient(ClientCIO) {
                        install(HttpTimeout) {
                            connectTimeoutMillis = 15_000
                            requestTimeoutMillis = 15_000
                            socketTimeoutMillis = 15_000
                        }
                    }.use { client: HttpClient ->
                        val attachment: ByteArray = ByteArray(8192) { index: Int -> (index % 256).toByte() }
                        val response: HttpResponse = client.submitFormWithBinaryData(
                            url = "http://127.0.0.1:$port/tickets",
                            formData = formData {
                                append("subject", "Invoice amount needs review")
                                append("category", "billing")
                                append(
                                    "attachment",
                                    attachment,
                                    Headers.build {
                                        append(HttpHeaders.ContentType, "application/octet-stream")
                                        append(HttpHeaders.ContentDisposition, "filename=\"invoice.bin\"")
                                    },
                                )
                            },
                        )
                        assertEquals(HttpStatusCode.Created, response.status)
                        assertEquals(
                            "ticket:billing:Invoice amount needs review:invoice.bin:8192",
                            response.bodyAsText(),
                        )
                        assertArrayEquals(attachment, Files.readAllBytes(storedAttachment))
                    }
                }
            } finally {
                server.stop(0, 10_000)
            }
        }
}
