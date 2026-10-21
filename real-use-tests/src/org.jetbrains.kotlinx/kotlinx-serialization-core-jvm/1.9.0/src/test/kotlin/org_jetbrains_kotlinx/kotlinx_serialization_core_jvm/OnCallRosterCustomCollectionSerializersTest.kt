/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_serialization_core_jvm

import java.util.SortedSet
import java.util.TreeSet
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.mapSerialDescriptor
import kotlinx.serialization.descriptors.setSerialDescriptor
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeCollection
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises hand-written serializers for application collection types: list and map kinds
 * built with `setSerialDescriptor`/`mapSerialDescriptor`, `encodeCollection`, the
 * `decodeElementIndex`/`decodeSequentially` read loops, and generic serializers the plugin
 * instantiates with the serializers of the type arguments.
 *
 * An on-call scheduler stores team rosters whose tags live in a normalized [TagSet] and whose
 * rotations live in a [Multimap], and exchanges them as JSON with its web UI and as CBOR
 * with its paging gateway.
 */
@OptIn(ExperimentalSerializationApi::class)
public class OnCallRosterCustomCollectionSerializersTest {

    /** Lower-case, trimmed, de-duplicated and sorted tags; blank tags are rejected. */
    @Serializable(with = TagSetSerializer::class)
    public class TagSet private constructor(private val tags: SortedSet<String>) : Iterable<String> {
        override fun iterator(): Iterator<String> = tags.iterator()

        public val size: Int get() = tags.size

        override fun equals(other: Any?): Boolean = other is TagSet && tags == other.tags

        override fun hashCode(): Int = tags.hashCode()

        override fun toString(): String = "TagSet$tags"

        public companion object {
            public fun of(raw: Iterable<String>): TagSet {
                val normalized: SortedSet<String> = TreeSet()
                for (tag: String in raw) {
                    val clean: String = tag.trim().lowercase()
                    if (clean.isEmpty()) throw SerializationException("Roster tags must not be blank")
                    normalized += clean
                }
                return TagSet(normalized)
            }

            public fun of(vararg raw: String): TagSet = of(raw.asIterable())
        }
    }

    /** Writes a [TagSet] as a JSON array or CBOR list and normalizes whatever it reads back. */
    public object TagSetSerializer : KSerializer<TagSet> {
        override val descriptor: SerialDescriptor =
            SerialDescriptor("roster.TagSet", setSerialDescriptor<String>())

        override fun serialize(encoder: Encoder, value: TagSet) {
            encoder.encodeCollection(descriptor, value.size) {
                value.forEachIndexed { index: Int, tag: String -> encodeStringElement(descriptor, index, tag) }
            }
        }

        override fun deserialize(decoder: Decoder): TagSet = decoder.decodeStructure(descriptor) {
            val raw: MutableList<String> = mutableListOf()
            if (decodeSequentially()) {
                repeat(decodeCollectionSize(descriptor)) { index: Int ->
                    raw += decodeStringElement(descriptor, index)
                }
            } else {
                while (true) {
                    val index: Int = decodeElementIndex(descriptor)
                    if (index == CompositeDecoder.DECODE_DONE) break
                    raw += decodeStringElement(descriptor, index)
                }
            }
            TagSet.of(raw)
        }
    }

    /** Groups several values under one key, keeping the order in which keys were first seen. */
    @Serializable(with = MultimapSerializer::class)
    public class Multimap<K, V>(groups: Map<K, List<V>>) {
        private val groups: Map<K, List<V>> = groups.mapValues { (_, values: List<V>) -> values.toList() }

        public operator fun get(key: K): List<V> = groups[key].orEmpty()

        public val keys: Set<K> get() = groups.keys

        public fun asMap(): Map<K, List<V>> = groups

        override fun equals(other: Any?): Boolean = other is Multimap<*, *> && groups == other.groups

        override fun hashCode(): Int = groups.hashCode()

        override fun toString(): String = "Multimap$groups"

        public companion object {
            public fun <K, V> of(vararg entries: Pair<K, V>): Multimap<K, V> {
                val groups: MutableMap<K, MutableList<V>> = LinkedHashMap()
                for ((key: K, value: V) in entries) groups.getOrPut(key) { mutableListOf() } += value
                return Multimap(groups)
            }
        }
    }

