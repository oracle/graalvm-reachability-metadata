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
import scala.Enumeration
import scala.collection.JavaConverters._

final class EnumerationTest {
  @Test
  def infersNamesForPublicValues(): Unit = {
    val names: Seq[String] = CardinalDirection.values.toSeq.map(_.toString)

    assertThat(names.asJava).containsExactly("North", "East", "South", "West")
    assertThat(CardinalDirection.withName("North")).isSameAs(CardinalDirection.North)
    assertThat(CardinalDirection.withName("West")).isSameAs(CardinalDirection.West)
  }

  @Test
  def deserializesValueToCanonicalEnumerationMember(): Unit = {
    val restored: CardinalDirection.Value = deserializeValue(serialize(CardinalDirection.East))

    assertThat(restored).isSameAs(CardinalDirection.East)
    assertThat(restored.toString).isEqualTo("East")
  }

  private def serialize(value: CardinalDirection.Value): Array[Byte] = {
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

  private def deserializeValue(bytes: Array[Byte]): CardinalDirection.Value = {
    val in: ObjectInputStream = new ObjectInputStream(new ByteArrayInputStream(bytes))
    try {
      in.readObject().asInstanceOf[CardinalDirection.Value]
    } finally {
      in.close()
    }
  }
}

object CardinalDirection extends Enumeration {
  val North: Value = Value
  val East: Value = Value
  val South: Value = Value
  val West: Value = Value
}
