/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_squareup_okio.okio_jvm

import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

public class ByteStringTest {
    @Test
    fun deserializesSerializedByteString(): Unit {
        val original: ByteString = "ByteString serialization preserves data 🚀".encodeUtf8()

        val restored: ByteString = deserialize(serialize(original))

        assertNotSame(original, restored)
        assertEquals(original, restored)
        assertEquals(original.utf8(), restored.utf8())
        assertArrayEquals(original.toByteArray(), restored.toByteArray())
    }

    private fun serialize(value: ByteString): ByteArray {
        val outputStream: ByteArrayOutputStream = ByteArrayOutputStream()
        ObjectOutputStream(outputStream).use { objectOutputStream: ObjectOutputStream ->
            objectOutputStream.writeObject(value)
        }
        return outputStream.toByteArray()
    }

    private fun deserialize(serialized: ByteArray): ByteString =
        ObjectInputStream(ByteArrayInputStream(serialized)).use { objectInputStream: ObjectInputStream ->
            objectInputStream.readObject() as ByteString
        }
}
