/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_json_jvm

import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.serializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises runtime serializer resolution for generic types: `serializer<T>()` and
 * `serializer(KType)` from `typeOf` for parameterized classes and nested collections, with
 * `@JvmInline` value classes, `@Transient` and `@Required` properties, and
 * `allowStructuredMapKeys` for data-class map keys.
 *
 * A storefront caches catalog pages and stock layouts as JSON strings keyed by name; callers
 * store and load them by type, without naming a serializer, and the cache resolves it.
 */
@OptIn(ExperimentalSerializationApi::class)
public class CatalogSnapshotCacheGenericSerializerTest {

    @Serializable
    @JvmInline
    public value class Sku(public val code: String)

    @Serializable
    @JvmInline
    public value class Cents(public val value: Long)

    @Serializable
    public data class Product(
        val sku: Sku,
        val price: Cents,
        @Required val currency: String = "EUR",
        val tags: Set<String> = emptySet(),
    )

    @Serializable
    public data class Page<T>(
        val items: List<T>,
        val cursor: String? = null,
        @Transient val servedFromCache: Boolean = false,
    )

    @Serializable
    public data class Slot(val aisle: Int, val shelf: Char)

    /** Stores values as JSON text and resolves their serializers from the requested type. */
    public class SnapshotCache(public val json: Json) {
        private val entries: MutableMap<String, String> = linkedMapOf()

        public fun put(key: String, type: KType, value: Any?) {
            val serializer: KSerializer<Any?> = json.serializersModule.serializer(type)
            entries[key] = json.encodeToString(serializer, value)
        }

        public fun get(key: String, type: KType): Any? {
            val text: String = entries[key] ?: return null
            return json.decodeFromString(json.serializersModule.serializer(type), text)
        }

        public inline fun <reified T> store(key: String, value: T) {
            put(key, typeOf<T>(), value)
        }

        public inline fun <reified T> load(key: String): T? = get(key, typeOf<T>()) as T?

        public fun raw(key: String): String? = entries[key]
    }

    private val catalog: List<Product> = listOf(
        Product(Sku("TEA-001"), Cents(1299), tags = setOf("organic", "loose-leaf")),
        Product(Sku("MUG-014"), Cents(850), currency = "USD"),
    )

    @Test
    fun cachesGenericPageResolvedFromReifiedType() {
        val cache: SnapshotCache = SnapshotCache(Json)
        val page: Page<Product> = Page(catalog, cursor = "p2", servedFromCache = true)

        cache.store("catalog:page1", page)

        val stored: JsonObject = Json.parseToJsonElement(cache.raw("catalog:page1")!!).jsonObject
        assertEquals(setOf("items", "cursor"), stored.keys)
        val first: JsonObject = stored.getValue("items").jsonArray[0].jsonObject
        assertEquals("TEA-001", first.getValue("sku").jsonPrimitive.content)
        assertEquals(1299L, first.getValue("price").jsonPrimitive.long)
        assertEquals("EUR", first.getValue("currency").jsonPrimitive.content)

        val loaded: Page<Product>? = cache.load<Page<Product>>("catalog:page1")
        assertEquals(Page(catalog, cursor = "p2"), loaded)
        assertFalse(loaded!!.servedFromCache)
        assertEquals(Sku("MUG-014"), loaded.items[1].sku)
        assertNull(cache.load<Page<Product>>("catalog:page9"))
    }

    @Test
    fun cachesNestedCollectionsOfValueClasses() {
        val cache: SnapshotCache = SnapshotCache(Json)
        val stockBySku: Map<Sku, Int> = linkedMapOf(Sku("TEA-001") to 40, Sku("MUG-014") to 3)
        val priceHistory: Map<String, List<Pair<Sku, Cents>>> = mapOf(
            "2026-10" to listOf(Sku("TEA-001") to Cents(1199), Sku("TEA-001") to Cents(1299)),
        )

        cache.store("stock", stockBySku)
        cache.store("history", priceHistory)

        assertEquals("""{"TEA-001":40,"MUG-014":3}""", cache.raw("stock"))
        val history: JsonArray = Json.parseToJsonElement(cache.raw("history")!!)
            .jsonObject.getValue("2026-10").jsonArray
        assertEquals("TEA-001", history[1].jsonObject.getValue("first").jsonPrimitive.content)
        assertEquals(1299, history[1].jsonObject.getValue("second").jsonPrimitive.int)

        assertEquals(stockBySku, cache.load<Map<Sku, Int>>("stock"))
        assertEquals(priceHistory, cache.load<Map<String, List<Pair<Sku, Cents>>>>("history"))
        val nullable: List<Cents?>? = listOf(Cents(5), null)
        cache.store("discounts", nullable)
        assertEquals("[5,null]", cache.raw("discounts"))
        assertEquals(nullable, cache.load<List<Cents?>>("discounts"))
    }

    @Test
    fun rejectsPayloadMissingRequiredProperty() {
        val serializer: KSerializer<Page<Product>> = serializer<Page<Product>>()
        val withCurrency: String = """{"items":[{"sku":"TEA-001","price":1299,"currency":"EUR"}]}"""
        val withoutCurrency: String = """{"items":[{"sku":"TEA-001","price":1299}]}"""

        val decoded: Page<Product> = Json.decodeFromString(serializer, withCurrency)
        assertEquals(Page(listOf(Product(Sku("TEA-001"), Cents(1299)))), decoded)

        val failure: SerializationException = assertThrows(SerializationException::class.java) {
            Json.decodeFromString(serializer, withoutCurrency)
        }
        assertTrue(failure.message!!.contains("currency"), failure.message)
    }

    @Test
    fun storesShelfLayoutWithStructuredMapKeys() {
        val layout: Map<Slot, Sku> = linkedMapOf(
            Slot(aisle = 3, shelf = 'B') to Sku("TEA-001"),
            Slot(aisle = 7, shelf = 'A') to Sku("MUG-014"),
        )

        val plainCache: SnapshotCache = SnapshotCache(Json)
        assertThrows(SerializationException::class.java) { plainCache.store("layout", layout) }

        val cache: SnapshotCache = SnapshotCache(Json { allowStructuredMapKeys = true })
        cache.store("layout", layout)

        val stored: JsonArray = Json.parseToJsonElement(cache.raw("layout")!!).jsonArray
        assertEquals(4, stored.size)
        val firstKey: JsonElement = stored[0]
        assertEquals(3, firstKey.jsonObject.getValue("aisle").jsonPrimitive.int)
        assertEquals("B", firstKey.jsonObject.getValue("shelf").jsonPrimitive.content)
        assertEquals("TEA-001", stored[1].jsonPrimitive.content)

        val restored: Map<Slot, Sku>? = cache.load<Map<Slot, Sku>>("layout")
        assertEquals(layout, restored)
        assertEquals(Sku("MUG-014"), restored!![Slot(7, 'A')])
        assertEquals(
            cache.raw("layout"),
            cache.json.encodeToString<Map<Slot, Sku>>(restored),
        )
    }
}
