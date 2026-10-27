/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_http_cio_jvm

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.Application
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.readByteArray
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Covers multipart/form-data decoding: an upload form whose text fields and binary file parts
 * are split on the request's boundary and streamed to a server route part by part, in order.
 */
class MultipartFormUploadTest {

    /** A file the upload route stored, with the metadata the client sent alongside it. */
    private class StoredFile(
        val field: String,
        val fileName: String,
        val contentType: ContentType?,
        val bytes: ByteArray,
    )

    /** What the upload route collected from one submitted form. */
    private class Submission(
        val fields: Map<String, String>,
        val files: List<StoredFile>,
    )

    /** Consumes every part of [form], reading each file body before the next part arrives. */
    private class UploadHandler(private val form: MultiPartData) {

        suspend fun collect(): Submission {
            val fields: MutableMap<String, String> = linkedMapOf()
            val files: MutableList<StoredFile> = mutableListOf()
            form.forEachPart { part: PartData ->
                when (part) {
                    is PartData.FormItem -> fields[part.name!!] = part.value
                    is PartData.FileItem -> files += StoredFile(
                        field = part.name!!,
                        fileName = part.originalFileName!!,
                        contentType = part.contentType,
                        bytes = part.provider().readRemaining().readByteArray(),
                    )
                    else -> error("Unexpected part ${part::class.simpleName}")
                }
                part.dispose()
            }
            return Submission(fields, files)
        }
    }

    /** The application module: one route that stores an uploaded form and acknowledges it. */
    private class UploadModule(private val received: CompletableDeferred<Submission>) {

        fun install(application: Application) {
            application.routing {
                post("/reports") {
                    val submission: Submission = UploadHandler(call.receiveMultipart()).collect()
                    received.complete(submission)
                    call.respondText(
                        "stored ${submission.fields.size} fields and ${submission.files.size} files",
                        status = HttpStatusCode.Created,
                    )
                }
            }
        }
    }

    @Test
    fun uploadRouteReceivesFieldsAndFilesFromMultipartForm(): Unit = runBlocking {
        val csv = "region,revenue\r\nnorth,1200\r\nsouth,950"
        val thumbnail: ByteArray = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x2D, 0x2D, 0x0D, 0x0A, 0x7F, 0xFF.toByte(),
        )
        val received: CompletableDeferred<Submission> = CompletableDeferred()
        val server: EmbeddedServer<*, *> = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            UploadModule(received).install(this)
        }.start(wait = false)
        val client = HttpClient(ClientCIO)
        try {
            val port: Int = server.engine.resolvedConnectors().first().port
            val response: HttpResponse = withTimeout(30_000) {
                client.submitFormWithBinaryData(
                    url = "http://127.0.0.1:$port/reports",
                    formData = formData {
                        append("title", "Quarterly report")
                        append("tags", "finance, q3")
                        append(
                            "report",
                            csv.encodeToByteArray(),
                            Headers.build {
                                append(HttpHeaders.ContentType, "text/csv")
                                append(HttpHeaders.ContentDisposition, "filename=\"report.csv\"")
                            },
                        )
                        append(
                            "thumbnail",
                            thumbnail,
                            Headers.build {
                                append(HttpHeaders.ContentType, "image/png")
                                append(HttpHeaders.ContentDisposition, "filename=\"chart.png\"")
                            },
                        )
                    },
                )
            }

            assertEquals(HttpStatusCode.Created, response.status)
            assertEquals("stored 2 fields and 2 files", response.bodyAsText())

            val submission: Submission = withTimeout(10_000) { received.await() }
            assertEquals(listOf("title", "tags"), submission.fields.keys.toList())
            assertEquals("Quarterly report", submission.fields["title"])
            assertEquals("finance, q3", submission.fields["tags"])

            assertEquals(2, submission.files.size)
            val report: StoredFile = submission.files[0]
            assertEquals("report", report.field)
            assertEquals("report.csv", report.fileName)
            assertEquals(ContentType.Text.CSV, report.contentType)
            assertEquals(csv, report.bytes.decodeToString())

            val chart: StoredFile = submission.files[1]
            assertEquals("thumbnail", chart.field)
            assertEquals("chart.png", chart.fileName)
            assertEquals(ContentType.Image.PNG, chart.contentType)
            assertArrayEquals(thumbnail, chart.bytes)
        } finally {
            client.close()
            server.stop(0, 0)
        }
    }
}
