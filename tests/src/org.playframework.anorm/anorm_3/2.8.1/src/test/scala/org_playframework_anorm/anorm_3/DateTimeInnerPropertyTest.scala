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

import org.joda.time.DateTime
import org.joda.time.DateTimeFieldType
import org.joda.time.DateTimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DateTimeInnerPropertyTest {
  @Test
  def serializesAndDeserializesDateTimeProperty(): Unit = {
    val originalDateTime: DateTime = new DateTime(2024, 5, 14, 9, 30, 15, DateTimeZone.UTC)
    val originalProperty: DateTime.Property = originalDateTime.dayOfMonth()

    val serialized: Array[Byte] = serialize(originalProperty)
    val restoredProperty: DateTime.Property = deserializeProperty(serialized)

    assertEquals(originalDateTime, restoredProperty.getDateTime)
    assertEquals(DateTimeFieldType.dayOfMonth(), restoredProperty.getField.getType)
    assertEquals(14, restoredProperty.get)
  }

  private def serialize(property: DateTime.Property): Array[Byte] = {
    val bytes: ByteArrayOutputStream = new ByteArrayOutputStream()
    val output: ObjectOutputStream = new ObjectOutputStream(bytes)
    try {
      output.writeObject(property)
    } finally {
      output.close()
    }
    bytes.toByteArray
  }

  private def deserializeProperty(serialized: Array[Byte]): DateTime.Property = {
    val input: ObjectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))
    try {
      input.readObject().asInstanceOf[DateTime.Property]
    } finally {
      input.close()
    }
  }
}
