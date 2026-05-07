/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package org_jetbrains_kotlinx.kotlinx_serialization_json_io_jvm

import java.io.IOException
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.buffered
import kotlinx.io.readString
import kotlinx.io.writeString
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
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.io.decodeFromSource
import kotlinx.serialization.json.io.decodeSourceToSequence
import kotlinx.serialization.json.io.encodeToSink
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

public class Kotlinx_serialization_json_io_jvmTest {
    @Test
    fun encodesStructuredValueToKotlinxIoSink(): Unit {
        val json: Json = Json { prettyPrint = false }
        val project: Project = Project(
            name = "json-io",
            stars = 11,
            tags = listOf("json", "sink"),
            active = true,
        )
        val sink: Buffer = Buffer()

        json.encodeToSink(ProjectSerializer, project, sink)

        assertThat(sink.readString()).isEqualTo(
            """{"name":"json-io","stars":11,"tags":["json","sink"],"active":true}""",
        )
        assertThat(sink.exhausted()).isTrue()
    }

    @Test
    fun decodesStructuredValueFromKotlinxIoSourceWithJsonConfiguration(): Unit {
        val json: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        val source: Buffer = Buffer().apply {
            writeString(
                """
                {
                  name: "metadata",
                  "stars": 21,
                  tags: ["native-image", "io"],
                  active: false,
                  ignored: {"nested": true}
                }
                """.trimIndent(),
            )
        }

        val decoded: Project = json.decodeFromSource(ProjectSerializer, source)

        assertThat(decoded).isEqualTo(
            Project(
                name = "metadata",
                stars = 21,
                tags = listOf("native-image", "io"),
                active = false,
            ),
        )
        assertThat(source.exhausted()).isTrue()
    }

    @Test
    fun roundTripsNestedCollectionsThroughKotlinxIoBuffer(): Unit {
        val serializer: KSerializer<List<Map<String, List<Int>>>> = ListSerializer(
            MapSerializer(String.serializer(), ListSerializer(Int.serializer())),
        )
        val input: List<Map<String, List<Int>>> = listOf(
            linkedMapOf("first" to listOf(1, 2, 3)),
            linkedMapOf("second" to listOf(5, 8), "empty" to emptyList()),
        )
        val buffer: Buffer = Buffer()

        Json.encodeToSink(serializer, input, buffer)
        val decoded: List<Map<String, List<Int>>> = Json.decodeFromSource(serializer, buffer)

        assertThat(decoded).isEqualTo(input)
        assertThat(buffer.exhausted()).isTrue()
    }

    @Test
    fun usesReifiedSourceAndSinkOverloadsForBuiltInSerializers(): Unit {
        val input: Map<String, List<Int>> = linkedMapOf(
            "fibonacci" to listOf(1, 1, 2, 3, 5),
            "primes" to listOf(2, 3, 5, 7),
        )
        val buffer: Buffer = Buffer()

        Json.encodeToSink(input, buffer)
        val decoded: Map<String, List<Int>> = Json.decodeFromSource(buffer)

        assertThat(decoded).isEqualTo(input)
        assertThat(buffer.exhausted()).isTrue()
    }

    @Test
    fun decodesArrayWrappedSequenceFromSource(): Unit {
        val source: Buffer = Buffer().apply {
            writeString(
                """
                [
                  {"name":"one","stars":1,"tags":["array"],"active":true},
                  {"name":"two","stars":2,"tags":["array","stream"],"active":false}
                ]
                """.trimIndent(),
            )
        }

        val decoded: List<Project> = Json.decodeSourceToSequence(
            source,
            ProjectSerializer,
            DecodeSequenceMode.ARRAY_WRAPPED,
        ).toList()

        assertThat(decoded).containsExactly(
            Project(name = "one", stars = 1, tags = listOf("array"), active = true),
            Project(name = "two", stars = 2, tags = listOf("array", "stream"), active = false),
        )
        assertThat(source.exhausted()).isTrue()
    }

    @Test
    fun decodesWhitespaceSeparatedSequenceFromSource(): Unit {
        val source: Buffer = Buffer().apply {
            writeString(
                """
                {"name":"alpha","stars":3,"tags":["ws"],"active":true}
                {"name":"beta","stars":5,"tags":["ws","sequence"],"active":false}

                {"name":"gamma","stars":8,"tags":[],"active":true}
                """.trimIndent(),
            )
        }

        val decoded: List<Project> = Json.decodeSourceToSequence(
            source,
            ProjectSerializer,
            DecodeSequenceMode.WHITESPACE_SEPARATED,
        ).toList()

        assertThat(decoded.map { project: Project -> project.name }).containsExactly("alpha", "beta", "gamma")
        assertThat(decoded.map { project: Project -> project.stars }).containsExactly(3, 5, 8)
        assertThat(source.exhausted()).isTrue()
    }

