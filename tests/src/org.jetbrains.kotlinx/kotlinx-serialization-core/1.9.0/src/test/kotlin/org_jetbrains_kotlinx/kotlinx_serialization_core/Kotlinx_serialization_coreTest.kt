/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_core

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.PairSerializer
import kotlinx.serialization.builtins.TripleSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

@OptIn(ExperimentalSerializationApi::class)
public class Kotlinx_serialization_coreTest {
    @Test
    fun customSerializerEncodesAndDecodesClassDescriptorElements() {
        val value = User(id = 7, name = "Ada Lovelace")
        val encoder = FieldMapEncoder()

        UserSerializer.serialize(encoder, value)

        assertThat(encoder.values).containsExactlyEntriesOf(
            mapOf(
                "id" to 7,
                "name" to "Ada Lovelace",
            ),
        )
        val decoded = UserSerializer.deserialize(FieldMapDecoder(encoder.values))
        assertThat(decoded).isEqualTo(value)
    }

    @Test
    fun nullableSerializerUsesPublicNullEncodingContract() {
        val nullEncoder = FieldMapEncoder()
        UserSerializer.nullable.serialize(nullEncoder, null)

        assertThat(nullEncoder.encodedRootNull).isTrue()
        assertThat(UserSerializer.nullable.deserialize(FieldMapDecoder(rootIsNull = true))).isNull()

        val valueEncoder = FieldMapEncoder()
        val value = User(id = 42, name = "Grace Hopper")
        UserSerializer.nullable.serialize(valueEncoder, value)

        assertThat(valueEncoder.encodedRootNull).isFalse()
        assertThat(UserSerializer.nullable.deserialize(FieldMapDecoder(valueEncoder.values))).isEqualTo(value)
    }

    @Test
    fun descriptorsExposePrimitiveClassListMapAndNullableShapes() {
        val userDescriptor = UserSerializer.descriptor

        assertThat(userDescriptor.serialName).isEqualTo("sample.User")
        assertThat(userDescriptor.kind).isEqualTo(StructureKind.CLASS)
        assertThat(userDescriptor.elementsCount).isEqualTo(2)
        assertThat(userDescriptor.getElementName(0)).isEqualTo("id")
        assertThat(userDescriptor.getElementDescriptor(0).kind).isEqualTo(PrimitiveKind.INT)
        assertThat(userDescriptor.getElementName(1)).isEqualTo("name")
        assertThat(userDescriptor.getElementDescriptor(1).kind).isEqualTo(PrimitiveKind.STRING)
        assertThat(userDescriptor.isNullable).isFalse()

        val nullableUserDescriptor = UserSerializer.nullable.descriptor
        assertThat(nullableUserDescriptor.serialName).isEqualTo(userDescriptor.serialName + "?")
        assertThat(nullableUserDescriptor.isNullable).isTrue()

        val usersDescriptor = ListSerializer(UserSerializer).descriptor
        assertThat(usersDescriptor.kind).isEqualTo(StructureKind.LIST)
        assertThat(usersDescriptor.getElementDescriptor(0).serialName).isEqualTo("sample.User")

        val mapDescriptor = MapSerializer(String.serializer(), Int.serializer()).descriptor
        assertThat(mapDescriptor.kind).isEqualTo(StructureKind.MAP)
        assertThat(mapDescriptor.getElementDescriptor(0).kind).isEqualTo(PrimitiveKind.STRING)
        assertThat(mapDescriptor.getElementDescriptor(1).kind).isEqualTo(PrimitiveKind.INT)
    }

    @Test
    fun tupleSerializersEncodeAndDecodePublicKotlinTupleTypes() {
        val pairSerializer = PairSerializer(Int.serializer(), String.serializer())
        val pair = 12 to "dozen"
        val pairEncoder = FieldMapEncoder()

        pairSerializer.serialize(pairEncoder, pair)

        assertThat(pairEncoder.values).containsExactlyEntriesOf(
            mapOf(
                "first" to 12,
                "second" to "dozen",
            ),
        )
        assertThat(pairSerializer.deserialize(FieldMapDecoder(pairEncoder.values))).isEqualTo(pair)

        val tripleSerializer = TripleSerializer(String.serializer(), Int.serializer(), String.serializer())
        val triple = Triple("left", 3, "right")
        val tripleEncoder = FieldMapEncoder()

        tripleSerializer.serialize(tripleEncoder, triple)

        assertThat(tripleEncoder.values).containsExactlyEntriesOf(
            mapOf(
                "first" to "left",
                "second" to 3,
                "third" to "right",
            ),
        )
        assertThat(tripleSerializer.deserialize(FieldMapDecoder(tripleEncoder.values))).isEqualTo(triple)
    }

