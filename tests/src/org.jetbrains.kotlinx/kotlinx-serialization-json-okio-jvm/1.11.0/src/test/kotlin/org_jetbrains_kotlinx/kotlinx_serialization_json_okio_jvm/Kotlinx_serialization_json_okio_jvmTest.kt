/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package org_jetbrains_kotlinx.kotlinx_serialization_json_okio_jvm

import java.io.IOException
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.okio.decodeBufferedSourceToSequence
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.json.okio.encodeToBufferedSink
import okio.Buffer
import okio.Sink
import okio.Timeout
import okio.buffer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

public class Kotlinx_serialization_json_okio_jvmTest {
    @Test
    fun encodesStructuredValueToBufferedSink(): Unit {
        val json: Json = Json { prettyPrint = false }
        val project: Project = Project(
            name = "okio",
            stars = 11,
            tags = listOf("json", "sink"),
            active = true,
        )
        val sink: Buffer = Buffer()

        json.encodeToBufferedSink(ProjectSerializer, project, sink)

        val encoded: String = sink.readUtf8()
        assertThat(encoded).isEqualTo(
            """{"name":"okio","stars":11,"tags":["json","sink"],"active":true}""",
        )
    }

    @Test
    fun decodesStructuredValueFromBufferedSourceWithJsonConfiguration(): Unit {
        val json: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        val source: Buffer = Buffer().writeUtf8(
            """
            {
              name: "metadata",
              "stars": 21,
              tags: ["native-image", "okio"],
              active: false,
              ignored: {"nested": true}
            }
            """.trimIndent(),
        )

        val decoded: Project = json.decodeFromBufferedSource(ProjectSerializer, source)

        assertThat(decoded).isEqualTo(
            Project(
                name = "metadata",
                stars = 21,
                tags = listOf("native-image", "okio"),
                active = false,
            ),
        )
        assertThat(source.exhausted()).isTrue()
    }

    @Test
    fun roundTripsNestedCollectionsThroughAnOkioBuffer(): Unit {
        val serializer: KSerializer<List<Map<String, List<Int>>>> = ListSerializer(
            MapSerializer(String.serializer(), ListSerializer(Int.serializer())),
        )
        val input: List<Map<String, List<Int>>> = listOf(
            linkedMapOf("first" to listOf(1, 2, 3)),
            linkedMapOf("second" to listOf(5, 8), "empty" to emptyList()),
        )
        val buffer: Buffer = Buffer()

        Json.encodeToBufferedSink(serializer, input, buffer)
        val decoded: List<Map<String, List<Int>>> = Json.decodeFromBufferedSource(serializer, buffer)

        assertThat(decoded).isEqualTo(input)
        assertThat(buffer.exhausted()).isTrue()
    }

    @Test
    fun usesReifiedBufferedSinkAndSourceOverloadsForBuiltInTypes(): Unit {
        val input: Map<String, List<Int>> = linkedMapOf(
            "fibonacci" to listOf(1, 1, 2, 3, 5),
            "primes" to listOf(2, 3, 5, 7),
        )
        val buffer: Buffer = Buffer()

        Json.encodeToBufferedSink(input, buffer)
        val decoded: Map<String, List<Int>> = Json.decodeFromBufferedSource(buffer)

        assertThat(decoded).isEqualTo(input)
        assertThat(buffer.exhausted()).isTrue()
    }

    @Test
    fun decodesArrayWrappedSequenceFromBufferedSource(): Unit {
        val source: Buffer = Buffer().writeUtf8(
            """
            [
              {"name":"one","stars":1,"tags":["array"],"active":true},
              {"name":"two","stars":2,"tags":["array","stream"],"active":false}
            ]
            """.trimIndent(),
        )

        val decoded: List<Project> = Json.decodeBufferedSourceToSequence(
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
    fun decodesWhitespaceSeparatedSequenceFromBufferedSource(): Unit {
        val source: Buffer = Buffer().writeUtf8(
            """
            {"name":"alpha","stars":3,"tags":["ws"],"active":true}
            {"name":"beta","stars":5,"tags":["ws","sequence"],"active":false}

            {"name":"gamma","stars":8,"tags":[],"active":true}
            """.trimIndent(),
        )

        val decoded: List<Project> = Json.decodeBufferedSourceToSequence(
            source,
            ProjectSerializer,
            DecodeSequenceMode.WHITESPACE_SEPARATED,
        ).toList()

        assertThat(decoded.map { project: Project -> project.name }).containsExactly("alpha", "beta", "gamma")
        assertThat(decoded.map { project: Project -> project.stars }).containsExactly(3, 5, 8)
        assertThat(source.exhausted()).isTrue()
    }

    @Test
    fun autoDetectsSequenceFormatFromBufferedSource(): Unit {
        val arrayWrappedSource: Buffer = Buffer().writeUtf8("[1, 2, 3]")
        val whitespaceSeparatedSource: Buffer = Buffer().writeUtf8("5\n8 13")

        val arrayWrapped: List<Int> = Json.decodeBufferedSourceToSequence(
            arrayWrappedSource,
            Int.serializer(),
        ).toList()
        val whitespaceSeparated: List<Int> = Json.decodeBufferedSourceToSequence(
            whitespaceSeparatedSource,
            Int.serializer(),
        ).toList()

        assertThat(arrayWrapped).containsExactly(1, 2, 3)
        assertThat(whitespaceSeparated).containsExactly(5, 8, 13)
        assertThat(arrayWrappedSource.exhausted()).isTrue()
        assertThat(whitespaceSeparatedSource.exhausted()).isTrue()
    }

    @Test
    fun decodesJsonTreeFromOkioSource(): Unit {
        val source: Buffer = Buffer().writeUtf8(
            """
            {
              "projects": [
                {"name": "json-okio", "stars": 13},
                {"name": "native", "stars": 17}
              ],
              "count": 2
            }
            """.trimIndent(),
        )

        val decoded: JsonObject = Json.decodeFromBufferedSource(JsonObject.serializer(), source)
        val projects: JsonArray = decoded.getValue("projects").jsonArray

        assertThat(decoded.getValue("count").jsonPrimitive.int).isEqualTo(2)
        assertThat(projects.map { element -> element.jsonObject.getValue("name").jsonPrimitive.content })
            .containsExactly("json-okio", "native")
        assertThat(source.exhausted()).isTrue()
    }

    @Test
    fun malformedOkioSourceReportsSerializationException(): Unit {
        val source: Buffer = Buffer().writeUtf8("""{"name":"broken","tags":[],"active":true}""")

        assertThatThrownBy { Json.decodeFromBufferedSource(ProjectSerializer, source) }
            .isInstanceOf(SerializationException::class.java)
            .hasMessageContaining("Missing stars")
    }

    @Test
    fun encodeToBufferedSinkPropagatesSinkFailures(): Unit {
        val failingSink = object : Sink {
            override fun write(source: Buffer, byteCount: Long): Unit {
                throw IOException("sink is closed")
            }

            override fun flush(): Unit = Unit

            override fun timeout(): Timeout = Timeout.NONE

            override fun close(): Unit = Unit
        }.buffer()

        assertThatThrownBy {
            Json.encodeToBufferedSink(
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
                else -> error("Unexpected element index $index")
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
