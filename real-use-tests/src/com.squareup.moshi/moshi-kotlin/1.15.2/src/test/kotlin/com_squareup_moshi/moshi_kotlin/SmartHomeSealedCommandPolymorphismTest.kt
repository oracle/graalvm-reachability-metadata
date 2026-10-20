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
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises polymorphic Kotlin sealed-class hierarchies: `PolymorphicJsonAdapterFactory` picks a
 * subtype by a label field and hands each subtype to a `KotlinJsonAdapterFactory` adapter,
 * including recursive hierarchies, sealed-typed properties, lists, and an unknown-label fallback.
 *
 * A smart-home hub keeps a journal of device commands as JSON. Each command kind is a subclass
 * of a sealed interface; routines nest commands inside commands, and the hub must keep reading
 * the journal when a newer firmware writes a command kind it does not know yet.
 */
@OptIn(ExperimentalStdlibApi::class)
public class SmartHomeSealedCommandPolymorphismTest {

    /** Every command the hub can send to a device. */
    public sealed interface DeviceCommand

    /** Switches a device on or off. */
    public data class Power(
        @Json(name = "device_id") val deviceId: String,
        val on: Boolean,
    ) : DeviceCommand

    /** Dims a light to a level, optionally fading over a duration. */
    public data class Dim(
        @Json(name = "device_id") val deviceId: String,
        val level: Int,
        @Json(name = "fade_ms") val fadeMs: Long = 0L,
    ) : DeviceCommand

    /** Sets a thermostat target in a given unit. */
    public data class SetTemperature(
        @Json(name = "device_id") val deviceId: String,
        val target: Double,
        val unit: TemperatureUnit = TemperatureUnit.CELSIUS,
    ) : DeviceCommand

    /** A named routine that runs other commands in order, possibly other routines. */
    public data class Routine(
        val name: String,
        val steps: List<DeviceCommand>,
    ) : DeviceCommand

    /** Placeholder for a command kind written by firmware the hub does not understand yet. */
    public object Unsupported : DeviceCommand

    /** Unit a thermostat target is expressed in. */
    public enum class TemperatureUnit {
        @Json(name = "C")
        CELSIUS,

        @Json(name = "F")
        FAHRENHEIT,
    }

    /** One entry of the hub journal: when a command was issued and by whom. */
    public data class JournalEntry(
        val sequence: Long,
        @Json(name = "issued_by") val issuedBy: String,
        val command: DeviceCommand,
    )

    private val commandFactory: PolymorphicJsonAdapterFactory<DeviceCommand> =
        PolymorphicJsonAdapterFactory.of(DeviceCommand::class.java, "kind")
            .withSubtype(Power::class.java, "power")
            .withSubtype(Dim::class.java, "dim")
            .withSubtype(SetTemperature::class.java, "set_temperature")
            .withSubtype(Routine::class.java, "routine")

    private val moshi: Moshi = Moshi.Builder()
        .add(commandFactory.withDefaultValue(Unsupported))
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val commandAdapter: JsonAdapter<DeviceCommand> = moshi.adapter<DeviceCommand>()

    private val journalAdapter: JsonAdapter<List<JournalEntry>> = moshi.adapter<List<JournalEntry>>()

    /** Describes a command the way the hub's activity screen would show it. */
    private fun describe(command: DeviceCommand): String = when (command) {
        is Power -> "${command.deviceId} ${if (command.on) "on" else "off"}"
        is Dim -> "${command.deviceId} to ${command.level}%"
        is SetTemperature -> "${command.deviceId} to ${command.target}${if (command.unit == TemperatureUnit.CELSIUS) "C" else "F"}"
        is Routine -> "${command.name}: " + command.steps.joinToString(", ") { describe(it) }
        Unsupported -> "unsupported"
    }

    @Test
    fun labelSelectsTheKotlinSubtypeAndItsDefaults() {
        val dim: DeviceCommand = commandAdapter.fromJson("""{"kind":"dim","device_id":"lamp-1","level":40}""")!!
        val heat: DeviceCommand = commandAdapter.fromJson(
            """{"device_id":"thermo","target":68.5,"unit":"F","kind":"set_temperature"}""",
        )!!

        assertEquals(Dim("lamp-1", 40, 0L), dim)
        assertEquals(SetTemperature("thermo", 68.5, TemperatureUnit.FAHRENHEIT), heat)
        assertEquals("thermo to 68.5F", describe(heat))
    }

