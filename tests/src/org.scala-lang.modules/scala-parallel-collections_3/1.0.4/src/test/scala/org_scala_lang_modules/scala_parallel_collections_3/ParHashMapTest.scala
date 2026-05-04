/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang_modules.scala_parallel_collections_3

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.math.BigInteger

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import scala.collection.parallel.mutable.ParHashMap

class ParHashMapTest {
  @Test
  def serializesAndDeserializesBigIntegerKeysAndIntegerValues(): Unit = {
    val firstKey: BigInteger = BigInteger.valueOf(101L)
    val secondKey: BigInteger = BigInteger.valueOf(202L)
    val thirdKey: BigInteger = BigInteger.valueOf(303L)
    val fourthKey: BigInteger = BigInteger.valueOf(404L)
    val original: ParHashMap[BigInteger, Int] = ParHashMap.empty[BigInteger, Int]
    original.put(firstKey, 1)
    original.put(secondKey, 2)
    original.put(thirdKey, 3)

    val restored: ParHashMap[BigInteger, Int] = roundTrip(original)

    assertEquals(original.seq, restored.seq)
    assertEquals(Some(1), restored.get(firstKey))
    assertEquals(Some(2), restored.get(secondKey))
    assertEquals(Some(3), restored.get(thirdKey))

    restored.put(fourthKey, 4)
    assertEquals(Some(4), restored.get(fourthKey))
    assertTrue(restored.remove(BigInteger.ZERO).isEmpty)
  }

  @Test
  def serializesAndDeserializesIntegerKeysAndStringValues(): Unit = {
    val original: ParHashMap[Int, String] = ParHashMap.empty[Int, String]
    original.put(1, "one")
    original.put(2, "two")
    original.put(3, "three")

    val restored: ParHashMap[Int, String] = roundTrip(original)

    assertEquals(original.seq, restored.seq)
    assertEquals(Some("one"), restored.get(1))
    assertEquals(Some("two"), restored.get(2))
    assertEquals(Some("three"), restored.get(3))
  }

  private def roundTrip[K, V](map: ParHashMap[K, V]): ParHashMap[K, V] = {
    deserialize(serialize(map))
  }

  private def serialize[K, V](map: ParHashMap[K, V]): Array[Byte] = {
    val bytes: ByteArrayOutputStream = new ByteArrayOutputStream()
    val output: ObjectOutputStream = new ObjectOutputStream(bytes)
    try {
      output.writeObject(map)
    } finally {
      output.close()
    }
    bytes.toByteArray
  }

  private def deserialize[K, V](serialized: Array[Byte]): ParHashMap[K, V] = {
    val input: ObjectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))
    try {
      input.readObject().asInstanceOf[ParHashMap[K, V]]
    } finally {
      input.close()
    }
  }
}
