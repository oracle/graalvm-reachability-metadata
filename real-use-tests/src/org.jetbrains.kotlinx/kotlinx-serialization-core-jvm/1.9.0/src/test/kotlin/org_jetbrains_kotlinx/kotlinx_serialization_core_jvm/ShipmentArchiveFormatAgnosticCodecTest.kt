/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_core_jvm

import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.StringFormat
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.properties.Properties
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises format-agnostic application code over the `SerialFormat`, `StringFormat` and
 * `BinaryFormat` contracts: one codec layer swapping JSON, CBOR and ProtoBuf, reified format
 * extensions resolving contextual types through each format's `serializersModule`, the hex-string
 * helpers of `BinaryFormat`, and the `SerializationException` subtypes every format reports for
 * rows it cannot map.
 *
 * A parcel carrier archives delivered shipments in H2: each archive table is configured with a
 * serial format, text formats land in a character column and binary formats in a binary column,
 * and an audit trail keeps binary snapshots as hex strings.
 */
@OptIn(ExperimentalSerializationApi::class)
public class ShipmentArchiveFormatAgnosticCodecTest {

    /** Amount in minor units; a plain class serialized only through a contextual serializer. */
    public data class Money(val minorUnits: Long, val currency: String)

    /** Writes money as `"EUR 12.50"`, the form every archive format stores. */
    public object MoneySerializer : KSerializer<Money> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("carrier.Money", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: Money) {
            val major: Long = value.minorUnits / 100
            val minor: Long = value.minorUnits % 100
            encoder.encodeString("${value.currency} $major.${minor.toString().padStart(2, '0')}")
        }

