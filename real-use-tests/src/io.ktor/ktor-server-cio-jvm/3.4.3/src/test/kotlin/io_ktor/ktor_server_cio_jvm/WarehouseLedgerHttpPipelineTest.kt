/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_cio_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readLine
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

/**
 * The CIO engine's HTTP/1.1 request pipeline: routed JSON calls and chunked streaming
 * bodies in both directions.
 *
 * A warehouse ledger service runs on an embedded CIO server. Clerks post single movements
 * as JSON, a scanner uploads a batch of movements as a chunked line stream of unknown
 * length, and the accounting job downloads the ledger as a CSV the server writes while
 * the response is in flight. The test checks the balances, that the engine decoded the
 * chunked upload and chunk-encoded the streamed export, that every routed call was parsed
 * as HTTP/1.1, and that an unrouted path is answered with 404.
 */
class WarehouseLedgerHttpPipelineTest {

    @Serializable
    data class Movement(val sku: String, val delta: Int)

    @Serializable
    data class Balance(val sku: String, val onHand: Int)

    @Serializable
    data class BatchReceipt(val accepted: Int, val transferEncoding: String)

    /** A request body of unknown length, so the client must send it chunked. */
    class ScannerBatch(private val lines: List<String>) : OutgoingContent.WriteChannelContent() {
        override val contentType: ContentType = ContentType.Text.Plain

        override suspend fun writeTo(channel: ByteWriteChannel) {
            lines.forEach { line: String ->
                channel.writeStringUtf8("$line\n")
                channel.flush()
            }
        }
    }

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0
    private val ledger: MutableMap<String, Int> = sortedMapOf()
    private val protocols: MutableList<String> = CopyOnWriteArrayList()

    @BeforeEach
    fun start() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(ServerContentNegotiation) { json() }
            routing {
                post("/movements") {
                    protocols += call.request.local.version
                    val movement: Movement = call.receive()
                    call.respond(HttpStatusCode.Created, apply(movement))
                }
                post("/movements/batch") {
                    protocols += call.request.local.version
                    val transferEncoding: String = call.request.headers[HttpHeaders.TransferEncoding] ?: "none"
                    val body: ByteReadChannel = call.receiveChannel()
                    var accepted = 0
                    while (true) {
                        val line: String = body.readLine() ?: break
                        if (line.isBlank()) continue
                        val (sku: String, delta: String) = line.split(',')
                        apply(Movement(sku.trim(), delta.trim().toInt()))
                        accepted++
                    }
                    call.respond(BatchReceipt(accepted, transferEncoding))
                }
                get("/ledger.csv") {
                    protocols += call.request.local.version
                    val snapshot: List<Balance> = synchronized(ledger) { ledger.map { Balance(it.key, it.value) } }
                    call.respondBytesWriter(ContentType.Text.CSV) {
                        writeStringUtf8("sku,onHand\n")
                        snapshot.forEach { balance: Balance ->
                            writeStringUtf8("${balance.sku},${balance.onHand}\n")
                            flush()
                        }
                    }
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
    fun ledgerTrafficFlowsThroughTheCioPipeline() {
        val client = HttpClient(ClientCIO) {
            install(ClientContentNegotiation) { json() }
        }

        client.use {
            runBlocking {
                val first: Balance = it.post("http://127.0.0.1:$port/movements") {
                    contentType(ContentType.Application.Json)
                    setBody(Movement("pallet-eu", 12))
                }.body()
                assertEquals(Balance("pallet-eu", 12), first)

                val picked: HttpResponse = it.post("http://127.0.0.1:$port/movements") {
                    contentType(ContentType.Application.Json)
                    setBody(Movement("pallet-eu", -5))
                }
                assertEquals(HttpStatusCode.Created, picked.status)
                assertEquals(Balance("pallet-eu", 7), picked.body<Balance>())

                val batch = ScannerBatch(listOf("crate-s, 30", "crate-l, 8", "pallet-eu, 3", "", "crate-s, -4"))
                val receipt: BatchReceipt = it.post("http://127.0.0.1:$port/movements/batch") {
                    setBody(batch)
                }.body()
                assertEquals(BatchReceipt(4, "chunked"), receipt)

                val export: HttpResponse = it.get("http://127.0.0.1:$port/ledger.csv")
                assertEquals(HttpStatusCode.OK, export.status)
                assertEquals("chunked", export.headers[HttpHeaders.TransferEncoding])
                assertTrue(export.contentType()?.match(ContentType.Text.CSV) == true, "${export.contentType()}")
                assertEquals(
                    """
                    sku,onHand
                    crate-l,8
                    crate-s,26
                    pallet-eu,10

                    """.trimIndent(),
                    export.bodyAsText(),
                )

                val missing: HttpResponse = it.get("http://127.0.0.1:$port/ledger.xlsx")
                assertEquals(HttpStatusCode.NotFound, missing.status)
            }
        }

        assertEquals(List(4) { "HTTP/1.1" }, protocols)
    }

    private fun apply(movement: Movement): Balance = synchronized(ledger) {
        val onHand: Int = (ledger[movement.sku] ?: 0) + movement.delta
        ledger[movement.sku] = onHand
        Balance(movement.sku, onHand)
    }
}
