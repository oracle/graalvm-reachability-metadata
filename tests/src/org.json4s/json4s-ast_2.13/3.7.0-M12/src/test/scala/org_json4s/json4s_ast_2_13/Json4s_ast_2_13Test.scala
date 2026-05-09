/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_json4s.json4s_ast_2_13

import org.json4s.Diff
import org.json4s.JsonAST
import org.json4s.JsonAST._
import org.json4s.JValue._
import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.Test

import scala.language.implicitConversions

class Json4s_ast_2_13Test {
  @Test
  def constructsAstNodesAndUnboxesValues(): Unit = {
    val profile: JObject = JObject(
      JField("name", JString("Ada")),
      JField("age", JInt(36)),
      JField("active", JBool(true)),
      JField("ratio", JDouble(1.5d)),
      JField("balance", JDecimal(BigDecimal("123.45"))),
      JField("longId", JLong(9000000000L)),
      JField("tags", JArray(List(JString("scala"), JInt(3)))),
      JField("missing", JNull),
      JField("absent", JNothing)
    )

    assertEquals(
      Map(
        "name" -> "Ada",
        "age" -> BigInt(36),
        "active" -> true,
        "ratio" -> 1.5d,
        "balance" -> BigDecimal("123.45"),
        "longId" -> 9000000000L,
        "tags" -> List("scala", BigInt(3)),
        "missing" -> null,
        "absent" -> None
      ),
      profile.values
    )
    assertEquals(List(JString("Ada"), JInt(36), JBool.True, JDouble(1.5d), JDecimal(BigDecimal("123.45")), JLong(9000000000L), JArray(List(JString("scala"), JInt(3))), JNull, JNothing), profile.children)
    assertEquals(List(JString("scala"), JInt(3)), profile.obj.find(_._1 == "tags").map(_._2.children).get)
    assertEquals(JInt(3), JArray(List(JString("scala"), JInt(3)))(1))
    try {
      JArray(Nil)(0)
      fail("Expected an empty array access to fail")
    } catch {
      case _: IndexOutOfBoundsException => ()
    }
    assertEquals(Some(JString("Ada")), JString("Ada").toOption)
    assertEquals(None, JNull.toOption)
    assertEquals(None, JNothing.toOption)
    assertEquals(Some(JNull), JNull.toSome)
    assertEquals(None, JNothing.toSome)
    assertSame(JBool.True, JBool(true))
    assertSame(JBool.False, JBool(false))
  }

  @Test
  def comparesObjectsByFieldSetAndSupportsCaseClassOperations(): Unit = {
    val first: JObject = JObject(
      JField("name", JString("Ada")),
      JField("age", JInt(36))
    )
    val reordered: JObject = JObject(
      JField("age", JInt(36)),
      JField("name", JString("Ada"))
    )
    val changed: JObject = first.copy(obj = JField("name", JString("Grace")) :: first.obj.tail)

    assertEquals(first, reordered)
    assertEquals(first.hashCode(), reordered.hashCode())
    assertNotEquals(first, changed)
    assertEquals(List(JField("name", JString("Ada")), JField("age", JInt(36))), first.obj)
    assertEquals(JArray(List(JInt(1), JInt(2))), JArray(List(JInt(1))).copy(arr = List(JInt(1), JInt(2))))
    assertEquals(JSet(Set(JInt(1), JInt(2))), JSet(Set(JInt(1))).copy(set = Set(JInt(1), JInt(2))))
  }

  @Test
  def deconstructsAstNodesWithPublicExtractors(): Unit = {
    val document: JValue = JObject(
      JField("items", JArray(List(JString("book"), JInt(2)))),
      JField("enabled", JBool(true))
    )

    val extracted: (String, List[JValue], Boolean) = document match {
      case JObject(List(JField(itemsName, JArray(items)), JField(enabledName, JBool(enabled)))) =>
        (s"$itemsName:$enabledName", items, enabled)
      case other => fail(s"Unexpected JSON shape: $other")
    }

    assertEquals("items:enabled", extracted._1)
    assertEquals(List(JString("book"), JInt(2)), extracted._2)
    assertTrue(extracted._3)
  }

