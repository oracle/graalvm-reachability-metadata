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
import scala.jdk.AnyAccumulator
import scala.jdk.CollectionConverters._

class AnyAccumulatorInnerSerializationProxyTest {
  @Test
  def serializesAndRestoresElementsThroughSerializationProxy(): Unit = {
    val original: AnyAccumulator[String] = new AnyAccumulator[String]()
    original.addOne("alpha")
    original.addOne("beta")
    original.addOne("gamma")

    val restored: AnyAccumulator[String] = deserializeAnyAccumulator[String](serialize(original))

    assertThat(restored).isNotSameAs(original)
    assertThat(restored.toList.asJava).containsExactly("alpha", "beta", "gamma")

    restored.addOne("delta")
    assertThat(restored.toList.asJava).containsExactly("alpha", "beta", "gamma", "delta")
  }

  private def serialize(value: AnyAccumulator[String]): Array[Byte] = {
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

  private def deserializeAnyAccumulator[A](bytes: Array[Byte]): AnyAccumulator[A] = {
    val in: ObjectInputStream = new ObjectInputStream(new ByteArrayInputStream(bytes))
    try {
      in.readObject().asInstanceOf[AnyAccumulator[A]]
    } finally {
      in.close()
    }
  }
}
