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
import scala.collection.JavaConverters._
import scala.collection.immutable.LazyList

class LazyListInnerSerializationProxyTest {
  @Test
  def serializesAndRestoresEvaluatedElementsThroughSerializationProxy(): Unit = {
    val original: LazyList[String] = LazyList("alpha", "beta", "gamma")
    val forcedOriginal: LazyList[String] = original.force

    val restored: LazyList[String] = deserializeLazyList[String](serialize(forcedOriginal))

    assertThat(restored).isNotSameAs(forcedOriginal)
    assertThat(restored.toList.asJava).containsExactly("alpha", "beta", "gamma")
    assertThat(restored.appended("delta").toList.asJava).containsExactly("alpha", "beta", "gamma", "delta")
  }

  private def serialize(value: LazyList[String]): Array[Byte] = {
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

  private def deserializeLazyList[A](bytes: Array[Byte]): LazyList[A] = {
    val in: ObjectInputStream = new ObjectInputStream(new ByteArrayInputStream(bytes))
    try {
      in.readObject().asInstanceOf[LazyList[A]]
    } finally {
      in.close()
    }
  }
}
