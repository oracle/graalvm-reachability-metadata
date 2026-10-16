/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_module.jackson_module_kotlin

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIdentityInfo
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.annotation.JsonRawValue
import com.fasterxml.jackson.annotation.ObjectIdGenerators
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.databind.annotation.JsonSerialize
import tools.jackson.databind.cfg.EnumFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.util.StdConverter
import tools.jackson.module.kotlin.jsonMapper
import tools.jackson.module.kotlin.kotlinModule
import tools.jackson.module.kotlin.readValue

/**
 * Annotation-driven property output of Kotlin data classes: @JsonInclude suppression rules,
 * @JsonFormat date patterns, @JsonSerialize/@JsonDeserialize converters on creator parameters,
 * @JsonIdentityInfo object references, @JsonRawValue, and @JsonProperty/@JsonEnumDefaultValue
 * on Kotlin enum entries, as a flight-booking service stores and reloads itineraries.
 */
public class FlightBookingPropertyAnnotationsTest {

    enum class FareClass {
        @JsonProperty("Y")
        ECONOMY,

        @JsonProperty("J")
        BUSINESS,

        @JsonEnumDefaultValue
        UNKNOWN,
    }

    /** Writes cents as a decimal string such as "412.50" and reads it back. */
    class CentsToDecimal : StdConverter<Long, String>() {
        override fun convert(value: Long): String = "%d.%02d".format(value / 100, value % 100)
    }

