/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:OptIn(
    ExperimentalSerializationApi::class,
    ExperimentalTime::class,
    ExperimentalUuidApi::class,
    ExperimentalUnsignedTypes::class,
)

package org_jetbrains_kotlinx.kotlinx_serialization_core_jvm

import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ArraySerializer
import kotlinx.serialization.builtins.BooleanArraySerializer
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.InstantComponentSerializer
import kotlinx.serialization.builtins.IntArraySerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.LongAsStringSerializer
import kotlinx.serialization.builtins.MapEntrySerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.PairSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.TripleSerializer
import kotlinx.serialization.builtins.UIntArraySerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises the built-in serializers for standard Kotlin types: tuples, arrays, unsigned
 * numbers, `Char`, `Unit`, `Uuid`, `kotlin.time.Instant`, `LongAsStringSerializer` and the
 * collection combinators from `kotlinx.serialization.builtins`.
 *
 * A warehouse sync service ships stock snapshots between sites. The snapshot model uses only
 * standard library types, and the service composes explicit serializers for the side payloads
 * it exchanges, so it relies on the library's built-in serializers for every wire shape.
 */
public class WarehouseStockSyncBuiltinSerializersTest {

    /** One stock count of a bin, typed entirely with standard library types. */
    @Serializable
    public data class StockSnapshot(
        val id: Uuid,
        val takenAt: Instant,
        val bin: Pair<String, Int>,
        val dimensionsCm: Triple<Double, Double, Double>,
        val quantity: UInt,
        val checksum: ULong,
        val grade: Char,
        val tags: Set<String>,
        @Serializable(with = LongAsStringSerializer::class)
        val ledgerOffset: Long,
    )

    private val json: Json = Json

    private val snapshot: StockSnapshot = StockSnapshot(
        id = Uuid.parse("6f1c2a5e-8d3b-4c7a-9e21-0b5d4f3a2c19"),
        takenAt = Instant.parse("2026-03-14T09:26:53.589Z"),
        bin = "A-07" to 3,
        dimensionsCm = Triple(40.0, 30.5, 12.25),
        quantity = 4_000_000_000u,
        checksum = ULong.MAX_VALUE,
        grade = 'B',
        tags = linkedSetOf("fragile", "cold-chain"),
        ledgerOffset = 9_007_199_254_740_993L,
    )

    @Test
    fun snapshotUsesTheBuiltinWireShapeForEveryStandardType() {
        val encoded: String = json.encodeToString(StockSnapshot.serializer(), snapshot)

        assertEquals(
            """{"id":"6f1c2a5e-8d3b-4c7a-9e21-0b5d4f3a2c19","takenAt":"2026-03-14T09:26:53.589Z",""" +
                """"bin":{"first":"A-07","second":3},"dimensionsCm":{"first":40.0,"second":30.5,"third":12.25},""" +
                """"quantity":4000000000,"checksum":18446744073709551615,"grade":"B",""" +
                """"tags":["fragile","cold-chain"],"ledgerOffset":"9007199254740993"}""",
            encoded,
        )
        assertEquals(snapshot, json.decodeFromString(StockSnapshot.serializer(), encoded))
    }

    @Test
    fun snapshotRoundTripsThroughCbor() {
        val bytes: ByteArray = Cbor.encodeToByteArray(snapshot)

        val decoded: StockSnapshot = Cbor.decodeFromByteArray(bytes)

        assertEquals(snapshot, decoded)
        assertEquals(ULong.MAX_VALUE, decoded.checksum)
        assertEquals(9_007_199_254_740_993L, decoded.ledgerOffset)
    }

    @Test
    fun composedCollectionSerializersCarryTheTransferManifest() {
        val manifestSerializer: KSerializer<Map<String, List<Pair<Int, UShort>>>> =
            MapSerializer(String.serializer(), ListSerializer(PairSerializer(Int.serializer(), UShort.serializer())))
        val manifest: Map<String, List<Pair<Int, UShort>>> = linkedMapOf(
            "north" to listOf(1 to 65_535.toUShort(), 2 to 12.toUShort()),
            "south" to emptyList(),
        )

        val encoded: String = json.encodeToString(manifestSerializer, manifest)

        assertEquals(
            """{"north":[{"first":1,"second":65535},{"first":2,"second":12}],"south":[]}""",
            encoded,
        )
        assertEquals(manifest, json.decodeFromString(manifestSerializer, encoded))
    }

    @Test
    fun setAndTripleSerializersCollapseDuplicatesAndKeepOrder() {
        val routeSerializer: KSerializer<Set<Triple<String, String, Byte>>> =
            SetSerializer(TripleSerializer(String.serializer(), String.serializer(), Byte.serializer()))

        val routes: Set<Triple<String, String, Byte>> = json.decodeFromString(
            routeSerializer,
            """[{"first":"BER","second":"WAW","third":2},{"first":"BER","second":"WAW","third":2},""" +
                """{"first":"WAW","second":"PRG","third":-1}]""",
        )

        assertEquals(listOf(Triple("BER", "WAW", 2.toByte()), Triple("WAW", "PRG", (-1).toByte())), routes.toList())
    }

