/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_slick.slick_2_13

import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.ZoneId
import java.time.ZoneOffset

import oracle.sql.TIMESTAMPTZ
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import slick.jdbc.TimestamptzConverter

class TimestamptzConverterTest {
  @Test
  def convertsOffsetTimeToTimestamptzAndBack(): Unit = {
    val offsetTime: OffsetTime = OffsetTime.of(23, 45, 7, 123456789, ZoneOffset.ofHoursMinutes(5, 30))

    val timestamptz: Object = TimestamptzConverter.offsetTimeToTimestamptz(offsetTime).asInstanceOf[Object]
    val converted: OffsetTime = TimestamptzConverter.timestamptzToOffsetTime(timestamptz)

    assertThat(timestamptz).isInstanceOf(classOf[TIMESTAMPTZ])
    assertThat(converted).isEqualTo(offsetTime)
  }

  @Test
  def convertsRegionBasedTimestamptzToOffsetTime(): Unit = {
    val utcDateTime: OffsetDateTime = OffsetDateTime
      .of(2001, 1, 1, 11, 30, 15, 987654321, ZoneOffset.UTC)
    val timestamptz: TIMESTAMPTZ = new TIMESTAMPTZ(regionBasedBytes(utcDateTime, regionCode = 1))

    val converted: OffsetTime = TimestamptzConverter.timestamptzToOffsetTime(timestamptz)

    val expected: OffsetTime = utcDateTime
      .atZoneSameInstant(ZoneId.of("Europe/Paris"))
      .toOffsetDateTime
      .toOffsetTime
    assertThat(converted).isEqualTo(expected)
  }

  private def regionBasedBytes(utcDateTime: OffsetDateTime, regionCode: Int): Array[Byte] = {
    val bytes: Array[Byte] = new Array[Byte](13)
    val year: Int = utcDateTime.getYear
    bytes(0) = (year / 100 + 100).toByte
    bytes(1) = (year % 100 + 100).toByte
    bytes(2) = utcDateTime.getMonthValue.toByte
    bytes(3) = utcDateTime.getDayOfMonth.toByte
    bytes(4) = (utcDateTime.getHour + 1).toByte
    bytes(5) = (utcDateTime.getMinute + 1).toByte
    bytes(6) = (utcDateTime.getSecond + 1).toByte
    writeNanos(bytes, utcDateTime.getNano)
    writeRegionCode(bytes, regionCode)
    bytes
  }

  private def writeNanos(bytes: Array[Byte], nano: Int): Unit = {
    bytes(7) = ((nano >>> 24) & 0xff).toByte
    bytes(8) = ((nano >>> 16) & 0xff).toByte
    bytes(9) = ((nano >>> 8) & 0xff).toByte
    bytes(10) = (nano & 0xff).toByte
  }

  private def writeRegionCode(bytes: Array[Byte], regionCode: Int): Unit = {
    bytes(11) = (0x80 | ((regionCode >>> 6) & 0x7f)).toByte
    bytes(12) = ((regionCode & 0x3f) << 2).toByte
  }
}
