/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_tpolecat.typename_3

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertNotEquals, assertTrue}
import org.junit.jupiter.api.Test
import org.tpolecat.typename.{TypeName, typeName}

class Typename_3Test {
  @Test
  def derivesNamesForPrimitiveStandardAndUserDefinedTypes(): Unit = {
    assertTypeNameContains[Int]("Int")
    assertTypeNameContains[Boolean]("Boolean")
    assertTypeNameContains[String]("String")
    assertTypeNameContains[NamedRecord]("NamedRecord")
    assertTypeNameContains[SealedValue]("SealedValue")
  }

  @Test
  def derivesNamesForParameterizedNestedTupleAndFunctionTypes(): Unit = {
    assertTypeNameContains[Option[Int]]("Option", "Int")
    assertTypeNameContains[Either[String, NamedRecord]]("Either", "String", "NamedRecord")
    assertTypeNameContains[Map[String, List[NamedRecord]]]("Map", "String", "List", "NamedRecord")
    assertTypeNameContains[(String, Int, NamedRecord)]("String", "Int", "NamedRecord")
    assertTypeNameContains[NamedRecord => Either[String, Int]]("NamedRecord", "Either", "String", "Int")
  }

  @Test
  def packageFunctionAndSummonedTypeClassUseTheSameDerivedName(): Unit = {
    val fromPackageFunction: String = typeName[Map[String, List[NamedRecord]]]
    val fromSummonedTypeClass: TypeName[Map[String, List[NamedRecord]]] = summon[TypeName[Map[String, List[NamedRecord]]]]

    assertEquals(fromSummonedTypeClass.value, fromPackageFunction)
    assertContainsAll(fromPackageFunction, "Map", "String", "List", "NamedRecord")
  }

  @Test
  def explicitTypeNameInstancesTakePrecedenceOverMacroDerivation(): Unit = {
    given TypeName[NamedRecord] = TypeName("explicit-name")

    assertEquals("explicit-name", typeName[NamedRecord])
    assertEquals("explicit-name", summon[TypeName[NamedRecord]].value)
  }

  @Test
  def contextualTypeNameCanBeRequiredByPublicApiStyleHelpers(): Unit = {
    val renderedPrimitive: String = renderDiagnostic(123)
    val renderedRecord: String = renderDiagnostic(NamedRecord("Ada", 42))

    assertContainsAll(renderedPrimitive, "Int", "123")
    assertContainsAll(renderedRecord, "NamedRecord", "Ada", "42")
  }

  @Test
  def typeNameHasCaseClassValueSemantics(): Unit = {
    val name: TypeName[NamedRecord] = TypeName("record")
    val sameName: TypeName[NamedRecord] = TypeName.apply("record")
    val copied: TypeName[NamedRecord] = name.copy(value = "renamed")
    val TypeName(extracted) = name

    assertEquals("record", name.value)
    assertEquals("record", extracted)
    assertEquals(name, sameName)
    assertEquals(name.hashCode, sameName.hashCode)
    assertEquals(TypeName("renamed"), copied)
    assertNotEquals(name, copied)
    assertEquals(1, name.productArity)
    assertEquals("TypeName", name.productPrefix)
    assertEquals("record", name.productElement(0))
    assertEquals("value", name.productElementName(0))
    assertEquals(List("value"), name.productElementNames.toList)
    assertEquals(List("record"), name.productIterator.toList)
    assertTrue(name.canEqual(TypeName("other")))
    assertFalse(name.canEqual("record"))
    assertEquals("TypeName(record)", name.toString)
  }

  private def renderDiagnostic[A](value: A)(using name: TypeName[A]): String =
    s"${name.value}: $value"

  private def assertTypeNameContains[A](expectedParts: String*)(using name: TypeName[A]): Unit =
    assertContainsAll(name.value, expectedParts*)

  private def assertContainsAll(actual: String, expectedParts: String*): Unit =
    expectedParts.foreach { expectedPart =>
      assertTrue(
        actual.contains(expectedPart),
        s"Expected derived type name '$actual' to contain '$expectedPart'"
      )
    }
}

final case class NamedRecord(name: String, count: Int)

sealed trait SealedValue
case object FirstSealedValue extends SealedValue
