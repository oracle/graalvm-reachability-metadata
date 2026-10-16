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
import java.io.IOException
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Covers multipart per-part size limits set with `formFieldLimit`: an inbound-mail webhook caps
 * every part it decodes, accepting deliveries whose fields and attachments fit the cap and
 * aborting a delivery as soon as one part exceeds it.
 */
class MultipartFieldLimitTest {

    /** Decodes the parts of one webhook delivery into one summary line per part. */
    private class MailDecoder(private val form: MultiPartData) {

        suspend fun summarize(): String {
            val lines: MutableList<String> = mutableListOf()
            while (true) {
                val part: PartData = form.readPart() ?: break
                try {
                    lines += when (part) {
                        is PartData.FormItem -> "field ${part.name}=${part.value.length}"
                        is PartData.FileItem -> {
                            val bytes: ByteArray = part.provider().readRemaining().readByteArray()
                            "file ${part.originalFileName} ${part.contentType} ${bytes.size} ${bytes.sum()}"
                        }
                        else -> error("Unexpected part ${part::class.simpleName}")
                    }
                } finally {
                    part.dispose()
                }
            }
            return lines.joinToString("\n")
        }
    }

    /**
     * The webhook module: decodes deliveries with every part capped at [partLimit] bytes and
     * records why a delivery was aborted before letting the engine fail the call.
     */
    private class WebhookModule(
        private val partLimit: Long,
        private val rejections: CompletableDeferred<IOException>,
    ) {

        fun install(application: Application) {
            application.routing {
                post("/inbound-mail") {
                    val summary: String = try {
                        MailDecoder(call.receiveMultipart(formFieldLimit = partLimit)).summarize()
                    } catch (failure: IOException) {
                        rejections.complete(failure)
                        throw failure
                    }
                    call.respondText(summary, status = HttpStatusCode.Accepted)
                }
            }
        }
    }

    private val rejections: CompletableDeferred<IOException> = CompletableDeferred()
    private lateinit var server: EmbeddedServer<*, *>
    private lateinit var client: HttpClient
    private var port: Int = 0

    @BeforeEach
    fun startWebhook() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            WebhookModule(partLimit = 8192, rejections = rejections).install(this)
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
        client = HttpClient(ClientCIO)
    }

    @AfterEach
    fun stopWebhook() {
        client.close()
        server.stop(0, 0)
    }

    private suspend fun deliver(text: String, attachment: ByteArray): HttpResponse = withTimeout(30_000) {
        client.submitFormWithBinaryData(
            url = "http://127.0.0.1:$port/inbound-mail",
            formData = formData {
                append("from", "billing@example.com")
                append("text", text)
                append(
                    "attachment1",
                    attachment,
                    Headers.build {
                        append(HttpHeaders.ContentType, "application/pdf")
                        append(HttpHeaders.ContentDisposition, "filename=\"invoice.pdf\"")
                    },
                )
            },
        )
    }

    @Test
    fun partsWithinLimitAreDecoded(): Unit = runBlocking {
        val invoice = ByteArray(8192) { index: Int -> (index * 31 % 251).toByte() }

        val response: HttpResponse = deliver("x".repeat(8192), invoice)

        assertEquals(HttpStatusCode.Accepted, response.status)
        assertEquals(
            listOf(
                "field from=19",
                "field text=8192",
                "file invoice.pdf ${ContentType.Application.Pdf} 8192 ${invoice.sum()}",
            ),
            response.bodyAsText().lines(),
        )
        assertFalse(rejections.isCompleted)
    }

    @Test
    fun attachmentLargerThanLimitAbortsDelivery(): Unit = runBlocking {
        val invoice = ByteArray(8193) { index: Int -> (index % 7).toByte() }

        val outcome: Result<HttpResponse> = runCatching { deliver("Please find the invoice attached.", invoice) }

        assertTrue(
            outcome.isFailure || outcome.getOrThrow().status == HttpStatusCode.InternalServerError,
            "delivery was not aborted: $outcome",
        )
        val rejection: IOException = withTimeout(10_000) { rejections.await() }
        assertEquals(
            "Multipart content length exceeds limit 8193 > 8192; limit is defined using 'formFieldLimit' argument",
            rejection.message,
        )
    }
}
