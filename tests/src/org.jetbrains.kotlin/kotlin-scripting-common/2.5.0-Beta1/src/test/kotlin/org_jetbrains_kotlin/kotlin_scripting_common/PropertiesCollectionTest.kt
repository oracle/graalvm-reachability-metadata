/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_scripting_common

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import kotlin.script.experimental.util.PropertiesCollection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class PropertiesCollectionTest {
    @Test
    fun serializesOnlyNonTransientSerializableEntries(): Unit {
        val persistedKey = PropertiesCollection.Key<String>("persisted")
        val transientKey = PropertiesCollection.TransientKey<String>("transient") { "transient default" }
        val nonSerializableKey = PropertiesCollection.Key<Any>("nonSerializable")
        val sourceData: Map<PropertiesCollection.Key<*>, Any?> = linkedMapOf(
            persistedKey to "kept",
            transientKey to "dropped transient value",
            nonSerializableKey to Any(),
        )

        val restored = roundTrip(PropertiesCollection(sourceData))

        assertThat(restored.get(persistedKey)).isEqualTo("kept")
        assertThat(restored.containsKey(persistedKey)).isTrue()
        assertThat(restored.containsKey(transientKey)).isFalse()
        assertThat(restored.containsKey(nonSerializableKey)).isFalse()
        assertThat(restored.entries()).hasSize(1)
    }

    private fun roundTrip(propertiesCollection: PropertiesCollection): PropertiesCollection {
        val serialized = ByteArrayOutputStream().use { byteStream ->
            ObjectOutputStream(byteStream).use { objectStream ->
                objectStream.writeObject(propertiesCollection)
            }
            byteStream.toByteArray()
        }

        return ObjectInputStream(ByteArrayInputStream(serialized)).use { objectStream ->
            objectStream.readObject() as PropertiesCollection
        }
    }
}
