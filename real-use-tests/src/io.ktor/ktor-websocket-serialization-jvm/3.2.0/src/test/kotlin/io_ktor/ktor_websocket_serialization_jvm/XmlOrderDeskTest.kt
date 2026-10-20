/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_websocket_serialization_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.xml.DefaultXml
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.websocket.WebSockets as ServerWebSockets

/**
 * Typed XML messaging over WebSockets, where kotlinx-serialization classes mapped with
 * xmlutil annotations travel as text frames through `sendSerialized` and
 * `receiveDeserialized` with the kotlinx XML WebSocket converter.
 *
 * An order desk answers every order line with the running total and, after the last
 * line, sends a receipt and closes normally. A customer that writes its XML by hand is
 * served the same way as one that serializes typed lines.
 */
public class XmlOrderDeskTest {
    /** One line a customer adds to the order. */
    @Serializable
    @XmlSerialName("line", "", "")
    data class OrderLine(val sku: String, val quantity: Int, val unitCents: Long, val last: Boolean = false)

    /** The running total the desk answers every line with. */
    @Serializable
    @XmlSerialName("total", "", "")
    data class OrderTotal(val lineCount: Int, val itemCount: Int, val totalCents: Long)

    /** The receipt the desk sends after the last line. */
    @Serializable
    @XmlSerialName("receipt", "", "")
    data class Receipt(
        val totalCents: Long,
        @XmlElement(true) @XmlSerialName("greeting", "", "") val greeting: String,
        @XmlSerialName("sku", "", "") val skus: List<String>,
    )

    private lateinit var server: EmbeddedServer<*, *>
    private var port: Int = 0

    @BeforeEach
    fun startDesk() {
        server = embeddedServer(ServerCIO, port = 0, host = HOST) {
            install(ServerWebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(DefaultXml)
            }
            routing {
                webSocket("/orders") {
                    val skus: MutableList<String> = mutableListOf()
                    var itemCount: Int = 0
                    var totalCents: Long = 0
                    while (true) {
                        val line: OrderLine = receiveDeserialized<OrderLine>()
                        skus += line.sku
                        itemCount += line.quantity
                        totalCents += line.quantity * line.unitCents
                        sendSerialized(OrderTotal(skus.size, itemCount, totalCents))
                        if (line.last) {
                            sendSerialized(Receipt(totalCents, "Merci, \u00e0 bient\u00f4t!", skus.toList()))
                            close(CloseReason(CloseReason.Codes.NORMAL, "order of ${skus.size} lines placed"))
                            break
                        }
                    }
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().single().port }
    }

    @AfterEach
    fun stopDesk() {
        server.stop(gracePeriodMillis = 100, timeoutMillis = 5_000)
    }

    @Test
    fun `customer receives running totals and a receipt with non-ASCII text intact`(): Unit = runBlocking {
        val order: List<OrderLine> = listOf(
            OrderLine("cr\u00e8me-br\u00fbl\u00e9e", 2, 650),
            OrderLine("espresso", 3, 280),
            OrderLine("caf\u00e9-au-lait", 1, 390, last = true),
        )
        val totals: MutableList<OrderTotal> = mutableListOf()
        var receipt: Receipt? = null
        var observedClose: CloseReason? = null

        withTimeout(TIMEOUT_MILLIS) {
            customerClient().use { client: HttpClient ->
                client.webSocket(host = HOST, port = port, path = "/orders") {
                    for (line: OrderLine in order) {
                        sendSerialized(line)
                        totals += receiveDeserialized<OrderTotal>()
                    }
                    receipt = receiveDeserialized<Receipt>()
                    observedClose = closeReason.await()
                }
            }
        }

        assertEquals(
            listOf(OrderTotal(1, 2, 1_300), OrderTotal(2, 5, 2_140), OrderTotal(3, 6, 2_530)),
            totals,
        )
        assertEquals(
            Receipt(2_530, "Merci, \u00e0 bient\u00f4t!", order.map { line: OrderLine -> line.sku }),
            receipt,
        )
        assertEquals(CloseReason(CloseReason.Codes.NORMAL, "order of 3 lines placed"), observedClose)
    }

    @Test
    fun `replies travel as XML text frames named by the annotations`(): Unit = runBlocking {
        val frames: MutableList<Frame> = mutableListOf()

        withTimeout(TIMEOUT_MILLIS) {
            customerClient().use { client: HttpClient ->
                client.webSocket(host = HOST, port = port, path = "/orders") {
                    sendSerialized(OrderLine("espresso", 2, 450, last = true))
                    frames += incoming.receive()
                    frames += incoming.receive()
                    closeReason.await()
                }
            }
        }

        val totalXml: String = assertInstanceOf(Frame.Text::class.java, frames[0]).readText()
        assertTrue(totalXml.contains("<total "), totalXml)
        assertTrue(totalXml.contains("totalCents=\"900\""), totalXml)
        val receiptXml: String = assertInstanceOf(Frame.Text::class.java, frames[1]).readText()
        assertTrue(receiptXml.contains("<receipt "), receiptXml)
        assertTrue(receiptXml.contains("<greeting>Merci, \u00e0 bient\u00f4t!</greeting>"), receiptXml)
        assertTrue(receiptXml.contains("<sku>espresso</sku>"), receiptXml)
    }

    @Test
    fun `hand-written XML lines are served like typed ones`(): Unit = runBlocking {
        var total: OrderTotal? = null
        var receipt: Receipt? = null

        withTimeout(TIMEOUT_MILLIS) {
            customerClient().use { client: HttpClient ->
                client.webSocket(host = HOST, port = port, path = "/orders") {
                    send(Frame.Text("""<line sku="green-tea" quantity="4" unitCents="200" last="true"/>"""))
                    total = receiveDeserialized<OrderTotal>()
                    receipt = receiveDeserialized<Receipt>()
                    closeReason.await()
                }
            }
        }

        assertEquals(OrderTotal(1, 4, 800), total)
        assertEquals(Receipt(800, "Merci, \u00e0 bient\u00f4t!", listOf("green-tea")), receipt)
    }

    private fun customerClient(): HttpClient = HttpClient(ClientCIO) {
        install(ClientWebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(DefaultXml)
        }
    }

    private companion object {
        const val HOST: String = "127.0.0.1"
        const val TIMEOUT_MILLIS: Long = 30_000
    }
}
