/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_moshi.moshi_kotlin

import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.lang.reflect.Type
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises the generated-adapter lookup in `KotlinJsonAdapterFactory`: Kotlin models annotated
 * `@JsonClass(generateAdapter = true)` whose codegen adapter class is absent fall back to
 * reflective binding, for plain, generic, and sealed classes, while a `Moshi` without the factory
 * reports the missing generated adapter.
 *
 * A payments client shares its models with a codegen build, so every model carries `@JsonClass`;
 * this build ships without kapt or KSP and relies on `KotlinJsonAdapterFactory` instead.
 */
@OptIn(ExperimentalStdlibApi::class)
public class PaymentGatewayCodegenFallbackTest {

    /** Currency a charge is settled in. */
    public enum class Currency {
        @Json(name = "usd")
        USD,

        @Json(name = "eur")
        EUR,
    }

    /** A card charge as the gateway reports it. */
    @JsonClass(generateAdapter = true)
    public data class Charge(
        val id: String,
        @Json(name = "amount_cents") val amountCents: Long,
        val currency: Currency = Currency.USD,
        val captured: Boolean = false,
        val description: String? = null,
        val metadata: Map<String, String> = emptyMap(),
    )

    /** One page of a cursor-paginated listing. */
    @JsonClass(generateAdapter = true)
    public data class Page<T>(
        val data: List<T>,
        @Json(name = "has_more") val hasMore: Boolean,
        @Json(name = "next_cursor") val nextCursor: String? = null,
    )

    /** A client-side request body that opts out of codegen explicitly. */
    @JsonClass(generateAdapter = false)
    public data class RefundRequest(
        @Json(name = "charge") val chargeId: String,
        @Json(name = "amount_cents") val amountCents: Long? = null,
        val reason: String = "requested_by_customer",
    )

    /** A stored payment method; the gateway labels each one with a `type` field. */
    @JsonClass(generateAdapter = true)
    public sealed class PaymentMethod {
        public abstract val id: String

        /** A card on file. */
        @JsonClass(generateAdapter = true)
        public data class Card(
            override val id: String,
            val brand: String,
            val last4: String,
            @Json(name = "exp_month") val expMonth: Int,
            @Json(name = "exp_year") val expYear: Int,
        ) : PaymentMethod()

        /** A bank account debited directly. */
        @JsonClass(generateAdapter = true)
        public data class BankAccount(
            override val id: String,
            @Json(name = "bank_name") val bankName: String,
            val last4: String,
        ) : PaymentMethod()
    }

