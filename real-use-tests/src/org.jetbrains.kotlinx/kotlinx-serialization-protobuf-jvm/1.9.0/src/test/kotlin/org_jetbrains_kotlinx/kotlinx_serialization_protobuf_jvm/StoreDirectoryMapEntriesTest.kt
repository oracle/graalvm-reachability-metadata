/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_protobuf_jvm

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Protobuf `map` fields: Kotlin `Map` properties written by `ProtoBuf` as repeated key/value entry messages.
 *
 * A store directory service keeps, per store, its opening hours keyed by weekday, its aisle
 * labels keyed by aisle number and its shelf stock keyed by product id. A legacy client that
 * predates protobuf maps writes the same data as a repeated entry message with key field 1 and
 * value field 2, so the service checks that both shapes share one wire format, that a
 * duplicated key resolves to the last entry the way protobuf requires, and that insertion order
 * survives a round trip.
 */
@OptIn(ExperimentalSerializationApi::class)
class StoreDirectoryMapEntriesTest {

    @Serializable
    enum class Weekday {
        @ProtoNumber(1)
        MONDAY,

        @ProtoNumber(2)
        TUESDAY,

        @ProtoNumber(6)
        SATURDAY,

        @ProtoNumber(7)
        SUNDAY,
    }

    @Serializable
    data class Hours(
        @ProtoNumber(1) val opensAtMinute: Int,
        @ProtoNumber(2) val closesAtMinute: Int,
    ) {
        fun openMinutes(): Int = closesAtMinute - opensAtMinute
    }

    @Serializable
    data class ShelfStock(
        @ProtoNumber(1) val shelf: String,
        @ProtoNumber(2) val units: Int,
    )

    @Serializable
    data class Store(
        @ProtoNumber(1) val storeId: Int,
        @ProtoNumber(2) val openingHours: Map<Weekday, Hours> = emptyMap(),
        @ProtoNumber(3) val aisleLabels: Map<Int, String> = emptyMap(),
        @ProtoNumber(4) val stockByProduct: Map<Long, ShelfStock> = emptyMap(),
    )

    /** The entry message a protobuf `map<int32, string>` is defined to be equivalent to. */
    @Serializable
    data class AisleLabelEntry(
        @ProtoNumber(1) val aisle: Int,
        @ProtoNumber(2) val label: String,
    )

    /** How the legacy client writes a store: aisle labels as a repeated entry message. */
    @Serializable
    data class LegacyStore(
        @ProtoNumber(1) val storeId: Int,
        @ProtoNumber(3) val aisleLabels: List<AisleLabelEntry> = emptyList(),
    )

    private val protoBuf: ProtoBuf = ProtoBuf

    @Test
    fun roundTripsMapsWithEnumIntegerAndLongKeys() {
        val store = Store(
            storeId = 31,
            openingHours = linkedMapOf(
                Weekday.SATURDAY to Hours(opensAtMinute = 9 * 60, closesAtMinute = 14 * 60),
                Weekday.MONDAY to Hours(opensAtMinute = 8 * 60, closesAtMinute = 20 * 60),
                Weekday.SUNDAY to Hours(opensAtMinute = 10 * 60, closesAtMinute = 12 * 60),
            ),
            aisleLabels = linkedMapOf(4 to "Bakery", 1 to "Produce", 12 to "Frozen"),
            stockByProduct = linkedMapOf(
                9_000_000_017L to ShelfStock(shelf = "B-2", units = 14),
                42L to ShelfStock(shelf = "A-7", units = 3),
            ),
        )

        val restored: Store = protoBuf.decodeFromByteArray(protoBuf.encodeToByteArray(store))

        assertEquals(store, restored)
        assertEquals(listOf(Weekday.SATURDAY, Weekday.MONDAY, Weekday.SUNDAY), restored.openingHours.keys.toList())
        assertEquals(listOf(4, 1, 12), restored.aisleLabels.keys.toList())
        assertEquals(720, restored.openingHours.getValue(Weekday.MONDAY).openMinutes())
        assertEquals(17, restored.stockByProduct.values.sumOf { it.units })
        assertEquals("B-2", restored.stockByProduct.getValue(9_000_000_017L).shelf)
    }

    @Test
    fun writesEachEntryAsNestedKeyValueMessage() {
        val store = Store(storeId = 5, aisleLabels = mapOf(3 to "Tea"))

        val bytes: ByteArray = protoBuf.encodeToByteArray(store)

        // Field 1 varint 5, then field 3 length-delimited: an entry with key field 1 = 3 and
        // value field 2 = "Tea".
        val expected: ByteArray = byteArrayOf(
            0x08, 0x05,
            0x1A, 0x07, 0x08, 0x03, 0x12, 0x03, 'T'.code.toByte(), 'e'.code.toByte(), 'a'.code.toByte(),
        )
        assertArrayEquals(expected, bytes)
    }

    @Test
    fun sharesWireFormatWithLegacyRepeatedEntries() {
        val store = Store(storeId = 77, aisleLabels = linkedMapOf(2 to "Dairy", 9 to "Pet care", 5 to "Snacks"))
        val legacy = LegacyStore(
            storeId = 77,
            aisleLabels = listOf(
                AisleLabelEntry(aisle = 2, label = "Dairy"),
                AisleLabelEntry(aisle = 9, label = "Pet care"),
                AisleLabelEntry(aisle = 5, label = "Snacks"),
            ),
        )

        val storeBytes: ByteArray = protoBuf.encodeToByteArray(store)
        val legacyBytes: ByteArray = protoBuf.encodeToByteArray(legacy)

        assertArrayEquals(legacyBytes, storeBytes)
        assertEquals(store, protoBuf.decodeFromByteArray<Store>(legacyBytes))
        assertEquals(legacy, protoBuf.decodeFromByteArray<LegacyStore>(storeBytes))
    }

    @Test
    fun keepsLastEntryWhenLegacyClientRepeatsKey() {
        val legacy = LegacyStore(
            storeId = 8,
            aisleLabels = listOf(
                AisleLabelEntry(aisle = 6, label = "Cleaning"),
                AisleLabelEntry(aisle = 1, label = "Fruit"),
                AisleLabelEntry(aisle = 6, label = "Household"),
            ),
        )

        val store: Store = protoBuf.decodeFromByteArray(protoBuf.encodeToByteArray(legacy))

        assertEquals(mapOf(6 to "Household", 1 to "Fruit"), store.aisleLabels)
        assertEquals(2, store.aisleLabels.size)
    }
}
