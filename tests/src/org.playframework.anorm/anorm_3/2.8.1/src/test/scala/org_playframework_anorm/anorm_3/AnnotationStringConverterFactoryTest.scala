/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework_anorm.anorm_3

import org.joda.convert.FromString
import org.joda.convert.FromStringFactory
import org.joda.convert.StringConvert
import org.joda.convert.ToString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AnnotationStringConverterFactoryTest {
  @Test
  def reachesAnnotatedConstructorInterfaceAndFactoryDiscoveryPaths(): Unit = {
    val convert: StringConvert = StringConvert.create()

    val stringConstructed: StringAnnotatedConstructorValue = convert.convertFromString(
      classOf[StringAnnotatedConstructorValue],
      "string-constructor"
    )
    val charSequenceConstructed: CharSequenceAnnotatedConstructorValue = convert.convertFromString(
      classOf[CharSequenceAnnotatedConstructorValue],
      "char-sequence-constructor"
    )
    val interfaceValue: InterfaceAnnotatedValue = new InterfaceAnnotatedValue("interface")
    val factoryFailure: IllegalStateException = assertThrows(
      classOf[IllegalStateException],
      () => convert.convertFromString(classOf[FactoryAnnotatedValue], "factory")
    )

    assertEquals("string-constructor", convert.convertToString(stringConstructed))
    assertEquals("char-sequence-constructor", convert.convertToString(charSequenceConstructed))
    assertEquals("interface", convert.convertToString(interfaceValue))
    assertEquals(
      s"Class annotated with @ToString but not with @FromString: ${classOf[FactoryAnnotatedValue].getName}",
      factoryFailure.getMessage
    )
  }
}

final class StringAnnotatedConstructorValue @FromString() (private val value: String) {
  @ToString
  def asText: String = value
}

final class CharSequenceAnnotatedConstructorValue @FromString() (private val value: CharSequence) {
  @ToString
  def asText: String = value.toString
}

trait ConvertibleAnnotatedInterface {
  @ToString
  def asText: String
}

final class InterfaceAnnotatedValue @FromString() (private val value: String)
    extends ConvertibleAnnotatedInterface {
  override def asText: String = value
}

@FromStringFactory(factory = classOf[FactoryAnnotatedValueFactory])
final class FactoryAnnotatedValue(private val value: String) {
  @ToString
  def asText: String = value
}

final class FactoryAnnotatedValueFactory private ()