    @Test
    fun encodingWritesTheLabelFirstThenTheSubtypeProperties() {
        val commands: List<DeviceCommand> = listOf(
            Power("plug-7", on = true),
            Dim("lamp-1", 75, fadeMs = 1500L),
            SetTemperature("thermo", 21.0),
        )

        val json: List<String> = commands.map { commandAdapter.toJson(it) }

        assertEquals(
            listOf(
                """{"kind":"power","device_id":"plug-7","on":true}""",
                """{"kind":"dim","device_id":"lamp-1","level":75,"fade_ms":1500}""",
                """{"kind":"set_temperature","device_id":"thermo","target":21.0,"unit":"C"}""",
            ),
            json,
        )
        assertEquals(commands, json.map { commandAdapter.fromJson(it)!! })
    }

    @Test
    fun nestedRoutinesRoundTripThroughTheSameSealedAdapter() {
        val goodNight: Routine = Routine(
            name = "good night",
            steps = listOf(
                Dim("hall", 10, fadeMs = 3000L),
                Routine("downstairs off", listOf(Power("tv", false), Power("kitchen", false))),
                SetTemperature("thermo", 18.0),
            ),
        )

        val json: String = commandAdapter.toJson(goodNight)
        val restored: DeviceCommand = commandAdapter.fromJson(json)!!

        assertEquals(
            """{"kind":"routine","name":"good night","steps":[""" +
                """{"kind":"dim","device_id":"hall","level":10,"fade_ms":3000},""" +
                """{"kind":"routine","name":"downstairs off","steps":[""" +
                """{"kind":"power","device_id":"tv","on":false},{"kind":"power","device_id":"kitchen","on":false}]},""" +
                """{"kind":"set_temperature","device_id":"thermo","target":18.0,"unit":"C"}]}""",
            json,
        )
        assertEquals(goodNight, restored)
        assertEquals(
            "good night: hall to 10%, downstairs off: tv off, kitchen off, thermo to 18.0C",
            describe(restored),
        )
    }

    @Test
    fun journalDecodesSealedPropertiesAndFallsBackForUnknownKinds() {
        val journal: String = """
            [
              {"sequence":1,"issued_by":"alice","command":{"kind":"power","device_id":"porch","on":true}},
              {"sequence":2,"issued_by":"scheduler","command":{"kind":"open_blinds","device_id":"blinds","percent":80}},
              {"sequence":3,"issued_by":"bob","command":{"kind":"routine","name":"movie",
                "steps":[{"kind":"dim","device_id":"lamp-1","level":5},{"kind":"lock","device_id":"door"}]}}
            ]
        """.trimIndent()

        val entries: List<JournalEntry> = journalAdapter.fromJson(journal)!!

        assertEquals(listOf(1L, 2L, 3L), entries.map { it.sequence })
        assertEquals(listOf("alice", "scheduler", "bob"), entries.map { it.issuedBy })
        assertEquals(Power("porch", true), entries[0].command)
        assertSame(Unsupported, entries[1].command)
        val movie: Routine = entries[2].command as Routine
        assertEquals(Dim("lamp-1", 5), movie.steps[0])
        assertSame(Unsupported, movie.steps[1])
        assertEquals(
            listOf("porch on", "unsupported", "movie: lamp-1 to 5%, unsupported"),
            entries.map { describe(it.command) },
        )
    }

    @Test
    fun strictAdapterWithoutFallbackRejectsUnknownAndMissingLabels() {
        val strict: JsonAdapter<DeviceCommand> = Moshi.Builder()
            .add(commandFactory)
            .addLast(KotlinJsonAdapterFactory())
            .build()
            .adapter<DeviceCommand>()

        val unknown: JsonDataException = assertThrows(JsonDataException::class.java) {
            strict.fromJson("""{"kind":"open_blinds","device_id":"blinds"}""")
        }
        val missing: JsonDataException = assertThrows(JsonDataException::class.java) {
            strict.fromJson("""{"device_id":"plug-7","on":true}""")
        }

        assertEquals(
            "Expected one of [power, dim, set_temperature, routine] for key 'kind' but found 'open_blinds'. " +
                "Register a subtype for this label.",
            unknown.message,
        )
        assertEquals("Missing label for kind", missing.message)
    }

    @Test
    fun subtypeMissingRequiredPropertyFailsWithItsJsonName() {
        val error: JsonDataException = assertThrows(JsonDataException::class.java) {
            commandAdapter.fromJson("""{"kind":"set_temperature","target":20.0}""")
        }

        assertEquals("Required value 'deviceId' (JSON name 'device_id') missing at \$", error.message)
    }

    @Test
    fun sealedTypeWithoutPolymorphicFactoryIsRefusedByTheReflectiveFactory() {
        val plain: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

        val error: IllegalArgumentException = assertThrows(IllegalArgumentException::class.java) {
            plain.adapter<DeviceCommand>()
        }

        assertTrue(error.message!!.contains("DeviceCommand"), error.message)
    }
}
