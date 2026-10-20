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
import com.squareup.moshi.Types
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises platform-type handling around `KotlinJsonAdapterFactory`: Kotlin data classes whose
 * properties are Kotlin stdlib and JDK platform types (`Pair`, `Triple`, `Instant`, `UUID`,
 * `BigDecimal`) are refused until the application registers explicit adapters, and bind once a
 * generic tuple factory and per-type adapters are installed, including as map keys.
 *
 * A travel agency exports booked itineraries to a partner and re-imports the partner's edits.
 */
@OptIn(ExperimentalStdlibApi::class)
public class FlightItineraryPlatformTypeAdapterTest {

    /** One flight segment of an itinerary. */
    public data class Segment(
        @Json(name = "flight") val flightNumber: String,
        val route: Pair<String, String>,
        val departure: Instant,
        val arrival: Instant,
        @Json(name = "seat") val seatRowAndLetter: Pair<Int, String>? = null,
    )

    /** A booked itinerary with fares per passenger. */
    public data class Itinerary(
        val id: UUID,
        val segments: List<Segment>,
        val fares: Map<UUID, BigDecimal>,
        @Json(name = "baggage") val baggageAllowance: Triple<Int, Int, String>,
        val total: BigDecimal,
        val notes: String? = null,
    )

    /** Encodes an `Instant` as its ISO-8601 text. */
    public class InstantAdapter : JsonAdapter<Instant>() {
        override fun fromJson(reader: JsonReader): Instant = Instant.parse(reader.nextString())

        override fun toJson(writer: JsonWriter, value: Instant?) {
            writer.value(value!!.toString())
        }
    }

    /** Encodes a `UUID` as its canonical text. */
    public class UuidAdapter : JsonAdapter<UUID>() {
        override fun fromJson(reader: JsonReader): UUID = UUID.fromString(reader.nextString())

        override fun toJson(writer: JsonWriter, value: UUID?) {
            writer.value(value!!.toString())
        }
    }

    /** Encodes money as a string so no precision is lost to doubles. */
    public class MoneyAdapter : JsonAdapter<BigDecimal>() {
        override fun fromJson(reader: JsonReader): BigDecimal {
            val text: String = reader.nextString()
            return text.toBigDecimalOrNull()
                ?: throw JsonDataException(
                    "Expected a decimal amount but was $text at path ${reader.path}",
                )
        }

        override fun toJson(writer: JsonWriter, value: BigDecimal?) {
            writer.value(value!!.toPlainString())
        }
    }

