/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:UseSerializers(ReminderCalendarCustomSerializerWiringTest.LocalDateAsIsoSerializer::class)
@file:OptIn(ExperimentalSerializationApi::class)

package org_jetbrains_kotlinx.kotlinx_serialization_core_jvm

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Currency
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises custom serializer wiring for types the application does not own: surrogate
 * serializers that delegate to a generated one, `@Serializable(with)` on properties,
 * file-level `@UseSerializers`, `@KeepGeneratedSerializer` and the built-in `Duration` serializer.
 *
 * A calendar service stores reminders whose fields are `java.time` and `java.util` types, and
 * checks that the same model round-trips through JSON and CBOR with the wire shape it publishes.
 */
public class ReminderCalendarCustomSerializerWiringTest {

    /** Writes a [LocalDate] as its ISO-8601 text; applied file-wide through `@UseSerializers`. */
    public object LocalDateAsIsoSerializer : KSerializer<LocalDate> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("calendar.LocalDate", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: LocalDate) {
            encoder.encodeString(value.toString())
        }

        override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
    }

    /** Wire form of a [ZonedDateTime]: the instant and the region it is shown in. */
    @Serializable
    @SerialName("ZonedDateTimeSurrogate")
    public data class ZonedDateTimeSurrogate(val epochSecond: Long, val zone: String)

    /** Serializes [ZonedDateTime] by delegating to the generated serializer of its surrogate. */
    public object ZonedDateTimeSerializer : KSerializer<ZonedDateTime> {
        override val descriptor: SerialDescriptor =
            SerialDescriptor("calendar.ZonedDateTime", ZonedDateTimeSurrogate.serializer().descriptor)

        override fun serialize(encoder: Encoder, value: ZonedDateTime) {
            val surrogate = ZonedDateTimeSurrogate(value.toEpochSecond(), value.zone.id)
            encoder.encodeSerializableValue(ZonedDateTimeSurrogate.serializer(), surrogate)
        }

        override fun deserialize(decoder: Decoder): ZonedDateTime {
            val surrogate: ZonedDateTimeSurrogate =
                decoder.decodeSerializableValue(ZonedDateTimeSurrogate.serializer())
            return ZonedDateTime.ofInstant(
                Instant.ofEpochSecond(surrogate.epochSecond),
                ZoneId.of(surrogate.zone),
            )
        }
    }

    /** Writes a [Currency] as its ISO 4217 code and rejects codes the JDK does not know. */
    public object CurrencyCodeSerializer : KSerializer<Currency> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("calendar.Currency", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: Currency) {
            encoder.encodeString(value.currencyCode)
        }

        override fun deserialize(decoder: Decoder): Currency {
            val code: String = decoder.decodeString()
            return try {
                Currency.getInstance(code)
            } catch (e: IllegalArgumentException) {
                throw SerializationException("Unknown currency code '$code'", e)
            }
        }
    }

    /** Spending cap of a reminder; compact text by default, object form for the export API. */
    @KeepGeneratedSerializer
    @Serializable(with = BudgetAsTextSerializer::class)
    public data class Budget(
        val minorUnits: Long,
        @Serializable(with = CurrencyCodeSerializer::class)
        val currency: Currency,
    )

    /** Writes a [Budget] as `"<minorUnits> <currency>"`. */
    public object BudgetAsTextSerializer : KSerializer<Budget> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("calendar.Budget", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: Budget) {
            encoder.encodeString("${value.minorUnits} ${value.currency.currencyCode}")
        }

        override fun deserialize(decoder: Decoder): Budget {
            val parts: List<String> = decoder.decodeString().split(' ')
            return Budget(parts[0].toLong(), Currency.getInstance(parts[1]))
        }
    }

    /** A calendar reminder as the service stores and publishes it. */
    @Serializable
    public data class Reminder(
        val title: String,
        @Serializable(with = ZonedDateTimeSerializer::class)
        val due: ZonedDateTime,
        val snooze: Duration,
        val skipDates: List<LocalDate> = emptyList(),
        val budget: Budget? = null,
    )

    private val json: Json = Json { encodeDefaults = false }

    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")

    private val standup = Reminder(
        title = "Team stand-up",
        due = ZonedDateTime.of(2026, 3, 30, 9, 30, 0, 0, berlin),
        snooze = 15.minutes,
        skipDates = listOf(LocalDate.of(2026, 4, 3), LocalDate.of(2026, 4, 6)),
    )

    private val dinner = Reminder(
        title = "Client dinner",
        due = ZonedDateTime.of(2026, 4, 2, 19, 0, 0, 0, ZoneId.of("Asia/Tokyo")),
        snooze = Duration.parse("1h 30m"),
        budget = Budget(25_000, Currency.getInstance("JPY")),
    )

    @Test
    fun remindersAreWrittenInThePublishedJsonShape() {
        val encoded: String = json.encodeToString(Reminder.serializer(), dinner)

        val expected: String = """
            {"title":"Client dinner",
            "due":{"epochSecond":${dinner.due.toEpochSecond()},"zone":"Asia/Tokyo"},
            "snooze":"PT1H30M",
            "budget":"25000 JPY"}
        """.trimIndent().replace("\n", "")
        assertEquals(expected, encoded)
        assertEquals(dinner, json.decodeFromString(Reminder.serializer(), encoded))

        val standupJson: String = json.encodeToString(Reminder.serializer(), standup)
        assertTrue(standupJson.contains(""""skipDates":["2026-04-03","2026-04-06"]"""), standupJson)
        assertTrue(standupJson.contains(""""snooze":"PT15M""""), standupJson)
    }

    @Test
    fun surrogateRoundTripPreservesZoneAcrossDaylightSavingChange() {
        val beforeSwitch: ZonedDateTime = ZonedDateTime.of(2026, 3, 28, 9, 30, 0, 0, berlin)
        val reminders: List<Reminder> = listOf(standup.copy(due = beforeSwitch), standup)
        val listSerializer: KSerializer<List<Reminder>> = ListSerializer(Reminder.serializer())

        val decoded: List<Reminder> = json.decodeFromString(listSerializer, json.encodeToString(listSerializer, reminders))

        assertEquals(reminders, decoded)
        assertEquals(9, decoded[0].due.hour)
        assertEquals(9, decoded[1].due.hour)
        assertEquals(3600, decoded[0].due.offset.totalSeconds)
        assertEquals(7200, decoded[1].due.offset.totalSeconds)
    }

    @Test
    fun customSerializersAreFormatIndependent() {
        val cbor: Cbor = Cbor { }
        val reminders: List<Reminder> = listOf(standup, dinner)

        val bytes: ByteArray = cbor.encodeToByteArray(reminders)
        val decoded: List<Reminder> = cbor.decodeFromByteArray(bytes)

        assertEquals(reminders, decoded)
        assertEquals(Currency.getInstance("JPY"), decoded[1].budget?.currency)
        assertEquals(90.minutes, decoded[1].snooze)
    }

    @Test
    fun keptGeneratedSerializerServesTheExportApi() {
        val budget = Budget(1250, Currency.getInstance("EUR"))

        assertEquals("\"1250 EUR\"", json.encodeToString(Budget.serializer(), budget))
        val exported: String = json.encodeToString(Budget.generatedSerializer(), budget)
        assertEquals("""{"minorUnits":1250,"currency":"EUR"}""", exported)
        assertEquals(budget, json.decodeFromString(Budget.generatedSerializer(), exported))

        val unknownCurrency = """{"minorUnits":1250,"currency":"ZZZ"}"""
        val failure: SerializationException = assertThrows(SerializationException::class.java) {
            json.decodeFromString(Budget.generatedSerializer(), unknownCurrency)
        }
        assertTrue(failure.message!!.contains("ZZZ"), failure.message)
    }

    @Test
    fun descriptorsReflectTheCustomWireShapes() {
        val descriptor: SerialDescriptor = Reminder.serializer().descriptor

        val due: SerialDescriptor = descriptor.getElementDescriptor(descriptor.getElementIndex("due"))
        assertEquals("calendar.ZonedDateTime", due.serialName)
        assertEquals(StructureKind.CLASS, due.kind)
        assertEquals(listOf("epochSecond", "zone"), due.elementNames.toList())

        val skipDates: SerialDescriptor =
            descriptor.getElementDescriptor(descriptor.getElementIndex("skipDates"))
        assertEquals(StructureKind.LIST, skipDates.kind)
        assertEquals("calendar.LocalDate", skipDates.getElementDescriptor(0).serialName)

        val snooze: SerialDescriptor = descriptor.getElementDescriptor(descriptor.getElementIndex("snooze"))
        assertEquals(PrimitiveKind.STRING, snooze.kind)

        val budget: SerialDescriptor = descriptor.getElementDescriptor(descriptor.getElementIndex("budget"))
        assertEquals("calendar.Budget?", budget.serialName)
        assertTrue(budget.isNullable)
        assertEquals(StructureKind.CLASS, Budget.generatedSerializer().descriptor.kind)
    }
}
