/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalameta.trees_2_13

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import scala.meta.inputs.Input

class InputInnerStreamInnerSerializationProxyTest {
  @Test
  def serializesAndDeserializesStreamInputThroughProxy(): Unit = {
    val charset: Charset = StandardCharsets.UTF_16BE
    val content: String = "object StreamProxySample { val greeting = \"Grüße\" }\n"
    val bytes: Array[Byte] = content.getBytes(charset)
    val original: Input.Stream = Input.Stream(new ByteArrayInputStream(bytes), charset)

    val serialized: Array[Byte] = serialize(original)
    val deserialized: Input.Stream = deserialize(serialized).asInstanceOf[Input.Stream]

    assertEquals(charset, deserialized.charset)
    assertEquals(content, deserialized.text)
    assertArrayEquals(content.toCharArray, deserialized.chars)
  }

  private def serialize(value: AnyRef): Array[Byte] = {
    val bytes: ByteArrayOutputStream = new ByteArrayOutputStream()
    val out: ObjectOutputStream = new ObjectOutputStream(bytes)
    try {
      out.writeObject(value)
    } finally {
      out.close()
    }
    bytes.toByteArray
  }

  private def deserialize(bytes: Array[Byte]): AnyRef = {
    val in: ObjectInputStream = new ObjectInputStream(new ByteArrayInputStream(bytes))
    try {
      in.readObject()
    } finally {
      in.close()
    }
  }
}
