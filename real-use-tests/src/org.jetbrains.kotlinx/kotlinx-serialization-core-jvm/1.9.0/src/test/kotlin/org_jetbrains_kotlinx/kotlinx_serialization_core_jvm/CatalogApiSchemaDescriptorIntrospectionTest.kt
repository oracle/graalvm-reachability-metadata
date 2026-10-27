/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_core_jvm

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Required
import kotlinx.serialization.SerialInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.serializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises `SerialDescriptor` introspection: element names, kinds, optionality, nullability,
 * inline descriptors and `@SerialInfo` annotations read back from generated serializers.
 *
 * A product catalog service publishes a JSON-Schema document for its API models by walking
 * their descriptors, the way OpenAPI generators for Ktor do, and checks that the payloads it
 * actually sends and rejects agree with the published schema.
 */
@OptIn(ExperimentalSerializationApi::class)
public class CatalogApiSchemaDescriptorIntrospectionTest {

    /** Human-readable documentation the schema generator copies into `description`. */
    @SerialInfo
    @Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
    public annotation class Doc(val text: String)

    /** Stock keeping unit, inlined on the wire as its code. */
    @Serializable
    @JvmInline
    public value class Sku(val code: String)

    /** Whether a product can be ordered. */
    @Serializable
    public enum class Availability {
        IN_STOCK,

        @SerialName("back-order")
        BACK_ORDER,
        DISCONTINUED,
    }

    /** An amount of money in minor units; written as `"<cents> <currency>"`. */
    @Serializable(with = MoneyAsStringSerializer::class)
    public data class Money(val cents: Long, val currency: String)

    /** Custom serializer whose descriptor tells schema readers that [Money] is a string. */
    public object MoneyAsStringSerializer : KSerializer<Money> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("catalog.Money", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: Money) {
            encoder.encodeString("${value.cents} ${value.currency}")
        }

