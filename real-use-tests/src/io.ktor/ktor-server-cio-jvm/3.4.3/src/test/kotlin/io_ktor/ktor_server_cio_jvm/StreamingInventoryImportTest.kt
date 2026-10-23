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
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises CIO HTTP body streaming by importing an inventory batch and returning a streamed receipt.
 */
class StreamingInventoryImportTest {
    @Test
    @Timeout(60)
    fun `imports a chunked inventory upload and returns every accepted item in order`(): Unit = runBlocking {
        val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            routing {
                post("/inventory/import") {
                    val input: ByteReadChannel = call.receiveChannel()
                    val accepted: MutableList<String> = mutableListOf()
                    var totalUnits: Int = 0
                    while (true) {
                        val line: String = input.readUTF8Line() ?: break
                        val fields: List<String> = line.split(',')
                        val quantity: Int = fields[1].toInt()
                        totalUnits += quantity
                        accepted.add("accepted:${fields[0]}:$quantity")
                    }
                    call.respondTextWriter(
                        contentType = ContentType.Text.Plain,
                        status = HttpStatusCode.Created,
                    ) {
                        for (item: String in accepted) {
                            write("$item\n")
                            flush()
                        }
                        write("total:${accepted.size}:$totalUnits\n")
                    }
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
                    val records: List<String> = (1..256).map { index: Int -> "sku-$index,$index" }
                    val response: HttpResponse = client.post("http://127.0.0.1:$port/inventory/import") {
                        setBody(InventoryUpload(records))
                    }
                    assertEquals(HttpStatusCode.Created, response.status)
                    assertTrue(response.headers[HttpHeaders.ContentType]!!.startsWith("text/plain"))
                    val receipt: List<String> = response.bodyAsText().trimEnd('\n').split('\n')
                    val expected: List<String> = (1..256).map { index: Int -> "accepted:sku-$index:$index" } +
                        "total:256:32896"
                    assertEquals(expected, receipt)
                }
            }
        } finally {
            server.stop(0, 10_000)
        }
    }

    private class InventoryUpload(private val records: List<String>) : OutgoingContent.WriteChannelContent() {
        override val contentType: ContentType = ContentType.Text.Plain

        override suspend fun writeTo(channel: ByteWriteChannel): Unit {
            for (record: String in records) {
                channel.writeStringUtf8("$record\n")
                channel.flush()
            }
        }
    }
}