        override fun deserialize(decoder: Decoder): Money {
            val text: String = decoder.decodeString()
            val match: MatchResult = Regex("([A-Z]{3}) ([0-9]+)\\.([0-9]{2})").matchEntire(text)
                ?: throw SerializationException("Malformed amount '$text'")
            val (currency: String, major: String, minor: String) = match.destructured
            return Money(major.toLong() * 100 + minor.toLong(), currency)
        }
    }

    /** Delivery outcome of an archived shipment. */
    @Serializable
    public enum class Outcome {
        @SerialName("delivered") DELIVERED,
        @SerialName("returned") RETURNED,
    }

    /** One parcel of a shipment. */
    @Serializable
    public data class Parcel(val weightGrams: Int, val fragile: Boolean = false)

    /** Shipment as the carrier archives it once delivery has finished. */
    @Serializable
    public data class ShipmentRecord(
        val trackingNumber: String,
        val outcome: Outcome,
        val parcels: List<Parcel>,
        @Contextual val charge: Money,
        val labels: Map<String, String> = emptyMap(),
        val signedBy: String? = null,
    )

    /** Snapshot shape written before charges were archived; rows in it lack the charge. */
    @Serializable
    public data class LegacySnapshot(val trackingNumber: String, val outcome: Outcome, val parcels: List<Parcel>)

    /** Body of an archive row: the column a document lands in depends on the format's contract. */
    public sealed interface StoredBody {
        public data class Text(val text: String) : StoredBody

        public class Binary(public val bytes: ByteArray) : StoredBody
    }

    /** Encodes documents with whatever format a table is configured with. */
    public class ArchiveCodec(public val format: SerialFormat) {
        init {
            require(format is StringFormat || format is BinaryFormat) {
                "Archive cannot store documents in ${format::class.simpleName}"
            }
        }

        public fun <T> encode(serializer: KSerializer<T>, value: T): StoredBody = when (format) {
            is StringFormat -> StoredBody.Text(format.encodeToString(serializer, value))
            is BinaryFormat -> StoredBody.Binary(format.encodeToByteArray(serializer, value))
            else -> error("unreachable")
        }

        public fun <T> decode(serializer: KSerializer<T>, body: StoredBody): T = when (body) {
            is StoredBody.Text -> (format as StringFormat).decodeFromString(serializer, body.text)
            is StoredBody.Binary -> (format as BinaryFormat).decodeFromByteArray(serializer, body.bytes)
        }

        /** Resolves the serializer of [T] through the format's module, as the reified extensions do. */
        public inline fun <reified T> serializerOf(): KSerializer<T> = format.serializersModule.serializer<T>()
    }

    /** Archive table backed by H2; one row per shipment. */
    public class ShipmentArchive(private val connection: Connection, private val table: String, format: SerialFormat) {
        public val codec: ArchiveCodec = ArchiveCodec(format)

        init {
            connection.createStatement().use {
                it.execute("CREATE TABLE $table (id VARCHAR(32) PRIMARY KEY, text_body CLOB, binary_body VARBINARY(4096))")
            }
        }

        public fun put(record: ShipmentRecord) {
            val body: StoredBody = codec.encode(codec.serializerOf<ShipmentRecord>(), record)
            connection.prepareStatement("INSERT INTO $table VALUES (?, ?, ?)").use {
                it.setString(1, record.trackingNumber)
                it.setString(2, (body as? StoredBody.Text)?.text)
                it.setBytes(3, (body as? StoredBody.Binary)?.bytes)
                it.executeUpdate()
            }
        }

        public fun body(trackingNumber: String): StoredBody =
            connection.prepareStatement("SELECT text_body, binary_body FROM $table WHERE id = ?").use {
                it.setString(1, trackingNumber)
                it.executeQuery().use { rows: ResultSet ->
                    check(rows.next()) { "No archived shipment $trackingNumber" }
                    rows.getString(1)?.let { text: String -> StoredBody.Text(text) }
                        ?: StoredBody.Binary(rows.getBytes(2))
                }
            }

        public fun get(trackingNumber: String): ShipmentRecord =
            codec.decode(codec.serializerOf<ShipmentRecord>(), body(trackingNumber))

        public fun overwrite(trackingNumber: String, body: StoredBody) {
            connection.prepareStatement("UPDATE $table SET text_body = ?, binary_body = ? WHERE id = ?").use {
                it.setString(1, (body as? StoredBody.Text)?.text)
                it.setBytes(2, (body as? StoredBody.Binary)?.bytes)
                it.setString(3, trackingNumber)
                it.executeUpdate()
            }
        }
    }

    private val carrierModule: SerializersModule = SerializersModule { contextual(MoneySerializer) }

    private val json: Json = Json { serializersModule = carrierModule }

    private val cbor: Cbor = Cbor { serializersModule = carrierModule }

    private val protoBuf: ProtoBuf = ProtoBuf { serializersModule = carrierModule }

    private val delivered: ShipmentRecord = ShipmentRecord(
        trackingNumber = "TRK-1001",
        outcome = Outcome.DELIVERED,
        parcels = listOf(Parcel(3_500), Parcel(800, fragile = true)),
        charge = Money(1_250, "EUR"),
        labels = mapOf("service" to "express", "zone" to "DE-2"),
        signedBy = "K. Weber",
    )

    private val returned: ShipmentRecord = ShipmentRecord(
        trackingNumber = "TRK-1002",
        outcome = Outcome.RETURNED,
        parcels = listOf(Parcel(1_200)),
        charge = Money(705, "CHF"),
    )

    private fun <R> withDatabase(block: (Connection) -> R): R {
        val url = "jdbc:h2:mem:shipment-archive-${System.nanoTime()};DB_CLOSE_DELAY=0"
        return DriverManager.getConnection(url, "sa", "").use(block)
    }

    @Test
    fun everyFormatArchivesShipmentsInTheColumnItsContractSelects() {
        withDatabase { connection: Connection ->
            val archives: Map<String, ShipmentArchive> = linkedMapOf(
                "json" to ShipmentArchive(connection, "archive_json", json),
                "cbor" to ShipmentArchive(connection, "archive_cbor", cbor),
                "proto" to ShipmentArchive(connection, "archive_proto", protoBuf),
            )
            archives.values.forEach { archive: ShipmentArchive ->
                archive.put(delivered)
                archive.put(returned)
            }

            val jsonBody: StoredBody = archives.getValue("json").body("TRK-1002")
            assertEquals(
                StoredBody.Text(
                    """{"trackingNumber":"TRK-1002","outcome":"returned","parcels":[{"weightGrams":1200}],"charge":"CHF 7.05"}""",
                ),
                jsonBody,
            )
            val cborBody: StoredBody = archives.getValue("cbor").body("TRK-1001")
            val protoBody: StoredBody = archives.getValue("proto").body("TRK-1001")
            assertArrayEquals(cbor.encodeToByteArray(delivered), (cborBody as StoredBody.Binary).bytes)
            assertArrayEquals(protoBuf.encodeToByteArray(delivered), (protoBody as StoredBody.Binary).bytes)
            assertTrue(protoBody.bytes.size < json.encodeToString(delivered).length)

            archives.forEach { (name: String, archive: ShipmentArchive) ->
                assertEquals(delivered, archive.get("TRK-1001"), name)
                assertEquals(returned, archive.get("TRK-1002"), name)
                assertNull(archive.get("TRK-1002").signedBy, name)
            }
        }
    }

    @Test
    fun reifiedFormatExtensionsResolveContextualTypesThroughTheFormatModule() {
        val formats: List<SerialFormat> = listOf(json, cbor, protoBuf)
        formats.forEach { format: SerialFormat ->
            assertSame(MoneySerializer, format.serializersModule.getContextual(Money::class))
            assertSame(MoneySerializer, ArchiveCodec(format).serializerOf<Money>())
        }

        val document: String = json.encodeToString(delivered)
        assertTrue(document.contains(""""charge":"EUR 12.50""""), document)
        assertEquals(delivered, json.decodeFromString<ShipmentRecord>(document))
        assertEquals(delivered, cbor.decodeFromByteArray<ShipmentRecord>(cbor.encodeToByteArray(delivered)))
        assertEquals(Money(99, "USD"), protoBuf.decodeFromByteArray<Money>(protoBuf.encodeToByteArray(Money(99, "USD"))))

        val withoutModule: SerializationException = assertThrows(SerializationException::class.java) {
            Cbor.encodeToByteArray(delivered)
        }
        assertTrue(withoutModule.message!!.contains("Money"), withoutModule.message)
    }

    @Test
    fun auditTrailKeepsBinarySnapshotsAsHexStrings() {
        withDatabase { connection: Connection ->
            connection.createStatement().use {
                it.execute("CREATE TABLE audit (seq INT PRIMARY KEY, format VARCHAR(8), snapshot VARCHAR(4096))")
            }
            val snapshotFormats: List<Pair<String, BinaryFormat>> = listOf("cbor" to cbor, "proto" to protoBuf)
            connection.prepareStatement("INSERT INTO audit VALUES (?, ?, ?)").use { insert ->
                snapshotFormats.forEachIndexed { index: Int, (name: String, format: BinaryFormat) ->
                    insert.setInt(1, index)
                    insert.setString(2, name)
                    insert.setString(3, format.encodeToHexString(delivered))
                    insert.executeUpdate()
                }
            }

            val snapshots: Map<String, String> = connection.createStatement().use { statement ->
                statement.executeQuery("SELECT format, snapshot FROM audit ORDER BY seq").use { rows: ResultSet ->
                    buildMap { while (rows.next()) put(rows.getString(1), rows.getString(2)) }
                }
            }
            assertEquals(listOf("cbor", "proto"), snapshots.keys.toList())
            snapshotFormats.forEach { (name: String, format: BinaryFormat) ->
                val hex: String = snapshots.getValue(name)
                val expected: String = format.encodeToByteArray(delivered).joinToString("") { "%02x".format(it) }
                assertTrue(hex.equals(expected, ignoreCase = true), "$name: $hex")
                assertEquals(delivered, format.decodeFromHexString<ShipmentRecord>(hex), name)
            }
            assertTrue(snapshots.getValue("cbor").startsWith("bf", ignoreCase = true))
        }
    }

    @Test
    fun corruptArchiveRowsFailWithSerializationExceptionInEveryFormat() {
        assertThrows(IllegalArgumentException::class.java) { ArchiveCodec(Properties) }

        withDatabase { connection: Connection ->
            val legacy: LegacySnapshot = LegacySnapshot("TRK-1001", Outcome.DELIVERED, listOf(Parcel(3_500)))
            val formats: List<Pair<String, SerialFormat>> = listOf("json" to json, "cbor" to cbor, "proto" to protoBuf)
            formats.forEach { (name: String, format: SerialFormat) ->
                val archive = ShipmentArchive(connection, "legacy_$name", format)
                archive.put(delivered)
                archive.overwrite("TRK-1001", archive.codec.encode(LegacySnapshot.serializer(), legacy))
                val missing: MissingFieldException =
                    assertThrows(MissingFieldException::class.java) { archive.get("TRK-1001") }
                assertEquals(listOf("charge"), missing.missingFields, name)
            }

            val unknownOutcome = ShipmentArchive(connection, "archive_outcome", json)
            unknownOutcome.put(delivered)
            unknownOutcome.overwrite(
                "TRK-1001",
                StoredBody.Text("""{"trackingNumber":"TRK-1001","outcome":"lost","parcels":[],"charge":"EUR 1.00"}"""),
            )
            val lost: SerializationException =
                assertThrows(SerializationException::class.java) { unknownOutcome.get("TRK-1001") }
            assertTrue(lost.message!!.contains("lost"), lost.message)

            val badAmount = ShipmentArchive(connection, "archive_amount", json)
            badAmount.put(returned)
            badAmount.overwrite(
                "TRK-1002",
                StoredBody.Text("""{"trackingNumber":"TRK-1002","outcome":"returned","parcels":[],"charge":"7 CHF"}"""),
            )
            val malformed: SerializationException =
                assertThrows(SerializationException::class.java) { badAmount.get("TRK-1002") }
            assertTrue(malformed.message!!.contains("Malformed amount '7 CHF'"), malformed.message)
        }
    }
}
