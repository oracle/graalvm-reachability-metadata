/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_main_kts

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.com.google.common.collect.LinkedHashMultimap
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.AbstractMap

public class OrgJetbrainsKotlinComGoogleCommonCollectLinkedHashMultimapTest {
    @Test
    public fun serializesDistinctKeysAndOrderedEntries(): Unit {
        val multimap: LinkedHashMultimap<String, String> = LinkedHashMultimap.create()
        multimap.put("script", "main")
        multimap.put("script", "kts")
        multimap.put("compiler", "jvm")

        val copy: LinkedHashMultimap<String, String> = roundTrip(multimap)

        assertThat(copy.keySet()).containsExactly("script", "compiler")
        assertThat(copy.get("script")).containsExactly("main", "kts")
        assertThat(copy.get("compiler")).containsExactly("jvm")
        assertThat(copy.entries()).containsExactly(
            mapEntry("script", "main"),
            mapEntry("script", "kts"),
            mapEntry("compiler", "jvm"),
        )
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

    private fun <K, V> mapEntry(key: K, value: V): Map.Entry<K, V> = AbstractMap.SimpleImmutableEntry(key, value)
}