    class DecimalToCents : StdConverter<String, Long>() {
        override fun convert(value: String): Long {
            val (whole, fraction) = value.split('.')
            return whole.toLong() * 100 + fraction.padEnd(2, '0').toLong()
        }
    }

    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator::class, property = "code")
    data class Airport(val code: String, val city: String)

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    data class Leg(
        val flight: String,
        val from: Airport,
        val to: Airport,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
        val departure: LocalDateTime,
        val fare: FareClass,
        val seat: String? = null,
        val notes: List<String> = emptyList(),
    )

    @JsonPropertyOrder("reference", "airports", "legs")
    data class Booking(
        val reference: String,
        val airports: List<Airport>,
        val legs: List<Leg>,
        @JsonSerialize(converter = CentsToDecimal::class)
        @JsonDeserialize(converter = DecimalToCents::class)
        val totalCents: Long,
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        val loyaltyPoints: Int = 0,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        val promoCode: String? = null,
    )

    /** Pre-rendered partner payload that the service forwards verbatim. */
    data class PartnerEnvelope(val partner: String, @JsonRawValue val payload: String)

    private val mapper: JsonMapper = jsonMapper {
        addModule(kotlinModule())
        enable(EnumFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
    }

    private val amsterdam: Airport = Airport("AMS", "Amsterdam")
    private val lisbon: Airport = Airport("LIS", "Lisbon")
    private val madeira: Airport = Airport("FNC", "Funchal")

    private fun itinerary(): Booking = Booking(
        reference = "BK-7Q2",
        airports = listOf(amsterdam, lisbon, madeira),
        legs = listOf(
            Leg("TP669", amsterdam, lisbon, LocalDateTime.of(2026, 11, 3, 6, 5), FareClass.BUSINESS, seat = "2A"),
            Leg(
                "TP1691",
                lisbon,
                madeira,
                LocalDateTime.of(2026, 11, 3, 12, 40),
                FareClass.ECONOMY,
                notes = listOf("tight connection"),
            ),
        ),
        totalCents = 41250,
    )

    @Test
    fun writesReferencesFormatsAndSuppressedPropertiesTheWayTheStoreExpects(): Unit {
        val json: String = mapper.writeValueAsString(itinerary())

        val tree = mapper.readTree(json)
        assertThat(tree.propertyNames().asSequence().toList())
            .containsExactly("reference", "airports", "legs", "totalCents")
        assertThat(tree["totalCents"].asString()).isEqualTo("412.50")
        assertThat(tree["airports"][0]["city"].asString()).isEqualTo("Amsterdam")

        val first = tree["legs"][0]
        assertThat(first["from"].asString()).isEqualTo("AMS")
        assertThat(first["to"].asString()).isEqualTo("LIS")
        assertThat(first["departure"].asString()).isEqualTo("2026-11-03 06:05")
        assertThat(first["fare"].asString()).isEqualTo("J")
        assertThat(first["seat"].asString()).isEqualTo("2A")
        assertThat(first.has("notes")).isFalse()

        val second = tree["legs"][1]
        assertThat(second["fare"].asString()).isEqualTo("Y")
        assertThat(second.has("seat")).isFalse()
        assertThat(second["notes"][0].asString()).isEqualTo("tight connection")
    }

    @Test
    fun keepsLoyaltyPointsAndPromoCodeOnceTheyCarryValues(): Unit {
        val json: String = mapper.writeValueAsString(itinerary().copy(loyaltyPoints = 1200, promoCode = "AUTUMN"))

        val tree = mapper.readTree(json)
        assertThat(tree["loyaltyPoints"].asInt()).isEqualTo(1200)
        assertThat(tree["promoCode"].asString()).isEqualTo("AUTUMN")
    }

    @Test
    fun reloadsAnItineraryWithSharedAirportInstancesAndConvertedTotals(): Unit {
        val stored: Booking = itinerary()
        val reloaded: Booking = mapper.readValue(mapper.writeValueAsString(stored))

        assertThat(reloaded).isEqualTo(stored)
        assertThat(reloaded.totalCents).isEqualTo(41250L)
        assertThat(reloaded.legs[0].to).isSameAs(reloaded.legs[1].from)
        assertThat(reloaded.legs[0].from).isSameAs(reloaded.airports[0])
        assertThat(reloaded.legs[1].notes).containsExactly("tight connection")
        assertThat(reloaded.legs[0].notes).isEmpty()
    }

    @Test
    fun readsFareCodesFromAPartnerFeedAndFallsBackForUnknownOnes(): Unit {
        val feed = """
            {
              "reference": "BK-9X1",
              "airports": [
                {"code": "OPO", "city": "Porto"},
                {"code": "MAD", "city": "Madrid"}
              ],
              "legs": [
                {"flight": "IB8721", "from": "OPO", "to": "MAD",
                 "departure": "2026-12-20 18:15", "fare": "W"},
                {"flight": "IB8720", "from": "MAD", "to": "OPO",
                 "departure": "2027-01-04 09:30", "fare": "J", "seat": "3C"}
              ],
              "totalCents": "199.9",
              "loyaltyPoints": 450
            }
        """.trimIndent()

        val booking: Booking = mapper.readValue(feed)

        assertThat(booking.totalCents).isEqualTo(19990L)
        assertThat(booking.loyaltyPoints).isEqualTo(450)
        assertThat(booking.promoCode).isNull()
        assertThat(booking.legs.map { it.fare }).containsExactly(FareClass.UNKNOWN, FareClass.BUSINESS)
        assertThat(booking.legs[0].departure).isEqualTo(LocalDateTime.of(2026, 12, 20, 18, 15))
        assertThat(booking.legs[0].from).isSameAs(booking.legs[1].to)
        assertThat(booking.legs[1].from.city).isEqualTo("Madrid")
    }

    @Test
    fun forwardsAPreRenderedPartnerPayloadVerbatim(): Unit {
        val envelope = PartnerEnvelope("skyalliance", """{"pnr":"Q7XK2L","segments":[1,2]}""")

        val json: String = mapper.writeValueAsString(envelope)

        assertThat(json).isEqualTo("""{"partner":"skyalliance","payload":{"pnr":"Q7XK2L","segments":[1,2]}}""")
        assertThat(mapper.readTree(json)["payload"]["segments"].size()).isEqualTo(2)
    }
}
