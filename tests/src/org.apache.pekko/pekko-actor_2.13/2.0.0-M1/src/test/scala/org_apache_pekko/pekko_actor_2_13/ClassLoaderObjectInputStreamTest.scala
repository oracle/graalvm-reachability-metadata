/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_2_13

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream

import org.apache.pekko.util.ClassLoaderObjectInputStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ClassLoaderObjectInputStreamTest {
  @Test
  def fallsBackToObjectInputStreamResolverWhenProvidedClassLoaderCannotLoadClass(): Unit = {
    val payload: JavaSerializerPayload = new JavaSerializerPayload("fallback", 7)
    val bytes: Array[Byte] = serialize(payload)
    val input: ClassLoaderObjectInputStream = new ClassLoaderObjectInputStream(
      new RejectingClassLoader,
      new ByteArrayInputStream(bytes))

    try {
      val deserialized: AnyRef = input.readObject()

      assertThat(deserialized).isEqualTo(payload)
    } finally {
      input.close()
    }
  }

  private def serialize(payload: JavaSerializerPayload): Array[Byte] = {
    val bytes: ByteArrayOutputStream = new ByteArrayOutputStream()
    val output: ObjectOutputStream = new ObjectOutputStream(bytes)

    try {
      output.writeObject(payload)
    } finally {
      output.close()
    }

    bytes.toByteArray
  }

  private final class RejectingClassLoader extends ClassLoader(null) {
    @throws[ClassNotFoundException]
    override def loadClass(name: String, resolve: Boolean): Class[_] = throw new ClassNotFoundException(name)
  }
}
