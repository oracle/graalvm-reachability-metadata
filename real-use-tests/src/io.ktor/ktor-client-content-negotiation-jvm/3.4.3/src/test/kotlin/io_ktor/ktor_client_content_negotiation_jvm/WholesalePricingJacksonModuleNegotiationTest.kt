/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_content_negotiation_jvm

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.KeyDeserializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Jackson negotiation extended by the application's own modules: a `SimpleModule` with custom
 * value and map-key (de)serializers, a mix-in that renames and hides properties of a class the
 * client does not annotate, and jackson-module-kotlin features (default parameter values, null
 * treated as default, ranges), as a wholesale buyer reads a supplier price list and places orders.
 */
public class WholesalePricingJacksonModuleNegotiationTest {

    /** An amount in minor units; suppliers write it as a decimal followed by an ISO currency code. */
    data class Money(val minorUnits: Long, val currency: String) {
        override fun toString(): String = "${BigDecimal.valueOf(minorUnits, 2).toPlainString()} $currency"
    }

    /** A stock location; suppliers key per-site maps by `<site>-<bay>`. */
    data class Site(val code: String, val bay: Int) {
        override fun toString(): String = "$code-$bay"
    }

    /** Writes [Money] as `"3.40 EUR"`. */
    class MoneySerializer : StdSerializer<Money>(Money::class.java) {
        override fun serialize(value: Money, gen: JsonGenerator, provider: SerializerProvider) {
            gen.writeString(value.toString())
        }
    }

