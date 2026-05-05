/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_main_kts

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.com.google.common.collect.ArrayListMultimap
import org.jetbrains.kotlin.com.google.common.collect.HashMultiset
import org.jetbrains.kotlin.com.google.common.collect.ImmutableListMultimap
import org.jetbrains.kotlin.com.google.common.collect.ImmutableSetMultimap
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

public class SerializationTest {
    @Test
    fun serializesMutableMultisetEntries(): Unit {
        val multiset: HashMultiset<String> = HashMultiset.create()
        multiset.add("kotlin", 3)
        multiset.add("script", 2)

        val copy: HashMultiset<String> = roundTrip(multiset)

        assertThat(copy.count("kotlin")).isEqualTo(3)
        assertThat(copy.count("script")).isEqualTo(2)
        assertThat(copy.elementSet()).containsExactlyInAnyOrder("kotlin", "script")
    }

    @Test
    fun serializesMutableMultimapKeysAndValues(): Unit {
        val multimap: ArrayListMultimap<String, Int> = ArrayListMultimap.create()
        multimap.put("compiler", 1)
        multimap.put("compiler", 2)
        multimap.put("script", 3)

        val copy: ArrayListMultimap<String, Int> = roundTrip(multimap)

        assertThat(copy.get("compiler")).containsExactly(1, 2)
        assertThat(copy.get("script")).containsExactly(3)
        assertThat(copy.keySet()).containsExactlyInAnyOrder("compiler", "script")
    }

    @Test
    fun serializesImmutableListMultimapWithFieldSetters(): Unit {
        val multimap: ImmutableListMultimap<String, String> = ImmutableListMultimap.builder<String, String>()
            .put("language", "kotlin")
            .put("language", "main-kts")
            .put("runtime", "graalvm")
            .build()

        val copy: ImmutableListMultimap<String, String> = roundTrip(multimap)

        assertThat(copy.get("language")).containsExactly("kotlin", "main-kts")
        assertThat(copy.get("runtime")).containsExactly("graalvm")
        assertThat(copy.size()).isEqualTo(3)
    }

    @Test
    fun serializesImmutableSetMultimapWithFieldSetters(): Unit {
        val multimap: ImmutableSetMultimap<String, String> = ImmutableSetMultimap.builder<String, String>()
            .put("metadata", "reflection")
            .put("metadata", "serialization")
            .put("tests", "native-image")
            .build()

        val copy: ImmutableSetMultimap<String, String> = roundTrip(multimap)

        assertThat(copy.get("metadata")).containsExactlyInAnyOrder("reflection", "serialization")
        assertThat(copy.get("tests")).containsExactly("native-image")
        assertThat(copy.size()).isEqualTo(3)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> roundTrip(value: T): T {
        val bytes: ByteArray = ByteArrayOutputStream().use { byteOutput: ByteArrayOutputStream ->
            ObjectOutputStream(byteOutput).use { objectOutput: ObjectOutputStream ->
                objectOutput.writeObject(value)
            }
            byteOutput.toByteArray()
        }

        return ObjectInputStream(ByteArrayInputStream(bytes)).use { objectInput: ObjectInputStream ->
            objectInput.readObject() as T
        }
    }
}
