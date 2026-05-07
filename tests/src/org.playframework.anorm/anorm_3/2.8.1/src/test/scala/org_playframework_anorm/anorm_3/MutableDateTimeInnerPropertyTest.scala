/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework_anorm.anorm_3

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

import org.assertj.core.api.Assertions.assertThat
import org.joda.time.DateTimeZone
import org.joda.time.MutableDateTime
import org.junit.jupiter.api.Test

class MutableDateTimeInnerPropertyTest {
  @Test
  def serializesAndDeserializesHourOfDayProperty(): Unit = {
    val originalDateTime: MutableDateTime = new MutableDateTime(2020, 2, 29, 12, 34, 56, 789, DateTimeZone.UTC)
    val original: MutableDateTime.Property = originalDateTime.hourOfDay()

    val restored: MutableDateTime.Property = deserialize(serialize(original))

    assertThat(restored.get()).isEqualTo(original.get())
    assertThat(restored.getFieldType).isEqualTo(original.getFieldType)
    assertThat(restored.getMutableDateTime).isEqualTo(original.getMutableDateTime)
  }

  @throws[IOException]
  private def serialize(value: Serializable): Array[Byte] = {
    val output: ByteArrayOutputStream = new ByteArrayOutputStream()
    val objectOutput: ObjectOutputStream = new ObjectOutputStream(output)
    try {
      objectOutput.writeObject(value)
    } finally {
      objectOutput.close()
    }
    output.toByteArray
  }

  @throws[IOException]
  @throws[ClassNotFoundException]
  private def deserialize(serialized: Array[Byte]): MutableDateTime.Property = {
    val objectInput: ObjectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))
    try {
      objectInput.readObject().asInstanceOf[MutableDateTime.Property]
    } finally {
      objectInput.close()
    }
  }
}
