/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_moshi.moshi_kotlin

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises runtime-type encoding of untyped values: Kotlin objects held in `Any`-typed
 * properties, `Map<String, Any?>` bags, and `List<Any>` batches are encoded with the adapter
 * `KotlinJsonAdapterFactory` builds for their runtime class, alongside enums, numbers, arrays,
 * and Kotlin collection implementations, while untyped JSON decodes to maps, lists, and doubles.
 *
 * A product-analytics SDK lets the app attach arbitrary properties to tracked events, batches
 * heterogeneous audit records for upload, and relays events from a partner without knowing
 * their schema.
 */
@OptIn(ExperimentalStdlibApi::class)
public class AnalyticsEventRuntimeTypeEncodingTest {

    public enum class Channel {
        WEB,
        IOS,
        ANDROID,
    }

    /** A cart line the checkout screen attaches to its events. */
    public data class CartLine(val sku: String, val quantity: Int, val unitPriceCents: Long)

    /** The marketing campaign that brought the user in. */
    public data class Campaign(val code: String, val channel: Channel)

    /** A tracked event: the SDK does not know the shape of `properties` or `context`. */
    public data class AnalyticsEvent(
        val name: String,
        val occurredAtMillis: Long,
        val properties: Map<String, Any?>,
        val context: Any?,
    )

    /** Audit records of unrelated shapes that upload together in one batch. */
    public data class Login(val userId: String, val channel: Channel)

    public data class Purchase(val orderId: String, val lines: List<CartLine>)

    public data class Logout(val userId: String, val sessionSeconds: Int)

    /** A relayed partner event whose body is passed through without a schema. */
    public data class RelayedEvent(val schemaVersion: Int, val body: Any)

    /** Writes device identifiers as their canonical string form. */
    public class DeviceIdAdapter {
        @ToJson
        public fun toJson(id: UUID): String = id.toString()

        @FromJson
        public fun fromJson(id: String): UUID = UUID.fromString(id)
    }

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun eventPropertiesEncodeWithTheAdapterOfEachValueRuntimeClass() {
        val event = AnalyticsEvent(
            name = "checkout_started",
            occurredAtMillis = 1767225600123L,
            properties = linkedMapOf(
                "cart" to listOf(CartLine("TEE-M", 2, 1999L), CartLine("CAP-1", 1, 2450L)),
                "channel" to Channel.ANDROID,
                "itemCount" to 3,
                "subtotal" to 64.48,
                "discountRate" to 0.25f,
                "firstPurchase" to true,
                "coupon" to null,
                "tags" to setOf("summer", "returning"),
            ),
            context = Campaign("SPRING26", Channel.IOS),
        )
        val events: JsonAdapter<AnalyticsEvent> = moshi.adapter<AnalyticsEvent>()

        val expectedProperties: String = """"properties":{"cart":[""" +
            """{"sku":"TEE-M","quantity":2,"unitPriceCents":1999},""" +
            """{"sku":"CAP-1","quantity":1,"unitPriceCents":2450}],""" +
            """"channel":"ANDROID","itemCount":3,"subtotal":64.48,"discountRate":0.25,""" +
            """"firstPurchase":true"""
        val expectedTail: String = """"tags":["summer","returning"]},""" +
            """"context":{"code":"SPRING26","channel":"IOS"}}"""
        val expectedHead: String = """{"name":"checkout_started","occurredAtMillis":1767225600123,"""

        assertEquals("$expectedHead$expectedProperties,$expectedTail", events.toJson(event))
        assertEquals(
            "$expectedHead$expectedProperties,\"coupon\":null,$expectedTail",
            events.serializeNulls().toJson(event),
        )
    }

