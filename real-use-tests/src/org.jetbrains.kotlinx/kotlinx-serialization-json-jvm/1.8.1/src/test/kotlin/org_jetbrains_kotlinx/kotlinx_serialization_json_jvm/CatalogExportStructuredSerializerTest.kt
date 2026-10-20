/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_json_jvm

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.Currency

/**
 * Exercises hand-written structured `KSerializer`s in `Json`: a `buildClassSerialDescriptor`
 * serializer driven by `encodeStructure` and a `decodeElementIndex` loop in `decodeStructure`,
 * a surrogate serializer behind a renamed `SerialDescriptor`, and a walk of the resulting
 * descriptor tree.
 *
 * A storefront exports its catalog as JSON for a partner feed: prices carry an exact decimal and
 * a validated currency, colors travel as RGB channels, and the feed's field documentation is
 * generated from the serializers' descriptors so it can never drift from the wire format.
 */
@OptIn(ExperimentalSerializationApi::class)
public class CatalogExportStructuredSerializerTest {

    /** A shelf price; `taxIncluded` is omitted from the feed unless it differs from its default. */
    @Serializable(with = PriceSerializer::class)
    public data class Price(val amount: BigDecimal, val currency: Currency, val taxIncluded: Boolean = true)

    /** A display color held as one packed `0xRRGGBB` value in the domain. */
    @Serializable(with = ColorSerializer::class)
    public data class Color(val rgb: Int) {
        public val hex: String
            get() = "#%06X".format(rgb)
    }

    @Serializable
    public data class Product(
        val sku: String,
        val title: String,
        val price: Price,
        @SerialName("was_price") val wasPrice: Price? = null,
        val color: Color,
        val dimensionsCm: List<Int>,
    )

    /** Writes a price field by field and reads the fields back in whatever order they arrive. */
    public object PriceSerializer : KSerializer<Price> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("catalog.Price") {
            element<String>("amount")
            element<String>("currency")
            element<Boolean>("taxIncluded", isOptional = true)
        }

