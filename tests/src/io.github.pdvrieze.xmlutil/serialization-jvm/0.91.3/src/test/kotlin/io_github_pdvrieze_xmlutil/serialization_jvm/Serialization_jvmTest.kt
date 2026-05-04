/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_pdvrieze_xmlutil.serialization_jvm

import javax.xml.namespace.QName
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlCData
import nl.adaptivity.xmlutil.serialization.XmlChildrenName
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlOtherAttributes
import nl.adaptivity.xmlutil.serialization.XmlSerialException
import nl.adaptivity.xmlutil.serialization.XmlValue
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

public class Serialization_jvmTest {
    private val compactXml: XML = XML {
        xmlDeclMode = XmlDeclMode.None
        indentString = ""
        repairNamespaces = true
    }

    @Test
    fun serializesAttributeAndTextContentWithEscapingAndNamespaceRoot(): Unit {
        val note = Note(id = "n-7", message = "Remember <xml> & native-image")
        val rootName = QName("urn:test-notes", "note", "notes")

        val encoded: String = compactXml.encodeToString(NoteSerializer, note, rootName)
        val decoded: Note = compactXml.decodeFromString(NoteSerializer, encoded, rootName)

        assertThat(encoded).contains("<notes:note", "xmlns:notes=\"urn:test-notes\"", "id=\"n-7\"")
        assertThat(encoded).contains("Remember &lt;xml&gt; &amp; native-image")
        assertThat(decoded).isEqualTo(note)
    }

    @Test
    fun serializesNestedObjectsEnumsAndNamedLists(): Unit {
        val ticket = Ticket(
            id = "GR-913",
            priority = Priority.High,
            title = "Exercise XML serializers",
            comments = listOf("attributes", "nested values", "lists"),
        )

        val rootName = QName("ticket")
        val encoded: String = compactXml.encodeToString(TicketSerializer, ticket, rootName)
        val decoded: Ticket = compactXml.decodeFromString(TicketSerializer, encoded, rootName)

        assertThat(encoded).contains("id=\"GR-913\"", "priority=\"high\"")
        assertThat(encoded).contains("<title>Exercise XML serializers</title>")
        assertThat(encoded).contains("<comment>attributes</comment>", "<comment>nested values</comment>")
        assertThat(decoded).isEqualTo(ticket)
    }

    @Test
    fun roundTripsBuiltInPrimitiveSerializers(): Unit {
        val textRootName = QName("text")
        val numberRootName = QName("number")
        val booleanRootName = QName("enabled")

        val encodedText: String = compactXml.encodeToString(String.serializer(), "alpha & beta", textRootName)
        val encodedNumber: String = compactXml.encodeToString(Int.serializer(), 91, numberRootName)
        val encodedBoolean: String = compactXml.encodeToString(Boolean.serializer(), true, booleanRootName)

        assertThat(encodedText).contains("<text>alpha &amp; beta</text>")
        assertThat(compactXml.decodeFromString(String.serializer(), encodedText, textRootName)).isEqualTo("alpha & beta")
        assertThat(compactXml.decodeFromString(Int.serializer(), encodedNumber, numberRootName)).isEqualTo(91)
        assertThat(compactXml.decodeFromString(Boolean.serializer(), encodedBoolean, booleanRootName)).isTrue()
    }

    @Test
    fun preservesWildcardAttributesInDedicatedAttributeMap(): Unit {
        val item = LabelledItem(
            id = "item-91",
            otherAttributes = mapOf(
                "source" to "agent & test",
                "reviewed" to "true",
            ),
            label = "Native image metadata",
        )

        val encoded: String = compactXml.encodeToString(LabelledItemSerializer, item, QName("item"))
        val decoded: LabelledItem = compactXml.decodeFromString(LabelledItemSerializer, encoded, QName("item"))

        assertThat(encoded)
            .contains("id=\"item-91\"", "source=\"agent &amp; test\"", "reviewed=\"true\"")
            .contains("<label>Native image metadata</label>")
        assertThat(decoded).isEqualTo(item)
    }

