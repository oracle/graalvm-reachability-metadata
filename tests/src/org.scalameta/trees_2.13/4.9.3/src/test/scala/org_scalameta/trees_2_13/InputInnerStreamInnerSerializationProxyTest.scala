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

import scala.meta.inputs.Input

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InputInnerStreamInnerSerializationProxyTest {
  @Test
  def roundTripsStreamInputThroughSerializationProxy(): Unit = {
    val charset: Charset = StandardCharsets.UTF_16BE
    val text: String = "object Example { val greeting = \"hello, serialization\" }"
    val input: Input.Stream = Input.Stream(new ByteArrayInputStream(text.getBytes(charset)), charset)

    val deserialized: Input.Stream = deserialize(serialize(input)).asInstanceOf[Input.Stream]

    assertEquals(charset, deserialized.charset)
    assertEquals(text, deserialized.text)
    assertArrayEquals(text.toCharArray, deserialized.chars)
  }

  private def serialize(value: AnyRef): Array[Byte] = {
    val output: ByteArrayOutputStream = new ByteArrayOutputStream()
    val objectOutput: ObjectOutputStream = new ObjectOutputStream(output)
    try {
      objectOutput.writeObject(value)
    } finally {
      objectOutput.close()
    }
    output.toByteArray
  }

  private def deserialize(bytes: Array[Byte]): AnyRef = {
    val objectInput: ObjectInputStream = new ObjectInputStream(new ByteArrayInputStream(bytes))
    try {
      objectInput.readObject().asInstanceOf[AnyRef]
    } finally {
      objectInput.close()
    }
  }
}