    @Test
    fun serializersModuleResolvesContextualAndPolymorphicSerializers() {
        val module = SerializersModule {
            contextual(User::class, UserSerializer)
            polymorphic(DomainEvent::class) {
                subclass(UserRenamed::class, UserRenamedSerializer)
            }
        }
        val event: DomainEvent = UserRenamed(id = 11, newName = "Katherine Johnson")

        val contextual: KSerializer<User>? = module.getContextual(User::class)
        val polymorphicStrategy: SerializationStrategy<DomainEvent>? = module.getPolymorphic(DomainEvent::class, event)
        val polymorphicDeserializer: DeserializationStrategy<DomainEvent>? =
            module.getPolymorphic(DomainEvent::class, UserRenamedSerializer.descriptor.serialName)

        assertThat(contextual).isSameAs(UserSerializer)
        assertThat(polymorphicStrategy?.descriptor?.serialName).isEqualTo("sample.UserRenamed")
        assertThat(polymorphicDeserializer?.descriptor?.serialName).isEqualTo("sample.UserRenamed")
    }

    @Test
    fun customSerializerRejectsMissingRequiredElements() {
        val decoder = FieldMapDecoder(mapOf("id" to 100))

        assertThatThrownBy { UserSerializer.deserialize(decoder) }
            .isInstanceOf(SerializationException::class.java)
            .hasMessageContaining("name")
    }

    private data class User(val id: Int, val name: String)

    private sealed interface DomainEvent

    private data class UserRenamed(val id: Int, val newName: String) : DomainEvent

    private object UserSerializer : KSerializer<User> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("sample.User") {
            element<Int>("id")
            element<String>("name")
        }

        override fun serialize(encoder: Encoder, value: User) {
            encoder.encodeStructure(descriptor) {
                encodeIntElement(descriptor, 0, value.id)
                encodeStringElement(descriptor, 1, value.name)
            }
        }

        override fun deserialize(decoder: Decoder): User {
            var id: Int? = null
            var name: String? = null
            decoder.decodeStructure(descriptor) {
                while (true) {
                    when (val index = decodeElementIndex(descriptor)) {
                        CompositeDecoder.DECODE_DONE -> break
                        0 -> id = decodeIntElement(descriptor, 0)
                        1 -> name = decodeStringElement(descriptor, 1)
                        else -> throw SerializationException("Unexpected element index $index")
                    }
                }
            }
            return User(
                id = id ?: throw SerializationException("Missing required element id"),
                name = name ?: throw SerializationException("Missing required element name"),
            )
        }
    }

    private object UserRenamedSerializer : KSerializer<UserRenamed> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("sample.UserRenamed") {
            element<Int>("id")
            element<String>("newName")
        }

        override fun serialize(encoder: Encoder, value: UserRenamed) {
            encoder.encodeStructure(descriptor) {
                encodeIntElement(descriptor, 0, value.id)
                encodeStringElement(descriptor, 1, value.newName)
            }
        }

        override fun deserialize(decoder: Decoder): UserRenamed {
            var id: Int? = null
            var newName: String? = null
            decoder.decodeStructure(descriptor) {
                while (true) {
                    when (val index = decodeElementIndex(descriptor)) {
                        CompositeDecoder.DECODE_DONE -> break
                        0 -> id = decodeIntElement(descriptor, 0)
                        1 -> newName = decodeStringElement(descriptor, 1)
                        else -> throw SerializationException("Unexpected element index $index")
                    }
                }
            }
            return UserRenamed(
                id = id ?: throw SerializationException("Missing required element id"),
                newName = newName ?: throw SerializationException("Missing required element newName"),
            )
        }
    }

    private class FieldMapEncoder(
        override val serializersModule: SerializersModule = SerializersModule {},
    ) : AbstractEncoder() {
        val values: MutableMap<String, Any?> = linkedMapOf()
        var encodedRootNull: Boolean = false
            private set
        private var currentElementName: String? = null

        override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
            currentElementName = descriptor.getElementName(index)
            return true
        }

        override fun encodeInt(value: Int) {
            values[currentElementName ?: throw SerializationException("No element selected")] = value
        }

        override fun encodeString(value: String) {
            values[currentElementName ?: throw SerializationException("No element selected")] = value
        }

        override fun encodeNull() {
            val elementName = currentElementName
            if (elementName == null) {
                encodedRootNull = true
            } else {
                values[elementName] = null
            }
        }

        override fun encodeNotNullMark() {
            encodedRootNull = false
        }
    }

    private class FieldMapDecoder(
        private val values: Map<String, Any?> = emptyMap(),
        private val rootIsNull: Boolean = false,
        override val serializersModule: SerializersModule = SerializersModule {},
    ) : AbstractDecoder() {
        private var nextIndex: Int = 0
        private var currentElementName: String? = null

        override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
            while (nextIndex < descriptor.elementsCount) {
                val index = nextIndex++
                val elementName = descriptor.getElementName(index)
                if (values.containsKey(elementName)) {
                    currentElementName = elementName
                    return index
                }
            }
            return CompositeDecoder.DECODE_DONE
        }

        override fun decodeInt(): Int = values[currentElementName] as Int

        override fun decodeString(): String = values[currentElementName] as String

        override fun decodeNotNullMark(): Boolean = !rootIsNull

        override fun decodeNull(): Nothing? = null
    }
}
