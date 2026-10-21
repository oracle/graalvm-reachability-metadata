/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_moshi.moshi_kotlin

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.adapter
import com.squareup.moshi.addAdapter
import com.squareup.moshi.asArrayType
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.moshi.rawType
import com.squareup.moshi.subtypeOf
import com.squareup.moshi.supertypeOf
import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import kotlin.reflect.KType
import kotlin.reflect.javaType
import kotlin.reflect.typeOf
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises Kotlin type-token adapter lookup: `Moshi.adapter(KType)` driven by `typeOf`, where
 * the root type's nullability selects a null-safe or non-null adapter, `addAdapter` registering
 * a reified parameterized type, a `KType` registry routing event payloads, and the Kotlin type
 * helpers `subtypeOf`, `supertypeOf`, `asArrayType`, and `rawType`.
 */
@OptIn(ExperimentalStdlibApi::class)
public class FreightEventRouterKTypeLookupTest {

    /** An amount in minor currency units. */
    public data class Money(val amountMinor: Long, val currency: String)

    /** Stock on hand for one SKU at a depot. */
    public data class StockLevel(val sku: String, val onHand: Int, val reserved: Int = 0)

    /** A volume discount tier. */
    public data class PriceTier(val minQuantity: Int, val unitPrice: Money)

    /** One temperature reading from a reefer container. */
    public data class Reading(val sensor: String, val celsius: Double)

    /** A carrier invoice whose charges travel in the compact money wire form. */
    public data class Invoice(val number: String, val charges: List<Money>, val notes: List<String>)

    /** The routing envelope every event arrives in; the payload shape depends on `kind`. */
    public data class Envelope(val kind: String, val payload: Any?)

    /** Writes a list of money values as `"<minor> <currency>"` strings, the carrier wire form. */
    public class CompactMoneyListAdapter : JsonAdapter<List<Money>>() {
        override fun fromJson(reader: JsonReader): List<Money> {
            val charges: MutableList<Money> = mutableListOf()
            reader.beginArray()
            while (reader.hasNext()) {
                val path: String = reader.path
                val parts: List<String> = reader.nextString().split(' ')
                if (parts.size != 2) throw JsonDataException("Malformed money at $path")
                charges += Money(parts[0].toLong(), parts[1])
            }
            reader.endArray()
            return charges
        }

        override fun toJson(writer: JsonWriter, value: List<Money>?) {
            requireNotNull(value) { "CompactMoneyListAdapter does not encode null" }
            writer.beginArray()
            for (money: Money in value) writer.value("${money.amountMinor} ${money.currency}")
            writer.endArray()
        }
    }

    private val moshi: Moshi = Moshi.Builder()
        .addAdapter<List<Money>>(CompactMoneyListAdapter())
        .addLast(KotlinJsonAdapterFactory())
        .build()

    /** Payload types the router knows, keyed by envelope kind. */
    private val payloadTypes: Map<String, KType> = mapOf(
        "depot.stock" to typeOf<Map<String, List<StockLevel>>>(),
        "pricing.tiers" to typeOf<List<PriceTier?>>(),
        "reefer.readings" to typeOf<List<Reading>>(),
        "invoice.issued" to typeOf<Invoice>(),
    )

    @Test
    fun rootKTypeNullabilitySelectsNullSafeOrNonNullAdapter() {
        val nullableCharges: JsonAdapter<List<Money>?> = moshi.adapter(typeOf<List<Money>?>())
        assertNull(nullableCharges.fromJson("null"))
        assertEquals("null", nullableCharges.toJson(null))
        assertEquals(listOf(Money(1250, "USD")), nullableCharges.fromJson("""["1250 USD"]"""))

        val requiredCharges: JsonAdapter<List<Money>> = moshi.adapter(typeOf<List<Money>>())
        val nullCharges: JsonDataException =
            assertThrows(JsonDataException::class.java) { requiredCharges.fromJson("null") }
        assertTrue(nullCharges.message!!.startsWith("Unexpected null at $"), nullCharges.message)
        assertEquals("""["399 EUR"]""", requiredCharges.toJson(listOf(Money(399, "EUR"))))

        val requiredCount: JsonAdapter<Int> = moshi.adapter(typeOf<Int>())
        assertEquals(42, requiredCount.fromJson("42"))
        assertThrows(JsonDataException::class.java) { requiredCount.fromJson("null") }

        val optionalCount: JsonAdapter<Int?> = moshi.adapter(typeOf<Int?>())
        assertNull(optionalCount.fromJson("null"))
        assertEquals(7, optionalCount.fromJson("7"))
    }

