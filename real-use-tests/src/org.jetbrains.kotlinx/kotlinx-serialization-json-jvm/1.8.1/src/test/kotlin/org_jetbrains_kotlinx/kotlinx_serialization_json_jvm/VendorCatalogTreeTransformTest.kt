/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_json_jvm

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises JSON tree transformation: a `JsonTransformingSerializer` that normalizes a field
 * sent either as a scalar or as an array, and a `JsonContentPolymorphicSerializer` that picks
 * a subtype by inspecting which keys the object carries, with no class discriminator.
 *
 * A shop imports a legacy vendor catalog feed whose tags and pricing come in loose shapes,
 * computes quotes from it, and exports the normalized catalog back in the vendor's compact form.
 */
public class VendorCatalogTreeTransformTest {

    /** Accepts `"tags": "x"` as well as `"tags": ["x", "y"]`, and writes one tag back as a scalar. */
    public object TagListSerializer :
        JsonTransformingSerializer<List<String>>(ListSerializer(String.serializer())) {

        override fun transformDeserialize(element: JsonElement): JsonElement =
            if (element is JsonArray) element else JsonArray(listOf(element))

        override fun transformSerialize(element: JsonElement): JsonElement {
            val tags: JsonArray = element as JsonArray
            return if (tags.size == 1) tags.single() else tags
        }
    }

    @Serializable(with = PricingSerializer::class)
    public sealed interface Pricing {
        public fun quote(quantity: Int): Long
    }

    @Serializable
    public data class FlatPricing(val unitCents: Long) : Pricing {
        override fun quote(quantity: Int): Long = unitCents * quantity
    }

    @Serializable
    public data class Tier(val from: Int, val unitCents: Long)

    @Serializable
    public data class TieredPricing(val tiers: List<Tier>) : Pricing {
        override fun quote(quantity: Int): Long {
            val tier: Tier = tiers.filter { it.from <= quantity }.maxBy { it.from }
            return tier.unitCents * quantity
        }
    }

    /** Selects the pricing model from the shape of the object, as the vendor sends no type field. */
    public object PricingSerializer : JsonContentPolymorphicSerializer<Pricing>(Pricing::class) {
        override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Pricing> {
            val keys: Set<String> = element.jsonObject.keys
            return when {
                "tiers" in keys -> TieredPricing.serializer()
                "unitCents" in keys -> FlatPricing.serializer()
                else -> throw SerializationException("Unrecognized pricing shape: $keys")
            }
        }
    }

    @Serializable
    public data class Product(
        val sku: String,
        @Serializable(with = TagListSerializer::class)
        val tags: List<String>,
        val pricing: Pricing,
    )

    @Serializable
    public data class Catalog(val vendor: String, val products: List<Product>)

    private val feedJson: Json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun importsLooseVendorFeedAndQuotes() {
        val feed: String = """
            {
              "vendor": "northwind",
              "generatedAt": "2026-10-01T00:00:00Z",
              "products": [
                {"sku": "PEN-01", "tags": "stationery",
                 "pricing": {"unitCents": 150}},
                {"sku": "PAPER-A4", "tags": ["stationery", "bulk"],
                 "pricing": {"tiers": [{"from": 1, "unitCents": 900},
                                       {"from": 10, "unitCents": 750},
                                       {"from": 50, "unitCents": 600}]}},
                {"sku": "INK-BLK", "tags": [], "pricing": {"unitCents": 2400},
                 "discontinued": false}
              ]
            }
        """.trimIndent()

        val catalog: Catalog = feedJson.decodeFromString<Catalog>(feed)
        val bySku: Map<String, Product> = catalog.products.associateBy { it.sku }

        assertEquals("northwind", catalog.vendor)
        assertEquals(listOf("stationery"), bySku.getValue("PEN-01").tags)
        assertEquals(listOf("stationery", "bulk"), bySku.getValue("PAPER-A4").tags)
        assertEquals(emptyList<String>(), bySku.getValue("INK-BLK").tags)
        assertEquals(FlatPricing(150), bySku.getValue("PEN-01").pricing)
        assertTrue(bySku.getValue("PAPER-A4").pricing is TieredPricing)

        assertEquals(1500L, bySku.getValue("PEN-01").pricing.quote(10))
        assertEquals(2700L, bySku.getValue("PAPER-A4").pricing.quote(3))
        assertEquals(9000L, bySku.getValue("PAPER-A4").pricing.quote(12))
        assertEquals(30000L, bySku.getValue("PAPER-A4").pricing.quote(50))

        val stationery: List<String> = catalog.products
            .filter { "stationery" in it.tags }
            .map { it.sku }
        assertEquals(listOf("PEN-01", "PAPER-A4"), stationery)
    }

    @Test
    fun exportsCatalogInCompactVendorShape() {
        val catalog: Catalog = Catalog(
            vendor = "northwind",
            products = listOf(
                Product("MUG-01", listOf("kitchen"), FlatPricing(899)),
                Product(
                    "CUP-12",
                    listOf("kitchen", "bulk"),
                    TieredPricing(listOf(Tier(1, 300), Tier(12, 250))),
                ),
            ),
        )

        val exported: String = feedJson.encodeToString<Catalog>(catalog)

        assertEquals(
            """{"vendor":"northwind","products":[""" +
                """{"sku":"MUG-01","tags":"kitchen","pricing":{"unitCents":899}},""" +
                """{"sku":"CUP-12","tags":["kitchen","bulk"],""" +
                """"pricing":{"tiers":[{"from":1,"unitCents":300},{"from":12,"unitCents":250}]}}]}""",
            exported,
        )

        val tree: JsonObject = feedJson.parseToJsonElement(exported).jsonObject
        val firstTags: JsonElement = (tree["products"] as JsonArray)[0].jsonObject["tags"]!!
        assertTrue(firstTags is JsonPrimitive)
        assertEquals("kitchen", firstTags.jsonPrimitive.content)

        assertEquals(catalog, feedJson.decodeFromString<Catalog>(exported))
    }

    @Test
    fun rejectsUnrecognizedPricingShape() {
        val feed: String = """
            {"vendor": "northwind", "products": [
              {"sku": "GIFT-CARD", "tags": "gift", "pricing": {"onRequest": true}}
            ]}
        """.trimIndent()

        val failure: SerializationException = assertThrows(SerializationException::class.java) {
            feedJson.decodeFromString<Catalog>(feed)
        }

        assertTrue(failure.message!!.contains("onRequest"), failure.message)
    }

    @Test
    fun appliesTransformerToStandaloneTagValues() {
        val single: List<String> = feedJson.decodeFromString(TagListSerializer, "\"clearance\"")
        val many: List<String> = feedJson.decodeFromString(TagListSerializer, "[\"a\",\"b\"]")
        val serializer: KSerializer<List<String>> = TagListSerializer

        assertEquals(listOf("clearance"), single)
        assertEquals(listOf("a", "b"), many)
        assertEquals("\"clearance\"", feedJson.encodeToString(serializer, single))
        assertEquals("[\"a\",\"b\"]", feedJson.encodeToString(serializer, many))
    }
}
