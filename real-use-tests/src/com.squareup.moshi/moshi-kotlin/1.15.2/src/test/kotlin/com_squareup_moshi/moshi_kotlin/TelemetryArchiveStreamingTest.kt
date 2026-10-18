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
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Exercises streaming and tree-model JSON processing around Kotlin classes: `JsonWriter` and
 * `JsonReader` over okio buffers drive `KotlinJsonAdapterFactory` adapters one element at a time,
 * with `JsonReader.Options` name selection, `peekJson` lookahead, `toJsonValue`/`fromJsonValue`
 * tree migration, and the `serializeNulls`/`indent`/`failOnUnknown` adapter modifiers.
 *
 * A fleet of field sensors uploads telemetry archives: a header object followed by a long array
 * of readings. The ingest service must stream the array without materializing it, dispatch on a
 * record kind before decoding, migrate archives written by older firmware, and reject archives
 * that carry fields it does not understand when running in strict mode.
 */
@OptIn(ExperimentalStdlibApi::class)
public class TelemetryArchiveStreamingTest {

    /** The sensor that produced an archive. */
    public data class Device(
        val serial: String,
        val site: String,
        @Json(name = "firmware") val firmwareVersion: String,
    )

    /** One measurement; a missing battery level means the sensor is mains powered. */
    public data class Reading(
        @Json(name = "t") val epochSecond: Long,
        val celsius: Double,
        val humidity: Int,
        val battery: Int? = null,
    )

    /** A maintenance note interleaved with readings in the archive stream. */
    public data class Annotation(
        @Json(name = "t") val epochSecond: Long,
        val author: String,
        val text: String,
    )

    /** What the ingest service keeps after streaming an archive. */
    public data class ArchiveSummary(
        val device: Device,
        val version: Int,
        val count: Int,
        val minCelsius: Double,
        val maxCelsius: Double,
        val lowBatteryAt: List<Long>,
        val annotations: List<Annotation>,
    )

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val deviceAdapter: JsonAdapter<Device> = moshi.adapter<Device>()
    private val readingAdapter: JsonAdapter<Reading> = moshi.adapter<Reading>()
    private val annotationAdapter: JsonAdapter<Annotation> = moshi.adapter<Annotation>()

    private val headerNames: JsonReader.Options =
        JsonReader.Options.of("format", "version", "device", "records")
    private val kindNames: JsonReader.Options = JsonReader.Options.of("kind")
    private val recordKinds: JsonReader.Options = JsonReader.Options.of("reading", "note")

    private val device: Device = Device("SN-4471", "north-orchard", "3.2.0")

    @Test
    fun streamsLargeArchiveElementByElement(): Unit {
        val buffer = Buffer()
        val readingCount = 500
        JsonWriter.of(buffer).use { writer ->
            writer.beginObject()
            writer.name("format").value("telemetry-archive")
            writer.name("version").value(2)
            writer.name("device")
            deviceAdapter.toJson(writer, device)
            writer.name("diagnostics").beginObject().name("uptime").value(86_400).endObject()
            writer.name("records").beginArray()
            for (i in 0 until readingCount) {
                writer.beginObject().name("kind").value("reading").name("data")
                val battery: Int? = if (i % 100 == 99) 9 else if (i % 2 == 0) 80 else null
                readingAdapter.toJson(
                    writer,
                    Reading(1_700_000_000L + i * 60L, 10.0 + (i % 50) / 2.0, 40 + i % 30, battery),
                )
                writer.endObject()
                if (i == 250) {
                    writer.beginObject().name("kind").value("note").name("data")
                    annotationAdapter.toJson(
                        writer,
                        Annotation(1_700_015_000L, "ops", "probe cleaned"),
                    )
                    writer.endObject()
                }
            }
            writer.endArray()
            writer.endObject()
        }

        val summary: ArchiveSummary = JsonReader.of(buffer).use { readArchive(it) }

        assertEquals(device, summary.device)
        assertEquals(2, summary.version)
        assertEquals(readingCount, summary.count)
        assertEquals(10.0, summary.minCelsius)
        assertEquals(34.5, summary.maxCelsius)
        assertEquals(listOf(1_700_005_940L, 1_700_011_940L, 1_700_017_940L), summary.lowBatteryAt.take(3))
        assertEquals(5, summary.lowBatteryAt.size)
        assertEquals(listOf(Annotation(1_700_015_000L, "ops", "probe cleaned")), summary.annotations)
        assertTrue(buffer.exhausted())
    }

    @Test
    fun peeksRecordKindWithoutConsumingIt(): Unit {
        val json = """
            [
              {"data": {"t": 1700000000, "celsius": 18.5, "humidity": 51}, "kind": "reading"},
              {"kind": "note", "data": {"t": 1700000060, "author": "kim", "text": "door open"}},
              {"kind": "reading", "data": {"t": 1700000120, "celsius": 17.0, "humidity": 55, "battery": 64}}
            ]
        """.trimIndent()

        val records = mutableListOf<Any>()
        JsonReader.of(Buffer().writeUtf8(json)).use { reader ->
            reader.beginArray()
            while (reader.hasNext()) {
                val kind: Int = peekKind(reader)
                records += decodeRecord(reader, kind)
            }
            reader.endArray()
        }

        assertEquals(
            listOf(
                Reading(1_700_000_000L, 18.5, 51),
                Annotation(1_700_000_060L, "kim", "door open"),
                Reading(1_700_000_120L, 17.0, 55, 64),
            ),
            records,
        )
    }