    /**
     * Writes a [Multimap] as a map from each key to the list of its values; repeated keys in the
     * input are merged, so rotations that arrive from two feeds in one document add up.
     */
    public class MultimapSerializer<K, V>(
        private val keySerializer: KSerializer<K>,
        valueSerializer: KSerializer<V>,
    ) : KSerializer<Multimap<K, V>> {
        private val groupSerializer: KSerializer<List<V>> = ListSerializer(valueSerializer)

        override val descriptor: SerialDescriptor =
            mapSerialDescriptor(keySerializer.descriptor, groupSerializer.descriptor)

        override fun serialize(encoder: Encoder, value: Multimap<K, V>) {
            val groups: Map<K, List<V>> = value.asMap()
            encoder.encodeCollection(descriptor, groups.size) {
                var index = 0
                for ((key: K, values: List<V>) in groups) {
                    encodeSerializableElement(descriptor, index++, keySerializer, key)
                    encodeSerializableElement(descriptor, index++, groupSerializer, values)
                }
            }
        }

        override fun deserialize(decoder: Decoder): Multimap<K, V> = decoder.decodeStructure(descriptor) {
            val groups: MutableMap<K, MutableList<V>> = LinkedHashMap()
            fun readEntry(keyIndex: Int) {
                val key: K = decodeSerializableElement(descriptor, keyIndex, keySerializer)
                val valueIndex: Int = if (decodeSequentially()) keyIndex + 1 else decodeElementIndex(descriptor)
                if (valueIndex != keyIndex + 1) {
                    throw SerializationException("Rotation key at $keyIndex has no value list")
                }
                groups.getOrPut(key) { mutableListOf() } += decodeSerializableElement(descriptor, valueIndex, groupSerializer)
            }
            if (decodeSequentially()) {
                repeat(decodeCollectionSize(descriptor)) { entry: Int -> readEntry(entry * 2) }
            } else {
                while (true) {
                    val index: Int = decodeElementIndex(descriptor)
                    if (index == CompositeDecoder.DECODE_DONE) break
                    readEntry(index)
                }
            }
            Multimap(groups)
        }
    }

    /** A person who can be paged. */
    @Serializable
    @SerialName("roster.Engineer")
    public data class Engineer(val name: String, val pager: String)

    /** A data-centre zone; used as a structured map key. */
    @Serializable
    @SerialName("roster.Zone")
    public data class Zone(val region: String, val tier: Int)

    /** One team's roster: rotations by role and the engineers on shift by ISO week. */
    @Serializable
    @SerialName("roster.TeamRoster")
    public data class TeamRoster(
        val team: String,
        val tags: TagSet,
        val oncall: Multimap<String, Engineer>,
        val shiftsByWeek: Multimap<Int, String>,
    )

    private val roster: TeamRoster = TeamRoster(
        team = "payments",
        tags = TagSet.of("Payments", "oncall", " EU ", "payments"),
        oncall = Multimap.of(
            "primary" to Engineer("ana", "p-1"),
            "secondary" to Engineer("bo", "p-2"),
            "secondary" to Engineer("cy", "p-3"),
        ),
        shiftsByWeek = Multimap.of(41 to "ana", 41 to "bo", 42 to "cy"),
    )

    private val rosterJson: String =
        """{"team":"payments","tags":["eu","oncall","payments"],""" +
            """"oncall":{"primary":[{"name":"ana","pager":"p-1"}],""" +
            """"secondary":[{"name":"bo","pager":"p-2"},{"name":"cy","pager":"p-3"}]},""" +
            """"shiftsByWeek":{"41":["ana","bo"],"42":["cy"]}}"""

    @Test
    fun rosterCollectionsRoundTripThroughJsonForTheWebUi() {
        assertEquals(rosterJson, Json.encodeToString(roster))

        val restored: TeamRoster = Json.decodeFromString(rosterJson)
        assertEquals(roster, restored)
        assertEquals(listOf("eu", "oncall", "payments"), restored.tags.toList())
        assertEquals(listOf(Engineer("bo", "p-2"), Engineer("cy", "p-3")), restored.oncall["secondary"])
        assertEquals(setOf(41, 42), restored.shiftsByWeek.keys)
    }

    @Test
    fun rosterCollectionsRoundTripThroughCborForThePagingGateway() {
        val bytes: ByteArray = Cbor.encodeToByteArray(roster)
        val restored: TeamRoster = Cbor.decodeFromByteArray(bytes)

        assertEquals(roster, restored)
        assertEquals(listOf("ana", "bo"), restored.shiftsByWeek[41])
        assertEquals(Multimap.of(42 to "cy"), Cbor.decodeFromByteArray<Multimap<Int, String>>(
            Cbor.encodeToByteArray(Multimap.of(42 to "cy")),
        ))
    }

