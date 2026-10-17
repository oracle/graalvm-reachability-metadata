/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:OptIn(ExperimentalSerializationApi::class)

package org_jetbrains_kotlinx.kotlinx_serialization_core_jvm

import kotlin.reflect.typeOf
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.properties.Properties
import kotlinx.serialization.properties.decodeFromStringMap
import kotlinx.serialization.properties.encodeToStringMap
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises enum serialization: generated enum serializers with `@SerialName` entries, enums
 * without `@Serializable` resolved at run time, enum map keys and sets, entry-level `@SerialInfo`
 * annotations, ordinal encoding in ProtoBuf, lenient enum serializers and unknown-entry rejection.
 *
 * A flight information display system publishes its departure board as JSON to airport screens,
 * ships it to partner airlines as ProtoBuf and keeps a flat properties snapshot for recovery, so
 * every status, terminal and baggage-belt state on the board crosses each format as an enum.
 */
public class FlightBoardEnumSerializationTest {

    /** Colour a screen paints a status in, attached to each enum entry. */
    @SerialInfo
    public annotation class DisplayColor(val hex: String)

    /** Departure status; entries carry the lower-case names the public feed publishes. */
    @Serializable
    @SerialName("FlightStatus")
    public enum class FlightStatus(public val departed: Boolean) {
        @SerialName("scheduled")
        @DisplayColor("#FFFFFF")
        SCHEDULED(false),

        @SerialName("boarding")
        @DisplayColor("#00C853")
        BOARDING(false),

        @SerialName("delayed")
        @DisplayColor("#FFAB00")
        DELAYED(false),

        @SerialName("departed")
        @DisplayColor("#9E9E9E")
        DEPARTED(true),

        @DisplayColor("#D50000")
        CANCELLED(false),
    }

    /** Terminal building; an ordinary enum the application never annotated. */
    public enum class Terminal {
        NORTH,
        SOUTH,
        EAST,
    }

    /** Baggage-belt state reported by belt controllers that may send states this build lacks. */
    @Serializable(with = BeltStateSerializer::class)
    public enum class BeltState {
        RUNNING,
        STOPPED,
        UNKNOWN,
    }