    /** Reads [Money] from `"3.40 EUR"`, rejecting anything else as a weird string. */
    class MoneyDeserializer : StdDeserializer<Money>(Money::class.java) {
        override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): Money {
            val text: String = parser.valueAsString
            val parts: List<String> = text.split(' ')
            val amount: BigDecimal? = parts.firstOrNull()?.toBigDecimalOrNull()
            if (parts.size != 2 || amount == null || parts[1].length != 3) {
                throw ctxt.weirdStringException(text, Money::class.java, "expected '<amount> <currency>'")
            }
            return Money(amount.movePointRight(2).longValueExact(), parts[1])
        }
    }

    /** Writes a [Site] map key as `BER-2`. */
    class SiteKeySerializer : StdSerializer<Site>(Site::class.java) {
        override fun serialize(value: Site, gen: JsonGenerator, provider: SerializerProvider) {
            gen.writeFieldName(value.toString())
        }
    }

    /** Reads a [Site] map key from `BER-2`. */
    class SiteKeyDeserializer : KeyDeserializer() {
        override fun deserializeKey(key: String, ctxt: DeserializationContext): Site {
            val code: String = key.substringBefore('-')
            val bay: Int = key.substringAfter('-').toInt()
            return Site(code, bay)
        }
    }

    /** The supplier record the procurement core owns; its JSON shape comes from [SupplierRecordMixIn]. */
    data class SupplierRecord(
        val internalCode: String,
        val name: String,
        val auditTrail: List<String> = emptyList(),
    )

    /** Maps [SupplierRecord] onto the supplier API: `supplier_code`, and no audit trail on the wire. */
    abstract class SupplierRecordMixIn {
        @get:JsonProperty("supplier_code")
        abstract val internalCode: String

        @get:JsonIgnore
        abstract val auditTrail: List<String>
    }

    data class PriceListItem(
        val sku: String,
        val unitPrice: Money,
        val packaging: String = "sack",
        val discontinued: Boolean = false,
        val orderTier: IntRange,
        val stockBySite: Map<Site, Int> = emptyMap(),
    )

    data class PriceList(val supplier: SupplierRecord, val items: List<PriceListItem>)

    data class OrderLine(val sku: String, val quantity: Int, val unitPrice: Money)

    data class PurchaseOrder(
        val supplier: SupplierRecord,
        val lines: List<OrderLine>,
        val total: Money,
        val deliverTo: Map<Site, Int>,
    )

    data class OrderReceipt(val orderId: String, val total: Money, val note: String = "none")

    /** The supplier gateway; it answers with fixed bodies and records what the client sent. */
    class SupplierGateway : AutoCloseable {
        val receivedOrders: MutableList<String> = CopyOnWriteArrayList()

        private val server: EmbeddedServer<*, *> = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/price-list") {
                    call.respondText(
                        """
                        {"supplier":{"supplier_code":"SUP-77","name":"Nordic Grains"},
                         "items":[
                           {"sku":"OAT-1","unitPrice":"3.40 EUR","packaging":null,
                            "orderTier":{"start":10,"end":49},"stockBySite":{"BER-2":120,"HAM-5":0}},
                           {"sku":"RYE-4","unitPrice":"12.05 SEK","packaging":"pallet","discontinued":true,
                            "orderTier":{"start":1,"end":9}}
                         ]}
                        """.trimIndent(),
                        ContentType.Application.Json,
                    )
                }
                get("/price-list/garbled") {
                    call.respondText(
                        """{"supplier":{"supplier_code":"SUP-9","name":"Old Mill"},
                           "items":[{"sku":"BAR-2","unitPrice":"three euros","orderTier":{"start":1,"end":5}}]}""",
                        ContentType.Application.Json,
                    )
                }
                get("/price-list/incomplete") {
                    call.respondText(
                        """{"supplier":{"supplier_code":"SUP-9","name":"Old Mill"},
                           "items":[{"unitPrice":"1.00 EUR","orderTier":{"start":1,"end":5}}]}""",
                        ContentType.Application.Json,
                    )
                }
                post("/orders") {
                    receivedOrders += call.receiveText()
                    call.respondText(
                        """{"orderId":"PO-5531","total":"88.00 EUR"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.Created,
                    )
                }
            }
        }

        val baseUrl: String

        init {
            server.start(wait = false)
            val port: Int = runBlocking { server.engine.resolvedConnectors().first().port }
            baseUrl = "http://127.0.0.1:$port"
        }

        override fun close() {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 2_000)
        }
    }

    /** The mapper the procurement application shares between its HTTP clients. */
    private fun pricingMapper(): JsonMapper {
        val pricing: SimpleModule = SimpleModule("wholesale-pricing")
            .addSerializer(Money::class.java, MoneySerializer())
            .addDeserializer(Money::class.java, MoneyDeserializer())
            .addKeySerializer(Site::class.java, SiteKeySerializer())
            .addKeyDeserializer(Site::class.java, SiteKeyDeserializer())
        return JsonMapper.builder()
            .addModule(kotlinModule { enable(KotlinFeature.NullIsSameAsDefault) })
            .addModule(pricing)
            .addMixIn(SupplierRecord::class.java, SupplierRecordMixIn::class.java)
            .build()
    }

    private fun supplierClient(baseUrl: String): HttpClient = HttpClient(ClientCIO) {
        install(ContentNegotiation) {
            register(ContentType.Application.Json, JacksonConverter(pricingMapper()))
        }
        defaultRequest { url("$baseUrl/") }
    }

    @Test
    fun thePriceListIsDecodedThroughTheApplicationModules(): Unit = runBlocking {
        SupplierGateway().use { gateway: SupplierGateway ->
            supplierClient(gateway.baseUrl).use { client: HttpClient ->
                val priceList: PriceList = client.get("price-list").body()

                assertThat(priceList.supplier).isEqualTo(SupplierRecord("SUP-77", "Nordic Grains"))
                assertThat(priceList.items).containsExactly(
                    PriceListItem(
                        sku = "OAT-1",
                        unitPrice = Money(340, "EUR"),
                        orderTier = 10..49,
                        stockBySite = mapOf(Site("BER", 2) to 120, Site("HAM", 5) to 0),
                    ),
                    PriceListItem(
                        sku = "RYE-4",
                        unitPrice = Money(1205, "SEK"),
                        packaging = "pallet",
                        discontinued = true,
                        orderTier = 1..9,
                    ),
                )
                val oats: PriceListItem = priceList.items.first()
                assertThat(oats.packaging).isEqualTo("sack")
                assertThat(25 in oats.orderTier).isTrue()
                assertThat(oats.stockBySite.filterValues { stock: Int -> stock > 0 }.keys)
                    .containsExactly(Site("BER", 2))
            }
        }
    }

    @Test
    fun anOrderIsWrittenInTheSupplierShapeAndTheReceiptDecoded(): Unit = runBlocking {
        SupplierGateway().use { gateway: SupplierGateway ->
            supplierClient(gateway.baseUrl).use { client: HttpClient ->
                val order = PurchaseOrder(
                    supplier = SupplierRecord("SUP-77", "Nordic Grains", listOf("drafted by j.ito", "approved")),
                    lines = listOf(OrderLine("OAT-1", 20, Money(340, "EUR")), OrderLine("FLX-3", 5, Money(400, "EUR"))),
                    total = Money(8800, "EUR"),
                    deliverTo = mapOf(Site("BER", 2) to 20, Site("HAM", 5) to 5),
                )
                val response: HttpResponse = client.post("orders") {
                    contentType(ContentType.Application.Json)
                    setBody(order)
                }

                assertThat(response.status).isEqualTo(HttpStatusCode.Created)
                val receipt: OrderReceipt = response.body()
                assertThat(receipt).isEqualTo(OrderReceipt("PO-5531", Money(8800, "EUR")))

                val sent: String = gateway.receivedOrders.single()
                assertThat(sent)
                    .contains("\"supplier_code\":\"SUP-77\"")
                    .doesNotContain("internalCode")
                    .doesNotContain("auditTrail")
                    .doesNotContain("approved")
                    .contains("\"unitPrice\":\"3.40 EUR\"")
                    .contains("\"total\":\"88.00 EUR\"")
                    .contains("\"deliverTo\":{\"BER-2\":20,\"HAM-5\":5}")
            }
        }
    }

    @Test
    fun malformedOrIncompletePriceListsAreRejected(): Unit = runBlocking {
        SupplierGateway().use { gateway: SupplierGateway ->
            supplierClient(gateway.baseUrl).use { client: HttpClient ->
                assertThatThrownBy { runBlocking { client.get("price-list/garbled").body<PriceList>() } }
                    .isInstanceOf(JsonConvertException::class.java)
                    .hasCauseInstanceOf(JsonMappingException::class.java)
                    .hasMessageContaining("three euros")

                assertThatThrownBy { runBlocking { client.get("price-list/incomplete").body<PriceList>() } }
                    .isInstanceOf(JsonConvertException::class.java)
                    .hasCauseInstanceOf(JsonMappingException::class.java)
                    .hasMessageContaining("sku")
            }
        }
    }
}
