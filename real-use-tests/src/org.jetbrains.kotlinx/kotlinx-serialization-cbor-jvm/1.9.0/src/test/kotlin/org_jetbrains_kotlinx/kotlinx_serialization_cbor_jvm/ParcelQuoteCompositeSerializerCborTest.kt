/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_cbor_jvm

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises hand-written composite serializers on the `Cbor` format: class descriptors built
 * with `buildClassSerialDescriptor`, `encodeStructure`/`decodeStructure` element loops,
 * surrogate serializers, and serializers composed with the built-in collection serializers.
 *
 * A parcel-quoting service stores quotes as CBOR. Prices and addresses come from vendor SDK
 * types the service cannot annotate, so it writes their serializers by hand; temperatures for
 * cold-chain parcels are validated value objects serialized through a surrogate.
 */
@OptIn(ExperimentalSerializationApi::class)
public class ParcelQuoteCompositeSerializerCborTest {

    /** An amount from the billing SDK, in the currency's minor units. */
    public data class Money(val currency: String, val minorUnits: Long)

    /** A postal address from the geocoding SDK; `unit` is absent for single-unit buildings. */
    public data class Address(val street: String, val city: String, val unit: String? = null)

    /** Writes `Money` as the map `{"cur": ..., "minor": ...}`. */
    public object MoneySerializer : KSerializer<Money> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("parcel.Money") {
            element<String>("cur")
            element<Long>("minor")
        }

