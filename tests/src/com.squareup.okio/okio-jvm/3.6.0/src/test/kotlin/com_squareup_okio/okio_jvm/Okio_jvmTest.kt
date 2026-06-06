/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okio.okio_jvm

import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class Okio_jvmTest {
    @Test
    fun deserializesSerializedByteString(): Unit {
        val original: ByteString = "ByteString serialization preserves data".encodeUtf8()

        val restored: ByteString = deserialize(serialize(original))

        assertThat(restored).isEqualTo(original)
        assertThat(restored.utf8()).isEqualTo(original.utf8())
        assertThat(restored.toByteArray()).containsExactly(*original.toByteArray())
    }

    private fun serialize(value: ByteString): ByteArray {
        val outputStream = ByteArrayOutputStream()

        ObjectOutputStream(outputStream).use { objectOutputStream ->
            objectOutputStream.writeObject(value)
        }

        return outputStream.toByteArray()
    }

    private fun deserialize(serialized: ByteArray): ByteString {
        ObjectInputStream(ByteArrayInputStream(serialized)).use { objectInputStream ->
            return objectInputStream.readObject() as ByteString
        }
    }
}
