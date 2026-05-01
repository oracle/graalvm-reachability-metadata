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
import scala.collection.concurrent.TrieMap
import scala.math.Equiv
import scala.util.hashing.Hashing

final class TrieMapTest {
  @Test
  def serializesAndRestoresEntriesThroughObjectStreams(): Unit = {
    val original: TrieMap[String, Int] = newTrieMap()
    original.put("alpha", 1)
    original.put("beta", 2)
    original.put("gamma", 3)

    val restored: TrieMap[String, Int] = deserializeTrieMap(serialize(original))

    assertThat(restored).isNotSameAs(original)
    assertThat(restored.size).isEqualTo(3)
    assertThat(restored("alpha")).isEqualTo(1)
    assertThat(restored("beta")).isEqualTo(2)
    assertThat(restored("gamma")).isEqualTo(3)
    assertThat(restored.contains("missing")).isFalse()
  }

  @Test
  def deserializedTrieMapKeepsConcurrentMapMutationSemantics(): Unit = {
    val original: TrieMap[String, Int] = newTrieMap()
    original.update("initial", 10)

    val restored: TrieMap[String, Int] = deserializeTrieMap(serialize(original))
    val previous: Option[Int] = restored.put("initial", 11)
    val inserted: Option[Int] = restored.putIfAbsent("new", 12)
    val removed: Option[Int] = restored.remove("initial")

    assertThat(previous.get).isEqualTo(10)
    assertThat(inserted.isEmpty).isTrue()
    assertThat(removed.get).isEqualTo(11)
    assertThat(restored("new")).isEqualTo(12)
    assertThat(restored.contains("initial")).isFalse()
  }

  private def newTrieMap(): TrieMap[String, Int] =
    new TrieMap[String, Int](new TrieMapStringHashing(), new TrieMapStringEquiv())

  private def serialize(map: TrieMap[String, Int]): Array[Byte] = {
    val bytes: ByteArrayOutputStream = new ByteArrayOutputStream()
    val out: ObjectOutputStream = new ObjectOutputStream(bytes)
    try {
      out.writeObject(map)
      out.flush()
      bytes.toByteArray
    } finally {
      out.close()
    }
  }

  private def deserializeTrieMap(bytes: Array[Byte]): TrieMap[String, Int] = {
    val in: ObjectInputStream = new ObjectInputStream(new ByteArrayInputStream(bytes))
    try {
      in.readObject().asInstanceOf[TrieMap[String, Int]]
    } finally {
      in.close()
    }
  }
}

final class TrieMapStringHashing extends Hashing[String] {
  override def hash(value: String): Int = value.##
}

final class TrieMapStringEquiv extends Equiv[String] {
  override def equiv(left: String, right: String): Boolean = left == right
}
