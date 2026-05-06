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
import java.util.ArrayList

import org.apache.pekko.util.ClassLoaderObjectInputStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ClassLoaderObjectInputStreamTest {
  @Test
  def fallsBackToDefaultResolverWhenConfiguredClassLoaderCannotLoadClass(): Unit = {
    val original: ArrayList[String] = new ArrayList[String]()
    original.add("pekko")

    val rejectedClassName: String = classOf[ArrayList[_]].getName
    val input: ClassLoaderObjectInputStream = new ClassLoaderObjectInputStream(
      new ClassLoaderObjectInputStreamTest.RejectingClassLoader(rejectedClassName),
      new ByteArrayInputStream(serialize(original)))

    try {
      val restored: AnyRef = input.readObject()

      assertThat(restored).isInstanceOf(classOf[ArrayList[_]])
      val restoredList: ArrayList[String] = restored.asInstanceOf[ArrayList[String]]
      assertThat(restoredList).hasSize(1)
      assertThat(restoredList.get(0)).isEqualTo("pekko")
    } finally {
      input.close()
    }
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
}

object ClassLoaderObjectInputStreamTest {
  private final class RejectingClassLoader(rejectedClassName: String)
      extends ClassLoader(ClassLoader.getSystemClassLoader) {
    override def loadClass(name: String, resolve: Boolean): Class[_] = {
      if (name == rejectedClassName) {
        throw new ClassNotFoundException(name)
      }
      super.loadClass(name, resolve)
    }
  }
}
