/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework_anorm.anorm_3

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

import org.joda.time.DateTimeFieldType
import org.joda.time.LocalTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LocalTimeInnerPropertyTest {
  @Test
  def serializesAndDeserializesLocalTimeProperty(): Unit = {
    val originalTime: LocalTime = new LocalTime(9, 30, 15, 250)
    val originalProperty: LocalTime.Property = originalTime.minuteOfHour()

    val serialized: Array[Byte] = serialize(originalProperty)
    val restoredProperty: LocalTime.Property = deserializeProperty(serialized)

    assertEquals(originalTime, restoredProperty.getLocalTime)
    assertEquals(DateTimeFieldType.minuteOfHour(), restoredProperty.getField.getType)
    assertEquals(30, restoredProperty.get)
  }

  private def serialize(property: LocalTime.Property): Array[Byte] = {
    val bytes: ByteArrayOutputStream = new ByteArrayOutputStream()
    val output: ObjectOutputStream = new ObjectOutputStream(bytes)
    try {
      output.writeObject(property)
    } finally {
      output.close()
    }
    bytes.toByteArray
  }

  private def deserializeProperty(serialized: Array[Byte]): LocalTime.Property = {
    val input: ObjectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))
    try {
      input.readObject().asInstanceOf[LocalTime.Property]
    } finally {
      input.close()
    }
  }
}
