/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_actor_3

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.util.ArrayList

import org.apache.pekko.util.ClassLoaderObjectInputStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ClassLoaderObjectInputStreamTest {
  @Test
  def deserializesClassResolvedByProvidedClassLoader(): Unit = {
    val original: ArrayList[String] = messageList("resolved by provided loader")
    val bytes: Array[Byte] = serialize(original)

    val deserialized: ArrayList[?] = deserializeListWith(Thread.currentThread().getContextClassLoader, bytes)

    assertThat(deserialized).containsExactly("resolved by provided loader")
  }

  @Test
  def fallsBackToObjectInputStreamWhenProvidedClassLoaderCannotResolveClass(): Unit = {
    val original: ArrayList[String] = messageList("resolved by ObjectInputStream fallback")
    val bytes: Array[Byte] = serialize(original)
    val rejectingLoader: ClassLoader = new ClassLoader(Thread.currentThread().getContextClassLoader) {
      override protected def loadClass(name: String, resolve: Boolean): Class[?] = {
        if (name == classOf[ArrayList[?]].getName) {
          throw new ClassNotFoundException(name)
        }
        super.loadClass(name, resolve)
      }
    }

    val deserialized: ArrayList[?] = deserializeListWith(rejectingLoader, bytes)

    assertThat(deserialized).containsExactly("resolved by ObjectInputStream fallback")
  }

  private def messageList(message: String): ArrayList[String] = {
    val list: ArrayList[String] = new ArrayList[String]()
    list.add(message)
    list
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

  private def deserializeListWith(classLoader: ClassLoader, bytes: Array[Byte]): ArrayList[?] = {
    val input: ClassLoaderObjectInputStream =
      new ClassLoaderObjectInputStream(classLoader, new ByteArrayInputStream(bytes))
    try {
      input.readObject().asInstanceOf[ArrayList[?]]
    } finally {
      input.close()
    }
  }
}
