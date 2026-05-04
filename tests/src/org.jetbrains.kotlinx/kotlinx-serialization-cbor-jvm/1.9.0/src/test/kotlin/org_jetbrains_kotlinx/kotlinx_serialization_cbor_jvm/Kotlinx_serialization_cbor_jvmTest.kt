/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package org_jetbrains_kotlinx.kotlinx_serialization_cbor_jvm

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.cbor.CborArray
import kotlinx.serialization.cbor.CborDecoder
import kotlinx.serialization.cbor.CborEncoder
import kotlinx.serialization.cbor.CborLabel
import kotlinx.serialization.cbor.CborTag
import kotlinx.serialization.cbor.KeyTags
import kotlinx.serialization.cbor.ObjectTags
import kotlinx.serialization.cbor.ValueTags
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

public class Kotlinx_serialization_cbor_jvmTest {
    private val definiteCbor: Cbor = Cbor {
        useDefiniteLengthEncoding = true
    }

    @Test
    fun builtInPrimitiveCollectionAndNullableSerializersRoundTrip(): Unit {
        assertThat(definiteCbor.encodeToByteArray(Int.serializer(), 24).toHex()).isEqualTo("1818")
        assertThat(definiteCbor.decodeFromByteArray(Int.serializer(), "1818".hexToBytes())).isEqualTo(24)

        assertThat(definiteCbor.encodeToByteArray(Int.serializer(), -25).toHex()).isEqualTo("3818")
        assertThat(definiteCbor.decodeFromByteArray(Int.serializer(), "3818".hexToBytes())).isEqualTo(-25)

        val listSerializer: KSerializer<List<Int>> = ListSerializer(Int.serializer())
        val numbers: List<Int> = listOf(1, -1, 24, 256)
        assertThat(definiteCbor.encodeToByteArray(listSerializer, numbers).toHex()).isEqualTo("8401201818190100")
        assertThat(definiteCbor.decodeFromByteArray(listSerializer, "8401201818190100".hexToBytes())).isEqualTo(numbers)

        val mapSerializer: KSerializer<Map<String, List<Int>>> = MapSerializer(String.serializer(), listSerializer)
        val nested: Map<String, List<Int>> = linkedMapOf("a" to listOf(1, 2), "b" to listOf(3))
        assertThat(definiteCbor.encodeToByteArray(mapSerializer, nested).toHex()).isEqualTo("a2616182010261628103")
        assertThat(definiteCbor.decodeFromByteArray(mapSerializer, "a2616182010261628103".hexToBytes())).isEqualTo(nested)

        val nullableIntSerializer: KSerializer<Int?> = Int.serializer().nullable
        assertThat(definiteCbor.encodeToByteArray(nullableIntSerializer, null).toHex()).isEqualTo("f6")
        assertThat(definiteCbor.decodeFromByteArray(nullableIntSerializer, "f6".hexToBytes())).isNull()
        assertThat(definiteCbor.decodeFromByteArray(Boolean.serializer(), "f5".hexToBytes())).isTrue()
        assertThat(definiteCbor.decodeFromByteArray(String.serializer(), "694b6f746c696e20cf80".hexToBytes()))
            .isEqualTo("Kotlin π")
    }

    @Test
    fun byteArraysCanBeEncodedAsCborByteStrings(): Unit {
        val payload: ByteArray = byteArrayOf(0, 1, 2, 3, 127, -128, -1)
        val cbor: Cbor = Cbor {
            alwaysUseByteString = true
            useDefiniteLengthEncoding = true
        }

        val encoded: ByteArray = cbor.encodeToByteArray(ByteArraySerializer(), payload)

        assertThat(encoded.toHex()).isEqualTo("47000102037f80ff")
        assertArrayEquals(payload, cbor.decodeFromByteArray(ByteArraySerializer(), encoded))
    }

    @Test
    fun elementByteStringAnnotationEncodesSelectedByteArraysAsByteStrings(): Unit {
        val packet: BinaryPacket = BinaryPacket(byteArrayOf(10, 20, 30), listOf(1, 2, 3))
        val encoded: ByteArray = definiteCbor.encodeToByteArray(BinaryPacketSerializer, packet)

        assertThat(encoded.toHex()).isEqualTo("a2677061796c6f6164430a141e67766f6c7461676583010203")
        val decoded: BinaryPacket = definiteCbor.decodeFromByteArray(BinaryPacketSerializer, encoded)
        assertArrayEquals(packet.payload, decoded.payload)
        assertThat(decoded.voltageSamples).isEqualTo(packet.voltageSamples)
    }