    @Test
    fun heterogeneousAuditBatchEncodesEveryRecordByItsOwnClass() {
        val batch: List<Any> = listOf(
            Login("u-17", Channel.WEB),
            Purchase("ord-9001", listOf(CartLine("MUG-2", 4, 899L))),
            Logout("u-17", 1820),
            emptyList<Any>(),
            listOf("single"),
            arrayOf("export", "csv"),
            intArrayOf(3, 1, 4),
        )

        val json: String = moshi.adapter<List<Any>>().toJson(batch)

        assertEquals(
            """[{"userId":"u-17","channel":"WEB"},""" +
                """{"orderId":"ord-9001","lines":[{"sku":"MUG-2","quantity":4,"unitPriceCents":899}]},""" +
                """{"userId":"u-17","sessionSeconds":1820},[],["single"],["export","csv"],[3,1,4]]""",
            json,
        )

        val single: String = moshi.adapter(Any::class.java).toJson(Purchase("ord-1", emptyList()))
        assertEquals("""{"orderId":"ord-1","lines":[]}""", single)
    }

    @Test
    fun platformValuesNeedAnExplicitAdapterBeforeTheyEncodeUntyped() {
        val deviceId: UUID = UUID.fromString("6f1c2a52-8e0b-4d6e-9a3f-2b7c1d4e5f60")
        val event = AnalyticsEvent(
            name = "app_opened",
            occurredAtMillis = 1767225600000L,
            properties = mapOf("deviceId" to deviceId),
            context = null,
        )

        val refused: IllegalArgumentException = assertThrows(IllegalArgumentException::class.java) {
            moshi.adapter<AnalyticsEvent>().toJson(event)
        }
        assertTrue(refused.message!!.contains("java.util.UUID"), refused.message)
        assertTrue(refused.message!!.contains("requires explicit JsonAdapter"), refused.message)

        val withDeviceIds: Moshi = moshi.newBuilder().add(DeviceIdAdapter()).build()
        assertEquals(
            """{"name":"app_opened","occurredAtMillis":1767225600000,""" +
                """"properties":{"deviceId":"6f1c2a52-8e0b-4d6e-9a3f-2b7c1d4e5f60"}}""",
            withDeviceIds.adapter<AnalyticsEvent>().toJson(event),
        )
    }

    @Test
    fun relayedEventsDecodeUntypedAndRebindToTypedModels() {
        val partnerJson: String = """
            {"schemaVersion":2,"body":{"campaign":{"code":"FALL26","channel":"WEB"},
              "line":{"sku":"SOCK-3","quantity":3,"unitPriceCents":550},
              "scores":[1,2.5,null],"referrer":null,"consented":false}}
        """.trimIndent()
        val relays: JsonAdapter<RelayedEvent> = moshi.adapter<RelayedEvent>()

        val relayed: RelayedEvent = relays.fromJson(partnerJson)!!
        assertEquals(2, relayed.schemaVersion)
        @Suppress("UNCHECKED_CAST")
        val body: Map<String, Any?> = relayed.body as Map<String, Any?>
        assertEquals(
            mapOf<String, Any?>(
                "campaign" to mapOf("code" to "FALL26", "channel" to "WEB"),
                "line" to mapOf("sku" to "SOCK-3", "quantity" to 3.0, "unitPriceCents" to 550.0),
                "scores" to listOf(1.0, 2.5, null),
                "referrer" to null,
                "consented" to false,
            ),
            body,
        )

        assertEquals(Campaign("FALL26", Channel.WEB), moshi.adapter<Campaign>().fromJsonValue(body["campaign"]))
        assertEquals(CartLine("SOCK-3", 3, 550L), moshi.adapter<CartLine>().fromJsonValue(body["line"]))

        assertEquals(
            """{"schemaVersion":2,"body":{"campaign":{"code":"FALL26","channel":"WEB"},""" +
                """"line":{"sku":"SOCK-3","quantity":3.0,"unitPriceCents":550.0},""" +
                """"scores":[1.0,2.5,null],"referrer":null,"consented":false}}""",
            relays.serializeNulls().toJson(relayed),
        )
    }
}
