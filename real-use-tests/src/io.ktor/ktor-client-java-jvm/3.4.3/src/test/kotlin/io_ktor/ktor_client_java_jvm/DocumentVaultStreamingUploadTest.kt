/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_java_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.prepareGet
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.content.ChannelWriterContent
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.contentType
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readLine
import io.ktor.utils.io.toByteArray
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Multipart form uploads and streamed request and response bodies through the Java engine, as a
 * back-office tool files expense receipts into a document vault and streams audit exports out.
 */
public class DocumentVaultStreamingUploadTest {

    /** One part of a multipart upload as the vault received it. */
    data class ReceivedPart(val name: String, val fileName: String?, val contentType: String?, val content: String)

    /** The document vault: accepts multipart, form-encoded, and streamed uploads; streams exports. */
    class DocumentVault : AutoCloseable {
        /** The parts of each multipart upload, in arrival order. */
        val multipartUploads: MutableList<List<ReceivedPart>> = CopyOnWriteArrayList()

        /** The `Transfer-Encoding` header of each streamed upload, in arrival order. */
        val streamedTransferEncodings: MutableList<String> = CopyOnWriteArrayList()

        private val server: EmbeddedServer<*, *> = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            routing {
                post("/receipts") {
                    val parts: MutableList<ReceivedPart> = mutableListOf()
                    call.receiveMultipart().forEachPart { part: PartData ->
                        val content: String = when (part) {
                            is PartData.FormItem -> part.value
                            is PartData.FileItem -> part.provider().toByteArray().decodeToString()
                            else -> "<unexpected ${part::class.simpleName}>"
                        }
                        val fileName: String? = (part as? PartData.FileItem)?.originalFileName
                        parts += ReceivedPart(part.name!!, fileName, part.contentType?.toString(), content)
                        part.dispose()
                    }
                    multipartUploads += parts
                    call.respondText("filed ${parts.size} parts", status = HttpStatusCode.Created)
                }
                post("/search") {
                    val query: Parameters = call.receiveParameters()
                    call.respondText("merchant=${query["merchant"]};tags=${query.getAll("tag")}")
                }
                put("/archive/{name}") {
                    streamedTransferEncodings += call.request.headers[HttpHeaders.TransferEncoding] ?: "<none>"
                    val body: ByteArray = call.receiveChannel().toByteArray()
                    val lines: Int = body.decodeToString().lines().count { line: String -> line.isNotEmpty() }
                    call.respondText("${call.parameters["name"]}: ${body.size} bytes, $lines lines")
                }
                get("/audit/export") {
                    val rows: Int = call.request.queryParameters["rows"]!!.toInt()
                    call.respondBytesWriter(ContentType.Text.CSV) {
                        writeStringUtf8("id,amount\n")
                        for (row: Int in 1..rows) {
                            writeStringUtf8("$row,${row * 125}\n")
                            flush()
                        }
                    }
                }
            }
        }

        val port: Int

        init {
            server.start(wait = false)
            port = runBlocking { server.engine.resolvedConnectors().first().port }
        }

