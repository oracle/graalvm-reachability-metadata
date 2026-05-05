/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:OptIn(ExperimentalSerializationApi::class)

package org_jetbrains_kotlinx.kotlinx_serialization_protobuf_jvm

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoIntegerType
import kotlinx.serialization.protobuf.ProtoNumber
import kotlinx.serialization.protobuf.ProtoOneOf
import kotlinx.serialization.protobuf.ProtoPacked
import kotlinx.serialization.protobuf.ProtoType
import kotlinx.serialization.protobuf.schema.ProtoBufSchemaGenerator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class Kotlinx_serialization_protobuf_jvmTest {
    @Test
    fun encodesSimpleMessageWithExpectedWireFormat(): Unit {
        val message = SimpleMessage(id = 150, name = "Ada")

        val encoded: ByteArray = ProtoBuf.encodeToByteArray(message)
        val decoded: SimpleMessage = ProtoBuf.decodeFromByteArray(encoded)

        assertThat(encoded.toHexString()).isEqualTo("0896011203416461")
        assertThat(decoded).isEqualTo(message)
    }

    @Test
    fun decodesHexStringAndIgnoresUnknownFields(): Unit {
        val hexWithUnknownVarintField: String = "089601120341646198067B"

        val decoded: SimpleMessage = ProtoBuf.decodeFromHexString(hexWithUnknownVarintField)
        val reencodedHex: String = ProtoBuf.encodeToHexString(SimpleMessage.serializer(), decoded)

        assertThat(decoded).isEqualTo(SimpleMessage(id = 150, name = "Ada"))
        assertThat(reencodedHex.lowercase()).isEqualTo("0896011203416461")
    }

    @Test
    fun controlsDefaultValueEmissionWithProtoBufBuilder(): Unit {
        val message = DefaultsMessage()
        val skippingDefaults: ByteArray = ProtoBuf.encodeToByteArray(message)
        val encodingDefaults: ByteArray = ProtoBuf { encodeDefaults = true }.encodeToByteArray(message)

        assertThat(skippingDefaults).isEmpty()
        assertThat(encodingDefaults.toHexString()).isEqualTo("0A001007")
        assertThat(ProtoBuf.decodeFromByteArray<DefaultsMessage>(skippingDefaults)).isEqualTo(message)
        assertThat(ProtoBuf.decodeFromByteArray<DefaultsMessage>(encodingDefaults)).isEqualTo(message)
    }

    @Test
    fun roundTripsPackedRepeatedIntegers(): Unit {
        val message = PackedNumbers(values = listOf(2, 0, 17))

        val encoded: ByteArray = ProtoBuf.encodeToByteArray(message)
        val decoded: PackedNumbers = ProtoBuf.decodeFromByteArray(encoded)

        assertThat(encoded.toHexString()).isEqualTo("0A03020011")
        assertThat(decoded).isEqualTo(message)
    }

    @Test
    fun honorsSignedAndFixedIntegerAnnotations(): Unit {
        val message = IntegerTypesMessage(
            delta = -2,
            checksum = 0x01020304,
            stamp = 0x0102030405060708L
        )

        val encoded: ByteArray = ProtoBuf.encodeToByteArray(message)
        val decoded: IntegerTypesMessage = ProtoBuf.decodeFromByteArray(encoded)

        assertThat(encoded.toHexString()).isEqualTo("08031504030201190807060504030201")
        assertThat(decoded).isEqualTo(message)
    }

    @Test
    fun roundTripsNestedMapsListsAndBytes(): Unit {
        val message = ComplexMessage(
            title = "sample",
            attributes = mapOf("priority" to 5, "retries" to 2),
            entries = listOf(ChildMessage(code = 1, label = "created"), ChildMessage(code = 2, label = "sent")),
            payload = byteArrayOf(1, 1, 2, 3, 5, 8)
        )

        val decoded: ComplexMessage = ProtoBuf.decodeFromByteArray(ProtoBuf.encodeToByteArray(message))

        assertThat(decoded.title).isEqualTo(message.title)
        assertThat(decoded.attributes).isEqualTo(message.attributes)
        assertThat(decoded.entries).isEqualTo(message.entries)
        assertThat(decoded.payload).containsExactly(*message.payload)
    }

    @Test
    fun handlesNullableNestedMessageFields(): Unit {
        val absent = NullableChildMessage(label = "empty")
        val present = NullableChildMessage(label = "full", child = ChildMessage(code = 7, label = "attached"))

        val decodedAbsent: NullableChildMessage = ProtoBuf.decodeFromByteArray(ProtoBuf.encodeToByteArray(absent))
        val decodedPresent: NullableChildMessage = ProtoBuf.decodeFromByteArray(ProtoBuf.encodeToByteArray(present))

        assertThat(decodedAbsent).isEqualTo(absent)
        assertThat(decodedPresent).isEqualTo(present)
    }

    @Test
    fun generatesProtoSchemaFromSerialDescriptor(): Unit {
        val schema: String = ProtoBufSchemaGenerator.generateSchemaText(SimpleMessage.serializer().descriptor)

        assertThat(schema).contains("syntax = \"proto2\"")
        assertThat(schema).contains("message SimpleMessage")
        assertThat(schema).contains("int32 id = 1")
        assertThat(schema).contains("string name = 2")
    }

    @Test
    fun roundTripsProtoOneOfAlternatives(): Unit {
        val textMessage = OneOfEnvelope(id = 42, choice = TextChoice(text = "ready"))
        val numberMessage = OneOfEnvelope(id = 43, choice = NumberChoice(number = 99))

        val encodedText: ByteArray = ProtoBuf.encodeToByteArray(textMessage)
        val encodedNumber: ByteArray = ProtoBuf.encodeToByteArray(numberMessage)

        assertThat(encodedText.toHexString()).isEqualTo("082A12057265616479")
        assertThat(encodedNumber.toHexString()).isEqualTo("082B1863")
        assertThat(ProtoBuf.decodeFromByteArray<OneOfEnvelope>(encodedText)).isEqualTo(textMessage)
        assertThat(ProtoBuf.decodeFromByteArray<OneOfEnvelope>(encodedNumber)).isEqualTo(numberMessage)
    }

    @Test
    fun roundTripsEnumFieldsByNumericValue(): Unit {
        val message = DeliveryMessage(status = DeliveryStatus.SENT, description = "left facility")

        val encoded: ByteArray = ProtoBuf.encodeToByteArray(message)
        val decoded: DeliveryMessage = ProtoBuf.decodeFromByteArray(encoded)

        assertThat(encoded.toHexString()).isEqualTo("0801120D6C65667420666163696C697479")
        assertThat(decoded).isEqualTo(message)
    }
}