    /** Routes `PaymentMethod` by its `type` label to the reflective subtype adapters. */
    public object PaymentMethodFactory : JsonAdapter.Factory {
        override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
            if (Types.getRawType(type) != PaymentMethod::class.java || annotations.isNotEmpty()) {
                return null
            }
            return PaymentMethodAdapter(moshi.adapter<PaymentMethod.Card>(), moshi.adapter<PaymentMethod.BankAccount>())
        }
    }

    /** Reads the label from a lookahead copy, then decodes the payload with the subtype adapter. */
    public class PaymentMethodAdapter(
        private val cards: JsonAdapter<PaymentMethod.Card>,
        private val bankAccounts: JsonAdapter<PaymentMethod.BankAccount>,
    ) : JsonAdapter<PaymentMethod>() {
        override fun fromJson(reader: JsonReader): PaymentMethod? {
            val label: String = reader.peekJson().use { peek: JsonReader -> readLabel(peek) }
            return when (label) {
                "card" -> cards.fromJson(reader)
                "bank_account" -> bankAccounts.fromJson(reader)
                else -> throw IllegalArgumentException("Unknown payment method type '$label' at ${reader.path}")
            }
        }

        override fun toJson(writer: JsonWriter, value: PaymentMethod?) {
            when (value) {
                null -> writer.nullValue()
                is PaymentMethod.Card -> writeLabeled(writer, "card") { cards.toJson(writer, value) }
                is PaymentMethod.BankAccount -> writeLabeled(writer, "bank_account") { bankAccounts.toJson(writer, value) }
            }
        }

        private fun readLabel(reader: JsonReader): String {
            reader.beginObject()
            while (reader.hasNext()) {
                if (reader.selectName(TYPE_OPTIONS) == 0) {
                    return reader.nextString()
                }
                reader.skipName()
                reader.skipValue()
            }
            throw IllegalArgumentException("Missing payment method type at ${reader.path}")
        }

        /** Writes the label, then lets the subtype adapter fill in the same object. */
        private fun writeLabeled(writer: JsonWriter, label: String, body: () -> Unit) {
            writer.beginObject()
            writer.name("type").value(label)
            val token: Int = writer.beginFlatten()
            body()
            writer.endFlatten(token)
            writer.endObject()
        }

        private companion object {
            val TYPE_OPTIONS: JsonReader.Options = JsonReader.Options.of("type")
        }
    }

    private val moshi: Moshi = Moshi.Builder()
        .add(PaymentMethodFactory)
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun decodesAnnotatedChargesReflectivelyWhenNoGeneratedAdapterExists(): Unit {
        val expectedAdapterName: String = Charge::class.java.name.replace('$', '_') + "JsonAdapter"
        assertEquals(expectedAdapterName, Types.generatedJsonAdapterName(Charge::class.java))

        val adapter: JsonAdapter<Charge> = moshi.adapter<Charge>()
        assertTrue(adapter.toString().contains("KotlinJsonAdapter("), adapter.toString())

        val charge: Charge = adapter.fromJson(
            """
            {"id":"ch_1","amount_cents":2599,"currency":"eur","captured":true,
             "metadata":{"order":"A-17","channel":"web"},"unknown_field":[1,2]}
            """.trimIndent(),
        )!!
        assertEquals(
            Charge("ch_1", 2599, Currency.EUR, true, null, mapOf("order" to "A-17", "channel" to "web")),
            charge,
        )

        val defaulted: Charge = adapter.fromJson("""{"id":"ch_2","amount_cents":100}""")!!
        assertEquals(Currency.USD, defaulted.currency)
        assertEquals(false, defaulted.captured)
        assertNull(defaulted.description)
        assertEquals(emptyMap<String, String>(), defaulted.metadata)

        assertEquals(
            """{"id":"ch_2","amount_cents":100,"currency":"usd","captured":false,"metadata":{}}""",
            adapter.toJson(defaulted),
        )
        assertNull(adapter.fromJson("null"))
    }

    @Test
    fun pagesThroughGenericAnnotatedEnvelopes(): Unit {
        val pageAdapter: JsonAdapter<Page<Charge>> = moshi.adapter<Page<Charge>>()
        val firstPage: Page<Charge> = pageAdapter.fromJson(
            """
            {"data":[{"id":"ch_1","amount_cents":500},{"id":"ch_2","amount_cents":750,"captured":true}],
             "has_more":true,"next_cursor":"ch_2"}
            """.trimIndent(),
        )!!
        val lastPage: Page<Charge> = pageAdapter.fromJson(
            """{"data":[{"id":"ch_3","amount_cents":1250,"description":"refill"}],"has_more":false}""",
        )!!

        val charges: List<Charge> = firstPage.data + lastPage.data
        assertEquals(listOf("ch_1", "ch_2", "ch_3"), charges.map { charge: Charge -> charge.id })
        assertEquals(2500L, charges.sumOf { charge: Charge -> charge.amountCents })
        assertEquals("ch_2", firstPage.nextCursor)
        assertNull(lastPage.nextCursor)
        assertEquals("refill", lastPage.data.single().description)

        val idPage: Page<String> = moshi.adapter<Page<String>>()
            .fromJson("""{"data":["pm_1","pm_2"],"has_more":false}""")!!
        assertEquals(Page(listOf("pm_1", "pm_2"), false, null), idPage)
    }

    @Test
    fun encodesOptedOutRequestBodiesReflectively(): Unit {
        val adapter: JsonAdapter<RefundRequest> = moshi.adapter<RefundRequest>()

        assertEquals(
            """{"charge":"ch_1","reason":"requested_by_customer"}""",
            adapter.toJson(RefundRequest("ch_1")),
        )
        assertEquals(
            """{"charge":"ch_1","amount_cents":null,"reason":"duplicate"}""",
            adapter.serializeNulls().toJson(RefundRequest("ch_1", reason = "duplicate")),
        )
        assertEquals(
            RefundRequest("ch_9", 300, "fraudulent"),
            adapter.fromJson("""{"charge":"ch_9","amount_cents":300,"reason":"fraudulent"}"""),
        )
    }

    @Test
    fun rejectsAnnotatedSealedHierarchyUntilAnAdapterIsRegistered(): Unit {
        val reflectiveOnly: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val sealedFailure: IllegalArgumentException = assertThrows(IllegalArgumentException::class.java) {
            reflectiveOnly.adapter<PaymentMethod>()
        }
        assertTrue(
            sealedFailure.message!!.contains(
                "Cannot reflectively serialize sealed class ${PaymentMethod::class.java.name}. Please register an adapter.",
            ),
            sealedFailure.message,
        )

        val walletAdapter: JsonAdapter<List<PaymentMethod>> = moshi.adapter<List<PaymentMethod>>()
        val wallet: List<PaymentMethod> = walletAdapter.fromJson(
            """
            [{"type":"card","id":"pm_1","brand":"visa","last4":"4242","exp_month":12,"exp_year":2030},
             {"id":"pm_2","last4":"6789","type":"bank_account","bank_name":"First Savings"}]
            """.trimIndent(),
        )!!
        assertEquals(
            listOf(
                PaymentMethod.Card("pm_1", "visa", "4242", 12, 2030),
                PaymentMethod.BankAccount("pm_2", "First Savings", "6789"),
            ),
            wallet,
        )
        assertEquals(
            """[{"type":"card","id":"pm_1","brand":"visa","last4":"4242","exp_month":12,"exp_year":2030},""" +
                """{"type":"bank_account","id":"pm_2","bank_name":"First Savings","last4":"6789"}]""",
            walletAdapter.toJson(wallet),
        )

        val unknown: IllegalArgumentException = assertThrows(IllegalArgumentException::class.java) {
            walletAdapter.fromJson("""[{"type":"crypto","id":"pm_3"}]""")
        }
        assertEquals("Unknown payment method type 'crypto' at $[0]", unknown.message)
    }

    @Test
    fun reportsMissingGeneratedAdapterWithoutTheKotlinFactory(): Unit {
        val codegenOnly: Moshi = Moshi.Builder().build()

        val failure: RuntimeException = assertThrows(RuntimeException::class.java) {
            codegenOnly.adapter<Charge>()
        }
        assertTrue(
            failure.message!!.startsWith("Failed to find the generated JsonAdapter class for"),
            failure.message,
        )
        assertTrue(failure.message!!.contains(Charge::class.java.name), failure.message)
        val cause: ClassNotFoundException = assertInstanceOf(ClassNotFoundException::class.java, failure.cause)
        assertTrue(cause.message!!.contains(Types.generatedJsonAdapterName(Charge::class.java)), cause.message)
    }
}
