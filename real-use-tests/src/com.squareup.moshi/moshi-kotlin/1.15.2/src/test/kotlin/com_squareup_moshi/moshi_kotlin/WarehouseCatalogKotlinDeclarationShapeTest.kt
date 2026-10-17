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
import com.squareup.moshi.adapter
import com.squareup.moshi.addAdapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises Kotlin declaration shapes in `KotlinJsonAdapterFactory`: `@JvmInline` value-class
 * properties, private and internal primary constructors, private and internal properties,
 * `@JvmField` properties, secondary constructors bypassed for the primary one, and rejection of
 * local classes, classes without a primary constructor, and parameters typed unlike their
 * property.
 *
 * A warehouse catalog service keeps its identifiers and counts as value classes and exchanges
 * stock records, bin layouts, and pick tickets with a partner system as JSON.
 */
@OptIn(ExperimentalStdlibApi::class)
public class WarehouseCatalogKotlinDeclarationShapeTest {

    /** Stock-keeping unit code such as `A-100`. */
    @JvmInline
    public value class Sku(public val code: String)

    /** Count of physical units. */
    @JvmInline
    public value class Quantity(public val units: Int)

    /** Stock level of one SKU in one warehouse. */
    public data class StockRecord(
        val sku: Sku,
        val warehouse: String,
        val onHand: Quantity,
        val reserved: Quantity = Quantity(0),
        val replacement: Sku? = null,
    ) {
        /** Convenience constructor for an unreserved record; never used for decoding. */
        public constructor(code: String, units: Int) : this(Sku(code), "MAIN", Quantity(units))
    }

    /** A shelf location whose instances only come from the companion factories or from JSON. */
    public class Bin private constructor(
        @JvmField public val aisle: String,
        public val shelf: Int,
        internal val zoneCode: String,
        private val capacity: Int,
    ) {
        /** Answers whether `units` more items fit, using the private capacity. */
        public fun hasRoomFor(units: Int): Boolean = units <= capacity

        public companion object {
            /** Refrigerated bins hold fewer units. */
            public fun cold(aisle: String, shelf: Int): Bin = Bin(aisle, shelf, "COLD", 12)
        }
    }

    /** Module-private pick instruction for the floor crew. */
    internal data class PickTicket internal constructor(
        val id: String,
        val lines: List<Sku>,
        internal val priority: Int = 0,
    )

    /** Legacy label type with only a secondary constructor. */
    public class LegacyLabel {
        public var text: String = ""

        public constructor(text: String) {
            this.text = text
        }
    }

    /** Reading whose constructor takes a count but whose property of the same name is text. */
    public class ScaleReading(quantity: Int) {
        public val quantity: String = "$quantity units"
    }

    /** Encodes a [Sku] as its bare code and rejects malformed codes. */
    public object SkuAdapter : JsonAdapter<Sku>() {
        private val format: Regex = Regex("[A-Z]-[0-9]+")

        override fun fromJson(reader: JsonReader): Sku {
            val path: String = reader.path
            val code: String = reader.nextString()
            if (!format.matches(code)) {
                throw JsonDataException("Malformed SKU '$code' at $path")
            }
            return Sku(code)
        }

        override fun toJson(writer: JsonWriter, value: Sku?) {
            writer.value(value!!.code)
        }
    }

    /** Encodes a [Quantity] as a bare number and rejects negative counts. */
    public object QuantityAdapter : JsonAdapter<Quantity>() {
        override fun fromJson(reader: JsonReader): Quantity {
            val path: String = reader.path
            val units: Int = reader.nextInt()
            if (units < 0) {
                throw JsonDataException("Negative quantity $units at $path")
            }
            return Quantity(units)
        }

        override fun toJson(writer: JsonWriter, value: Quantity?) {
            writer.value(value!!.units.toLong())
        }
    }

    private val reflectiveMoshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    private val flatMoshi: Moshi = Moshi.Builder()
        .addAdapter<Sku>(SkuAdapter.nullSafe())
        .addAdapter<Quantity>(QuantityAdapter.nullSafe())
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun valueClassPropertiesEncodeAsNestedObjectsByDefault() {
        val adapter: JsonAdapter<StockRecord> = reflectiveMoshi.adapter<StockRecord>()

        val record: StockRecord = StockRecord("A-100", 12).copy(replacement = Sku("A-101"))

        val json: String = adapter.toJson(record)

        assertEquals(
            """{"sku":{"code":"A-100"},"warehouse":"MAIN","onHand":{"units":12},"reserved":{"units":0},""" +
                """"replacement":{"code":"A-101"}}""",
            json,
        )
        val decoded: StockRecord = adapter.fromJson(
            """{"sku":{"code":"B-7"},"warehouse":"EAST","onHand":{"units":3},"replacement":{"code":"B-8"}}""",
        )!!
        assertEquals(StockRecord(Sku("B-7"), "EAST", Quantity(3), Quantity(0), Sku("B-8")), decoded)
    }

