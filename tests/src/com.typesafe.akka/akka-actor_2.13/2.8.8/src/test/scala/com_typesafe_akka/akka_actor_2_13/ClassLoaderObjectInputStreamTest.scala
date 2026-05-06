/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_actor_2_13

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.io.Serializable

import akka.util.ClassLoaderObjectInputStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ClassLoaderObjectInputStreamTest {
  @Test
  def deserializesUsingProvidedClassLoader(): Unit = {
    val expected: ClassLoaderObjectInputStreamPayload = serializablePayload()
    val bytes: Array[Byte] = serialize(expected)

    val actual: ClassLoaderObjectInputStreamPayload = deserialize(bytes, getClass.getClassLoader)
      .asInstanceOf[ClassLoaderObjectInputStreamPayload]

    assertThat(actual.message).isEqualTo(expected.message)
  }

  @Test
  def fallsBackToObjectInputStreamClassResolutionWhenProvidedClassLoaderCannotLoadClass(): Unit = {
    val expected: ClassLoaderObjectInputStreamPayload = serializablePayload()
    val bytes: Array[Byte] = serialize(expected)

    val actual: ClassLoaderObjectInputStreamPayload = deserialize(bytes, null)
      .asInstanceOf[ClassLoaderObjectInputStreamPayload]

    assertThat(actual.message).isEqualTo(expected.message)
  }

  private def serializablePayload(): ClassLoaderObjectInputStreamPayload = {
    new ClassLoaderObjectInputStreamPayload("akka serialization")
  }

  private def serialize(value: AnyRef): Array[Byte] = {
    val bytes: ByteArrayOutputStream = new ByteArrayOutputStream()
    val output: ObjectOutputStream = new ObjectOutputStream(bytes)
    try {
      output.writeObject(value)
    } finally {
      output.close()
    }
    bytes.toByteArray
  }

  private def deserialize(bytes: Array[Byte], classLoader: ClassLoader): AnyRef = {
    val input: ClassLoaderObjectInputStream = new ClassLoaderObjectInputStream(
      classLoader,
      new ByteArrayInputStream(bytes))
    try {
      input.readObject().asInstanceOf[AnyRef]
    } finally {
      input.close()
    }
  }
}

final class ClassLoaderObjectInputStreamPayload(val message: String) extends Serializable