    /** Encodes a `Pair` or `Triple` as a fixed-length JSON array of its components. */
    public object TupleAdapterFactory : JsonAdapter.Factory {
        override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
            if (annotations.isNotEmpty() || type !is ParameterizedType) {
                return null
            }
            val rawType: Class<*> = Types.getRawType(type)
            if (rawType != Pair::class.java && rawType != Triple::class.java) {
                return null
            }
            val componentAdapters: List<JsonAdapter<Any?>> =
                type.actualTypeArguments.map { argument: Type -> moshi.adapter<Any?>(argument) }
            return TupleAdapter(rawType == Triple::class.java, componentAdapters).nullSafe()
        }
    }

    /** Reads and writes tuple components in declaration order. */
    public class TupleAdapter(
        private val triple: Boolean,
        private val componentAdapters: List<JsonAdapter<Any?>>,
    ) : JsonAdapter<Any>() {
        override fun fromJson(reader: JsonReader): Any {
            reader.beginArray()
            val components: MutableList<Any?> = mutableListOf()
            for (adapter: JsonAdapter<Any?> in componentAdapters) {
                if (!reader.hasNext()) {
                    throw JsonDataException(
                        "Expected ${componentAdapters.size} tuple components at path ${reader.path}",
                    )
                }
                components.add(adapter.fromJson(reader))
            }
            if (reader.hasNext()) {
                throw JsonDataException(
                    "Expected ${componentAdapters.size} tuple components at path ${reader.path}",
                )
            }
            reader.endArray()
            return if (triple) {
                Triple(components[0], components[1], components[2])
            } else {
                Pair(components[0], components[1])
            }
        }

        override fun toJson(writer: JsonWriter, value: Any?) {
            val components: List<Any?> = when (value) {
                is Pair<*, *> -> value.toList()
                is Triple<*, *, *> -> value.toList()
                else -> throw IllegalArgumentException("Not a tuple: $value")
            }
            writer.beginArray()
            componentAdapters.zip(components).forEach { (adapter: JsonAdapter<Any?>, component: Any?) ->
                adapter.toJson(writer, component)
            }
            writer.endArray()
        }
    }

    private val moshi: Moshi = Moshi.Builder()
        .add(TupleAdapterFactory)
        .add(Instant::class.java, InstantAdapter().nullSafe())
        .add(UUID::class.java, UuidAdapter().nullSafe())
        .add(BigDecimal::class.java, MoneyAdapter().nullSafe())
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val alice: UUID = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e")
    private val bob: UUID = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7")

    private val itinerary: Itinerary = Itinerary(
        id = UUID.fromString("3b241101-e2bb-4255-8caf-4136c566a962"),
        segments = listOf(
            Segment(
                flightNumber = "LH401",
                route = "FRA" to "JFK",
                departure = Instant.parse("2026-11-02T10:15:00Z"),
                arrival = Instant.parse("2026-11-02T18:40:00Z"),
                seatRowAndLetter = 23 to "C",
            ),
            Segment(
                flightNumber = "UA88",
                route = "JFK" to "SFO",
                departure = Instant.parse("2026-11-03T14:05:00Z"),
                arrival = Instant.parse("2026-11-03T20:30:00Z"),
            ),
        ),
        fares = linkedMapOf(alice to BigDecimal("612.40"), bob to BigDecimal("587.15")),
        baggageAllowance = Triple(2, 23, "kg"),
        total = BigDecimal("1199.55"),
    )

    @Test
    fun exportsItineraryWithPlatformTypesThroughRegisteredAdapters() {
        val json: String = moshi.adapter<Itinerary>().toJson(itinerary)

        assertEquals(
            """{"id":"3b241101-e2bb-4255-8caf-4136c566a962","segments":[""" +
                """{"flight":"LH401","route":["FRA","JFK"],"departure":"2026-11-02T10:15:00Z",""" +
                """"arrival":"2026-11-02T18:40:00Z","seat":[23,"C"]},""" +
                """{"flight":"UA88","route":["JFK","SFO"],"departure":"2026-11-03T14:05:00Z",""" +
                """"arrival":"2026-11-03T20:30:00Z"}],""" +
                """"fares":{"0f8fad5b-d9cb-469f-a165-70867728950e":"612.40",""" +
                """"7c9e6679-7425-40de-944b-e07fc1f90ae7":"587.15"},""" +
                """"baggage":[2,23,"kg"],"total":"1199.55"}""",
            json,
        )
        assertEquals(itinerary, moshi.adapter<Itinerary>().fromJson(json))
    }

    @Test
    fun importsPartnerEditsAndKeepsDecimalPrecision() {
        val partnerJson: String = """
            {
              "id": "3b241101-e2bb-4255-8caf-4136c566a962",
              "segments": [
                {
                  "flight": "LH401",
                  "route": ["FRA", "EWR"],
                  "departure": "2026-11-02T10:15:00Z",
                  "arrival": "2026-11-02T18:55:00Z",
                  "seat": [24, "A"]
                }
              ],
              "fares": {"0f8fad5b-d9cb-469f-a165-70867728950e": "0.10"},
              "baggage": [1, 8, "kg"],
              "total": "0.10",
              "notes": "rebooked after schedule change"
            }
        """.trimIndent()

        val edited: Itinerary = moshi.adapter<Itinerary>().fromJson(partnerJson)!!

        val segment: Segment = edited.segments.single()
        assertEquals("FRA" to "EWR", segment.route)
        assertEquals(24 to "A", segment.seatRowAndLetter)
        val flightMinutes: Long = Duration.between(segment.departure, segment.arrival).toMinutes()
        assertEquals(8L * 60 + 40, flightMinutes)
        assertEquals(mapOf(alice to BigDecimal("0.10")), edited.fares)
        assertEquals(BigDecimal("0.10"), edited.total)
        assertEquals(2, edited.total.scale())
        assertEquals(Triple(1, 8, "kg"), edited.baggageAllowance)
        assertEquals("rebooked after schedule change", edited.notes)
    }

    @Test
    fun bindsTopLevelTuplesThroughKotlinTypeTokens() {
        val layover: JsonAdapter<Pair<String, Instant>> = moshi.adapter<Pair<String, Instant>>()
        val stop: Pair<String, Instant> = "JFK" to Instant.parse("2026-11-03T12:00:00Z")

        assertEquals("""["JFK","2026-11-03T12:00:00Z"]""", layover.toJson(stop))
        assertEquals(stop, layover.fromJson("""["JFK","2026-11-03T12:00:00Z"]"""))

        val legs: List<Pair<String, String>> = moshi.adapter<List<Pair<String, String>>>()
            .fromJson("""[["FRA","JFK"],["JFK","SFO"]]""")!!
        assertEquals(listOf("FRA" to "JFK", "JFK" to "SFO"), legs)
    }

    @Test
    fun rejectsMalformedPartnerValuesWithJsonPaths() {
        val shortRoute: JsonDataException = assertThrows(JsonDataException::class.java) {
            moshi.adapter<Segment>().fromJson(
                """{"flight":"LH401","route":["FRA"],"departure":"2026-11-02T10:15:00Z",""" +
                    """"arrival":"2026-11-02T18:40:00Z"}""",
            )
        }
        assertEquals("Expected 2 tuple components at path $.route[1]", shortRoute.message)

        val badTotal: String = moshi.adapter<Itinerary>().toJson(itinerary)
            .replace(""""total":"1199.55"""", """"total":"about 1200"""")
        val money: JsonDataException = assertThrows(JsonDataException::class.java) {
            moshi.adapter<Itinerary>().fromJson(badTotal)
        }
        assertEquals("Expected a decimal amount but was about 1200 at path $.total", money.message)
    }

    @Test
    fun refusesPlatformTypesWithoutExplicitAdapters() {
        val bare: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

        val instant: IllegalArgumentException = assertThrows(IllegalArgumentException::class.java) {
            bare.adapter<Segment>()
        }
        assertTrue(
            instant.message!!.startsWith("Platform class java.time.Instant requires explicit JsonAdapter"),
            instant.message,
        )
        assertTrue(instant.message!!.contains("for class ${Segment::class.java.name}"), instant.message)

        val pair: IllegalArgumentException = assertThrows(IllegalArgumentException::class.java) {
            bare.adapter<Pair<String, String>>()
        }
        assertEquals(
            "Platform class kotlin.Pair in kotlin.Pair<java.lang.String, java.lang.String> " +
                "requires explicit JsonAdapter to be registered",
            pair.message,
        )

        val withTimeOnly: Moshi = bare.newBuilder()
            .add(Instant::class.java, InstantAdapter().nullSafe())
            .add(TupleAdapterFactory)
            .build()
        val uuid: IllegalArgumentException = assertThrows(IllegalArgumentException::class.java) {
            withTimeOnly.adapter<Itinerary>()
        }
        assertTrue(
            uuid.message!!.startsWith("Platform class java.util.UUID requires explicit JsonAdapter"),
            uuid.message,
        )
        val segmentJson: String = moshi.adapter<Segment>().toJson(itinerary.segments[1])
        assertEquals(itinerary.segments[1], withTimeOnly.adapter<Segment>().fromJson(segmentJson))
    }
}