    @Test
    fun serializesCDataValueWithoutEscapingMarkup(): Unit {
        val snippet = CDataSnippet(content = "<section>Tom & Jerry</section>")

        val encoded: String = compactXml.encodeToString(CDataSnippetSerializer, snippet, QName("snippet"))
        val decoded: CDataSnippet = compactXml.decodeFromString(CDataSnippetSerializer, encoded, QName("snippet"))

        assertThat(encoded).contains("<![CDATA[<section>Tom & Jerry</section>]]>")
        assertThat(encoded).doesNotContain("&lt;section&gt;Tom &amp; Jerry&lt;/section&gt;")
        assertThat(decoded).isEqualTo(snippet)
    }

    @Test
    fun exposesXmlDescriptorForCustomSerializer(): Unit {
        val descriptor = compactXml.xmlDescriptor(TicketSerializer, QName("ticket"))

        val ticketDescriptor = descriptor.getElementDescriptor(0)

        assertThat(descriptor.tagName.localPart).isEqualTo("ticket")
        assertThat(descriptor.elementsCount).isEqualTo(1)
        assertThat(ticketDescriptor.serialDescriptor.serialName).isEqualTo("Ticket")
        assertThat(ticketDescriptor.elementsCount).isEqualTo(4)
        assertThat(ticketDescriptor.getElementDescriptor(0).tagName.localPart).isEqualTo("id")
        assertThat(ticketDescriptor.getElementDescriptor(2).tagName.localPart).isEqualTo("title")
    }

    @Test
    fun rejectsInvalidElementsByDefault(): Unit {
        val invalidXml = """
            <note id="n-8">
                <unexpected>extra</unexpected>
                body
            </note>
        """.trimIndent()

        assertThatThrownBy {
            compactXml.decodeFromString(NoteSerializer, invalidXml, QName("note"))
        }.isInstanceOf(XmlSerialException::class.java)
    }

    private data class Note(val id: String, val message: String)

    private data class Ticket(
        val id: String,
        val priority: Priority,
        val title: String,
        val comments: List<String>,
    )

    private data class LabelledItem(
        val id: String,
        val otherAttributes: Map<String, String>,
        val label: String,
    )

    private data class CDataSnippet(val content: String)

    private enum class Priority(val xmlValue: String) {
        Low("low"),
        Normal("normal"),
        High("high"),
    }

