/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala_library

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import scala.collection.Factory
import scala.collection.JavaConverters._
import scala.collection.generic.DefaultSerializationProxy
import scala.collection.immutable.Vector

final class DefaultSerializationProxyTest {
  @Test
  def serializesAndRestoresKnownSizeElementsThroughDefaultSerializationProxy(): Unit = {
    val original: Vector[String] = Vector("alpha", "beta", "gamma")
    val factory: Factory[String, Any] = Vector.iterableFactory.asInstanceOf[Factory[String, Any]]
    val proxy: DefaultSerializationProxy[String] = new DefaultSerializationProxy[String](factory, original)

    val restored: Vector[String] = deserializeVector[String](serialize(proxy))

    assertThat(restored.toList.asJava).containsExactly("alpha", "beta", "gamma")
    assertThat(restored.appended("delta").toList.asJava).containsExactly("alpha", "beta", "gamma", "delta")
  }

  private def serialize(value: DefaultSerializationProxy[String]): Array[Byte] = {
    val bytes: ByteArrayOutputStream = new ByteArrayOutputStream()
    val out: ObjectOutputStream = new ObjectOutputStream(bytes)
    try {
      out.writeObject(value)
      out.flush()
      bytes.toByteArray
    } finally {
      out.close()
    }
  }

  private def deserializeVector[A](bytes: Array[Byte]): Vector[A] = {
    val in: ObjectInputStream = new ObjectInputStream(new ByteArrayInputStream(bytes))
    try {
      in.readObject().asInstanceOf[Vector[A]]
    } finally {
      in.close()
    }
  }
}