    @Test
    fun reifiedAddAdapterAppliesOnlyToTheRegisteredParameterizedType() {
        val invoices: JsonAdapter<Invoice> = moshi.adapter<Invoice>()
        val invoice = Invoice(
            number = "INV-2026-0042",
            charges = listOf(Money(125000, "USD"), Money(8999, "USD")),
            notes = listOf("fuel surcharge", "liftgate"),
        )

        val json: String = invoices.toJson(invoice)
        assertEquals(
            """{"number":"INV-2026-0042","charges":["125000 USD","8999 USD"],""" +
                """"notes":["fuel surcharge","liftgate"]}""",
            json,
        )
        assertEquals(invoice, invoices.fromJson(json))

        val malformed: JsonDataException = assertThrows(JsonDataException::class.java) {
            invoices.fromJson("""{"number":"INV-1","charges":["12USD"],"notes":[]}""")
        }
        assertEquals("Malformed money at $.charges[0]", malformed.message)

        val tiers: JsonAdapter<List<PriceTier>> = moshi.adapter<List<PriceTier>>()
        assertEquals(
            """[{"minQuantity":10,"unitPrice":{"amountMinor":450,"currency":"USD"}}]""",
            tiers.toJson(listOf(PriceTier(10, Money(450, "USD")))),
        )
    }

    @Test
    fun kTypeRegistryRoutesEnvelopePayloads() {
        val batch: String = """
            [
              {"kind":"depot.stock","payload":{"rotterdam":[{"sku":"PAL-01","onHand":40,"reserved":5}],
                "memphis":[{"sku":"PAL-01","onHand":12},{"sku":"CRT-09","onHand":3}]}},
              {"kind":"pricing.tiers","payload":[{"minQuantity":1,"unitPrice":{"amountMinor":500,
                "currency":"EUR"}},null,{"minQuantity":100,"unitPrice":{"amountMinor":410,"currency":"EUR"}}]},
              {"kind":"reefer.readings","payload":[{"sensor":"door","celsius":4.5},
                {"sensor":"core","celsius":-18.25}]},
              {"kind":"invoice.issued","payload":{"number":"INV-7","charges":["300 GBP"],"notes":[]}}
            ]
        """.trimIndent()

        val envelopes: List<Envelope> = moshi.adapter<List<Envelope>>().fromJson(batch)!!
        val routed: Map<String, Any?> = envelopes.associate { envelope: Envelope ->
            val payloadType: KType = payloadTypes.getValue(envelope.kind)
            envelope.kind to moshi.adapter<Any?>(payloadType).fromJsonValue(envelope.payload)
        }

        assertEquals(
            mapOf(
                "rotterdam" to listOf(StockLevel("PAL-01", 40, 5)),
                "memphis" to listOf(StockLevel("PAL-01", 12), StockLevel("CRT-09", 3)),
            ),
            routed["depot.stock"],
        )
        assertEquals(
            listOf(PriceTier(1, Money(500, "EUR")), null, PriceTier(100, Money(410, "EUR"))),
            routed["pricing.tiers"],
        )
        assertEquals(
            listOf(Reading("door", 4.5), Reading("core", -18.25)),
            routed["reefer.readings"],
        )
        assertEquals(Invoice("INV-7", listOf(Money(300, "GBP")), emptyList()), routed["invoice.issued"])

        val stockType: Type = payloadTypes.getValue("depot.stock").javaType
        assertEquals(Map::class.java, stockType.rawType)
        val stockArguments: Array<Type> = (stockType as ParameterizedType).actualTypeArguments
        assertEquals(String::class.java, stockArguments[0].rawType)
        assertEquals(List::class.java, stockArguments[1].rawType)
    }

    @Test
    fun wildcardAndArrayTypesFromKotlinHelpers() {
        val upperBounded: WildcardType = subtypeOf<Reading>()
        assertArrayEquals(arrayOf<Type>(Reading::class.java), upperBounded.upperBounds)
        val lowerBounded: WildcardType = supertypeOf<Reading>()
        assertArrayEquals(arrayOf<Type>(Reading::class.java), lowerBounded.lowerBounds)
        assertArrayEquals(arrayOf<Type>(Any::class.java), lowerBounded.upperBounds)

        val readingsJson: String = """[{"sensor":"door","celsius":3.0},{"sensor":"core","celsius":-20.5}]"""
        val expected: List<Reading> = listOf(Reading("door", 3.0), Reading("core", -20.5))

        val covariantList: JsonAdapter<List<Reading>> =
            moshi.adapter(Types.newParameterizedType(List::class.java, upperBounded))
        assertEquals(expected, covariantList.fromJson(readingsJson))

        val readingArrayType: GenericArrayType = Reading::class.asArrayType()
        assertTrue(Types.equals(readingArrayType, typeOf<Reading>().asArrayType()))
        val readingArrays: JsonAdapter<Array<Reading>> = moshi.adapter(readingArrayType)
        val decoded: Array<Reading> = readingArrays.fromJson(readingsJson)!!
        assertEquals(expected, decoded.toList())
        assertEquals(readingsJson, readingArrays.toJson(decoded))

        val shiftsType: GenericArrayType = typeOf<List<Reading>>().javaType.asArrayType()
        assertEquals(List::class.java, shiftsType.genericComponentType.rawType)
        val shifts: JsonAdapter<Array<List<Reading>>> = moshi.adapter(shiftsType)
        val decodedShifts: Array<List<Reading>> = shifts.fromJson("[$readingsJson,[]]")!!
        assertEquals(listOf(expected, emptyList<Reading>()), decodedShifts.toList())
    }
}
