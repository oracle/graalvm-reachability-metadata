/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework_anorm.anorm_3

import java.io.File
import java.time.Instant
import java.util.OptionalDouble
import java.util.OptionalInt
import java.util.OptionalLong

import com.google.common.reflect.TypeToken
import org.assertj.core.api.Assertions.assertThat
import org.joda.convert.StringConvert
import org.junit.jupiter.api.Test

class StringConvertTest {
  @Test
  def registersJava8OptionalAndGuavaConvertersDuringConstruction(): Unit = {
    val thread: Thread = Thread.currentThread()
    val originalLoader: ClassLoader = thread.getContextClassLoader

    try {
      thread.setContextClassLoader(classOf[StringConvert].getClassLoader)
      val convert: StringConvert = new StringConvert()

      assertThat(convert.convertToString(OptionalInt.of(12))).isEqualTo("12")
      assertThat(convert.convertToString(OptionalInt.empty())).isEqualTo("")
      assertThat(convert.convertFromString(classOf[OptionalInt], "34")).isEqualTo(OptionalInt.of(34))

      assertThat(convert.convertToString(OptionalLong.of(56L))).isEqualTo("56")
      assertThat(convert.convertFromString(classOf[OptionalLong], "78")).isEqualTo(OptionalLong.of(78L))

      assertThat(convert.convertToString(OptionalDouble.of(1.25d))).isEqualTo("1.25")
      assertThat(convert.convertFromString(classOf[OptionalDouble], "2.5")).isEqualTo(OptionalDouble.of(2.5d))

      val token: TypeToken[?] = convert.convertFromString(classOf[TypeToken[?]], "java.util.List<java.lang.String>")
      assertThat(convert.convertToString(classOf[TypeToken[?]], token)).isEqualTo("java.util.List<java.lang.String>")
    } finally {
      thread.setContextClassLoader(originalLoader)
    }
  }

  @Test
  def loadsTypesWithoutAContextClassLoaderDuringConstruction(): Unit = {
    val thread: Thread = Thread.currentThread()
    val originalLoader: ClassLoader = thread.getContextClassLoader

    try {
      thread.setContextClassLoader(null)
      val convert: StringConvert = new StringConvert()

      assertThat(convert.convertFromString(classOf[OptionalInt], "123")).isEqualTo(OptionalInt.of(123))
    } finally {
      thread.setContextClassLoader(originalLoader)
    }
  }

  @Test
  def registersConvertersUsingPublicMethodNamesAndConstructors(): Unit = {
    val convert: StringConvert = new StringConvert(false)

    convert.registerMethods(classOf[Integer], "toString", "valueOf")
    assertThat(convert.convertToString(classOf[Integer], Integer.valueOf(7))).isEqualTo("7")
    assertThat(convert.convertFromString(classOf[Integer], "42")).isEqualTo(Integer.valueOf(42))

    convert.registerMethods(classOf[Instant], "toString", "parse")
    val instant: Instant = Instant.parse("2024-01-02T03:04:05Z")
    assertThat(convert.convertToString(instant)).isEqualTo("2024-01-02T03:04:05Z")
    assertThat(convert.convertFromString(classOf[Instant], "2024-01-02T03:04:05Z")).isEqualTo(instant)

    convert.registerMethodConstructor(classOf[File], "getPath")
    assertThat(convert.convertFromString(classOf[File], "build.gradle").getPath).isEqualTo("build.gradle")

    convert.registerMethodConstructor(classOf[CharSequenceConstructorValue], "asText")
    assertThat(convert.convertFromString(classOf[CharSequenceConstructorValue], "delta").asText()).isEqualTo("delta")
  }
}

final class CharSequenceConstructorValue(raw: CharSequence) {
  private val value: String = raw.toString

  def asText(): String = value
}