  @Test
  def concatenatesValuesWithJNothingAsIdentity(): Unit = {
    assertEquals(JInt(1), JNothing ++ JInt(1))
    assertEquals(JInt(1), JInt(1) ++ JNothing)
    assertEquals(JArray(List(JInt(1), JInt(2))), JInt(1) ++ JInt(2))
    assertEquals(JArray(List(JInt(1), JInt(2), JInt(3))), JArray(List(JInt(1), JInt(2))) ++ JInt(3))
    assertEquals(JArray(List(JInt(0), JInt(1), JInt(2))), JInt(0) ++ JArray(List(JInt(1), JInt(2))))
    assertEquals(JArray(List(JInt(1), JInt(2), JInt(3))), JArray(List(JInt(1))) ++ JArray(List(JInt(2), JInt(3))))
    assertEquals(JArray(List(JInt(1), JInt(2), JInt(3))), JsonAST.concat(JInt(1), JInt(2), JInt(3)))
    assertEquals(JNothing, JsonAST.concat())
  }

  @Test
  def mergesObjectsArraysAndScalarValues(): Unit = {
    val left: JObject = JObject(
      JField("name", JString("joe")),
      JField("age", JInt(10)),
      JField("tags", JArray(List(JString("a"), JString("b")))),
      JField("nested", JObject(JField("x", JInt(1)))),
      JField("removed", JString("kept"))
    )
    val right: JObject = JObject(
      JField("name", JString("joe")),
      JField("age", JInt(11)),
      JField("tags", JArray(List(JString("b"), JString("c")))),
      JField("nested", JObject(JField("y", JInt(2)))),
      JField("iq", JInt(105)),
      JField("removed", JNothing)
    )

    assertEquals(
      JObject(
        JField("name", JString("joe")),
        JField("age", JInt(11)),
        JField("tags", JArray(List(JString("a"), JString("b"), JString("c")))),
        JField("nested", JObject(JField("x", JInt(1)), JField("y", JInt(2)))),
        JField("removed", JString("kept")),
        JField("iq", JInt(105))
      ),
      left merge right
    )
    assertEquals(JArray(List(JInt(1), JInt(2), JInt(3))), JArray(List(JInt(1), JInt(2))) merge JArray(List(JInt(2), JInt(3))))
    assertEquals(JString("replacement"), JInt(1) merge JString("replacement"))
    assertEquals(JInt(1), JNothing merge JInt(1))
    assertEquals(JInt(1), JInt(1) merge JNothing)
  }

  @Test
  def computesDiffsForObjectsArraysScalarsAndSets(): Unit = {
    val diff: Diff = JObject(JField("age", JInt(10)), JField("name", JString("joe"))).diff(
      JObject(JField("age", JInt(11)), JField("city", JString("Helsinki")))
    )
    assertEquals(JObject(JField("age", JInt(11))), diff.changed)
    assertEquals(JObject(JField("city", JString("Helsinki"))), diff.added)
    assertEquals(JObject(JField("name", JString("joe"))), diff.deleted)

    val arrayDiff: Diff = JArray(List(JInt(1), JString("old"), JBool(true))).diff(JArray(List(JInt(1), JString("new"))))
    assertEquals(JString("new"), arrayDiff.changed)
    assertEquals(JNothing, arrayDiff.added)
    assertEquals(JArray(List(JBool.True)), arrayDiff.deleted)

    assertEquals(Diff(JDouble(2.0d), JNothing, JNothing), JDouble(1.0d).diff(JDouble(2.0d)))
    assertEquals(Diff(JDecimal(BigDecimal("2.5")), JNothing, JNothing), JDecimal(BigDecimal("1.5")).diff(JDecimal(BigDecimal("2.5"))))
    assertEquals(Diff(JString("after"), JNothing, JNothing), JString("before").diff(JString("after")))
    assertEquals(Diff(JBool.False, JNothing, JNothing), JBool.True.diff(JBool.False))
    assertEquals(Diff(JNothing, JInt(1), JNothing), JNothing.diff(JInt(1)))
    assertEquals(Diff(JNothing, JNothing, JInt(1)), JInt(1).diff(JNothing))

    val firstSet: JSet = JSet(Set(JInt(1), JInt(2), JInt(3)))
    val secondSet: JSet = JSet(Set(JInt(2), JInt(3), JInt(4)))
    assertEquals(JSet(Set(JInt(2), JInt(3))), firstSet.intersect(secondSet))
    assertEquals(JSet(Set(JInt(1), JInt(2), JInt(3), JInt(4))), firstSet.union(secondSet))
    assertEquals(JSet(Set(JInt(1))), firstSet.difference(secondSet))

    val setDiff: Diff = firstSet.diff(secondSet)
    assertEquals(JNothing, setDiff.changed)
    assertEquals(JSet(Set(JInt(4))), setDiff.added)
    assertEquals(JSet(Set(JInt(1))), setDiff.deleted)
  }