    @Test
    fun migratesLegacyReadingsThroughTheJsonValueTree(): Unit {
        // Firmware 1.x wrote Fahrenheit under "temp_f" and humidity as a fraction.
        val legacy = """
            {"t": 1600000000, "temp_f": 68.0, "rh": 0.42, "battery": 77}
        """.trimIndent()
        val treeAdapter: JsonAdapter<Map<String, Any?>> = moshi.adapter<Map<String, Any?>>()
        val tree: Map<String, Any?> = treeAdapter.fromJson(legacy)!!

        val migrated: Map<String, Any?> = mapOf(
            "t" to tree["t"],
            "celsius" to ((tree["temp_f"] as Double) - 32.0) * 5.0 / 9.0,
            "humidity" to ((tree["rh"] as Double) * 100).toInt(),
            "battery" to tree["battery"],
        )
        val reading: Reading = readingAdapter.fromJsonValue(migrated)!!

        assertEquals(Reading(1_600_000_000L, 20.0, 42, 77), reading)

        @Suppress("UNCHECKED_CAST")
        val roundTrip = readingAdapter.toJsonValue(reading) as Map<String, Any?>
        assertEquals(setOf("t", "celsius", "humidity", "battery"), roundTrip.keys)
        assertEquals(1_600_000_000L, roundTrip["t"])
        assertEquals(20.0, roundTrip["celsius"])
    }

    @Test
    fun appliesAdapterModifiersForExportAndStrictIngest(): Unit {
        val mainsPowered = Reading(1_700_000_000L, 21.25, 48)

        assertEquals(
            """{"t":1700000000,"celsius":21.25,"humidity":48}""",
            readingAdapter.toJson(mainsPowered),
        )
        assertEquals(
            """{"t":1700000000,"celsius":21.25,"humidity":48,"battery":null}""",
            readingAdapter.serializeNulls().toJson(mainsPowered),
        )
        assertEquals(
            """
            {
              "serial": "SN-4471",
              "site": "north-orchard",
              "firmware": "3.2.0"
            }
            """.trimIndent(),
            deviceAdapter.indent("  ").toJson(device),
        )

        val withExtra = """{"t": 1700000000, "celsius": 21.25, "humidity": 48, "pressure": 1013}"""
        assertEquals(mainsPowered, readingAdapter.fromJson(withExtra))
        val strict: JsonAdapter<List<Reading>> = moshi.adapter<List<Reading>>().failOnUnknown()
        val error = assertThrows<JsonDataException> {
            strict.fromJson("[${readingAdapter.toJson(mainsPowered)}, $withExtra]")
        }
        assertEquals("Cannot skip unexpected NAME at $[1].pressure", error.message)

        val nullBattery = """{"t": 1700000000, "celsius": 21.25, "humidity": 48, "battery": null}"""
        assertNull(readingAdapter.fromJson(nullBattery)!!.battery)
        val missingHumidity = assertThrows<JsonDataException> {
            readingAdapter.fromJson("""{"t": 1700000000, "celsius": 21.25}""")
        }
        assertEquals("Required value 'humidity' missing at $", missingHumidity.message)
    }

    /** Streams an archive header and its records, keeping only aggregates in memory. */
    private fun readArchive(reader: JsonReader): ArchiveSummary {
        var archiveDevice: Device? = null
        var version = 0
        var count = 0
        var minCelsius = Double.MAX_VALUE
        var maxCelsius = -Double.MAX_VALUE
        val lowBatteryAt = mutableListOf<Long>()
        val annotations = mutableListOf<Annotation>()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.selectName(headerNames)) {
                0 -> assertEquals("telemetry-archive", reader.nextString())
                1 -> version = reader.nextInt()
                2 -> archiveDevice = deviceAdapter.fromJson(reader)
                3 -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        when (val record = decodeRecord(reader, peekKind(reader))) {
                            is Reading -> {
                                count++
                                minCelsius = minOf(minCelsius, record.celsius)
                                maxCelsius = maxOf(maxCelsius, record.celsius)
                                if (record.battery != null && record.battery < 10) {
                                    lowBatteryAt += record.epochSecond
                                }
                            }
                            is Annotation -> annotations += record
                        }
                    }
                    reader.endArray()
                }
                else -> {
                    reader.skipName()
                    reader.skipValue()
                }
            }
        }
        reader.endObject()
        return ArchiveSummary(
            checkNotNull(archiveDevice),
            version,
            count,
            minCelsius,
            maxCelsius,
            lowBatteryAt,
            annotations,
        )
    }

    /** Looks ahead at the record's `kind` field; the reader stays positioned before the record. */
    private fun peekKind(reader: JsonReader): Int = reader.peekJson().use { peek ->
        peek.beginObject()
        var kind = -1
        while (peek.hasNext()) {
            if (peek.selectName(kindNames) == 0) {
                kind = peek.selectString(recordKinds)
                break
            }
            peek.skipName()
            peek.skipValue()
        }
        check(kind >= 0) { "record without a known kind at ${reader.path}" }
        kind
    }

    /** Decodes the `data` field of one record envelope with the adapter for its kind. */
    private fun decodeRecord(reader: JsonReader, kind: Int): Any {
        var record: Any? = null
        reader.beginObject()
        while (reader.hasNext()) {
            if (reader.nextName() == "data") {
                record = if (kind == 0) readingAdapter.fromJson(reader) else annotationAdapter.fromJson(reader)
            } else {
                reader.skipValue()
            }
        }
        reader.endObject()
        return checkNotNull(record)
    }
}
