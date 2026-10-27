/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_core_jvm

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInput
import java.io.DataInputStream
import java.io.DataOutput
import java.io.DataOutputStream
import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.modules.SerializersModule
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises a user-defined binary format built on `AbstractEncoder` and `AbstractDecoder`,
 * driven by hand-written `KSerializer`s that describe their shape with
 * `buildClassSerialDescriptor` and the structured `encodeStructure`/`decodeStructure` API.
 *
 * An edge gateway packs sensor readings into a compact `DataOutput` frame before uploading them,
 * and the ingestion service reads the frame back through the same serializers.
 */
@OptIn(ExperimentalSerializationApi::class)
public class TelemetryBatchCustomBinaryFormatTest {

    /** Unit of measure a sensor reports in, written as its lower-case symbol. */
    public enum class MeasureUnit(public val symbol: String) {
        CELSIUS("c"),
        PERCENT("pct"),
        HECTOPASCAL("hpa");

        public companion object {
            public fun ofSymbol(symbol: String): MeasureUnit =
                entries.firstOrNull { it.symbol == symbol }
                    ?: throw SerializationException("Unknown measure unit '$symbol'")
        }
    }

    /** One measurement taken by a sensor. */
    public data class Reading(
        val sensorId: String,
        val value: Double,
        val unit: MeasureUnit,
        val tags: List<String>,
        val calibrationNote: String?,
    )

    /** The frame a gateway uploads: its identity, a sequence number and the readings. */
    public data class TelemetryBatch(
        val gatewayId: String,
        val sequence: Long,
        val compressed: Boolean,
        val readings: List<Reading>,
        val counters: Map<String, Int>,
    )

    /** Writes a measure unit as its symbol rather than its ordinal. */
    public object MeasureUnitSerializer : KSerializer<MeasureUnit> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("gateway.MeasureUnit", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: MeasureUnit) {
            encoder.encodeString(value.symbol)
        }