    /** Writes belt states in lower case and maps any state it does not know to [BeltState.UNKNOWN]. */
    public object BeltStateSerializer : KSerializer<BeltState> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BeltState", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: BeltState) {
            encoder.encodeString(value.name.lowercase())
        }

        override fun deserialize(decoder: Decoder): BeltState {
            val reported: String = decoder.decodeString()
            return BeltState.entries.firstOrNull { it.name.equals(reported, ignoreCase = true) } ?: BeltState.UNKNOWN
        }
    }

    /** One departure on the board. */
    @Serializable
    public data class BoardRow(
        val flight: String,
        val status: FlightStatus,
        val terminal: Terminal,
        val previous: FlightStatus? = null,
    )

    /** The whole board as screens receive it. */
    @Serializable
    public data class BoardSnapshot(
        val rows: List<BoardRow>,
        val countsByStatus: Map<FlightStatus, Int>,
        val openTerminals: Set<Terminal>,
        val belts: Map<Int, BeltState>,
    )

    /** The wire shape of [BoardRow] as a partner airline's ProtoBuf schema sees it: plain numbers. */
    @Serializable
    public data class PartnerBoardRow(val flight: String, val status: Int, val terminal: Int)

    private val feed: Json = Json

    @Test
    fun boardRowsPublishEntrySerialNamesToScreens() {
        val boarding: BoardRow = BoardRow("LH400", FlightStatus.BOARDING, Terminal.NORTH)
        val cancelled: BoardRow = BoardRow("BA117", FlightStatus.CANCELLED, Terminal.SOUTH, previous = FlightStatus.DELAYED)

        val boardingJson: String = feed.encodeToString(boarding)
        val cancelledJson: String = feed.encodeToString(cancelled)

        assertEquals("""{"flight":"LH400","status":"boarding","terminal":"NORTH"}""", boardingJson)
        assertEquals("""{"flight":"BA117","status":"CANCELLED","terminal":"SOUTH","previous":"delayed"}""", cancelledJson)
        assertEquals(boarding, feed.decodeFromString<BoardRow>(boardingJson))
        assertEquals(cancelled, feed.decodeFromString<BoardRow>(cancelledJson))
        assertTrue(feed.decodeFromString<BoardRow>("""{"flight":"UA90","status":"departed","terminal":"EAST"}""").status.departed)
    }

    @Test
    fun boardSnapshotKeysCountsAndTerminalsByEnum() {
        val snapshot: BoardSnapshot = BoardSnapshot(
            rows = listOf(
                BoardRow("LH400", FlightStatus.BOARDING, Terminal.NORTH),
                BoardRow("AF23", FlightStatus.DELAYED, Terminal.EAST, previous = FlightStatus.SCHEDULED),
            ),
            countsByStatus = linkedMapOf(FlightStatus.BOARDING to 1, FlightStatus.DELAYED to 1, FlightStatus.CANCELLED to 0),
            openTerminals = linkedSetOf(Terminal.EAST, Terminal.NORTH),
            belts = linkedMapOf(3 to BeltState.RUNNING, 7 to BeltState.STOPPED),
        )

        val published: String = feed.encodeToString(snapshot)

        assertEquals(
            """{"rows":[{"flight":"LH400","status":"boarding","terminal":"NORTH"},""" +
                """{"flight":"AF23","status":"delayed","terminal":"EAST","previous":"scheduled"}],""" +
                """"countsByStatus":{"boarding":1,"delayed":1,"CANCELLED":0},""" +
                """"openTerminals":["EAST","NORTH"],"belts":{"3":"running","7":"stopped"}}""",
            published,
        )
        val received: BoardSnapshot = feed.decodeFromString(published)
        assertEquals(snapshot, received)
        assertEquals(listOf(FlightStatus.BOARDING, FlightStatus.DELAYED, FlightStatus.CANCELLED), received.countsByStatus.keys.toList())
    }

    @Test
    fun unknownStatusesAndTerminalsAreRejected() {
        val badStatus: SerializationException = assertThrows(SerializationException::class.java) {
            feed.decodeFromString<BoardRow>("""{"flight":"LH400","status":"teleported","terminal":"NORTH"}""")
        }
        assertTrue(badStatus.message!!.contains("teleported"), badStatus.message)

        val upperCaseStatus: SerializationException = assertThrows(SerializationException::class.java) {
            feed.decodeFromString<BoardRow>("""{"flight":"LH400","status":"BOARDING","terminal":"NORTH"}""")
        }
        assertTrue(upperCaseStatus.message!!.contains("BOARDING"), upperCaseStatus.message)

        val badTerminal: SerializationException = assertThrows(SerializationException::class.java) {
            feed.decodeFromString<BoardRow>("""{"flight":"LH400","status":"boarding","terminal":"WEST"}""")
        }
        assertTrue(badTerminal.message!!.contains("WEST"), badTerminal.message)
    }

    @Test
    fun beltControllersReportingNewStatesFallBackToUnknown() {
        val belts: Map<Int, BeltState> = feed.decodeFromString("""{"1":"running","2":"STOPPED","3":"jammed"}""")

        assertEquals(mapOf(1 to BeltState.RUNNING, 2 to BeltState.STOPPED, 3 to BeltState.UNKNOWN), belts)
        assertEquals("\"unknown\"", feed.encodeToString(BeltState.UNKNOWN))
    }

    @Test
    fun unannotatedTerminalEnumIsResolvedAtRunTime() {
        @Suppress("UNCHECKED_CAST")
        val byKType: KSerializer<Terminal> = serializer(typeOf<Terminal>()) as KSerializer<Terminal>
        @Suppress("UNCHECKED_CAST")
        val byJavaType: KSerializer<Terminal> = serializer(Terminal::class.java) as KSerializer<Terminal>
        val terminals: KSerializer<List<Terminal>> = serializer()

        assertEquals(SerialKind.ENUM, byKType.descriptor.kind)
        assertEquals(listOf("NORTH", "SOUTH", "EAST"), byKType.descriptor.elementNames.toList())
        assertEquals("\"SOUTH\"", feed.encodeToString(byKType, Terminal.SOUTH))
        assertEquals(Terminal.EAST, feed.decodeFromString(byJavaType, "\"EAST\""))
        assertEquals(listOf(Terminal.NORTH, Terminal.EAST), feed.decodeFromString(terminals, """["NORTH","EAST"]"""))
    }

    @Test
    fun screensReadStatusColoursFromTheEnumDescriptor() {
        val descriptor: SerialDescriptor = FlightStatus.serializer().descriptor

        assertEquals("FlightStatus", descriptor.serialName)
        assertEquals(SerialKind.ENUM, descriptor.kind)
        assertEquals(listOf("scheduled", "boarding", "delayed", "departed", "CANCELLED"), descriptor.elementNames.toList())
        val colours: Map<String, String> = (0 until descriptor.elementsCount).associate { index: Int ->
            descriptor.getElementName(index) to
                descriptor.getElementAnnotations(index).filterIsInstance<DisplayColor>().single().hex
        }
        assertEquals(
            mapOf(
                "scheduled" to "#FFFFFF",
                "boarding" to "#00C853",
                "delayed" to "#FFAB00",
                "departed" to "#9E9E9E",
                "CANCELLED" to "#D50000",
            ),
            colours,
        )
        assertEquals(1, descriptor.getElementIndex("boarding"))
    }

    @Test
    fun partnerProtoBufFeedCarriesEntryOrdinals() {
        val row: BoardRow = BoardRow("LH400", FlightStatus.DEPARTED, Terminal.EAST)

        val partnerView: PartnerBoardRow = ProtoBuf.decodeFromByteArray(ProtoBuf.encodeToByteArray(row))
        assertEquals(PartnerBoardRow("LH400", 3, 2), partnerView)

        val fromPartner: BoardRow = ProtoBuf.decodeFromByteArray(ProtoBuf.encodeToByteArray(PartnerBoardRow("AF23", 2, 1)))
        assertEquals(BoardRow("AF23", FlightStatus.DELAYED, Terminal.SOUTH), fromPartner)

        val outOfRange: ByteArray = ProtoBuf.encodeToByteArray(PartnerBoardRow("BA117", 9, 0))
        assertThrows(SerializationException::class.java) { ProtoBuf.decodeFromByteArray<BoardRow>(outOfRange) }
    }

    @Test
    fun recoverySnapshotStoresEntryNamesAsProperties() {
        val row: BoardRow = BoardRow("UA90", FlightStatus.DELAYED, Terminal.SOUTH, previous = FlightStatus.BOARDING)

        val stored: Map<String, String> = Properties.encodeToStringMap(row)

        assertEquals(
            mapOf("flight" to "UA90", "status" to "delayed", "terminal" to "SOUTH", "previous" to "boarding"),
            stored,
        )
        assertEquals(row, Properties.decodeFromStringMap<BoardRow>(stored))
    }
}
