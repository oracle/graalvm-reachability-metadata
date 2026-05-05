/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_json_jvm

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.decodeToSequence
import kotlinx.serialization.json.double
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

public class Kotlinx_serialization_json_jvmTest {
    @Test
    fun parsesAndBuildsJsonElements(): Unit {
        val parsed: JsonElement = Json.parseToJsonElement(
            """
            {
              "name": "kotlinx",
              "numbers": [1, 2, 3.5],
              "enabled": true,
              "missing": null
            }
            """.trimIndent(),
        )
        val parsedObject: JsonObject = parsed.jsonObject

        assertThat(parsedObject["name"]?.jsonPrimitive?.content).isEqualTo("kotlinx")
        assertThat(parsedObject["numbers"]?.jsonArray?.map { it.jsonPrimitive.double }).containsExactly(1.0, 2.0, 3.5)
        assertThat(parsedObject["enabled"]?.jsonPrimitive?.boolean).isTrue()
        assertThat(parsedObject["missing"]).isSameAs(JsonNull)

        val built: JsonObject = buildJsonObject {
            put("name", parsedObject.getValue("name"))
            put("count", parsedObject.getValue("numbers").jsonArray.size)
            put("nested", buildJsonObject { put("enabled", true) })
            put(
                "labels",
                buildJsonArray {
                    add(JsonPrimitive("json"))
                    add(JsonPrimitive("native-image"))
                },
            )
        }

        assertThat(built["count"]?.jsonPrimitive?.int).isEqualTo(3)
        assertThat(built["nested"]?.jsonObject?.get("enabled")?.jsonPrimitive?.boolean).isTrue()
        assertThat(built["labels"]?.jsonArray?.map { it.jsonPrimitive.content }).containsExactly("json", "native-image")
    }

    @Test
    fun encodesAndDecodesCollectionSerializers(): Unit {
        val json: Json = Json { prettyPrint = false }
        val serializer: KSerializer<List<Map<String, Int>>> = ListSerializer(MapSerializer(String.serializer(), Int.serializer()))
        val input: List<Map<String, Int>> = listOf(
            linkedMapOf("apples" to 3, "oranges" to 5),
            linkedMapOf("pears" to 2),
        )

        val encoded: String = json.encodeToString(serializer, input)
        val decoded: List<Map<String, Int>> = json.decodeFromString(serializer, encoded)

        assertThat(Json.parseToJsonElement(encoded).jsonArray).hasSize(2)
        assertThat(decoded).isEqualTo(input)
    }

    @Test
    fun encodesAndDecodesStructuredValuesThroughJsonTree(): Unit {
        val json: Json = Json { prettyPrint = false }
        val project: Project = Project(
            name = "json tree",
            stars = 13,
            tags = listOf("element", "roundtrip"),
            active = true,
        )

        val encoded: JsonElement = json.encodeToJsonElement(ProjectSerializer, project)
        val encodedObject: JsonObject = encoded.jsonObject
        val edited: JsonObject = JsonObject(
            encodedObject + mapOf(
                "stars" to JsonPrimitive(21),
                "tags" to buildJsonArray {
                    add(JsonPrimitive("element"))
                    add(JsonPrimitive("edited"))
                },
            ),
        )
        val decoded: Project = json.decodeFromJsonElement(ProjectSerializer, edited)

        assertThat(encodedObject.keys).containsExactly("name", "stars", "tags", "active")
        assertThat(encodedObject.getValue("name").jsonPrimitive.content).isEqualTo("json tree")
        assertThat(decoded).isEqualTo(project.copy(stars = 21, tags = listOf("element", "edited")))
    }

    @Test
    fun honorsJsonConfigurationWhenDecodingStructuredValues(): Unit {
        val configuredJson: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        val decoded: Project = configuredJson.decodeFromString(
            ProjectSerializer,
            """
            {
              name: "reachability metadata",
              "stars": 42,
              tags: ["graalvm", "kotlin"],
              active: false,
              unknownField: "ignored"
            }
            """.trimIndent(),
        )

        assertThat(decoded).isEqualTo(
            Project(
                name = "reachability metadata",
                stars = 42,
                tags = listOf("graalvm", "kotlin"),
                active = false,
            ),
        )
        assertThatThrownBy {
            Json.decodeFromString(ProjectSerializer, """{"name":"only-name"}""")
        }.isInstanceOf(SerializationException::class.java)
    }

    @Test
    fun transformsJsonTreeBeforeDecodingAndAfterEncoding(): Unit {
        val json: Json = Json { prettyPrint = false }
        val input: String =
            """{"payload":{"name":"metadata","stars":7,"tags":["json"],"active":true},"ignored":1}"""

        val decoded: Project = json.decodeFromString(WrappedProjectSerializer, input)
        val encoded: String = json.encodeToString(WrappedProjectSerializer, decoded)
        val encodedObject: JsonObject = Json.parseToJsonElement(encoded).jsonObject

        assertThat(decoded.name).isEqualTo("metadata")
        assertThat(encodedObject.keys).containsExactly("payload")
        assertThat(encodedObject.getValue("payload").jsonObject.getValue("stars").jsonPrimitive.int).isEqualTo(7)
    }

