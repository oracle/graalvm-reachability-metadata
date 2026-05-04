/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_json4s.json4s_core_3

import org.json4s.DefaultFormats
import org.json4s.Formats
import org.json4s.reflect.ClassDescriptor
import org.json4s.reflect.ConstructorParamDescriptor
import org.json4s.reflect.Executable
import org.json4s.reflect.ParameterNameReader
import org.json4s.reflect.Reflector
import org.json4s.reflect.ScalaSigReader
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

case class ReflectorDescriptorSubject(name: String, age: Int = 30, aliases: List[String] = Nil) {
  val displayName: String = s"$name:$age"
}

class ReflectorOuterForCompanionMapping(val prefix: String) {
  object InnerDescriptorCompanion

  def Inner: AnyRef = InnerDescriptorCompanion

  class Inner(val name: String) {
    val qualifiedName: String = s"$prefix-$name"
  }
}

class ReflectorDescriptorParameterNameReader extends ParameterNameReader {
  override def lookupParameterNames(constructor: Executable): Seq[String] = {
    constructor.getParameterTypes().length match {
      case 3 => Seq("name", "age", "aliases")
      case 2 => Seq(ScalaSigReader.OuterFieldName, "name")
      case 1 => Seq("name")
      case _ => Seq.empty
    }
  }
}

class ReflectorMappedCompanionParameterNameReader extends ParameterNameReader {
  override def lookupParameterNames(constructor: Executable): Seq[String] = {
    val parameterCount: Int = constructor.getParameterTypes().length
    if (parameterCount == 0) Seq.empty
    else ScalaSigReader.OuterFieldName +: (1 until parameterCount).map(index => s"arg$index")
  }
}

class ReflectorInnerClassDescriptorBuilderTest {
  @Test
  def describesCaseClassConstructorsCompanionMethodsAndFields(): Unit = {
    implicit val formats: Formats = DefaultFormats
    Reflector.clearCaches()

    val descriptor: ClassDescriptor = Reflector
      .createDescriptorWithFormats(
        Reflector.scalaTypeOf[ReflectorDescriptorSubject],
        new ReflectorDescriptorParameterNameReader
      )
      .asInstanceOf[ClassDescriptor]
    val constructorParamNames: Set[String] = descriptor.mostComprehensive.map(_.name).toSet
    val propertyNames: Set[String] = descriptor.properties.map(_.name).toSet

    assertEquals("ReflectorDescriptorSubject", descriptor.simpleName)
    assertTrue(constructorParamNames.contains("name"))
    assertTrue(constructorParamNames.contains("age"))
    assertTrue(constructorParamNames.contains("aliases"))
    assertTrue(propertyNames.contains("name"))
    assertTrue(propertyNames.contains("age"))
    assertTrue(propertyNames.contains("aliases"))
    assertTrue(propertyNames.contains("displayName"))
    assertTrue(descriptor.constructors.exists(_.constructor.method != null))
  }

  @Test
  def resolvesInnerClassCompanionThroughExplicitCompanionMapping(): Unit = {
    implicit val formats: Formats = DefaultFormats
    Reflector.clearCaches()

    val outer: ReflectorOuterForCompanionMapping = new ReflectorOuterForCompanionMapping("team")
    val inner: outer.Inner = new outer.Inner("ada")
    val companionMappings: List[(Class[_], AnyRef)] = List(inner.getClass -> outer.asInstanceOf[AnyRef])
    val descriptor: ClassDescriptor = Reflector
      .createDescriptorWithFormats(
        Reflector.scalaTypeOf(inner.getClass),
        new ReflectorMappedCompanionParameterNameReader,
        companionMappings
      )
      .asInstanceOf[ClassDescriptor]
    val mostComprehensiveParams: Seq[ConstructorParamDescriptor] = descriptor.mostComprehensive
    val outerParam: Option[ConstructorParamDescriptor] =
      mostComprehensiveParams.find(_.name == ScalaSigReader.OuterFieldName)

    assertEquals("Inner", descriptor.simpleName)
    assertTrue(descriptor.companion.isDefined)
    assertFalse(mostComprehensiveParams.isEmpty)
    assertTrue(outerParam.isDefined)
    assertTrue(outerParam.get.defaultValue.isDefined)
    assertSame(outer, outerParam.get.defaultValue.get.apply())
    assertTrue(descriptor.properties.exists(_.name == "name"))
    assertTrue(descriptor.properties.exists(_.name == "qualifiedName"))
    assertFalse(descriptor.properties.exists(_.name == ScalaSigReader.OuterFieldName))
  }
}