@Serializable
private data class SimpleMessage(
    @ProtoNumber(1)
    val id: Int,
    @ProtoNumber(2)
    val name: String
)

@Serializable
private data class DefaultsMessage(
    @ProtoNumber(1)
    val name: String = "",
    @ProtoNumber(2)
    val count: Int = 7
)

@Serializable
private data class PackedNumbers(
    @ProtoNumber(1)
    @ProtoPacked
    val values: List<Int>
)

@Serializable
private data class IntegerTypesMessage(
    @ProtoNumber(1)
    @ProtoType(ProtoIntegerType.SIGNED)
    val delta: Int,
    @ProtoNumber(2)
    @ProtoType(ProtoIntegerType.FIXED)
    val checksum: Int,
    @ProtoNumber(3)
    @ProtoType(ProtoIntegerType.FIXED)
    val stamp: Long
)

@Serializable
private data class ComplexMessage(
    @ProtoNumber(1)
    val title: String,
    @ProtoNumber(2)
    val attributes: Map<String, Int>,
    @ProtoNumber(3)
    val entries: List<ChildMessage>,
    @ProtoNumber(4)
    val payload: ByteArray
)

@Serializable
private data class ChildMessage(
    @ProtoNumber(1)
    val code: Int,
    @ProtoNumber(2)
    val label: String
)

@Serializable
private data class NullableChildMessage(
    @ProtoNumber(1)
    val label: String,
    @ProtoNumber(2)
    val child: ChildMessage? = null
)

@Serializable
private data class OneOfEnvelope(
    @ProtoNumber(1)
    val id: Int,
    @ProtoOneOf
    val choice: OneOfChoice
)

@Serializable
private sealed interface OneOfChoice

@Serializable
@SerialName("text_choice")
private data class TextChoice(
    @ProtoNumber(2)
    val text: String
) : OneOfChoice

@Serializable
@SerialName("number_choice")
private data class NumberChoice(
    @ProtoNumber(3)
    val number: Int
) : OneOfChoice

@Serializable
private data class DeliveryMessage(
    @ProtoNumber(1)
    val status: DeliveryStatus,
    @ProtoNumber(2)
    val description: String
)

@Serializable
private enum class DeliveryStatus {
    CREATED,
    SENT,
    DELIVERED
}

private fun ByteArray.toHexString(): String = joinToString(separator = "") { byte ->
    "%02X".format(byte.toInt() and 0xFF)
}