    @Test
    fun readersNormalizeTagsAndMergeRepeatedRotationKeys() {
        val edited: String =
            """{"team":"search","tags":["Search"," DB ","search"],"oncall":{},""" +
                """"shiftsByWeek":{"41":["ana"],"42":["cy"],"41":["bo"]}}"""
        val restored: TeamRoster = Json.decodeFromString(edited)

        assertEquals(TagSet.of("db", "search"), restored.tags)
        assertEquals(emptySet<String>(), restored.oncall.keys)
        assertEquals(Multimap.of(41 to "ana", 41 to "bo", 42 to "cy"), restored.shiftsByWeek)
        assertEquals(
            """{"team":"search","tags":["db","search"],"oncall":{},"shiftsByWeek":{"41":["ana","bo"],"42":["cy"]}}""",
            Json.encodeToString(restored),
        )

        val blank: SerializationException = assertThrows(SerializationException::class.java) {
            Json.decodeFromString<TagSet>("""["ops","   "]""")
        }
        assertTrue(blank.message.orEmpty().contains("must not be blank"), blank.message)
    }

    @Test
    fun zoneKeyedRotationsNeedStructuredMapKeysInJson() {
        val byZone: Multimap<Zone, String> = Multimap.of(
            Zone("eu", 1) to "ana",
            Zone("us", 2) to "bo",
            Zone("eu", 1) to "cy",
        )

        val rejected: SerializationException = assertThrows(SerializationException::class.java) {
            Json.encodeToString(byZone)
        }
        assertTrue(rejected.message.orEmpty().contains("allowStructuredMapKeys"), rejected.message)

        val structured: Json = Json { allowStructuredMapKeys = true }
        val body: String = structured.encodeToString(byZone)
        assertEquals("""[{"region":"eu","tier":1},["ana","cy"],{"region":"us","tier":2},["bo"]]""", body)
        assertEquals(byZone, structured.decodeFromString<Multimap<Zone, String>>(body))
        assertEquals(byZone, Cbor.decodeFromByteArray<Multimap<Zone, String>>(Cbor.encodeToByteArray(byZone)))
    }

    @Test
    fun pluginAndRuntimeLookupBuildTheSerializersFromTypeArguments() {
        assertSame(TagSetSerializer, TagSet.serializer())
        assertSame(TagSetSerializer, serializer<TagSet>())

        val explicit: KSerializer<Multimap<Int, String>> =
            MultimapSerializer(Int.serializer(), String.serializer())
        val generated: KSerializer<Multimap<Int, String>> =
            Multimap.serializer(Int.serializer(), String.serializer())
        val looked: KSerializer<Multimap<Int, String>> = serializer<Multimap<Int, String>>()
        assertTrue(generated is MultimapSerializer<*, *>)
        assertTrue(looked is MultimapSerializer<*, *>)

        val shifts: Multimap<Int, String> = roster.shiftsByWeek
        val expected: String = """{"41":["ana","bo"],"42":["cy"]}"""
        assertEquals(expected, Json.encodeToString(explicit, shifts))
        assertEquals(expected, Json.encodeToString(generated, shifts))
        assertEquals(expected, Json.encodeToString(looked, shifts))
        assertEquals(shifts, Json.decodeFromString(looked, expected))
    }

    @Test
    fun rosterDescriptorReportsTheCollectionShapesToSchemaTools() {
        val descriptor: SerialDescriptor = TeamRoster.serializer().descriptor
        assertEquals(listOf("team", "tags", "oncall", "shiftsByWeek"), (0 until descriptor.elementsCount).map {
            descriptor.getElementName(it)
        })

        val tags: SerialDescriptor = descriptor.getElementDescriptor(1)
        assertEquals("roster.TagSet", tags.serialName)
        assertEquals(StructureKind.LIST, tags.kind)
        assertEquals(PrimitiveKind.STRING, tags.getElementDescriptor(0).kind)

        val oncall: SerialDescriptor = descriptor.getElementDescriptor(2)
        assertEquals(StructureKind.MAP, oncall.kind)
        assertEquals(2, oncall.elementsCount)
        assertEquals(PrimitiveKind.STRING, oncall.getElementDescriptor(0).kind)
        val rotation: SerialDescriptor = oncall.getElementDescriptor(1)
        assertEquals(StructureKind.LIST, rotation.kind)
        assertEquals("roster.Engineer", rotation.getElementDescriptor(0).serialName)

        val shifts: SerialDescriptor = descriptor.getElementDescriptor(3)
        assertEquals(StructureKind.MAP, shifts.kind)
        assertEquals(PrimitiveKind.INT, shifts.getElementDescriptor(0).kind)
        assertEquals(PrimitiveKind.STRING, shifts.getElementDescriptor(1).getElementDescriptor(0).kind)
    }
}
