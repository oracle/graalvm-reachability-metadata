/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class, kotlinx.serialization.InternalSerializationApi::class)

package org_jetbrains_kotlinx.kotlinx_serialization_core_jvm

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ArraySerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.internal.NamedCompanion
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class PlatformKtTest {
    @Test
    fun findsSerializerOnDefaultCompanion(): Unit {
        val actual: KSerializer<Any> = serializer(DefaultCompanionModel::class.java)

        assertThat(actual).isSameAs(DefaultCompanionModelSerializer)
    }

    @Test
    fun findsSerializerOnNamedCompanion(): Unit {
        val actual: KSerializer<Any> = serializer(NamedCompanionModel::class.java)

        assertThat(actual).isSameAs(NamedCompanionModelSerializer)
    }

    @Test
    fun findsGeneratedStyleNestedSerializerSingleton(): Unit {
        val actual: KSerializer<Any> = serializer(GeneratedStyleModel::class.java)

        assertThat(actual).isSameAs(GeneratedStyleModel.`$serializer`)
    }

    @Test
    fun findsSerializerOnKotlinObjectSingleton(): Unit {
        val actual: KSerializer<Any> = serializer(SerializableSingleton::class.java)

        assertThat(actual).isSameAs(SerializableSingletonSerializer)
    }

    @Test
    fun deserializesReferenceArrayWithNativeArrayConversion(): Unit {
        val arraySerializer: KSerializer<Array<String>> = ArraySerializer(String::class, String.serializer())

        val actual: Array<String> = arraySerializer.deserialize(SequentialStringArrayDecoder(listOf("alpha", "beta")))

        assertThat(actual).containsExactly("alpha", "beta")
        assertThat(actual::class.java.componentType).isSameAs(String::class.java)
    }
}

public class DefaultCompanionModel {
    public companion object {
        public fun serializer(): KSerializer<DefaultCompanionModel> = DefaultCompanionModelSerializer
    }
}

public class NamedCompanionModel {
    @NamedCompanion
    public companion object Custom {
        public fun serializer(): KSerializer<NamedCompanionModel> = NamedCompanionModelSerializer
    }
}

public class GeneratedStyleModel {
    public object `$serializer` : KSerializer<GeneratedStyleModel> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
            "org_jetbrains_kotlinx.kotlinx_serialization_core_jvm.GeneratedStyleModel",
            PrimitiveKind.STRING
        )

        override fun deserialize(decoder: Decoder): GeneratedStyleModel = GeneratedStyleModel()

        override fun serialize(encoder: Encoder, value: GeneratedStyleModel): Unit {
            encoder.encodeString("generated")
        }
    }
}

public object SerializableSingleton {
    public fun serializer(): KSerializer<SerializableSingleton> = SerializableSingletonSerializer
}

private object DefaultCompanionModelSerializer : KSerializer<DefaultCompanionModel> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        "org_jetbrains_kotlinx.kotlinx_serialization_core_jvm.DefaultCompanionModel",
        PrimitiveKind.STRING
    )

    override fun deserialize(decoder: Decoder): DefaultCompanionModel = DefaultCompanionModel()

    override fun serialize(encoder: Encoder, value: DefaultCompanionModel): Unit {
        encoder.encodeString("default")
    }
}

private object NamedCompanionModelSerializer : KSerializer<NamedCompanionModel> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        "org_jetbrains_kotlinx.kotlinx_serialization_core_jvm.NamedCompanionModel",
        PrimitiveKind.STRING
    )

    override fun deserialize(decoder: Decoder): NamedCompanionModel = NamedCompanionModel()

    override fun serialize(encoder: Encoder, value: NamedCompanionModel): Unit {
        encoder.encodeString("named")
    }
}

private object SerializableSingletonSerializer : KSerializer<SerializableSingleton> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        "org_jetbrains_kotlinx.kotlinx_serialization_core_jvm.SerializableSingleton",
        PrimitiveKind.STRING
    )

    override fun deserialize(decoder: Decoder): SerializableSingleton = SerializableSingleton

    override fun serialize(encoder: Encoder, value: SerializableSingleton): Unit {
        encoder.encodeString("singleton")
    }
}

private class SequentialStringArrayDecoder(
    private val values: List<String>
) : AbstractDecoder() {
    private var nextValueIndex: Int = 0

    override val serializersModule: SerializersModule = EmptySerializersModule()

    override fun decodeSequentially(): Boolean = true

    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int = values.size

    override fun decodeString(): String = values[nextValueIndex++]

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int = throw UnsupportedOperationException()
}
