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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import scala.collection.parallel.mutable.ParHashMap

class ParHashMapTest {
  @Test
  def serializesAndDeserializesEntries(): Unit = {
    val original: ParHashMap[String, Int] = ParHashMap.empty[String, Int]
    original.put("one", 1)
    original.put("two", 2)
    original.put("three", 3)

    val restored: ParHashMap[String, Int] = deserialize(serialize(original))

    assertEquals(original.seq, restored.seq)
    assertEquals(Some(1), restored.get("one"))
    assertEquals(Some(2), restored.get("two"))
    assertEquals(Some(3), restored.get("three"))

    restored.put("four", 4)
    assertEquals(Some(4), restored.get("four"))
    assertTrue(restored.remove("missing").isEmpty)
  }

  private def serialize(map: ParHashMap[String, Int]): Array[Byte] = {
    val bytes: ByteArrayOutputStream = new ByteArrayOutputStream()
    val output: ObjectOutputStream = new ObjectOutputStream(bytes)
    try {
      output.writeObject(map)
    } finally {
      output.close()
    }
    bytes.toByteArray
  }

  private def deserialize(serialized: Array[Byte]): ParHashMap[String, Int] = {
    val input: ObjectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))
    try {
      input.readObject().asInstanceOf[ParHashMap[String, Int]]
    } finally {
      input.close()
    }
  }
}