        override fun serialize(encoder: Encoder, value: Price): Unit = encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.amount.toPlainString())
            encodeStringElement(descriptor, 1, value.currency.currencyCode)
            if (!value.taxIncluded || shouldEncodeElementDefault(descriptor, 2)) {
                encodeBooleanElement(descriptor, 2, value.taxIncluded)
            }
        }

        override fun deserialize(decoder: Decoder): Price = decoder.decodeStructure(descriptor) {
            var amount: BigDecimal? = null
            var currency: Currency? = null
            var taxIncluded: Boolean = true
            while (true) {
                when (val index: Int = decodeElementIndex(descriptor)) {
                    0 -> amount = BigDecimal(decodeStringElement(descriptor, 0))
                    1 -> currency = parseCurrency(decodeStringElement(descriptor, 1))
                    2 -> taxIncluded = decodeBooleanElement(descriptor, 2)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> throw SerializationException("Unexpected price element index $index")
                }
            }
            Price(
                amount ?: throw SerializationException("Price is missing 'amount'"),
                currency ?: throw SerializationException("Price is missing 'currency'"),
                taxIncluded,
            )
        }

        private fun parseCurrency(code: String): Currency =
            try {
                Currency.getInstance(code)
            } catch (e: IllegalArgumentException) {
                throw SerializationException("Unknown currency '$code'")
            }
    }

    /** The wire shape of a color: one JSON object with a field per channel. */
    @Serializable
    public data class RgbSurrogate(val r: Int, val g: Int, val b: Int) {
        init {
            require(r in 0..255 && g in 0..255 && b in 0..255) { "RGB channels must be within 0..255" }
        }
    }

    /** Delegates to the surrogate's serializer under the feed's own serial name. */
    public object ColorSerializer : KSerializer<Color> {
        override val descriptor: SerialDescriptor = SerialDescriptor("catalog.Color", RgbSurrogate.serializer().descriptor)

        override fun serialize(encoder: Encoder, value: Color) {
            val surrogate = RgbSurrogate(value.rgb shr 16 and 0xFF, value.rgb shr 8 and 0xFF, value.rgb and 0xFF)
            encoder.encodeSerializableValue(RgbSurrogate.serializer(), surrogate)
        }

        override fun deserialize(decoder: Decoder): Color {
            val surrogate: RgbSurrogate = decoder.decodeSerializableValue(RgbSurrogate.serializer())
            return Color(surrogate.r shl 16 or (surrogate.g shl 8) or surrogate.b)
        }
    }

    /** Generates the partner feed's field reference by walking a serializer's descriptor tree. */
    public object FeedDocumentation {
        public fun fields(descriptor: SerialDescriptor): List<String> {
            val lines: MutableList<String> = mutableListOf()
            describe(descriptor, "", lines)
            return lines
        }

        private fun describe(descriptor: SerialDescriptor, prefix: String, lines: MutableList<String>) {
            for (index: Int in 0 until descriptor.elementsCount) {
                val child: SerialDescriptor = descriptor.getElementDescriptor(index)
                var path: String = prefix + descriptor.getElementName(index)
                if (child.kind == StructureKind.LIST) {
                    path += "[]"
                }
                val flags: String = buildString {
                    if (descriptor.isElementOptional(index)) append(" optional")
                    if (child.isNullable) append(" nullable")
                }
                val leaf: SerialDescriptor = if (child.kind == StructureKind.LIST) child.getElementDescriptor(0) else child
                lines += "$path: ${kindName(leaf.kind, leaf.serialName)}$flags"
                if (leaf.kind == StructureKind.CLASS) {
                    describe(leaf, "$path.", lines)
                }
            }
        }

        private fun kindName(kind: SerialKind, serialName: String): String = when (kind) {
            PrimitiveKind.STRING -> "string"
            PrimitiveKind.INT -> "integer"
            PrimitiveKind.BOOLEAN -> "boolean"
            StructureKind.CLASS -> "object ${serialName.removeSuffix("?")}"
            else -> throw IllegalStateException("Feed has no type for $kind")
        }
    }

    private val feedJson: Json = Json { prettyPrint = false }

    private val lamp: Product = Product(
        sku = "LMP-204",
        title = "Desk lamp",
        price = Price(BigDecimal("49.90"), Currency.getInstance("EUR")),
        wasPrice = Price(BigDecimal("59.00"), Currency.getInstance("EUR")),
        color = Color(0x1E90FF),
        dimensionsCm = listOf(18, 18, 45),
    )

    @Test
    fun exportsProductsInTheFeedWireShape() {
        val wholesale: Product = lamp.copy(
            sku = "LMP-204-W",
            price = Price(BigDecimal("31.25"), Currency.getInstance("USD"), taxIncluded = false),
            wasPrice = null,
        )

        val feed: String = feedJson.encodeToString(listOf(lamp, wholesale))

        assertEquals(
            """[{"sku":"LMP-204","title":"Desk lamp","price":{"amount":"49.90","currency":"EUR"},""" +
                """"was_price":{"amount":"59.00","currency":"EUR"},"color":{"r":30,"g":144,"b":255},""" +
                """"dimensionsCm":[18,18,45]},""" +
                """{"sku":"LMP-204-W","title":"Desk lamp","price":{"amount":"31.25","currency":"USD",""" +
                """"taxIncluded":false},"color":{"r":30,"g":144,"b":255},"dimensionsCm":[18,18,45]}]""",
            feed,
        )
        assertEquals(listOf(lamp, wholesale), feedJson.decodeFromString<List<Product>>(feed))
    }

    @Test
    fun encodeDefaultsMakesTheOptionalPriceFieldExplicit() {
        val explicitJson: Json = Json(feedJson) { encodeDefaults = true }

        val price: String = explicitJson.encodeToString(lamp.price)

        assertEquals("""{"amount":"49.90","currency":"EUR","taxIncluded":true}""", price)
        assertEquals("""{"amount":"49.90","currency":"EUR"}""", feedJson.encodeToString(lamp.price))
    }

    @Test
    fun importsPartnerUpdatesWithFieldsInAnyOrder() {
        val update: String = """
            {
              "dimensionsCm": [20, 20, 50],
              "color": {"b": 0, "r": 255, "g": 165},
              "price": {"taxIncluded": false, "currency": "GBP", "amount": "42.0000"},
              "title": "Floor lamp",
              "sku": "LMP-310"
            }
        """.trimIndent()

        val product: Product = feedJson.decodeFromString(update)

        assertEquals("LMP-310", product.sku)
        assertEquals(0, BigDecimal("42").compareTo(product.price.amount))
        assertEquals("42.0000", product.price.amount.toPlainString())
        assertEquals("GBP", product.price.currency.currencyCode)
        assertEquals(false, product.price.taxIncluded)
        assertNull(product.wasPrice)
        assertEquals("#FFA500", product.color.hex)
        assertEquals(listOf(20, 20, 50), product.dimensionsCm)
    }

    @Test
    fun rejectsMalformedPricesWithSerializationErrors() {
        val missingCurrency: SerializationException = assertThrows(SerializationException::class.java) {
            feedJson.decodeFromString<Price>("""{"amount":"10.00"}""")
        }
        assertEquals("Price is missing 'currency'", missingCurrency.message)

        val unknownCurrency: SerializationException = assertThrows(SerializationException::class.java) {
            feedJson.decodeFromString<Price>("""{"amount":"10.00","currency":"XYZ1"}""")
        }
        assertEquals("Unknown currency 'XYZ1'", unknownCurrency.message)

        val unknownField: SerializationException = assertThrows(SerializationException::class.java) {
            feedJson.decodeFromString<Price>("""{"amount":"10.00","currency":"EUR","discount":"5"}""")
        }
        assertTrue(unknownField.message!!.contains("discount"), unknownField.message)

        val lenientJson: Json = Json(feedJson) { ignoreUnknownKeys = true }
        val price: Price = lenientJson.decodeFromString("""{"discount":"5","amount":"10.00","currency":"EUR"}""")
        assertEquals(Price(BigDecimal("10.00"), Currency.getInstance("EUR")), price)
    }

    @Test
    fun rejectsColorsOutsideTheChannelRange() {
        assertThrows(IllegalArgumentException::class.java) {
            feedJson.decodeFromString<Color>("""{"r":300,"g":0,"b":0}""")
        }
    }

    @Test
    fun generatesFeedDocumentationFromDescriptors() {
        val fields: List<String> = FeedDocumentation.fields(Product.serializer().descriptor)

        assertEquals(
            listOf(
                "sku: string",
                "title: string",
                "price: object catalog.Price",
                "price.amount: string",
                "price.currency: string",
                "price.taxIncluded: boolean optional",
                "was_price: object catalog.Price optional nullable",
                "was_price.amount: string",
                "was_price.currency: string",
                "was_price.taxIncluded: boolean optional",
                "color: object catalog.Color",
                "color.r: integer",
                "color.g: integer",
                "color.b: integer",
                "dimensionsCm[]: integer",
            ),
            fields,
        )
        assertEquals("catalog.Color", ColorSerializer.descriptor.serialName)
        assertEquals(RgbSurrogate.serializer().descriptor.elementNames.toList(), ColorSerializer.descriptor.elementNames.toList())
    }
}