    private object PrioritySerializer : KSerializer<Priority> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Priority", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: Priority): Unit {
            encoder.encodeString(value.xmlValue)
        }

        override fun deserialize(decoder: Decoder): Priority {
            val xmlValue: String = decoder.decodeString()
            return Priority.entries.firstOrNull { it.xmlValue == xmlValue }
                ?: throw SerializationException("Unknown priority: $xmlValue")
        }
    }

    private object NoteSerializer : KSerializer<Note> {
        @OptIn(ExperimentalSerializationApi::class)
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Note") {
            element<String>("id", annotations = listOf(XmlElement(false)))
            element<String>("message", annotations = listOf(XmlValue(true)))
        }

        override fun serialize(encoder: Encoder, value: Note): Unit = encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.id)
            encodeStringElement(descriptor, 1, value.message)
        }

        override fun deserialize(decoder: Decoder): Note = decoder.decodeStructure(descriptor) {
            var id: String? = null
            var message: String? = null
            while (true) {
                when (val index: Int = decodeElementIndex(descriptor)) {
                    CompositeDecoder.DECODE_DONE -> break
                    0 -> id = decodeStringElement(descriptor, index)
                    1 -> message = decodeStringElement(descriptor, index)
                    else -> error("Unexpected element index: $index")
                }
            }
            Note(
                id = requireNotNull(id) { "Missing note id" },
                message = requireNotNull(message) { "Missing note message" },
            )
        }
    }

    private object LabelledItemSerializer : KSerializer<LabelledItem> {
        private val otherAttributesSerializer: KSerializer<Map<String, String>> =
            MapSerializer(String.serializer(), String.serializer())

        @OptIn(ExperimentalSerializationApi::class)
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("LabelledItem") {
            element<String>("id", annotations = listOf(XmlElement(false)))
            element(
                "otherAttributes",
                otherAttributesSerializer.descriptor,
                annotations = listOf(XmlOtherAttributes()),
            )
            element<String>("label", annotations = listOf(XmlElement(true)))
        }

        override fun serialize(encoder: Encoder, value: LabelledItem): Unit = encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.id)
            encodeSerializableElement(descriptor, 1, otherAttributesSerializer, value.otherAttributes)
            encodeStringElement(descriptor, 2, value.label)
        }

        override fun deserialize(decoder: Decoder): LabelledItem = decoder.decodeStructure(descriptor) {
            var id: String? = null
            var otherAttributes: Map<String, String> = emptyMap()
            var label: String? = null
            while (true) {
                when (val index: Int = decodeElementIndex(descriptor)) {
                    CompositeDecoder.DECODE_DONE -> break
                    0 -> id = decodeStringElement(descriptor, index)
                    1 -> otherAttributes += decodeSerializableElement(descriptor, index, otherAttributesSerializer)
                    2 -> label = decodeStringElement(descriptor, index)
                    else -> error("Unexpected element index: $index")
                }
            }
            LabelledItem(
                id = requireNotNull(id) { "Missing item id" },
                otherAttributes = otherAttributes,
                label = requireNotNull(label) { "Missing item label" },
            )
        }
    }

    private object CDataSnippetSerializer : KSerializer<CDataSnippet> {
        @OptIn(ExperimentalSerializationApi::class)
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("CDataSnippet") {
            element<String>("content", annotations = listOf(XmlValue(true), XmlCData(true)))
        }

        override fun serialize(encoder: Encoder, value: CDataSnippet): Unit = encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.content)
        }

        override fun deserialize(decoder: Decoder): CDataSnippet = decoder.decodeStructure(descriptor) {
            var content: String? = null
            while (true) {
                when (val index: Int = decodeElementIndex(descriptor)) {
                    CompositeDecoder.DECODE_DONE -> break
                    0 -> content = decodeStringElement(descriptor, index)
                    else -> error("Unexpected element index: $index")
                }
            }
            CDataSnippet(content = requireNotNull(content) { "Missing snippet content" })
        }
    }

    private object TicketSerializer : KSerializer<Ticket> {
        private val commentsSerializer: KSerializer<List<String>> = ListSerializer(String.serializer())

        @OptIn(ExperimentalSerializationApi::class)
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Ticket") {
            element<String>("id", annotations = listOf(XmlElement(false)))
            element("priority", PrioritySerializer.descriptor, annotations = listOf(XmlElement(false)))
            element<String>("title", annotations = listOf(XmlElement(true)))
            element(
                "comments",
                commentsSerializer.descriptor,
                annotations = listOf(XmlElement(true), XmlChildrenName("comment", "", "")),
            )
        }

        override fun serialize(encoder: Encoder, value: Ticket): Unit = encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.id)
            encodeSerializableElement(descriptor, 1, PrioritySerializer, value.priority)
            encodeStringElement(descriptor, 2, value.title)
            encodeSerializableElement(descriptor, 3, commentsSerializer, value.comments)
        }

        override fun deserialize(decoder: Decoder): Ticket = decoder.decodeStructure(descriptor) {
            var id: String? = null
            var priority: Priority? = null
            var title: String? = null
            var comments: List<String> = emptyList()
            while (true) {
                when (val index: Int = decodeElementIndex(descriptor)) {
                    CompositeDecoder.DECODE_DONE -> break
                    0 -> id = decodeStringElement(descriptor, index)
                    1 -> priority = decodeSerializableElement(descriptor, index, PrioritySerializer)
                    2 -> title = decodeStringElement(descriptor, index)
                    3 -> comments = decodeSerializableElement(descriptor, index, commentsSerializer)
                    else -> error("Unexpected element index: $index")
                }
            }
            Ticket(
                id = requireNotNull(id) { "Missing ticket id" },
                priority = requireNotNull(priority) { "Missing ticket priority" },
                title = requireNotNull(title) { "Missing ticket title" },
                comments = comments,
            )
        }
    }
}
