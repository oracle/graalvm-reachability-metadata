/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_content_negotiation_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.withCharset
import io.ktor.serialization.kotlinx.xml.DefaultXml
import io.ktor.serialization.kotlinx.xml.xml
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.contentType
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlChildrenName
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * XML content negotiation with the kotlinx.serialization xmlutil converter registered for both
 * `application/xml` and `text/xml`, as a bookshop reads a publisher's namespaced catalog, submits
 * purchase orders as XML, and reads the stock report a legacy warehouse endpoint serves as `text/xml`.
 */
public class PublisherCatalogXmlNegotiationTest {

    enum class BookFormat { HARDCOVER, PAPERBACK, EBOOK }

    enum class OrderStatus { ACCEPTED, PARTIAL, REJECTED }

    /** A price in minor units; the amount is the element's text and the currency an attribute. */
    @Serializable
    @XmlSerialName("price", PUBLISHER_NS, "")
    data class Price(val currency: String, @XmlValue(true) val amountCents: Long)

    @Serializable
    @XmlSerialName("book", PUBLISHER_NS, "")
    data class Book(
        val isbn: String,
        @XmlElement(false) val format: BookFormat,
        @XmlElement(true) val title: String,
        @XmlElement(true) @XmlSerialName("author", PUBLISHER_NS, "") val authors: List<String>,
        val price: Price,
    )

    /** The publisher's catalog; its books sit inside a `<books>` wrapper element. */
    @Serializable
    @XmlSerialName("catalog", PUBLISHER_NS, "")
    data class Catalog(
        val publisher: String,
        @XmlElement(true) val season: String,
        @XmlSerialName("books", PUBLISHER_NS, "") @XmlChildrenName("book", PUBLISHER_NS, "") val books: List<Book>,
    )

    @Serializable
    @XmlSerialName("line", PUBLISHER_NS, "")
    data class OrderLine(val isbn: String, val quantity: Int)

    @Serializable
    @XmlSerialName("order", PUBLISHER_NS, "")
    data class PurchaseOrder(
        val orderId: String,
        val customer: String,
        val lines: List<OrderLine>,
        @XmlElement(true) val note: String? = null,
    )

    @Serializable
    @XmlSerialName("confirmation", PUBLISHER_NS, "")
    data class OrderConfirmation(
        val orderId: String,
        @XmlElement(false) val status: OrderStatus,
        val totalCents: Long,
        @XmlElement(true) @XmlSerialName("backordered", PUBLISHER_NS, "") val backorderedIsbns: List<String>,
    )

    @Serializable
    @XmlSerialName("item", "", "")
    data class StockItem(val isbn: String, val onHand: Int, val reserved: Int)

    /** The legacy warehouse's report; it carries no namespace at all. */
    @Serializable
    @XmlSerialName("stock", "", "")
    data class StockReport(val warehouse: String, val items: List<StockItem>)

    /** What the publisher service saw of one incoming request. */
    class ReceivedRequest(val path: String, val accept: String?, val contentType: String?, val body: String)

    /**
     * The publisher's and warehouse's endpoints. Responses are XML documents written out by hand, as a
     * foreign service would send them, so the client's decoding is what is checked.
     */
    class PublisherService : AutoCloseable {
        val received: MutableList<ReceivedRequest> = CopyOnWriteArrayList()
        val orders: MutableList<PurchaseOrder> = CopyOnWriteArrayList()

