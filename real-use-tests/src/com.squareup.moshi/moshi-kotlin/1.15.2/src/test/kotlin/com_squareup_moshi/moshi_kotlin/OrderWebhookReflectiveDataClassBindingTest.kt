/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_moshi.moshi_kotlin

import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Exercises reflective Kotlin class binding through `KotlinJsonAdapterFactory`: primary
 * constructor defaults, nullability enforcement, `@Json` renaming and ignoring, `@Transient`
 * properties, mutable body properties, nested data classes, enums, and collections.
 *
 * A storefront receives order webhooks from a payment provider as JSON, decodes them into its
 * own Kotlin model, and echoes the normalized order back to an audit log.
 */
@OptIn(ExperimentalStdlibApi::class)
public class OrderWebhookReflectiveDataClassBindingTest {

    /** Lifecycle of an order as the provider reports it. */
    public enum class OrderStatus {
        @Json(name = "pending_payment")
        PENDING_PAYMENT,

        @Json(name = "paid")
        PAID,

        @Json(name = "shipped")
        SHIPPED,
    }

    /** Postal address the order ships to. */
    public data class Address(
        val street: String,
        val city: String,
        @Json(name = "postal_code") val postalCode: String,
        val country: String = "US",
    )

    /** One purchased line. */
    public data class LineItem(
        val sku: String,
        val quantity: Int = 1,
        @Json(name = "unit_price_cents") val unitPriceCents: Long,
    ) {
        /** Derived locally; never sent or read. */
        @Transient
        val totalCents: Long = quantity * unitPriceCents
    }

    /** The order as the storefront models it. */
    public data class Order(
        @Json(name = "order_id") val id: String,
        val status: OrderStatus,
        val items: List<LineItem>,
        @Json(name = "shipping_address") val shippingAddress: Address,
        val coupon: String? = null,
        val tags: Set<String> = emptySet(),
        val gift: Boolean = false,
        @Json(ignore = true) val receivedLocally: Boolean = true,
    ) {
        /** Free-form note support staff may attach after decoding. */
        var note: String? = null
    }

    /** Webhook envelope that wraps every provider event. */
    public data class WebhookEvent(
        val type: String,
        val attempt: Int = 1,
        val order: Order,
        val metadata: Map<String, String> = emptyMap(),
    )

    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    private val eventAdapter: JsonAdapter<WebhookEvent> = moshi.adapter<WebhookEvent>()

    private val orderAdapter: JsonAdapter<Order> = moshi.adapter<Order>()

    @Test
    fun webhookDecodesIntoNestedKotlinModelWithRenamedFields() {
        val payload: String = """
            {
              "type": "order.paid",
              "attempt": 2,
              "order": {
                "order_id": "ord_1001",
                "status": "paid",
                "items": [
                  {"sku": "MUG-RED", "quantity": 3, "unit_price_cents": 1250},
                  {"sku": "TEE-L", "unit_price_cents": 2400}
                ],
                "shipping_address": {
                  "street": "1 Main St",
                  "city": "Springfield",
                  "postal_code": "12345",
                  "country": "CA"
                },
                "coupon": "FALL10",
                "tags": ["vip", "repeat", "vip"],
                "gift": true,
                "note": "leave at door"
              },
              "metadata": {"provider": "acme-pay", "region": "us-east"}
            }
        """.trimIndent()

        val event: WebhookEvent = eventAdapter.fromJson(payload)!!

        assertEquals("order.paid", event.type)
        assertEquals(2, event.attempt)
        assertEquals(mapOf("provider" to "acme-pay", "region" to "us-east"), event.metadata)
        val order: Order = event.order
        assertEquals("ord_1001", order.id)
        assertEquals(OrderStatus.PAID, order.status)
        assertEquals(
            listOf(LineItem("MUG-RED", 3, 1250L), LineItem("TEE-L", 1, 2400L)),
            order.items,
        )
        assertEquals(listOf(3750L, 2400L), order.items.map { it.totalCents })
        assertEquals(Address("1 Main St", "Springfield", "12345", "CA"), order.shippingAddress)
        assertEquals("FALL10", order.coupon)
        assertEquals(setOf("vip", "repeat"), order.tags)
        assertEquals(true, order.gift)
        assertEquals("leave at door", order.note)
    }

    @Test
    fun absentOptionalFieldsTakeTheirConstructorDefaults() {
        val minimal: String = """
            {"type":"order.created","order":{"order_id":"ord_1002","status":"pending_payment",
            "items":[{"sku":"PEN","unit_price_cents":199}],
            "shipping_address":{"street":"9 Elm","city":"Austin","postal_code":"73301"},
            "receivedLocally":false,"surcharge_cents":50}}
        """.trimIndent()

        val event: WebhookEvent = eventAdapter.fromJson(minimal)!!

        assertEquals(1, event.attempt)
        assertEquals(emptyMap<String, String>(), event.metadata)
        val order: Order = event.order
        assertEquals(OrderStatus.PENDING_PAYMENT, order.status)
        assertEquals(1, order.items.single().quantity)
        assertEquals(199L, order.items.single().totalCents)
        assertEquals("US", order.shippingAddress.country)
        assertNull(order.coupon)
        assertEquals(emptySet<String>(), order.tags)
        assertEquals(false, order.gift)
        assertEquals(true, order.receivedLocally)
        assertNull(order.note)
    }