    @Test
    fun selectsJsonContentPolymorphicSerializerFromObjectShape(): Unit {
        val module: SerializersModule = SerializersModule {
            polymorphic(Event::class) {
                subclass(MetricEvent::class, MetricEventSerializer)
                subclass(MessageEvent::class, MessageEventSerializer)
            }
        }
        val json: Json = Json { serializersModule = module }
        val serializer: KSerializer<List<Event>> = ListSerializer(EventSerializer)
        val input: String =
            """[{"metric":"requests","value":12},{"message":"started","severity":"INFO"}]"""

        val events: List<Event> = json.decodeFromString(serializer, input)
        val encoded: JsonArray = Json.parseToJsonElement(json.encodeToString(serializer, events)).jsonArray

        assertThat(events).containsExactly(MetricEvent("requests", 12), MessageEvent("started", "INFO"))
        assertThat(encoded[0].jsonObject.keys).containsExactly("metric", "value")
        assertThat(encoded[1].jsonObject.keys).containsExactly("message", "severity")
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun decodesWhitespaceSeparatedJsonValuesFromStream(): Unit {
        val input: String =
            """
            {"name":"metadata","stars":11,"tags":["json","stream"],"active":true}
            {"name":"native","stars":17,"tags":["graalvm"],"active":false}
            """.trimIndent()

        val decoded: List<Project> = input.byteInputStream().use { stream ->
            Json.decodeToSequence(stream, ProjectSerializer, DecodeSequenceMode.WHITESPACE_SEPARATED).toList()
        }

        assertThat(decoded).containsExactly(
            Project(name = "metadata", stars = 11, tags = listOf("json", "stream"), active = true),
            Project(name = "native", stars = 17, tags = listOf("graalvm"), active = false),
        )
    }
}

private data class Project(
    val name: String,
    val stars: Int,
    val tags: List<String>,
    val active: Boolean,
)

private object ProjectSerializer : KSerializer<Project> {
    private val tagListSerializer: KSerializer<List<String>> = ListSerializer(String.serializer())

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Project") {
        element<String>("name")
        element<Int>("stars")
        element("tags", tagListSerializer.descriptor)
        element<Boolean>("active")
    }

    override fun serialize(encoder: Encoder, value: Project): Unit {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeStringElement(descriptor, 0, value.name)
        composite.encodeIntElement(descriptor, 1, value.stars)
        composite.encodeSerializableElement(descriptor, 2, tagListSerializer, value.tags)
        composite.encodeBooleanElement(descriptor, 3, value.active)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): Project {
        val composite = decoder.beginStructure(descriptor)
        var name: String? = null
        var stars: Int? = null
        var tags: List<String>? = null
        var active: Boolean? = null

        loop@ while (true) {
            when (val index: Int = composite.decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break@loop
                0 -> name = composite.decodeStringElement(descriptor, index)
                1 -> stars = composite.decodeIntElement(descriptor, index)
                2 -> tags = composite.decodeSerializableElement(descriptor, index, tagListSerializer)
                3 -> active = composite.decodeBooleanElement(descriptor, index)
                else -> throw SerializationException("Unexpected index $index")
            }
        }
        composite.endStructure(descriptor)

        return Project(
            name = name ?: throw SerializationException("Missing name"),
            stars = stars ?: throw SerializationException("Missing stars"),
            tags = tags ?: throw SerializationException("Missing tags"),
            active = active ?: throw SerializationException("Missing active"),
        )
    }
}

private object WrappedProjectSerializer : JsonTransformingSerializer<Project>(ProjectSerializer) {
    override fun transformDeserialize(element: JsonElement): JsonElement = element.jsonObject.getValue("payload")

    override fun transformSerialize(element: JsonElement): JsonElement = JsonObject(mapOf("payload" to element))
}

private sealed interface Event

private data class MetricEvent(val metric: String, val value: Int) : Event

private data class MessageEvent(val message: String, val severity: String) : Event

private object EventSerializer : JsonContentPolymorphicSerializer<Event>(Event::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<out Event> =
        if ("metric" in element.jsonObject) {
            MetricEventSerializer
        } else {
            MessageEventSerializer
        }
}

private object MetricEventSerializer : KSerializer<MetricEvent> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("MetricEvent") {
        element<String>("metric")
        element<Int>("value")
    }

    override fun serialize(encoder: Encoder, value: MetricEvent): Unit {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeStringElement(descriptor, 0, value.metric)
        composite.encodeIntElement(descriptor, 1, value.value)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): MetricEvent {
        val composite = decoder.beginStructure(descriptor)
        var metric: String? = null
        var value: Int? = null

        loop@ while (true) {
            when (val index: Int = composite.decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break@loop
                0 -> metric = composite.decodeStringElement(descriptor, index)
                1 -> value = composite.decodeIntElement(descriptor, index)
                else -> throw SerializationException("Unexpected index $index")
            }
        }
        composite.endStructure(descriptor)

        return MetricEvent(
            metric = metric ?: throw SerializationException("Missing metric"),
            value = value ?: throw SerializationException("Missing value"),
        )
    }
}

private object MessageEventSerializer : KSerializer<MessageEvent> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("MessageEvent") {
        element<String>("message")
        element<String>("severity")
    }

    override fun serialize(encoder: Encoder, value: MessageEvent): Unit {
        val composite = encoder.beginStructure(descriptor)
        composite.encodeStringElement(descriptor, 0, value.message)
        composite.encodeStringElement(descriptor, 1, value.severity)
        composite.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): MessageEvent {
        val composite = decoder.beginStructure(descriptor)
        var message: String? = null
        var severity: String? = null

        loop@ while (true) {
            when (val index: Int = composite.decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break@loop
                0 -> message = composite.decodeStringElement(descriptor, index)
                1 -> severity = composite.decodeStringElement(descriptor, index)
                else -> throw SerializationException("Unexpected index $index")
            }
        }
        composite.endStructure(descriptor)

        return MessageEvent(
            message = message ?: throw SerializationException("Missing message"),
            severity = severity ?: throw SerializationException("Missing severity"),
        )
    }
}
