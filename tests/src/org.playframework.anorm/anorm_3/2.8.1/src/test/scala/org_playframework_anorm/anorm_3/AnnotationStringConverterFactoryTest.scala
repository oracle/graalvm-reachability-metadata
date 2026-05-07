/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework_anorm.anorm_3

import scala.annotation.static

import org.assertj.core.api.Assertions.assertThat
import org.joda.convert.FromString
import org.joda.convert.FromStringFactory
import org.joda.convert.StringConvert
import org.joda.convert.ToString
import org.junit.jupiter.api.Test

class AnnotationStringConverterFactoryTest {
  @Test
  def findsAnnotatedConstructorsMethodsInterfacesAndFactories(): Unit = {
    val convert: StringConvert = new StringConvert(false)

    val fromStringConstructor: StringConstructorFixture =
      convert.convertFromString(classOf[StringConstructorFixture], "string-constructor")
    val fromCharSequenceConstructor: CharSequenceConstructorFixture =
      convert.convertFromString(classOf[CharSequenceConstructorFixture], "char-sequence-constructor")
    val fromMethod: MethodAnnotatedValue =
      convert.convertFromString(classOf[MethodAnnotatedValue], "method")
    val fromInterface: InterfaceAnnotatedValue =
      convert.convertFromString(classOf[InterfaceAnnotatedValue], "interface")
    val fromFactory: FactoryAnnotatedValue =
      convert.convertFromString(classOf[FactoryAnnotatedValue], "factory")

    assertThat(convert.convertToString(fromStringConstructor)).isEqualTo("string-constructor")
    assertThat(convert.convertToString(fromCharSequenceConstructor)).isEqualTo("char-sequence-constructor")
    assertThat(convert.convertToString(fromMethod)).isEqualTo("method")
    assertThat(convert.convertToString(fromInterface)).isEqualTo("interface")
    assertThat(convert.convertToString(fromFactory)).isEqualTo("factory")
  }
}

final class StringConstructorFixture @FromString() (raw: String) {
  private val value: String = raw

  @ToString
  def asText(): String = value
}

final class CharSequenceConstructorFixture @FromString() (raw: CharSequence) {
  private val value: String = raw.toString

  @ToString
  def asText(): String = value
}

final class MethodAnnotatedValue(raw: String) {
  private val value: String = raw

  @ToString
  def asText(): String = value
}

object MethodAnnotatedValue {
  @FromString
  @static
  def parse(value: String): MethodAnnotatedValue = new MethodAnnotatedValue(value)
}

trait ConvertibleInterface {
  @ToString
  def asText(): String
}

final class InterfaceAnnotatedValue @FromString() (raw: String) extends ConvertibleInterface {
  private val value: String = raw

  override def asText(): String = value
}

@FromStringFactory(factory = classOf[FactoryAnnotatedValueFactory])
final class FactoryAnnotatedValue(raw: String) {
  private val value: String = raw

  @ToString
  def asText(): String = value
}

final class FactoryAnnotatedValueFactory private ()

object FactoryAnnotatedValueFactory {
  @FromString
  @static
  def parse(value: String): FactoryAnnotatedValue = new FactoryAnnotatedValue(value)
}