        private val server: EmbeddedServer<*, *> = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/catalog") {
                    record(call.request.local.uri, call.request.header(HttpHeaders.Accept), null, "")
                    call.respondText(CATALOG_XML, ContentType.Application.Xml)
                }
                post("/orders") {
                    val body: String = call.receiveText()
                    record(call.request.local.uri, call.request.header(HttpHeaders.Accept), call.request.contentType().toString(), body)
                    val order: PurchaseOrder = DefaultXml.decodeFromString(PurchaseOrder.serializer(), body)
                    orders += order
                    val backordered: List<String> = order.lines.filter { it.quantity > 10 }.map { it.isbn }
                    val total: Long = order.lines.filter { it.quantity <= 10 }.sumOf { it.quantity * 2_490L }
                    val status: String = if (backordered.isEmpty()) "ACCEPTED" else "PARTIAL"
                    val backorderedXml: String = backordered.joinToString("") { "<backordered>$it</backordered>" }
                    val confirmation = """
                        <confirmation xmlns="$PUBLISHER_NS" orderId="${order.orderId}" status="$status" totalCents="$total">$backorderedXml</confirmation>
                    """.trimIndent()
                    call.respondText(confirmation, ContentType.Application.Xml, HttpStatusCode.Created)
                }
                get("/legacy/stock") {
                    record(call.request.local.uri, call.request.header(HttpHeaders.Accept), null, "")
                    call.respondText(STOCK_XML, ContentType.Text.Xml.withCharset(Charsets.UTF_8))
                }
            }
        }

        val baseUrl: String

        init {
            server.start(wait = false)
            val port: Int = runBlocking { server.engine.resolvedConnectors().first().port }
            baseUrl = "http://127.0.0.1:$port"
        }

        private fun record(path: String, accept: String?, contentType: String?, body: String) {
            received += ReceivedRequest(path, accept, contentType, body)
        }

        override fun close() {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 2_000)
        }
    }

    private fun bookshopClient(baseUrl: String): HttpClient = HttpClient(ClientCIO) {
        install(ContentNegotiation) {
            xml()
            xml(contentType = ContentType.Text.Xml)
        }
        defaultRequest { url("$baseUrl/") }
    }

    @Test
    fun theNamespacedCatalogIsDecodedFromAttributesElementsAndText(): Unit = runBlocking {
        PublisherService().use { service: PublisherService ->
            bookshopClient(service.baseUrl).use { client: HttpClient ->
                val response: HttpResponse = client.get("catalog") {
                    accept(ContentType.Application.Xml)
                }

                assertThat(response.status).isEqualTo(HttpStatusCode.OK)
                val catalog: Catalog = response.body()
                assertThat(catalog.publisher).isEqualTo("Northwind Press")
                assertThat(catalog.season).isEqualTo("Autumn 2026")
                assertThat(catalog.books).containsExactly(
                    Book(
                        isbn = "978-0-00-000001-1",
                        format = BookFormat.HARDCOVER,
                        title = "Tides & Harbours",
                        authors = listOf("Ana\u00efs Moreau", "Jon Field"),
                        price = Price("EUR", 2_490),
                    ),
                    Book(
                        isbn = "978-0-00-000002-8",
                        format = BookFormat.EBOOK,
                        title = "The Quiet Ledger",
                        authors = listOf("Ruth Okafor"),
                        price = Price("USD", 999),
                    ),
                )
                assertThat(service.received.single().accept).startsWith("application/xml")
            }
        }
    }

    @Test
    fun aPurchaseOrderIsSubmittedAsXmlAndTheConfirmationDecoded(): Unit = runBlocking {
        PublisherService().use { service: PublisherService ->
            bookshopClient(service.baseUrl).use { client: HttpClient ->
                val order = PurchaseOrder(
                    orderId = "PO-2026-0412",
                    customer = "Riverside Books",
                    lines = listOf(OrderLine("978-0-00-000001-1", 4), OrderLine("978-0-00-000002-8", 25)),
                    note = "Deliver before <Friday> & call ahead",
                )
                val response: HttpResponse = client.post("orders") {
                    contentType(ContentType.Application.Xml)
                    setBody(order)
                }

                assertThat(response.status).isEqualTo(HttpStatusCode.Created)
                val confirmation: OrderConfirmation = response.body()
                assertThat(confirmation).isEqualTo(
                    OrderConfirmation("PO-2026-0412", OrderStatus.PARTIAL, 9_960, listOf("978-0-00-000002-8")),
                )

                assertThat(service.orders.single()).isEqualTo(order)
                val seen: ReceivedRequest = service.received.single()
                assertThat(seen.contentType).startsWith("application/xml")
                assertThat(seen.accept).contains("application/xml").contains("text/xml")
                // Scalars become attributes, the note an escaped element, all in the publisher's namespace.
                assertThat(seen.body)
                    .startsWith("<order")
                    .contains("xmlns=\"$PUBLISHER_NS\"")
                    .contains("orderId=\"PO-2026-0412\"")
                    .contains("<line isbn=\"978-0-00-000002-8\" quantity=\"25\"")
                    .contains("&lt;Friday&gt; &amp; call ahead")
                    .doesNotStartWith("<?xml")
            }
        }
    }

    @Test
    fun theLegacyStockReportIsDecodedFromTextXml(): Unit = runBlocking {
        PublisherService().use { service: PublisherService ->
            bookshopClient(service.baseUrl).use { client: HttpClient ->
                val response: HttpResponse = client.get("legacy/stock") {
                    accept(ContentType.Text.Xml)
                }

                assertThat(response.contentType()?.withoutParameters()).isEqualTo(ContentType.Text.Xml)
                val report: StockReport = response.body()
                assertThat(report).isEqualTo(
                    StockReport(
                        warehouse = "Duisburg",
                        items = listOf(StockItem("978-0-00-000001-1", 140, 12), StockItem("978-0-00-000002-8", 0, 0)),
                    ),
                )
                assertThat(service.received.single().accept).startsWith("text/xml")
            }
        }
    }

    private companion object {
        const val PUBLISHER_NS: String = "urn:example:publisher:catalog:1"

        val CATALOG_XML: String = """
            <?xml version="1.0" encoding="UTF-8"?>
            <catalog xmlns="$PUBLISHER_NS" publisher="Northwind Press">
              <season>Autumn 2026</season>
              <books>
                <book isbn="978-0-00-000001-1" format="HARDCOVER">
                  <title>Tides &amp; Harbours</title>
                  <author>Ana&#239;s Moreau</author>
                  <author>Jon Field</author>
                  <price currency="EUR">2490</price>
                </book>
                <book isbn="978-0-00-000002-8" format="EBOOK">
                  <title>The Quiet Ledger</title>
                  <author>Ruth Okafor</author>
                  <price currency="USD">999</price>
                </book>
              </books>
            </catalog>
        """.trimIndent()

        val STOCK_XML: String = """
            <stock warehouse="Duisburg">
              <item isbn="978-0-00-000001-1" onHand="140" reserved="12"/>
              <item isbn="978-0-00-000002-8" onHand="0" reserved="0"/>
            </stock>
        """.trimIndent()
    }
}
