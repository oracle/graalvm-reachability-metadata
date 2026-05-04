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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import scala.collection.parallel.mutable.ParHashSet

class FlatHashTableTest {
  @Test
  def serializesAndDeserializesParHashSetElements(): Unit = {
    val original: ParHashSet[String] = ParHashSet.empty[String]
    original.addOne("alpha")
    original.addOne("bravo")
    original.addOne("charlie")

    val restored: ParHashSet[String] = roundTrip(original)

    assertEquals(original.seq, restored.seq)
    assertTrue(restored.contains("alpha"))
    assertTrue(restored.contains("bravo"))
    assertTrue(restored.contains("charlie"))
    assertFalse(restored.contains("delta"))

    restored.addOne("delta")
    assertTrue(restored.contains("delta"))
    restored.subtractOne("alpha")
    assertFalse(restored.contains("alpha"))
  }

  private def roundTrip(set: ParHashSet[String]): ParHashSet[String] = {
    deserialize(serialize(set))
  }

  private def serialize(set: ParHashSet[String]): Array[Byte] = {
    val bytes: ByteArrayOutputStream = new ByteArrayOutputStream()
    val output: ObjectOutputStream = new ObjectOutputStream(bytes)
    try {
      output.writeObject(set)
    } finally {
      output.close()
    }
    bytes.toByteArray
  }

  private def deserialize(serialized: Array[Byte]): ParHashSet[String] = {
    val input: ObjectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))
    try {
      input.readObject().asInstanceOf[ParHashSet[String]]
    } finally {
      input.close()
    }
  }
}