        override fun deserialize(decoder: Decoder): Money {
            val parts: List<String> = decoder.decodeString().split(' ')
            return Money(parts[0].toLong(), parts[1])
        }
    }

    /** A node of the category tree; refers to itself through [children]. */
    @Serializable
    @SerialName("Category")
    @Doc("A node of the product category tree.")
    public data class Category(
        val id: Int,
        val title: String,
        val children: List<Category> = emptyList(),
    )

    /** A product as the catalog API sends and accepts it. */
    @Serializable
    @SerialName("Product")
    @Doc("A product offered in the catalog.")
    public data class Product(
        @Doc("Stock keeping unit.")
        val sku: Sku,
        @Required
        val name: String = "",
        val price: Money,
        val availability: Availability = Availability.IN_STOCK,
        val tags: Set<String> = emptySet(),
        val attributes: Map<String, Int> = emptyMap(),
        @SerialName("discount_rate")
        val discountRate: Double? = null,
        val category: Category? = null,
        @Transient
        val cachedLabel: String = "",
    )

    /** Builds a JSON-Schema document from serial descriptors, emitting classes as definitions. */
    public class SchemaGenerator {
        private val definitions: LinkedHashMap<String, JsonObject> = LinkedHashMap()
        private val inProgress: MutableSet<String> = mutableSetOf()

        /** Returns the schema of [root] with every reachable class under `definitions`. */
        public fun generate(root: SerialDescriptor): JsonObject {
            val rootSchema: JsonObject = schemaOf(root)
            return JsonObject(rootSchema + ("definitions" to JsonObject(definitions)))
        }

        private fun schemaOf(descriptor: SerialDescriptor): JsonObject {
            val base: JsonObject = if (descriptor.isInline) {
                schemaOf(descriptor.getElementDescriptor(0))
            } else {
                when (descriptor.kind) {
                    PrimitiveKind.STRING, PrimitiveKind.CHAR -> typed("string")
                    PrimitiveKind.BOOLEAN -> typed("boolean")
                    PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT, PrimitiveKind.LONG ->
                        typed("integer")
                    PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> typed("number")
                    SerialKind.ENUM -> buildJsonObject {
                        put("type", "string")
                        putJsonArray("enum") { descriptor.elementNames.forEach { add(JsonPrimitive(it)) } }
                    }
                    StructureKind.LIST -> buildJsonObject {
                        put("type", "array")
                        put("items", schemaOf(descriptor.getElementDescriptor(0)))
                    }
                    StructureKind.MAP -> buildJsonObject {
                        put("type", "object")
                        put("additionalProperties", schemaOf(descriptor.getElementDescriptor(1)))
                    }
                    StructureKind.CLASS, StructureKind.OBJECT -> referenceTo(descriptor)
                    else -> throw IllegalArgumentException("Unsupported kind ${descriptor.kind}")
                }
            }
            if (!descriptor.isNullable) {
                return base
            }
            return buildJsonObject {
                put("anyOf", JsonArray(listOf(base, typed("null"))))
            }
        }

        private fun referenceTo(descriptor: SerialDescriptor): JsonObject {
            val name: String = descriptor.serialName.removeSuffix("?")
            if (name !in definitions && inProgress.add(name)) {
                definitions[name] = classSchema(descriptor)
                inProgress.remove(name)
            }
            return buildJsonObject { put("\$ref", "#/definitions/$name") }
        }

        private fun classSchema(descriptor: SerialDescriptor): JsonObject {
            val properties: LinkedHashMap<String, JsonElement> = LinkedHashMap()
            val required: MutableList<String> = mutableListOf()
            for (index: Int in 0 until descriptor.elementsCount) {
                val name: String = descriptor.getElementName(index)
                val property: JsonObject = schemaOf(descriptor.getElementDescriptor(index))
                properties[name] = withDoc(property, descriptor.getElementAnnotations(index))
                if (!descriptor.isElementOptional(index)) {
                    required += name
                }
            }
            val schema: JsonObject = buildJsonObject {
                put("type", "object")
                put("properties", JsonObject(properties))
                put("required", JsonArray(required.map { JsonPrimitive(it) }))
                put("additionalProperties", false)
            }
            return withDoc(schema, descriptor.annotations)
        }

        private fun withDoc(schema: JsonObject, annotations: List<Annotation>): JsonObject {
            val doc: Doc = annotations.filterIsInstance<Doc>().singleOrNull() ?: return schema
            return JsonObject(schema + ("description" to JsonPrimitive(doc.text)))
        }

        private fun typed(type: String): JsonObject = buildJsonObject { put("type", type) }
    }

    private val json: Json = Json { encodeDefaults = false }

    private fun definition(schema: JsonObject, name: String): JsonObject =
        schema.getValue("definitions").jsonObject.getValue(name).jsonObject

    private fun properties(definition: JsonObject): JsonObject =
        definition.getValue("properties").jsonObject

    private fun requiredNames(definition: JsonObject): List<String> =
        definition.getValue("required").jsonArray.map { it.jsonPrimitive.content }

    @Test
    fun publishedSchemaDescribesProductModel() {
        val schema: JsonObject = SchemaGenerator().generate(serializer<Product>().descriptor)

        assertEquals("#/definitions/Product", schema.getValue("\$ref").jsonPrimitive.content)
        assertEquals(setOf("Product", "Category"), schema.getValue("definitions").jsonObject.keys)

        val product: JsonObject = definition(schema, "Product")
        assertEquals("A product offered in the catalog.", product.getValue("description").jsonPrimitive.content)
        assertEquals(
            listOf(
                "sku", "name", "price", "availability", "tags", "attributes", "discount_rate", "category",
            ),
            properties(product).keys.toList(),
        )
        assertEquals(listOf("sku", "name", "price"), requiredNames(product))

        val sku: JsonObject = properties(product).getValue("sku").jsonObject
        assertEquals("string", sku.getValue("type").jsonPrimitive.content)
        assertEquals("Stock keeping unit.", sku.getValue("description").jsonPrimitive.content)
        assertEquals(
            "string",
            properties(product).getValue("price").jsonObject.getValue("type").jsonPrimitive.content,
        )
        assertEquals(
            listOf("IN_STOCK", "back-order", "DISCONTINUED"),
            properties(product).getValue("availability").jsonObject.getValue("enum").jsonArray
                .map { it.jsonPrimitive.content },
        )

        val tags: JsonObject = properties(product).getValue("tags").jsonObject
        assertEquals("array", tags.getValue("type").jsonPrimitive.content)
        assertEquals("string", tags.getValue("items").jsonObject.getValue("type").jsonPrimitive.content)
        val attributes: JsonObject = properties(product).getValue("attributes").jsonObject
        assertEquals(
            "integer",
            attributes.getValue("additionalProperties").jsonObject.getValue("type").jsonPrimitive.content,
        )

        val discount: JsonArray = properties(product).getValue("discount_rate").jsonObject.getValue("anyOf").jsonArray
        assertEquals(
            listOf("number", "null"),
            discount.map { it.jsonObject.getValue("type").jsonPrimitive.content },
        )
        val category: JsonArray = properties(product).getValue("category").jsonObject.getValue("anyOf").jsonArray
        assertEquals("#/definitions/Category", category[0].jsonObject.getValue("\$ref").jsonPrimitive.content)
    }

    @Test
    fun recursiveCategoryTreeIsEmittedOnceAndReferencesItself() {
        val schema: JsonObject = SchemaGenerator().generate(serializer<List<Category>>().descriptor)

        assertEquals("array", schema.getValue("type").jsonPrimitive.content)
        assertEquals(
            "#/definitions/Category",
            schema.getValue("items").jsonObject.getValue("\$ref").jsonPrimitive.content,
        )
        val category: JsonObject = definition(schema, "Category")
        assertEquals("A node of the product category tree.", category.getValue("description").jsonPrimitive.content)
        assertEquals(listOf("id", "title"), requiredNames(category))
        val children: JsonObject = properties(category).getValue("children").jsonObject
        assertEquals(
            "#/definitions/Category",
            children.getValue("items").jsonObject.getValue("\$ref").jsonPrimitive.content,
        )
    }

    @Test
    fun descriptorReflectsTransientRequiredAndInlineDeclarations() {
        val descriptor: SerialDescriptor = serializer<Product>().descriptor

        assertEquals("Product", descriptor.serialName)
        assertEquals(StructureKind.CLASS, descriptor.kind)
        assertFalse("cachedLabel" in descriptor.elementNames)
        assertEquals(-3, descriptor.getElementIndex("cachedLabel"))
        val nameIndex: Int = descriptor.getElementIndex("name")
        assertFalse(descriptor.isElementOptional(nameIndex))
        assertTrue(descriptor.getElementAnnotations(nameIndex).isEmpty())

        val skuDescriptor: SerialDescriptor = descriptor.getElementDescriptor(descriptor.getElementIndex("sku"))
        assertTrue(skuDescriptor.isInline)
        assertEquals(PrimitiveKind.STRING, skuDescriptor.getElementDescriptor(0).kind)
        assertEquals("catalog.Money", descriptor.getElementDescriptor(descriptor.getElementIndex("price")).serialName)

        val discountDescriptor: SerialDescriptor =
            descriptor.getElementDescriptor(descriptor.getElementIndex("discount_rate"))
        assertTrue(discountDescriptor.isNullable)
        assertEquals(PrimitiveKind.DOUBLE, discountDescriptor.kind)
        assertEquals(serializer<Category?>().descriptor, descriptor.getElementDescriptor(7))
    }

    @Test
    fun payloadsSentByTheServiceConformToThePublishedSchema() {
        val schema: JsonObject = SchemaGenerator().generate(serializer<Product>().descriptor)
        val product: JsonObject = definition(schema, "Product")
        val lamp: Product = Product(
            sku = Sku("LMP-01"),
            name = "Desk lamp",
            price = Money(4999, "EUR"),
            availability = Availability.BACK_ORDER,
            tags = setOf("lighting", "office"),
            discountRate = 0.15,
            category = Category(7, "Lighting", listOf(Category(8, "Desk"))),
            cachedLabel = "LMP-01 Desk lamp",
        )

        val payload: JsonObject = json.encodeToJsonElement(serializer<Product>(), lamp).jsonObject

        assertTrue(properties(product).keys.containsAll(payload.keys))
        assertTrue(payload.keys.containsAll(requiredNames(product)))
        assertEquals("LMP-01", payload.getValue("sku").jsonPrimitive.content)
        assertEquals("4999 EUR", payload.getValue("price").jsonPrimitive.content)
        assertEquals("back-order", payload.getValue("availability").jsonPrimitive.content)
        assertFalse("cachedLabel" in payload)
        assertFalse("attributes" in payload)
        assertEquals(lamp.copy(cachedLabel = ""), json.decodeFromJsonElement(serializer<Product>(), payload))

        val minimal: JsonObject = json.encodeToJsonElement(
            serializer<Product>(),
            Product(sku = Sku("PEN-02"), price = Money(150, "EUR")),
        ).jsonObject
        assertEquals(listOf("sku", "name", "price"), minimal.keys.toList())
        assertEquals("", minimal.getValue("name").jsonPrimitive.content)
        assertTrue(minimal.values.none { it is JsonNull })
    }

    @Test
    fun requestMissingRequiredFieldsIsRejectedWithTheirNames() {
        val schema: JsonObject = SchemaGenerator().generate(serializer<Product>().descriptor)
        val request: String = """{"sku":"CHR-03","availability":"DISCONTINUED"}"""

        val failure: MissingFieldException = assertThrows(MissingFieldException::class.java) {
            json.decodeFromString(serializer<Product>(), request)
        }

        assertEquals(listOf("name", "price"), failure.missingFields)
        val sentKeys: Set<String> = json.parseToJsonElement(request).jsonObject.keys
        assertEquals(failure.missingFields, requiredNames(definition(schema, "Product")) - sentKeys)
    }
}