  @Test
  def convertsFieldListsWithJsonDsl(): Unit = {
    import org.json4s.JsonDSL.WithDouble._

    val fields: List[JField] = List(
      JField("id", JInt(7)),
      JField("status", JString("open")),
      JField("labels", JArray(List(JString("new"), JString("triaged"))))
    )
    val fromFields: JObject = fields: JObject

    assertEquals(fields, fromFields.obj)
    assertEquals(JInt(7), field(fromFields, "id"))
    assertEquals(JString("open"), field(fromFields, "status"))
    assertEquals(JArray(List(JString("new"), JString("triaged"))), field(fromFields, "labels"))
  }

  @Test
  def mapsDiffPartsWithoutRewritingEmptyParts(): Unit = {
    val original: Diff = Diff(JObject(JField("changed", JInt(1))), JNothing, JObject(JField("deleted", JString("old"))))
    val mapped: Diff = original.map {
      case JObject(fields) => JObject(fields.map { case (name, value) => JField(name.toUpperCase, value) })
      case other => other
    }

    assertEquals(JObject(JField("CHANGED", JInt(1))), mapped.changed)
    assertEquals(JNothing, mapped.added)
    assertEquals(JObject(JField("DELETED", JString("old"))), mapped.deleted)

    val Diff(changed, added, deleted) = JInt(1).diff(JInt(2))
    assertEquals(JInt(2), changed)
    assertEquals(JNothing, added)
    assertEquals(JNothing, deleted)
  }

  @Test
  def buildsJsonObjectsWithBigDecimalDslConversions(): Unit = {
    import org.json4s.JsonDSL.WithBigDecimal._

    val attributes: Map[String, String] = Map("currency" -> "EUR", "channel" -> "online")
    val invoice: JObject =
      ("name" -> "invoice") ~
        ("paid" -> true) ~
        ("total" -> BigDecimal("19.99")) ~
        ("lineAmounts" -> List(1.25d, 2.50d)) ~
        ("labels" -> List("digital", "priority")) ~
        ("attributes" -> attributes) ~
        ("reference" -> Option.empty[String])

    assertEquals(JString("invoice"), field(invoice, "name"))
    assertEquals(JBool.True, field(invoice, "paid"))
    assertEquals(JDecimal(BigDecimal("19.99")), field(invoice, "total"))
    assertEquals(JArray(List(JDecimal(BigDecimal("1.25")), JDecimal(BigDecimal("2.5")))), field(invoice, "lineAmounts"))
    assertEquals(JArray(List(JString("digital"), JString("priority"))), field(invoice, "labels"))
    assertEquals(JString("EUR"), field(field(invoice, "attributes").asInstanceOf[JObject], "currency"))
    assertEquals(JString("online"), field(field(invoice, "attributes").asInstanceOf[JObject], "channel"))
    assertTrue(invoice.obj.contains(JField("reference", JNothing)))
  }

  @Test
  def buildsJsonObjectsWithDoubleDslConversionsAndAssociativeAliases(): Unit = {
    import org.json4s.JsonDSL.WithDouble._

    val tupleObject: JObject = ("double" -> 1.25d) ~~ ("float" -> 2.5f) ~~ ("long" -> 3L)
    val listObject: JObject = JObject(JField("base", JInt(1))) ~ ("extra" -> JString("value"))
    val combined: JObject = tupleObject ~ listObject

    assertEquals(JDouble(1.25d), field(combined, "double"))
    assertEquals(JDouble(2.5d), field(combined, "float"))
    assertEquals(JInt(3), field(combined, "long"))
    assertEquals(JInt(1), field(combined, "base"))
    assertEquals(JString("value"), field(combined, "extra"))
    assertEquals(JObject(JField("answer", JInt(42))), ("answer" -> 42): JObject)
    assertEquals(JArray(List(JInt(1), JInt(2), JInt(3))), List(1, 2, 3): JArray)
    assertEquals(JNothing, Option.empty[Int]: JValue)
    assertEquals(JInt(7), Option(7): JValue)
  }

  private def field(json: JObject, name: String): JValue = json.obj.find(_._1 == name).map(_._2).getOrElse(JNothing)
}