    @Test
    fun encodingUsesJsonNamesAndSkipsTransientAndIgnoredProperties() {
        val order: Order = Order(
            id = "ord_1003",
            status = OrderStatus.SHIPPED,
            items = listOf(LineItem("CAP", 2, 900L)),
            shippingAddress = Address("5 Oak Rd", "Denver", "80014"),
            tags = linkedSetOf("gift-wrap"),
            receivedLocally = false,
        )
        order.note = "fragile"

        val json: String = orderAdapter.toJson(order)

        assertEquals(
            """{"order_id":"ord_1003","status":"shipped",""" +
                """"items":[{"sku":"CAP","quantity":2,"unit_price_cents":900}],""" +
                """"shipping_address":{"street":"5 Oak Rd","city":"Denver","postal_code":"80014","country":"US"},""" +
                """"tags":["gift-wrap"],"gift":false,"note":"fragile"}""",
            json,
        )
        val restored: Order = orderAdapter.fromJson(json)!!
        assertEquals(order.copy(receivedLocally = true), restored)
        assertEquals("fragile", restored.note)
    }

    @Test
    fun serializeNullsWritesNullableFieldsExplicitly() {
        val order: Order = Order(
            id = "ord_1004",
            status = OrderStatus.PAID,
            items = emptyList(),
            shippingAddress = Address("2 Pine", "Boise", "83702", "US"),
        )

        val json: String = orderAdapter.serializeNulls().toJson(order)

        assertEquals(
            """{"order_id":"ord_1004","status":"paid","items":[],""" +
                """"shipping_address":{"street":"2 Pine","city":"Boise","postal_code":"83702","country":"US"},""" +
                """"coupon":null,"tags":[],"gift":false,"note":null}""",
            json,
        )
    }

    @Test
    fun missingRequiredFieldIsRejectedWithItsJsonName() {
        val withoutAddress: String = """
            {"order_id":"ord_1005","status":"paid","items":[]}
        """.trimIndent()

        val error: JsonDataException = assertThrows(JsonDataException::class.java) {
            orderAdapter.fromJson(withoutAddress)
        }

        assertEquals(
            "Required value 'shippingAddress' (JSON name 'shipping_address') missing at \$",
            error.message,
        )
    }

    @Test
    fun explicitNullForNonNullPropertyIsRejectedAtItsPath() {
        val nullSku: String = """
            {"type":"order.paid","order":{"order_id":"ord_1006","status":"paid",
            "items":[{"sku":"OK","unit_price_cents":1},{"sku":null,"unit_price_cents":2}],
            "shipping_address":{"street":"s","city":"c","postal_code":"p"}}}
        """.trimIndent()

        val error: JsonDataException = assertThrows(JsonDataException::class.java) {
            eventAdapter.fromJson(nullSku)
        }

        assertEquals("Non-null value 'sku' was null at \$.order.items[1].sku", error.message)
    }

    @Test
    fun nullForDefaultedNullablePropertyOverridesTheDefault() {
        val explicitNullCoupon: String = """
            {"order_id":"ord_1007","status":"paid","items":[],"coupon":null,
            "shipping_address":{"street":"s","city":"c","postal_code":"p"}}
        """.trimIndent()

        val order: Order = orderAdapter.fromJson(explicitNullCoupon)!!

        assertNull(order.coupon)
        assertEquals("ord_1007", order.id)
    }

    @Test
    fun unknownEnumConstantIsRejected() {
        val refunded: String = """
            {"order_id":"ord_1008","status":"refunded","items":[],
            "shipping_address":{"street":"s","city":"c","postal_code":"p"}}
        """.trimIndent()

        val error: JsonDataException = assertThrows(JsonDataException::class.java) {
            orderAdapter.fromJson(refunded)
        }

        assertEquals(
            "Expected one of [pending_payment, paid, shipped] but was refunded at path \$.status",
            error.message,
        )
    }

    @Test
    fun auditLogRoundTripsAListOfEvents() {
        val listAdapter: JsonAdapter<List<WebhookEvent>> = moshi.adapter<List<WebhookEvent>>()
        val events: List<WebhookEvent> = listOf(
            WebhookEvent(
                type = "order.created",
                order = Order(
                    id = "ord_2001",
                    status = OrderStatus.PENDING_PAYMENT,
                    items = listOf(LineItem("BOOK", unitPriceCents = 1599L)),
                    shippingAddress = Address("7 Bay St", "Seattle", "98101"),
                ),
            ),
            WebhookEvent(
                type = "order.shipped",
                attempt = 3,
                order = Order(
                    id = "ord_2002",
                    status = OrderStatus.SHIPPED,
                    items = listOf(LineItem("LAMP", 1, 4500L), LineItem("BULB", 4, 300L)),
                    shippingAddress = Address("8 Rue Haute", "Lyon", "69001", "FR"),
                    coupon = "WELCOME",
                    gift = true,
                ),
                metadata = mapOf("carrier" to "ups"),
            ),
        )

        val json: String = listAdapter.indent("  ").toJson(events)
        val restored: List<WebhookEvent> = listAdapter.fromJson(json)!!

        assertEquals(events, restored)
        assertEquals(listOf(1599L, 5700L), restored.map { event -> event.order.items.sumOf { it.totalCents } })
    }
}