    @Test
    fun autoDetectsSequenceFormatFromSource(): Unit {
        val arrayWrappedSource: Buffer = Buffer().apply { writeString("[1, 2, 3]") }
        val whitespaceSeparatedSource: Buffer = Buffer().apply { writeString("5\n8 13") }

        val arrayWrapped: List<Int> = Json.decodeSourceToSequence(
            arrayWrappedSource,
            Int.serializer(),
        ).toList()
        val whitespaceSeparated: List<Int> = Json.decodeSourceToSequence(
            whitespaceSeparatedSource,
            Int.serializer(),
        ).toList()

        assertThat(arrayWrapped).containsExactly(1, 2, 3)
        assertThat(whitespaceSeparated).containsExactly(5, 8, 13)
        assertThat(arrayWrappedSource.exhausted()).isTrue()
        assertThat(whitespaceSeparatedSource.exhausted()).isTrue()
    }

    @Test
    fun decodesJsonTreeFromSource(): Unit {
        val source: Buffer = Buffer().apply {
            writeString(
                """
                {
                  "projects": [
                    {"name": "json-io", "stars": 13},
                    {"name": "native", "stars": 17}
                  ],
                  "count": 2
                }
                """.trimIndent(),
            )
        }

        val decoded: JsonObject = Json.decodeFromSource(JsonObject.serializer(), source)
        val projects: JsonArray = decoded.getValue("projects").jsonArray

        assertThat(decoded.getValue("count").jsonPrimitive.int).isEqualTo(2)
        assertThat(projects.map { element -> element.jsonObject.getValue("name").jsonPrimitive.content })
            .containsExactly("json-io", "native")
        assertThat(source.exhausted()).isTrue()
    }

    @Test
    fun malformedSourceReportsSerializationException(): Unit {
        val source: Buffer = Buffer().apply {
            writeString("""{"name":"broken","tags":[],"active":true}""")
        }

        assertThatThrownBy { Json.decodeFromSource(ProjectSerializer, source) }
            .isInstanceOf(SerializationException::class.java)
            .hasMessageContaining("Missing stars")
    }

    @Test
    fun encodeToSinkPropagatesSinkFailures(): Unit {
        val failingSink = object : RawSink {
            override fun write(source: Buffer, byteCount: Long): Unit {
                throw IOException("sink is closed")
            }

            override fun flush(): Unit = Unit

            override fun close(): Unit = Unit
        }.buffered()

        assertThatThrownBy {
            Json.encodeToSink(
                ProjectSerializer,
                Project(name = "failure", stars = 1, tags = emptyList(), active = true),
                failingSink,
            )
            failingSink.flush()
        }.isInstanceOf(IOException::class.java)
            .hasMessageContaining("sink is closed")
    }
}

private data class Project(
    val name: String,
    val stars: Int,
    val tags: List<String>,
    val active: Boolean,
)

private object ProjectSerializer : KSerializer<Project> {
    private val tagsSerializer: KSerializer<List<String>> = ListSerializer(String.serializer())

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("tests.Project") {
        element<String>("name")
        element<Int>("stars")
        element("tags", tagsSerializer.descriptor)
        element<Boolean>("active")
    }

    override fun serialize(encoder: Encoder, value: Project): Unit = encoder.encodeStructure(descriptor) {
        encodeStringElement(descriptor, 0, value.name)
        encodeIntElement(descriptor, 1, value.stars)
        encodeSerializableElement(descriptor, 2, tagsSerializer, value.tags)
        encodeBooleanElement(descriptor, 3, value.active)
    }

    override fun deserialize(decoder: Decoder): Project = decoder.decodeStructure(descriptor) {
        var name: String? = null
        var stars: Int? = null
        var tags: List<String> = emptyList()
        var active: Boolean? = null

        while (true) {
            when (val index: Int = decodeElementIndex(descriptor)) {
                0 -> name = decodeStringElement(descriptor, 0)
                1 -> stars = decodeIntElement(descriptor, 1)
                2 -> tags = decodeSerializableElement(descriptor, 2, tagsSerializer)
                3 -> active = decodeBooleanElement(descriptor, 3)
                CompositeDecoder.DECODE_DONE -> break
                else -> throw SerializationException("Unexpected element index $index")
            }
        }

        Project(
            name = name ?: throw SerializationException("Missing name"),
            stars = stars ?: throw SerializationException("Missing stars"),
            tags = tags,
            active = active ?: throw SerializationException("Missing active"),
        )
    }
}