    @Test
    fun cborLabelsCanReplacePropertyNamesAndUnknownKeysCanBeIgnored(): Unit {
        val cbor: Cbor = Cbor {
            ignoreUnknownKeys = true
            preferCborLabelsOverNames = true
            useDefiniteLengthEncoding = true
        }
        val alice: Person = Person(name = "Alice", age = 41, aliases = listOf("ally", "a"))

        val encoded: ByteArray = cbor.encodeToByteArray(PersonSerializer, alice)

        assertThat(encoded.toHex()).isEqualTo("a30165416c696365021829038264616c6c796161")

        val withUnknownLabeledField: ByteArray = "a40165416c696365021829038264616c6c79616104636f6c64".hexToBytes()
        assertThat(cbor.decodeFromByteArray(PersonSerializer, withUnknownLabeledField)).isEqualTo(alice)

        val strictCbor: Cbor = Cbor {
            preferCborLabelsOverNames = true
            useDefiniteLengthEncoding = true
        }
        assertThatThrownBy { strictCbor.decodeFromByteArray(PersonSerializer, withUnknownLabeledField) }
            .isInstanceOf(SerializationException::class.java)
    }

    @Test
    fun cborArrayAnnotationSerializesClassDescriptorsAsArrays(): Unit {
        val point: Point = Point(x = -2, y = 3)
        val encoded: ByteArray = definiteCbor.encodeToByteArray(PointSerializer, point)

        assertThat(encoded.toHex()).isEqualTo("822103")
        assertThat(definiteCbor.decodeFromByteArray(PointSerializer, encoded)).isEqualTo(point)
    }

    @Test
    fun cborTagsCanBeEncodedAndVerifiedForObjectsKeysAndValues(): Unit {
        val cbor: Cbor = Cbor {
            encodeKeyTags = true
            encodeValueTags = true
            encodeObjectTags = true
            verifyKeyTags = true
            verifyValueTags = true
            verifyObjectTags = true
            useDefiniteLengthEncoding = true
        }
        val resource: TaggedResource = TaggedResource(uri = "https://kotlinlang.org", identifier = 7)

        val encoded: ByteArray = cbor.encodeToByteArray(TaggedResourceSerializer, resource)

        assertThat(cbor.decodeFromByteArray(TaggedResourceSerializer, encoded)).isEqualTo(resource)
        assertThat(encoded.toHex()).startsWith("d9d9f7")
        assertThat(encoded.toHex()).contains("d820")
    }

    @Test
    fun customSerializersCanInspectTheActiveCborInstance(): Unit {
        val cbor: Cbor = Cbor {
            alwaysUseByteString = true
            ignoreUnknownKeys = true
            useDefiniteLengthEncoding = true
        }
        val value: CborAwareValue = CborAwareValue("configured")

        val encoded: ByteArray = cbor.encodeToByteArray(CborAwareValueSerializer, value)

        assertThat(cbor.decodeFromByteArray(CborAwareValueSerializer, encoded)).isEqualTo(value)
    }
}

private data class Person(
    val name: String,
    val age: Int,
    val aliases: List<String>
)

private object PersonSerializer : KSerializer<Person> {
    private val aliasesSerializer: KSerializer<List<String>> = ListSerializer(String.serializer())

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("tests.Person") {
        element<String>("name", annotations = listOf(CborLabel(1)))
        element<Int>("age", annotations = listOf(CborLabel(2)))
        element("aliases", aliasesSerializer.descriptor, annotations = listOf(CborLabel(3)))
    }

    override fun serialize(encoder: Encoder, value: Person): Unit = encoder.encodeStructure(descriptor) {
        encodeStringElement(descriptor, 0, value.name)
        encodeIntElement(descriptor, 1, value.age)
        encodeSerializableElement(descriptor, 2, aliasesSerializer, value.aliases)
    }

    override fun deserialize(decoder: Decoder): Person = decoder.decodeStructure(descriptor) {
        var name: String? = null
        var age: Int? = null
        var aliases: List<String> = emptyList()
        while (true) {
            when (val index: Int = decodeElementIndex(descriptor)) {
                0 -> name = decodeStringElement(descriptor, 0)
                1 -> age = decodeIntElement(descriptor, 1)
                2 -> aliases = decodeSerializableElement(descriptor, 2, aliasesSerializer)
                CompositeDecoder.DECODE_DONE -> break
                else -> error("Unexpected element index $index")
            }
        }
        Person(
            name = requireNotNull(name) { "name is required" },
            age = requireNotNull(age) { "age is required" },
            aliases = aliases
        )
    }
}

private data class Point(val x: Int, val y: Int)

private object PointSerializer : KSerializer<Point> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("tests.Point") {
        annotations = listOf(CborArray())
        element<Int>("x")
        element<Int>("y")
    }

    override fun serialize(encoder: Encoder, value: Point): Unit = encoder.encodeStructure(descriptor) {
        encodeIntElement(descriptor, 0, value.x)
        encodeIntElement(descriptor, 1, value.y)
    }

    override fun deserialize(decoder: Decoder): Point = decoder.decodeStructure(descriptor) {
        var x: Int? = null
        var y: Int? = null
        while (true) {
            when (val index: Int = decodeElementIndex(descriptor)) {
                0 -> x = decodeIntElement(descriptor, 0)
                1 -> y = decodeIntElement(descriptor, 1)
                CompositeDecoder.DECODE_DONE -> break
                else -> error("Unexpected element index $index")
            }
        }
        Point(
            x = requireNotNull(x) { "x is required" },
            y = requireNotNull(y) { "y is required" }
        )
    }
}