        override fun deserialize(decoder: Decoder): MeasureUnit =
            MeasureUnit.ofSymbol(decoder.decodeString())
    }

    /** Field-by-field serializer for a reading, tolerant of formats that reorder elements. */
    public object ReadingSerializer : KSerializer<Reading> {
        private val tagsSerializer: KSerializer<List<String>> = ListSerializer(String.serializer())
        private val noteSerializer: KSerializer<String?> = String.serializer().nullable

        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("gateway.Reading") {
            element<String>("sensorId")
            element<Double>("value")
            element("unit", MeasureUnitSerializer.descriptor)
            element("tags", tagsSerializer.descriptor)
            element("calibrationNote", noteSerializer.descriptor, isOptional = true)
        }

        override fun serialize(encoder: Encoder, value: Reading) {
            encoder.encodeStructure(descriptor) {
                encodeStringElement(descriptor, 0, value.sensorId)
                encodeDoubleElement(descriptor, 1, value.value)
                encodeSerializableElement(descriptor, 2, MeasureUnitSerializer, value.unit)
                encodeSerializableElement(descriptor, 3, tagsSerializer, value.tags)
                encodeNullableSerializableElement(descriptor, 4, String.serializer(), value.calibrationNote)
            }
        }

        override fun deserialize(decoder: Decoder): Reading = decoder.decodeStructure(descriptor) {
            var sensorId: String? = null
            var value: Double? = null
            var unit: MeasureUnit? = null
            var tags: List<String> = emptyList()
            var calibrationNote: String? = null
            while (true) {
                when (val index: Int = decodeElementIndex(descriptor)) {
                    0 -> sensorId = decodeStringElement(descriptor, 0)
                    1 -> value = decodeDoubleElement(descriptor, 1)
                    2 -> unit = decodeSerializableElement(descriptor, 2, MeasureUnitSerializer)
                    3 -> tags = decodeSerializableElement(descriptor, 3, tagsSerializer)
                    4 -> calibrationNote = decodeNullableSerializableElement(descriptor, 4, noteSerializer)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> throw SerializationException("Unexpected element index $index")
                }
            }
            Reading(
                sensorId = sensorId ?: throw SerializationException("sensorId is missing"),
                value = value ?: throw SerializationException("value is missing"),
                unit = unit ?: throw SerializationException("unit is missing"),
                tags = tags,
                calibrationNote = calibrationNote,
            )
        }
    }

    /** Serializer for a whole batch, composing the reading serializer with built-in collections. */
    public object TelemetryBatchSerializer : KSerializer<TelemetryBatch> {
        private val readingsSerializer: KSerializer<List<Reading>> = ListSerializer(ReadingSerializer)
        private val countersSerializer: KSerializer<Map<String, Int>> =
            MapSerializer(String.serializer(), Int.serializer())

        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("gateway.TelemetryBatch") {
            element<String>("gatewayId")
            element<Long>("sequence")
            element<Boolean>("compressed")
            element("readings", readingsSerializer.descriptor)
            element("counters", countersSerializer.descriptor)
        }

        override fun serialize(encoder: Encoder, value: TelemetryBatch) {
            encoder.encodeStructure(descriptor) {
                encodeStringElement(descriptor, 0, value.gatewayId)
                encodeLongElement(descriptor, 1, value.sequence)
                encodeBooleanElement(descriptor, 2, value.compressed)
                encodeSerializableElement(descriptor, 3, readingsSerializer, value.readings)
                encodeSerializableElement(descriptor, 4, countersSerializer, value.counters)
            }
        }

        override fun deserialize(decoder: Decoder): TelemetryBatch = decoder.decodeStructure(descriptor) {
            var gatewayId: String? = null
            var sequence: Long? = null
            var compressed: Boolean? = null
            var readings: List<Reading> = emptyList()
            var counters: Map<String, Int> = emptyMap()
            while (true) {
                when (val index: Int = decodeElementIndex(descriptor)) {
                    0 -> gatewayId = decodeStringElement(descriptor, 0)
                    1 -> sequence = decodeLongElement(descriptor, 1)
                    2 -> compressed = decodeBooleanElement(descriptor, 2)
                    3 -> readings = decodeSerializableElement(descriptor, 3, readingsSerializer)
                    4 -> counters = decodeSerializableElement(descriptor, 4, countersSerializer)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> throw SerializationException("Unexpected element index $index")
                }
            }
            TelemetryBatch(
                gatewayId = gatewayId ?: throw SerializationException("gatewayId is missing"),
                sequence = sequence ?: throw SerializationException("sequence is missing"),
                compressed = compressed ?: throw SerializationException("compressed is missing"),
                readings = readings,
                counters = counters,
            )
        }
    }

    /** Writes every element in declaration order, prefixing collections with their size. */
    public class FrameEncoder(
        private val output: DataOutput,
        override val serializersModule: SerializersModule,
    ) : AbstractEncoder() {
        override fun encodeBoolean(value: Boolean) {
            output.writeByte(if (value) 1 else 0)
        }

        override fun encodeByte(value: Byte) {
            output.writeByte(value.toInt())
        }

        override fun encodeShort(value: Short) {
            output.writeShort(value.toInt())
        }

        override fun encodeInt(value: Int) {
            output.writeInt(value)
        }

        override fun encodeLong(value: Long) {
            output.writeLong(value)
        }

        override fun encodeFloat(value: Float) {
            output.writeFloat(value)
        }

        override fun encodeDouble(value: Double) {
            output.writeDouble(value)
        }

        override fun encodeChar(value: Char) {
            output.writeChar(value.code)
        }

        override fun encodeString(value: String) {
            output.writeUTF(value)
        }

        override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
            output.writeInt(index)
        }

        override fun beginCollection(descriptor: SerialDescriptor, collectionSize: Int): CompositeEncoder {
            encodeInt(collectionSize)
            return this
        }

        override fun encodeNull() {
            encodeBoolean(false)
        }

        override fun encodeNotNullMark() {
            encodeBoolean(true)
        }
    }

    /** Reads a frame written by [FrameEncoder], relying on its fixed element order. */
    public class FrameDecoder(
        private val input: DataInput,
        override val serializersModule: SerializersModule,
        private var elementsCount: Int = 0,
    ) : AbstractDecoder() {
        private var elementIndex: Int = 0

        override fun decodeBoolean(): Boolean = input.readByte().toInt() != 0

        override fun decodeByte(): Byte = input.readByte()

        override fun decodeShort(): Short = input.readShort()

        override fun decodeInt(): Int = input.readInt()

        override fun decodeLong(): Long = input.readLong()

        override fun decodeFloat(): Float = input.readFloat()

        override fun decodeDouble(): Double = input.readDouble()

        override fun decodeChar(): Char = input.readChar()

        override fun decodeString(): String = input.readUTF()

        override fun decodeEnum(enumDescriptor: SerialDescriptor): Int = input.readInt()

        override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
            if (elementIndex == elementsCount) return CompositeDecoder.DECODE_DONE
            return elementIndex++
        }

        override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder =
            FrameDecoder(input, serializersModule, descriptor.elementsCount)

        override fun decodeSequentially(): Boolean = true

        override fun decodeCollectionSize(descriptor: SerialDescriptor): Int =
            decodeInt().also { elementsCount = it }

        override fun decodeNotNullMark(): Boolean = decodeBoolean()
    }

    /** The gateway's wire format, exposed through the format-agnostic `BinaryFormat` interface. */
    public class FrameFormat(
        override val serializersModule: SerializersModule = SerializersModule { },
    ) : BinaryFormat {
        override fun <T> encodeToByteArray(serializer: SerializationStrategy<T>, value: T): ByteArray {
            val bytes = ByteArrayOutputStream()
            DataOutputStream(bytes).use { output ->
                FrameEncoder(output, serializersModule).encodeSerializableValue(serializer, value)
            }
            return bytes.toByteArray()
        }

        override fun <T> decodeFromByteArray(deserializer: DeserializationStrategy<T>, bytes: ByteArray): T =
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                FrameDecoder(input, serializersModule).decodeSerializableValue(deserializer)
            }
    }

    private val format: BinaryFormat = FrameFormat(
        SerializersModule {
            contextual(MeasureUnit::class, MeasureUnitSerializer)
        },
    )

    private val batch: TelemetryBatch = TelemetryBatch(
        gatewayId = "gw-north-07",
        sequence = 4_102L,
        compressed = false,
        readings = listOf(
            Reading("greenhouse-1/temp", 21.5, MeasureUnit.CELSIUS, listOf("zone:a", "indoor"), null),
            Reading("greenhouse-1/humidity", 64.0, MeasureUnit.PERCENT, emptyList(), "recalibrated 2026-09"),
            Reading("roof/pressure", 1013.25, MeasureUnit.HECTOPASCAL, listOf("outdoor"), null),
        ),
        counters = linkedMapOf("dropped" to 2, "retried" to 5),
    )

    @Test
    fun gatewayFrameRoundTripsThroughTheCustomFormat() {
        val frame: ByteArray = format.encodeToByteArray(TelemetryBatchSerializer, batch)
        val restored: TelemetryBatch = format.decodeFromByteArray(TelemetryBatchSerializer, frame)

        assertEquals(batch, restored)
        assertNull(restored.readings[0].calibrationNote)
        assertEquals("recalibrated 2026-09", restored.readings[1].calibrationNote)
        assertEquals(listOf("dropped", "retried"), restored.counters.keys.toList())
    }

    @Test
    fun frameLayoutFollowsTheDescriptorOrder() {
        val reading: Reading = Reading("s", 1.0, MeasureUnit.PERCENT, listOf("t"), null)
        val frame: ByteArray = format.encodeToByteArray(ReadingSerializer, reading)

        val expected: ByteArray = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeUTF("s")
                output.writeDouble(1.0)
                output.writeUTF("pct")
                output.writeInt(1)
                output.writeUTF("t")
                output.writeByte(0)
            }
        }.toByteArray()
        assertArrayEquals(expected, frame)
    }

    @Test
    fun builtInSerializersWorkThroughTheReifiedFormatExtensions() {
        val history: List<Map<String, Long>> = listOf(mapOf("a" to 1L), mapOf("b" to 2L, "c" to 3L))
        val frame: ByteArray = format.encodeToByteArray(history)

        assertEquals(history, format.decodeFromByteArray<List<Map<String, Long>>>(frame))
    }

    @Test
    fun descriptorsDescribeTheWireShape() {
        val descriptor: SerialDescriptor = ReadingSerializer.descriptor

        assertEquals("gateway.Reading", descriptor.serialName)
        assertEquals(StructureKind.CLASS, descriptor.kind)
        assertEquals(
            listOf("sensorId", "value", "unit", "tags", "calibrationNote"),
            descriptor.elementNames.toList(),
        )
        assertTrue(descriptor.isElementOptional(4))
        assertTrue(descriptor.getElementDescriptor(4).isNullable)
        assertEquals(StructureKind.LIST, descriptor.getElementDescriptor(3).kind)
        assertEquals(
            StructureKind.MAP,
            TelemetryBatchSerializer.descriptor.getElementDescriptor(
                TelemetryBatchSerializer.descriptor.getElementIndex("counters"),
            ).kind,
        )
        assertEquals(
            MeasureUnitSerializer,
            format.serializersModule.getContextual(MeasureUnit::class),
        )
    }

    @Test
    fun corruptFrameIsRejectedBySerializerValidation() {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeUTF("s")
            output.writeDouble(1.0)
            output.writeUTF("kelvin")
        }

        val error: SerializationException = assertThrows(SerializationException::class.java) {
            format.decodeFromByteArray(ReadingSerializer, bytes.toByteArray())
        }
        assertEquals("Unknown measure unit 'kelvin'", error.message)
    }
}