        override fun serialize(encoder: Encoder, value: Money): Unit = encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.currency)
            encodeLongElement(descriptor, 1, value.minorUnits)
        }

        override fun deserialize(decoder: Decoder): Money = decoder.decodeStructure(descriptor) {
            var currency: String? = null
            var minorUnits: Long? = null
            while (true) {
                when (val index: Int = decodeElementIndex(descriptor)) {
                    0 -> currency = decodeStringElement(descriptor, 0)
                    1 -> minorUnits = decodeLongElement(descriptor, 1)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> throw SerializationException("Unexpected Money element $index")
                }
            }
            Money(
                currency ?: throw SerializationException("Money is missing 'cur'"),
                minorUnits ?: throw SerializationException("Money is missing 'minor'"),
            )
        }
    }

    /** Writes `Address`, leaving the optional `unit` key out when there is no unit. */
    public object AddressSerializer : KSerializer<Address> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("parcel.Address") {
            element<String>("street")
            element<String>("city")
            element("unit", String.serializer().nullable.descriptor, isOptional = true)
        }

        override fun serialize(encoder: Encoder, value: Address): Unit = encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.street)
            encodeStringElement(descriptor, 1, value.city)
            if (value.unit != null) {
                encodeNullableSerializableElement(descriptor, 2, String.serializer(), value.unit)
            }
        }

        override fun deserialize(decoder: Decoder): Address = decoder.decodeStructure(descriptor) {
            var street: String? = null
            var city: String? = null
            var unit: String? = null
            while (true) {
                when (val index: Int = decodeElementIndex(descriptor)) {
                    0 -> street = decodeStringElement(descriptor, 0)
                    1 -> city = decodeStringElement(descriptor, 1)
                    2 -> unit = decodeNullableSerializableElement(descriptor, 2, String.serializer().nullable)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> throw SerializationException("Unexpected Address element $index")
                }
            }
            Address(
                street ?: throw SerializationException("Address is missing 'street'"),
                city ?: throw SerializationException("Address is missing 'city'"),
                unit,
            )
        }
    }

    /** A cold-chain temperature limit that cannot be below absolute zero. */
    @Serializable(with = TemperatureSerializer::class)
    public class Temperature private constructor(public val milliCelsius: Int) {
        override fun equals(other: Any?): Boolean = other is Temperature && other.milliCelsius == milliCelsius

        override fun hashCode(): Int = milliCelsius

        public companion object {
            public fun ofMilliCelsius(milliCelsius: Int): Temperature {
                require(milliCelsius >= -273_150) { "$milliCelsius millidegrees Celsius is below absolute zero" }
                return Temperature(milliCelsius)
            }
        }
    }

    /** The wire shape of [Temperature]; the serializer validates on the way back in. */
    @Serializable
    @SerialName("parcel.Temperature")
    private class TemperatureSurrogate(val milliCelsius: Int)

    /** Serializes [Temperature] through [TemperatureSurrogate]. */
    public object TemperatureSerializer : KSerializer<Temperature> {
        override val descriptor: SerialDescriptor = TemperatureSurrogate.serializer().descriptor

        override fun serialize(encoder: Encoder, value: Temperature): Unit =
            encoder.encodeSerializableValue(TemperatureSurrogate.serializer(), TemperatureSurrogate(value.milliCelsius))

        override fun deserialize(decoder: Decoder): Temperature =
            Temperature.ofMilliCelsius(decoder.decodeSerializableValue(TemperatureSurrogate.serializer()).milliCelsius)
    }

    @Serializable
    public data class ParcelQuote(
        val quoteId: String,
        @Serializable(with = MoneySerializer::class) val price: Money,
        val surcharges: Map<String, @Serializable(with = MoneySerializer::class) Money>,
        @Serializable(with = AddressSerializer::class) val destination: Address,
        val maxTemperature: Temperature? = null,
    )

    /** What a partner system sends for a price: keys in another order plus an exchange rate. */
    @Serializable
    public data class PartnerPrice(
        @SerialName("minor") val minorUnits: Long,
        val fxRate: String,
        @SerialName("cur") val currency: String,
    )

    /** A price record that lost its amount in transit. */
    @Serializable
    public data class TruncatedPrice(@SerialName("cur") val currency: String)

    private val cbor: Cbor = Cbor

    @Test
    fun quoteWithVendorTypesRoundTrips(): Unit {
        val quote: ParcelQuote = ParcelQuote(
            quoteId = "Q-2026-0042",
            price = Money("EUR", 1_250),
            surcharges = linkedMapOf("fuel" to Money("EUR", 85), "remote-area" to Money("EUR", 300)),
            destination = Address("Kneza Milosa 12", "Belgrade", unit = "4B"),
            maxTemperature = Temperature.ofMilliCelsius(-18_000),
        )

        val bytes: ByteArray = cbor.encodeToByteArray(quote)
        val restored: ParcelQuote = cbor.decodeFromByteArray(bytes)

        assertEquals(quote, restored)
        assertEquals(-18_000, restored.maxTemperature!!.milliCelsius)
        assertEquals(listOf("fuel", "remote-area"), restored.surcharges.keys.toList())
    }

    @Test
    fun handWrittenSerializerWritesTheDocumentedMap(): Unit {
        val hex: String = cbor.encodeToHexString(MoneySerializer, Money("EUR", 1_250))

        // An indefinite-length map: "cur" -> "EUR", "minor" -> 1250.
        assertEquals("bf6363757263455552656d696e6f721904e2ff", hex)
        assertEquals(Money("EUR", 1_250), cbor.decodeFromHexString(MoneySerializer, hex))
    }

    @Test
    fun optionalElementIsLeftOutWhenAbsent(): Unit {
        val bytes: ByteArray = cbor.encodeToByteArray(AddressSerializer, Address("Main Street 1", "Novi Sad"))

        val asMap: Map<String, String> = cbor.decodeFromByteArray(bytes)
        assertEquals(mapOf("street" to "Main Street 1", "city" to "Novi Sad"), asMap)

        val restored: Address = cbor.decodeFromByteArray(AddressSerializer, bytes)
        assertNull(restored.unit)
        assertEquals("Novi Sad", restored.city)
    }

    @Test
    fun elementLoopAcceptsPartnerKeyOrderAndSkipsUnknownKeys(): Unit {
        val partnerBytes: ByteArray = cbor.encodeToByteArray(PartnerPrice(minorUnits = 999, fxRate = "1.17", currency = "USD"))

        val lenient: Cbor = Cbor { ignoreUnknownKeys = true }
        assertEquals(Money("USD", 999), lenient.decodeFromByteArray(MoneySerializer, partnerBytes))

        assertThrows(SerializationException::class.java) {
            cbor.decodeFromByteArray(MoneySerializer, partnerBytes)
        }
    }

    @Test
    fun missingRequiredElementIsReported(): Unit {
        val truncated: ByteArray = cbor.encodeToByteArray(TruncatedPrice("GBP"))

        val failure: SerializationException = assertThrows(SerializationException::class.java) {
            cbor.decodeFromByteArray(MoneySerializer, truncated)
        }
        assertEquals("Money is missing 'minor'", failure.message)
    }

    @Test
    fun surrogateValidatesStoredTemperatures(): Unit {
        val stored: ByteArray = cbor.encodeToByteArray(Temperature.ofMilliCelsius(4_000))
        assertEquals(mapOf("milliCelsius" to 4_000), cbor.decodeFromByteArray<Map<String, Int>>(stored))
        assertEquals(Temperature.ofMilliCelsius(4_000), cbor.decodeFromByteArray<Temperature>(stored))

        val corrupt: ByteArray = cbor.encodeToByteArray(mapOf("milliCelsius" to -300_000))
        val failure: IllegalArgumentException = assertThrows(IllegalArgumentException::class.java) {
            cbor.decodeFromByteArray<Temperature>(corrupt)
        }
        assertTrue(failure.message!!.contains("below absolute zero"))
    }

    @Test
    fun composedCollectionSerializersCarryRateCards(): Unit {
        val rateCardSerializer: KSerializer<Map<String, Money>> = MapSerializer(String.serializer(), MoneySerializer)
        val rateCard: Map<String, Money> = linkedMapOf(
            "small" to Money("EUR", 499),
            "medium" to Money("EUR", 899),
            "large" to Money("EUR", 1_499),
        )
        val restoredCard: Map<String, Money> =
            cbor.decodeFromByteArray(rateCardSerializer, cbor.encodeToByteArray(rateCardSerializer, rateCard))
        assertEquals(rateCard, restoredCard)
        assertEquals(listOf("small", "medium", "large"), restoredCard.keys.toList())

        val historySerializer: KSerializer<List<Money?>> = ListSerializer(MoneySerializer.nullable)
        val history: List<Money?> = listOf(Money("EUR", 450), null, Money("EUR", 475))
        val restoredHistory: List<Money?> =
            cbor.decodeFromByteArray(historySerializer, cbor.encodeToByteArray(historySerializer, history))
        assertEquals(history, restoredHistory)
    }
}
