/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:UseSerializers(PartnerPriceFeedSerializerSelectionTest.DecimalSerializer::class)

package org_jetbrains_kotlinx.kotlinx_serialization_json_jvm

import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises serializer-selection annotations: `@KeepGeneratedSerializer` behind a custom class
 * serializer, file-level `@UseSerializers`, type-use `@Serializable(with = ...)` on property types
 * and generic arguments, and per-class `@JsonIgnoreUnknownKeys` under an otherwise strict `Json`.
 *
 * A retailer imports a partner's price feed, where prices arrive either as a `"19.90 EUR"`
 * shorthand or as an object, SKU codes arrive in any case, and line items carry extra keys the
 * retailer does not model. The import totals the feed in euros and re-exports it canonically.
 */
@OptIn(ExperimentalSerializationApi::class)
public class PartnerPriceFeedSerializerSelectionTest {

    /** Decimal amounts travel as JSON strings so no precision or scale is lost; numbers are accepted. */
    public object DecimalSerializer : KSerializer<BigDecimal> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("partner.feed.Decimal", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: BigDecimal) {
            encoder.encodeString(value.toPlainString())
        }

        override fun deserialize(decoder: Decoder): BigDecimal {
            val text: String = if (decoder is JsonDecoder) {
                decoder.decodeJsonElement().jsonPrimitive.content
            } else {
                decoder.decodeString()
            }
            return BigDecimal(text)
        }
    }

    /** Normalizes partner SKU codes to the retailer's trimmed upper-case form. */
    public object SkuCodeSerializer : KSerializer<String> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("partner.feed.Sku", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: String) {
            encoder.encodeString(value)
        }

        override fun deserialize(decoder: Decoder): String = decoder.decodeString().trim().uppercase()
    }

    @KeepGeneratedSerializer
    @Serializable(with = MoneySerializer::class)
    public data class Money(val amount: BigDecimal, val currency: String)

    /** Accepts the `"<amount> <currency>"` shorthand and delegates the object form to generated code. */
    public object MoneySerializer : JsonTransformingSerializer<Money>(Money.generatedSerializer()) {
        override fun transformDeserialize(element: JsonElement): JsonElement {
            if (element !is JsonPrimitive || !element.isString) {
                return element
            }
            val parts: List<String> = element.content.trim().split(' ')
            if (parts.size != 2) {
                throw SerializationException("Price shorthand '${element.content}' is not '<amount> <currency>'")
            }
            return buildJsonObject {
                put("amount", parts[0])
                put("currency", parts[1])
            }
        }
    }

    @Serializable
    @JsonIgnoreUnknownKeys
    public data class PartnerLine(
        val sku: @Serializable(with = SkuCodeSerializer::class) String,
        val alternateSkus: List<@Serializable(with = SkuCodeSerializer::class) String> = emptyList(),
        val unitPrice: Money,
        val quantity: Int,
        val discountRate: BigDecimal? = null,
    )

    @Serializable
    public data class PartnerFeed(
        val partner: String,
        val lines: List<PartnerLine>,
        val eurRates: Map<String, BigDecimal> = emptyMap(),
    )

    /** Totals the feed in euros, rounded to cents the way the retailer books it. */
    private fun totalInEuros(feed: PartnerFeed): BigDecimal {
        var total: BigDecimal = BigDecimal.ZERO
        for (line: PartnerLine in feed.lines) {
            val rate: BigDecimal = feed.eurRates[line.unitPrice.currency]
                ?: throw IllegalStateException("No rate for ${line.unitPrice.currency}")
            val keep: BigDecimal = BigDecimal.ONE - (line.discountRate ?: BigDecimal.ZERO)
            total += line.unitPrice.amount * BigDecimal(line.quantity) * keep * rate
        }
        return total.setScale(2, RoundingMode.HALF_EVEN)
    }

    private val feedText: String = """
        {
          "partner": "nordic-supply",
          "lines": [
            {
              "sku": " ab-100 ",
              "alternateSkus": ["ab-100-x", " Ab-100-Y"],
              "unitPrice": "19.90 EUR",
              "quantity": 3,
              "warehouse": "OSL-2"
            },
            {
              "sku": "cd-200",
              "unitPrice": {"amount": "4.05", "currency": "SEK"},
              "quantity": 10,
              "discountRate": 0.10,
              "promo": {"code": "WINTER", "stackable": false}
            },
            {
              "sku": "ef-300",
              "unitPrice": {"amount": 0.10, "currency": "EUR"},
              "quantity": 7
            }
          ],
          "eurRates": {"SEK": "0.0870", "EUR": 1}
        }
    """.trimIndent()

    @Test
    fun importsMixedPriceShapesAndTotalsTheFeedInEuros() {
        val feed: PartnerFeed = Json.decodeFromString<PartnerFeed>(feedText)

        assertEquals(listOf("AB-100", "CD-200", "EF-300"), feed.lines.map { it.sku })
        assertEquals(listOf("AB-100-X", "AB-100-Y"), feed.lines[0].alternateSkus)
        assertEquals(Money(BigDecimal("19.90"), "EUR"), feed.lines[0].unitPrice)
        assertEquals(Money(BigDecimal("4.05"), "SEK"), feed.lines[1].unitPrice)
        assertEquals(BigDecimal("0.10"), feed.lines[2].unitPrice.amount)
        assertEquals(BigDecimal("0.10"), feed.lines[1].discountRate)
        assertEquals(null, feed.lines[2].discountRate)
        assertEquals(BigDecimal("0.0870"), feed.eurRates["SEK"])

        // 59.70 + 4.05 * 10 * 0.90 * 0.0870 + 0.70 = 63.57115
        assertEquals(BigDecimal("63.57"), totalInEuros(feed))
    }

    @Test
    fun reExportsTheFeedInCanonicalShapeAndReadsItBack() {
        val feed: PartnerFeed = Json.decodeFromString<PartnerFeed>(feedText)

        val exported: String = Json.encodeToString(feed)
        val tree: JsonObject = Json.parseToJsonElement(exported).jsonObject
        val lines: List<JsonObject> = tree.getValue("lines").jsonArray.map { it.jsonObject }

        val shorthandPrice: JsonObject = lines[0].getValue("unitPrice").jsonObject
        assertEquals(JsonPrimitive("19.90"), shorthandPrice["amount"])
        assertEquals(JsonPrimitive("EUR"), shorthandPrice["currency"])
        assertEquals(JsonPrimitive("0.10"), lines[2].getValue("unitPrice").jsonObject["amount"])
        assertEquals(JsonPrimitive("0.10"), lines[1]["discountRate"])
        assertEquals(JsonPrimitive("AB-100-Y"), lines[0].getValue("alternateSkus").jsonArray[1])
        assertFalse(lines[0].containsKey("warehouse"))
        assertFalse(lines[1].containsKey("promo"))
        assertEquals(JsonPrimitive("1"), tree.getValue("eurRates").jsonObject["EUR"])

        assertEquals(feed, Json.decodeFromString<PartnerFeed>(exported))
    }

    @Test
    fun keepsTheGeneratedMoneySerializerForTheCanonicalObjectForm() {
        assertSame(MoneySerializer, Money.serializer())
        assertEquals(Money(BigDecimal("2.50"), "NOK"), Json.decodeFromString<Money>("\"2.50 NOK\""))

        val canonical: Money = Json.decodeFromString(
            Money.generatedSerializer(),
            """{"amount":"2.50","currency":"NOK"}""",
        )
        assertEquals(Money(BigDecimal("2.50"), "NOK"), canonical)
        assertEquals(
            """{"amount":"2.50","currency":"NOK"}""",
            Json.encodeToString(Money.generatedSerializer(), canonical),
        )

        assertThrows(SerializationException::class.java) {
            Json.decodeFromString(Money.generatedSerializer(), "\"2.50 NOK\"")
        }
        val malformed: SerializationException = assertThrows(SerializationException::class.java) {
            Json.decodeFromString<Money>("\"2.50\"")
        }
        assertTrue(malformed.message!!.contains("'2.50'"), malformed.message)
    }

    @Test
    fun ignoresUnknownKeysOnlyOnLineItemsAndRejectsThemOnTheEnvelope() {
        val withEnvelopeExtra: String = """
            {
              "partner": "nordic-supply",
              "generatedAt": "2026-10-17T08:00:00Z",
              "lines": [{"sku": "ab-100", "unitPrice": "1.00 EUR", "quantity": 1, "bin": "A1"}]
            }
        """.trimIndent()
        val rejected: SerializationException = assertThrows(SerializationException::class.java) {
            Json.decodeFromString<PartnerFeed>(withEnvelopeExtra)
        }
        assertTrue(rejected.message!!.contains("generatedAt"), rejected.message)

        val lenientEnvelope: Json = Json { ignoreUnknownKeys = true }
        val feed: PartnerFeed = lenientEnvelope.decodeFromString<PartnerFeed>(withEnvelopeExtra)
        assertEquals(listOf(PartnerLine("AB-100", emptyList(), Money(BigDecimal("1.00"), "EUR"), 1)), feed.lines)

        val lineDescriptor: SerialDescriptor = PartnerLine.serializer().descriptor
        assertTrue(lineDescriptor.annotations.any { it is JsonIgnoreUnknownKeys })
        assertFalse(PartnerFeed.serializer().descriptor.annotations.any { it is JsonIgnoreUnknownKeys })
        assertEquals("partner.feed.Sku", lineDescriptor.getElementDescriptor(0).serialName)
        assertEquals(
            "partner.feed.Sku",
            lineDescriptor.getElementDescriptor(1).getElementDescriptor(0).serialName,
        )
        val discountDescriptor: SerialDescriptor = lineDescriptor.getElementDescriptor(4)
        assertTrue(discountDescriptor.isNullable)
        assertTrue(discountDescriptor.serialName.startsWith("partner.feed.Decimal"))
    }
}