    @Test
    fun mapEntrySerializerWritesASingleKeyObject() {
        val entrySerializer: KSerializer<Map.Entry<String, Long>> =
            MapEntrySerializer(String.serializer(), Long.serializer())
        val reservation: Map.Entry<String, Long> = mapOf("SKU-42" to 17L).entries.single()

        val encoded: String = json.encodeToString(entrySerializer, reservation)

        assertEquals("""{"SKU-42":17}""", encoded)
        val decoded: Map.Entry<String, Long> = json.decodeFromString(entrySerializer, encoded)
        assertEquals("SKU-42", decoded.key)
        assertEquals(17L, decoded.value)
    }

    @Test
    fun arraySerializersRoundTripScannerPayloads() {
        val binCodes: Array<String> = arrayOf("A-01", "A-02", "B-11")
        val counts: IntArray = intArrayOf(12, 0, 7)
        val occupied: BooleanArray = booleanArrayOf(true, false, true)
        val rfid: ByteArray = byteArrayOf(0x0A, -0x01, 0x7F)
        val weightsGrams: UIntArray = uintArrayOf(3_000_000_000u, 250u)

        val binJson: String = json.encodeToString(ArraySerializer(String.serializer()), binCodes)
        val countsJson: String = json.encodeToString(IntArraySerializer(), counts)
        val occupiedJson: String = json.encodeToString(BooleanArraySerializer(), occupied)
        val rfidJson: String = json.encodeToString(ByteArraySerializer(), rfid)
        val weightsJson: String = json.encodeToString(UIntArraySerializer(), weightsGrams)

        assertEquals("""["A-01","A-02","B-11"]""", binJson)
        assertEquals("[12,0,7]", countsJson)
        assertEquals("[true,false,true]", occupiedJson)
        assertEquals("[10,-1,127]", rfidJson)
        assertEquals("[3000000000,250]", weightsJson)
        assertArrayEquals(binCodes, json.decodeFromString(ArraySerializer(String.serializer()), binJson))
        assertArrayEquals(counts, json.decodeFromString(IntArraySerializer(), countsJson))
        assertArrayEquals(occupied, json.decodeFromString(BooleanArraySerializer(), occupiedJson))
        assertArrayEquals(rfid, Cbor.decodeFromByteArray(ByteArraySerializer(), Cbor.encodeToByteArray(ByteArraySerializer(), rfid)))
        assertEquals(weightsGrams.toList(), json.decodeFromString(UIntArraySerializer(), weightsJson).toList())
    }

    @Test
    fun instantComponentSerializerSplitsSecondsAndNanoseconds() {
        val takenAt: Instant = Instant.parse("2026-03-14T09:26:53.589Z")

        val encoded: String = json.encodeToString(InstantComponentSerializer, takenAt)

        assertEquals("""{"epochSeconds":1773480413,"nanosecondsOfSecond":589000000}""", encoded)
        assertEquals(takenAt, json.decodeFromString(InstantComponentSerializer, encoded))
    }

    @Test
    fun unitSerializerAcknowledgesWithAnEmptyObject() {
        assertEquals("{}", json.encodeToString(Unit.serializer(), Unit))
        assertEquals(Unit, json.decodeFromString(Unit.serializer(), "{}"))
    }

    @Test
    fun builtinDescriptorsExposeTheirKindsAndNames() {
        val pairDescriptor = PairSerializer(String.serializer(), Int.serializer()).descriptor
        assertEquals("kotlin.Pair", pairDescriptor.serialName)
        assertEquals(StructureKind.CLASS, pairDescriptor.kind)
        assertEquals(listOf("first", "second"), (0 until pairDescriptor.elementsCount).map(pairDescriptor::getElementName))

        assertEquals("kotlin.Triple", TripleSerializer(Int.serializer(), Int.serializer(), Int.serializer()).descriptor.serialName)
        assertEquals(StructureKind.LIST, ArraySerializer(String.serializer()).descriptor.kind)
        assertEquals(StructureKind.LIST, IntArraySerializer().descriptor.kind)
        assertEquals(StructureKind.MAP, MapSerializer(String.serializer(), Long.serializer()).descriptor.kind)
        assertEquals(StructureKind.OBJECT, Unit.serializer().descriptor.kind)
        assertEquals(PrimitiveKind.STRING, Uuid.serializer().descriptor.kind)
        assertEquals(PrimitiveKind.STRING, LongAsStringSerializer.descriptor.kind)
        assertEquals(PrimitiveKind.CHAR, Char.serializer().descriptor.kind)
        assertTrue(UInt.serializer().descriptor.isInline)
        assertTrue(ULong.serializer().descriptor.isInline)

        val snapshotDescriptor = StockSnapshot.serializer().descriptor
        assertEquals("kotlin.Pair", snapshotDescriptor.getElementDescriptor(2).serialName)
        assertTrue(snapshotDescriptor.getElementDescriptor(4).isInline)
    }

    @Test
    fun malformedStandardValuesAreRejected() {
        val validJson: String = json.encodeToString(StockSnapshot.serializer(), snapshot)

        assertThrows(IllegalArgumentException::class.java) {
            json.decodeFromString(StockSnapshot.serializer(), validJson.replace("6f1c2a5e-8d3b", "not-a-uuid"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            json.decodeFromString(UInt.serializer(), "-1")
        }
        assertThrows(IllegalArgumentException::class.java) {
            json.decodeFromString(LongAsStringSerializer, "\"twelve\"")
        }
    }
}