private data class BinaryPacket(
    val payload: ByteArray,
    val voltageSamples: List<Int>
)

private object BinaryPacketSerializer : KSerializer<BinaryPacket> {
    private val payloadSerializer: KSerializer<ByteArray> = ByteArraySerializer()
    private val voltageSamplesSerializer: KSerializer<List<Int>> = ListSerializer(Int.serializer())

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("tests.BinaryPacket") {
        element("payload", payloadSerializer.descriptor, annotations = listOf(ByteString()))
        element("voltage", voltageSamplesSerializer.descriptor)
    }

    override fun serialize(encoder: Encoder, value: BinaryPacket): Unit = encoder.encodeStructure(descriptor) {
        encodeSerializableElement(descriptor, 0, payloadSerializer, value.payload)
        encodeSerializableElement(descriptor, 1, voltageSamplesSerializer, value.voltageSamples)
    }

    override fun deserialize(decoder: Decoder): BinaryPacket = decoder.decodeStructure(descriptor) {
        var payload: ByteArray? = null
        var voltageSamples: List<Int> = emptyList()
        while (true) {
            when (val index: Int = decodeElementIndex(descriptor)) {
                0 -> payload = decodeSerializableElement(descriptor, 0, payloadSerializer)
                1 -> voltageSamples = decodeSerializableElement(descriptor, 1, voltageSamplesSerializer)
                CompositeDecoder.DECODE_DONE -> break
                else -> error("Unexpected element index $index")
            }
        }
        BinaryPacket(
            payload = requireNotNull(payload) { "payload is required" },
            voltageSamples = voltageSamples
        )
    }
}

private data class TaggedResource(val uri: String, val identifier: Int)

private object TaggedResourceSerializer : KSerializer<TaggedResource> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("tests.TaggedResource") {
        annotations = listOf(ObjectTags(CborTag.CBOR_SELF_DESCRIBE))
        element<String>("uri", annotations = listOf(ValueTags(CborTag.URI)))
        element<Int>("identifier", annotations = listOf(KeyTags(32uL)))
    }

    override fun serialize(encoder: Encoder, value: TaggedResource): Unit = encoder.encodeStructure(descriptor) {
        encodeStringElement(descriptor, 0, value.uri)
        encodeIntElement(descriptor, 1, value.identifier)
    }

    override fun deserialize(decoder: Decoder): TaggedResource = decoder.decodeStructure(descriptor) {
        var uri: String? = null
        var identifier: Int? = null
        while (true) {
            when (val index: Int = decodeElementIndex(descriptor)) {
                0 -> uri = decodeStringElement(descriptor, 0)
                1 -> identifier = decodeIntElement(descriptor, 1)
                CompositeDecoder.DECODE_DONE -> break
                else -> error("Unexpected element index $index")
            }
        }
        TaggedResource(
            uri = requireNotNull(uri) { "uri is required" },
            identifier = requireNotNull(identifier) { "identifier is required" }
        )
    }
}

private data class CborAwareValue(val text: String)

private object CborAwareValueSerializer : KSerializer<CborAwareValue> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("tests.CborAwareValue") {
        element<String>("text")
    }

    override fun serialize(encoder: Encoder, value: CborAwareValue): Unit {
        val cborEncoder: CborEncoder = encoder as? CborEncoder ?: error("Expected CborEncoder")
        check(cborEncoder.cbor.configuration.alwaysUseByteString)
        check(cborEncoder.cbor.configuration.ignoreUnknownKeys)
        check(cborEncoder.cbor.configuration.useDefiniteLengthEncoding)
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.text)
        }
    }

    override fun deserialize(decoder: Decoder): CborAwareValue {
        val cborDecoder: CborDecoder = decoder as? CborDecoder ?: error("Expected CborDecoder")
        check(cborDecoder.cbor.configuration.alwaysUseByteString)
        check(cborDecoder.cbor.configuration.ignoreUnknownKeys)
        check(cborDecoder.cbor.configuration.useDefiniteLengthEncoding)
        return decoder.decodeStructure(descriptor) {
            var text: String? = null
            while (true) {
                when (val index: Int = decodeElementIndex(descriptor)) {
                    0 -> text = decodeStringElement(descriptor, 0)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected element index $index")
                }
            }
            CborAwareValue(requireNotNull(text) { "text is required" })
        }
    }
}

private fun ByteArray.toHex(): String = joinToString(separator = "") { byte: Byte -> "%02x".format(byte.toInt() and 0xff) }

private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "hex string must have an even number of characters" }
    return chunked(2)
        .map { byte: String -> byte.toInt(radix = 16).toByte() }
        .toByteArray()
}