        override fun close() {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 2_000)
        }
    }

    @Test
    fun filesReceiptsAsMultipartFormData(): Unit = runBlocking {
        DocumentVault().use { vault: DocumentVault ->
            val tool: HttpClient = HttpClient(Java) {
                defaultRequest { url("http://127.0.0.1:${vault.port}/") }
            }
            tool.use {
                val receipt: String = "date,merchant,amount\n2026-10-12,Cafe Nord,14.50\n"
                val sentBytes: MutableList<Long> = CopyOnWriteArrayList()
                val response: HttpResponse = tool.submitFormWithBinaryData(
                    url = "receipts",
                    formData = formData {
                        append("employee", "e-4471")
                        append("category", "meals")
                        append(
                            "receipt",
                            receipt.encodeToByteArray(),
                            Headers.build {
                                append(HttpHeaders.ContentType, ContentType.Text.CSV.toString())
                                append(HttpHeaders.ContentDisposition, "filename=\"cafe-nord.csv\"")
                            },
                        )
                    },
                ) {
                    onUpload { bytesSentTotal: Long, _: Long? -> sentBytes += bytesSentTotal }
                }
                assertThat(response.status).isEqualTo(HttpStatusCode.Created)
                assertThat(response.bodyAsText()).isEqualTo("filed 3 parts")
                assertThat(sentBytes).isNotEmpty()
                assertThat(sentBytes.last()).isGreaterThan(receipt.length.toLong())

                assertThat(vault.multipartUploads).hasSize(1)
                assertThat(vault.multipartUploads[0]).containsExactly(
                    ReceivedPart("employee", null, null, "e-4471"),
                    ReceivedPart("category", null, null, "meals"),
                    ReceivedPart("receipt", "cafe-nord.csv", "text/csv", receipt),
                )
            }
        }
    }

    @Test
    fun searchesWithAUrlEncodedForm(): Unit = runBlocking {
        DocumentVault().use { vault: DocumentVault ->
            val tool: HttpClient = HttpClient(Java) {
                defaultRequest { url("http://127.0.0.1:${vault.port}/") }
            }
            tool.use {
                val response: HttpResponse = tool.submitForm(
                    url = "search",
                    formParameters = Parameters.build {
                        append("merchant", "Cafe Nord & Sons")
                        append("tag", "q4")
                        append("tag", "travel")
                    },
                )
                assertThat(response.status).isEqualTo(HttpStatusCode.OK)
                assertThat(response.bodyAsText()).isEqualTo("merchant=Cafe Nord & Sons;tags=[q4, travel]")
            }
        }
    }

    @Test
    fun streamsArchivesUpWithoutAKnownLength(): Unit = runBlocking {
        DocumentVault().use { vault: DocumentVault ->
            val tool: HttpClient = HttpClient(Java) {
                defaultRequest { url("http://127.0.0.1:${vault.port}/") }
            }
            tool.use {
                val written: StringBuilder = StringBuilder()
                val streamed: HttpResponse = tool.put("archive/ledger-2026-q3.csv") {
                    setBody(
                        ChannelWriterContent(
                            body = {
                                for (entry: Int in 1..500) {
                                    val line: String = "ledger-$entry,${entry % 13},posted\n"
                                    written.append(line)
                                    writeStringUtf8(line)
                                }
                            },
                            contentType = ContentType.Text.CSV,
                        ),
                    )
                }
                assertThat(streamed.status).isEqualTo(HttpStatusCode.OK)
                assertThat(streamed.bodyAsText())
                    .isEqualTo("ledger-2026-q3.csv: ${written.length} bytes, 500 lines")

                // A body read from an input stream also travels through the engine's body publisher.
                val snapshot: String = "snapshot-1,frozen\nsnapshot-2,frozen\n"
                val fromStream: HttpResponse = tool.put("archive/snapshot.csv") {
                    contentType(ContentType.Text.CSV)
                    setBody(ByteArrayInputStream(snapshot.encodeToByteArray()))
                }
                assertThat(fromStream.bodyAsText()).isEqualTo("snapshot.csv: ${snapshot.length} bytes, 2 lines")
            }

            assertThat(vault.streamedTransferEncodings).hasSize(2)
            assertThat(vault.streamedTransferEncodings[0]).isEqualTo("chunked")
        }
    }

    @Test
    fun readsAStreamedAuditExportLineByLine(): Unit = runBlocking {
        DocumentVault().use { vault: DocumentVault ->
            val tool: HttpClient = HttpClient(Java) {
                defaultRequest { url("http://127.0.0.1:${vault.port}/") }
            }
            tool.use {
                val rows: MutableList<String> = mutableListOf()
                var contentType: String? = null
                tool.prepareGet("audit/export?rows=300").execute { response: HttpResponse ->
                    contentType = response.headers[HttpHeaders.ContentType]
                    val channel: ByteReadChannel = response.bodyAsChannel()
                    while (true) {
                        val line: String = channel.readLine() ?: break
                        rows += line
                    }
                }
                assertThat(contentType).startsWith("text/csv")
                assertThat(rows).hasSize(301)
                assertThat(rows.first()).isEqualTo("id,amount")
                assertThat(rows[1]).isEqualTo("1,125")
                assertThat(rows.last()).isEqualTo("300,37500")
                val total: Long = rows.drop(1).sumOf { row: String -> row.substringAfter(',').toLong() }
                assertThat(total).isEqualTo(125L * 300 * 301 / 2)
            }
        }
    }
}
