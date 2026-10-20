/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_cbor_jvm

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises CBOR maps with structured keys: maps keyed by serializable objects, by lists and
 * by negative integers, with nullable values and key sets nested inside other maps.
 *
 * A warehouse service keeps its stock snapshot keyed by bin location and pick route. CBOR
 * carries those composite keys natively, so the snapshot is stored and reloaded without
 * flattening keys into strings the way a JSON document would require.
 */
@OptIn(ExperimentalSerializationApi::class)
public class WarehouseStructuredMapKeysCborTest {

    @Serializable
    public data class BinLocation(val aisle: String, val shelf: Int)

    @Serializable
    public enum class Zone { COLD, DRY }

    @Serializable
    public data class StockSnapshot(
        val warehouse: String,
        val onHand: Map<BinLocation, Int>,
        val reserved: Map<BinLocation, Int?>,
        val floorNames: Map<Int, String>,
        val pickRouteMetres: Map<List<String>, Int>,
        val zones: Map<Zone, Set<BinLocation>>,
    )

    /** Applies stock movements to a snapshot and persists it as CBOR. */
    public class StockLedger(private val cbor: Cbor, private var snapshot: StockSnapshot) {

        public fun current(): StockSnapshot = snapshot

        public fun move(from: BinLocation, to: BinLocation, quantity: Int): Unit {
            val available: Int = snapshot.onHand[from] ?: 0
            require(available >= quantity) { "only $available units at $from" }
            val updated: MutableMap<BinLocation, Int> = LinkedHashMap(snapshot.onHand)
            updated[from] = available - quantity
            updated[to] = (updated[to] ?: 0) + quantity
            snapshot = snapshot.copy(onHand = updated)
        }

        public fun unitsInZone(zone: Zone): Int =
            snapshot.zones[zone].orEmpty().sumOf { bin: BinLocation -> snapshot.onHand[bin] ?: 0 }

        public fun save(): ByteArray = cbor.encodeToByteArray(snapshot)

        public companion object {
            public fun load(cbor: Cbor, stored: ByteArray): StockLedger =
                StockLedger(cbor, cbor.decodeFromByteArray<StockSnapshot>(stored))
        }
    }

    private val cbor: Cbor = Cbor { useDefiniteLengthEncoding = true }

    private val coldA1: BinLocation = BinLocation("A", 1)
    private val coldA2: BinLocation = BinLocation("A", 2)
    private val dryC4: BinLocation = BinLocation("C", 4)

    private val snapshot: StockSnapshot = StockSnapshot(
        warehouse = "north",
        onHand = linkedMapOf(coldA1 to 12, coldA2 to 7, dryC4 to 30),
        reserved = linkedMapOf(coldA1 to 4, dryC4 to null),
        floorNames = linkedMapOf(-1 to "freezer basement", 0 to "ground", 2 to "mezzanine"),
        pickRouteMetres = linkedMapOf(listOf("A", "C") to 42, listOf("C", "A", "B") to 65),
        zones = linkedMapOf(Zone.COLD to linkedSetOf(coldA1, coldA2), Zone.DRY to linkedSetOf(dryC4)),
    )

    @Test
    fun storesAndReloadsASnapshotWithStructuredKeys(): Unit {
        val ledger: StockLedger = StockLedger(cbor, snapshot)
        ledger.move(from = coldA1, to = coldA2, quantity = 5)

        val reloaded: StockLedger = StockLedger.load(cbor, ledger.save())
        val restored: StockSnapshot = reloaded.current()

        assertEquals(ledger.current(), restored)
        assertEquals(listOf(coldA1, coldA2, dryC4), restored.onHand.keys.toList())
        assertEquals(7, restored.onHand.getValue(BinLocation("A", 1)))
        assertEquals(12, restored.onHand.getValue(BinLocation("A", 2)))
        assertTrue(restored.reserved.containsKey(dryC4))
        assertNull(restored.reserved[dryC4])
        assertEquals("freezer basement", restored.floorNames[-1])
        assertEquals(65, restored.pickRouteMetres[listOf("C", "A", "B")])
        assertEquals(19, reloaded.unitsInZone(Zone.COLD))
        assertEquals(30, reloaded.unitsInZone(Zone.DRY))

        reloaded.move(from = dryC4, to = BinLocation("B", 9), quantity = 10)
        assertEquals(10, StockLedger.load(cbor, reloaded.save()).current().onHand[BinLocation("B", 9)])
    }

    @Test
    fun writesObjectListAndNegativeIntegerKeysAsCborItems(): Unit {
        val objectKeyed: ByteArray = cbor.encodeToByteArray(mapOf(coldA2 to 7))
        val expectedObjectKeyed: ByteArray = bytes(0xA1, 0xA2, 0x65) + "aisle".encodeToByteArray() +
            bytes(0x61, 0x41, 0x65) + "shelf".encodeToByteArray() + bytes(0x02, 0x07)
        assertArrayEquals(expectedObjectKeyed, objectKeyed)

        val listKeyed: ByteArray = cbor.encodeToByteArray(mapOf(listOf("A", "C") to 42))
        assertArrayEquals(bytes(0xA1, 0x82, 0x61, 0x41, 0x61, 0x43, 0x18, 0x2A), listKeyed)

        val negativeKeyed: ByteArray = cbor.encodeToByteArray(mapOf(-3 to "pit"))
        assertArrayEquals(bytes(0xA1, 0x22, 0x63) + "pit".encodeToByteArray(), negativeKeyed)

        val nullValued: ByteArray = cbor.encodeToByteArray(mapOf<BinLocation, Int?>(coldA2 to null))
        assertEquals(0xF6.toByte(), nullValued.last())
        assertEquals(mapOf<BinLocation, Int?>(coldA2 to null), cbor.decodeFromByteArray<Map<BinLocation, Int?>>(nullValued))
    }

    @Test
    fun readsStructuredKeysWrittenWithIndefiniteLengths(): Unit {
        // {[_ "A", "C"]: 42, [_ "B"]: 9} as a streaming encoder writes it: indefinite map, arrays as keys.
        val foreign: ByteArray = bytes(
            0xBF,
            0x9F, 0x61, 0x41, 0x61, 0x43, 0xFF, 0x18, 0x2A,
            0x9F, 0x61, 0x42, 0xFF, 0x09,
            0xFF,
        )

        val routes: Map<List<String>, Int> = cbor.decodeFromByteArray<Map<List<String>, Int>>(foreign)

        assertEquals(linkedMapOf(listOf("A", "C") to 42, listOf("B") to 9), routes)
    }

    @Test
    fun keepsCompositeKeysThatPlainJsonCannotCarry(): Unit {
        val onHand: Map<BinLocation, Int> = snapshot.onHand

        assertThrows(SerializationException::class.java) { Json.encodeToString(onHand) }

        val restored: Map<BinLocation, Int> = cbor.decodeFromByteArray<Map<BinLocation, Int>>(cbor.encodeToByteArray(onHand))
        assertEquals(onHand, restored)
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { index: Int -> values[index].toByte() }
}