    @Test
    fun registeredValueClassAdaptersFlattenIdentifiersWithoutTouchingPlainStrings() {
        val adapter: JsonAdapter<StockRecord> = flatMoshi.adapter<StockRecord>()
        val record: StockRecord = StockRecord(Sku("C-42"), "NORTH", Quantity(9), Quantity(2), Sku("C-43"))

        val json: String = adapter.toJson(record)

        assertEquals(
            """{"sku":"C-42","warehouse":"NORTH","onHand":9,"reserved":2,"replacement":"C-43"}""",
            json,
        )
        assertEquals(record, adapter.fromJson(json))
        val withoutReplacement: StockRecord =
            adapter.fromJson("""{"sku":"D-1","warehouse":"lower-case is fine","onHand":0,"replacement":null}""")!!
        assertEquals("lower-case is fine", withoutReplacement.warehouse)
        assertNull(withoutReplacement.replacement)
        assertEquals(Quantity(0), withoutReplacement.reserved)
    }

    @Test
    fun valueClassAdaptersValidateInsideCollectionsAndReportPaths() {
        val listAdapter: JsonAdapter<List<Sku>> = flatMoshi.adapter<List<Sku>>()
        val countsAdapter: JsonAdapter<Map<String, Quantity>> = flatMoshi.adapter<Map<String, Quantity>>()

        assertEquals(listOf(Sku("A-1"), Sku("B-22")), listAdapter.fromJson("""["A-1","B-22"]"""))
        assertEquals("""["Z-9"]""", listAdapter.toJson(listOf(Sku("Z-9"))))
        assertEquals(
            mapOf("EAST" to Quantity(4), "WEST" to Quantity(0)),
            countsAdapter.fromJson("""{"EAST":4,"WEST":0}"""),
        )

        val badSku: JsonDataException = assertThrows(JsonDataException::class.java) {
            listAdapter.fromJson("""["A-1","bad sku"]""")
        }
        assertEquals("Malformed SKU 'bad sku' at $[1]", badSku.message)
        val badCount: JsonDataException = assertThrows(JsonDataException::class.java) {
            flatMoshi.adapter<StockRecord>().fromJson("""{"sku":"A-1","warehouse":"W","onHand":-5}""")
        }
        assertEquals("Negative quantity -5 at $.onHand", badCount.message)
    }

    @Test
    fun privateConstructorAndHiddenPropertiesRoundTrip() {
        val adapter: JsonAdapter<Bin> = reflectiveMoshi.adapter<Bin>()

        val json: String = adapter.toJson(Bin.cold("C", 3))

        assertEquals("""{"aisle":"C","shelf":3,"zoneCode":"COLD","capacity":12}""", json)
        val decoded: Bin = adapter.fromJson("""{"aisle":"D","shelf":1,"zoneCode":"BULK","capacity":2}""")!!
        assertEquals("D", decoded.aisle)
        assertEquals(1, decoded.shelf)
        assertEquals("BULK", decoded.zoneCode)
        assertTrue(decoded.hasRoomFor(2))
        assertFalse(decoded.hasRoomFor(3))
        val missing: JsonDataException = assertThrows(JsonDataException::class.java) {
            adapter.fromJson("""{"aisle":"E","shelf":2,"zoneCode":"GEN"}""")
        }
        assertEquals("Required value 'capacity' missing at $", missing.message)
    }

    @Test
    fun internalClassWithInternalConstructorBindsValueClassList() {
        val adapter: JsonAdapter<PickTicket> = flatMoshi.adapter<PickTicket>()

        val urgent: PickTicket = adapter.fromJson("""{"id":"PT-1","lines":["A-1","A-2"],"priority":5}""")!!
        val routine: PickTicket = adapter.fromJson("""{"id":"PT-2","lines":[]}""")!!

        assertEquals(PickTicket("PT-1", listOf(Sku("A-1"), Sku("A-2")), 5), urgent)
        assertEquals(0, routine.priority)
        assertEquals("""{"id":"PT-2","lines":[],"priority":0}""", adapter.toJson(routine))
    }

    @Test
    fun unsupportedDeclarationShapesAreRejectedWhenTheAdapterIsRequested() {
        data class LocalNote(val text: String)

        val local: IllegalArgumentException = assertThrows(IllegalArgumentException::class.java) {
            reflectiveMoshi.adapter<LocalNote>()
        }
        assertTrue(local.message!!.startsWith("Cannot serialize local class or object expression"), local.message)
        val legacy: IllegalArgumentException = assertThrows(IllegalArgumentException::class.java) {
            reflectiveMoshi.adapter<LegacyLabel>()
        }
        assertTrue(legacy.message!!.startsWith("Cannot serialize Kotlin type"), legacy.message)
        val mismatch: IllegalArgumentException = assertThrows(IllegalArgumentException::class.java) {
            reflectiveMoshi.adapter<ScaleReading>()
        }
        assertEquals(
            "'quantity' has a constructor parameter of type kotlin.Int but a property of type kotlin.String.",
            mismatch.message,
        )
    }
}
