/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_json4s.json4s_ast_3

import org.json4s.*
import org.json4s.MonadicJValue.jvalueToMonadic
import org.json4s.prefs.EmptyValueStrategy
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

import java.io.StringWriter
import scala.language.dynamics
import scala.language.implicitConversions

class Json4s_ast_3Test {
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
      JField("missing", JNull)
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
        "missing" -> null
      ),
      profile.values
    )
    assertEquals(List(JString("scala"), JInt(3)), (profile \ "tags").children)
    assertEquals(JInt(3), (profile \ "tags")(1))
    assertEquals(Some(JString("Ada")), (profile \ "name").toOption)
    assertEquals(None, JNull.toOption)
    assertEquals(Some(JNull), JNull.toSome)
    assertEquals(None, JNothing.toSome)
    assertSame(JBool.True, JBool(true))
    assertSame(JBool.False, JBool(false))

    assertEquals(JInt(1), JNothing ++ JInt(1))
    assertEquals(JArray(List(JInt(1), JInt(2), JInt(3))), JInt(1) ++ JArray(List(JInt(2), JInt(3))))
    assertEquals(JArray(List(JInt(1), JInt(2), JInt(3))), JsonAST.concat(JInt(1), JInt(2), JInt(3)))
  }

  @Test
  def queriesNestedValuesWithXPathLikeNavigation(): Unit = {
    val json: JObject = libraryJson

    assertEquals(JString("root"), json \ "name")
    assertEquals(
      JArray(List(JString("Functional Programming"), JString("Domain Modeling"))),
      json \ "store" \ "books" \ "title"
    )
    assertEquals(JString("Domain Modeling"), (json \ "store" \ "books")(1) \ "title")
    assertEquals(JString("Julia"), json \ "store" \ "owner" \ "name")
    assertEquals(JNothing, json \ "doesNotExist")

    assertEquals(
      JObject(
        JField("name", JString("root")),
        JField("name", JString("Julia"))
      ),
      json \\ "name"
    )

    assertEquals(Some(JDecimal(BigDecimal("42.00"))), json.find(_ == JDecimal(BigDecimal("42.00"))))
    assertEquals(Some(JField("title", JString("Domain Modeling"))), json.findField(_ == JField("title", JString("Domain Modeling"))))
    assertEquals(List(JField("title", JString("Functional Programming")), JField("title", JString("Domain Modeling"))), json.filterField(_._1 == "title"))
    assertEquals(List(JDecimal(BigDecimal("39.50")), JDecimal(BigDecimal("42.00"))), json.filter {
      case JDecimal(price) => price >= BigDecimal("39.50")
      case _ => false
    })

    val titles: List[String] = (json \ "store" \ "books" \ "title").filter {
      case JString(title) => title.contains(" ")
      case _ => false
    }.map { case JString(title) => title }
    assertEquals(List("Functional Programming", "Domain Modeling"), titles)
  }

  @Test
  def transformsFindsRemovesAndRewritesNestedValues(): Unit = {
    val source: JObject = JObject(
      JField("first_name", JString("Grace")),
      JField("last_name", JString("Hopper")),
      JField("metadata", JObject(
        JField("zip_code", JString("10001")),
        JField("attempts", JArray(List(JInt(1), JInt(2), JNull, JNothing))),
        JField("enabled", JBool(false))
      )),
      JField("_links", JObject(JField("self_href", JString("/users/1"))))
    )

    val incrementedNumbers: JValue = source.transform { case JInt(number) => JInt(number + 10) }
    assertEquals(JArray(List(JInt(11), JInt(12), JNull, JNothing)), incrementedNumbers \ "metadata" \ "attempts")

    val renamedZip: JValue = source.transformField { case JField("zip_code", value) => JField("zipCode", value) }
    assertEquals(JString("10001"), renamedZip \ "metadata" \ "zipCode")
    assertEquals(JNothing, renamedZip \ "metadata" \ "zip_code")

    val upperCaseFields: JValue = source.mapField { case JField(name, value) => JField(name.toUpperCase, value) }
    assertEquals(JString("Grace"), upperCaseFields \ "FIRST_NAME")

    assertEquals(
      JObject(
        JField("firstName", JString("Grace")),
        JField("lastName", JString("Hopper")),
        JField("metadata", JObject(
          JField("zipCode", JString("10001")),
          JField("attempts", JArray(List(JInt(1), JInt(2), JNull, JNothing))),
          JField("enabled", JBool(false))
        )),
        JField("_links", JObject(JField("selfHref", JString("/users/1"))))
      ),
      source.camelizeKeys
    )
    assertEquals(JString("10001"), source.camelizeKeys.snakizeKeys \ "metadata" \ "zip_code")
    assertEquals(JString("Grace"), source.pascalizeKeys \ "FirstName")
    assertEquals(JString("Grace"), JObject(JField("firstName", JString("Grace"))).underscoreCamelCaseKeysOnly \ "first_name")

    val compacted: JValue = source.noNulls
    assertEquals(JArray(List(JInt(1), JInt(2))), compacted \ "metadata" \ "attempts")
    assertEquals(JNothing, compacted \ "metadata" \ "attempts" \ "missing")

    val withoutDisabledFlags: JValue = source.remove(_ == JBool(false))
    assertEquals(JNothing, withoutDisabledFlags \ "metadata" \ "enabled")

    val withoutMetadata: JValue = source.removeField(_._1 == "metadata")
    assertEquals(JNothing, withoutMetadata \ "metadata")
  }

  @Test
  def replacesValuesByObjectAndArrayPaths(): Unit = {
    val source: JObject = JObject(
      JField("people", JArray(List(
        JObject(JField("name", JString("Ann")), JField("address", JObject(JField("city", JString("Paris"))))),
        JObject(JField("name", JString("Ben")), JField("address", JObject(JField("city", JString("Rome")))))
      ))),
      JField("status", JString("draft"))
    )

    val allCitiesChanged: JValue = source.replace("people[]" :: "address" :: "city" :: Nil, JString("Berlin"))
    assertEquals(JString("Berlin"), (allCitiesChanged \ "people")(0) \ "address" \ "city")
    assertEquals(JString("Berlin"), (allCitiesChanged \ "people")(1) \ "address" \ "city")

    val secondNameChanged: JValue = source.replace("people[1]" :: "name" :: Nil, JString("Beatrice"))
    assertEquals(JString("Ann"), (secondNameChanged \ "people")(0) \ "name")
    assertEquals(JString("Beatrice"), (secondNameChanged \ "people")(1) \ "name")

    val topLevelChanged: JValue = source.replace("status" :: Nil, JString("published"))
    assertEquals(JString("published"), topLevelChanged \ "status")
  }

  @Test
  def mergesDiffsAndComparesCompositeValues(): Unit = {
    val left: JObject = JObject(
      JField("name", JString("joe")),
      JField("age", JInt(10)),
      JField("tags", JArray(List(JString("a"), JString("b")))),
      JField("nested", JObject(JField("x", JInt(1))))
    )
    val right: JObject = JObject(
      JField("name", JString("joe")),
      JField("age", JInt(11)),
      JField("tags", JArray(List(JString("b"), JString("c")))),
      JField("nested", JObject(JField("y", JInt(2)))),
      JField("iq", JInt(105))
    )

    assertEquals(
      JObject(
        JField("name", JString("joe")),
        JField("age", JInt(11)),
        JField("tags", JArray(List(JString("a"), JString("b"), JString("c")))),
        JField("nested", JObject(JField("x", JInt(1)), JField("y", JInt(2)))),
        JField("iq", JInt(105))
      ),
      Merge.merge(left, right)
    )

    val diff: Diff = JObject(JField("age", JInt(10)), JField("name", JString("joe"))).diff(
      JObject(JField("age", JInt(11)), JField("city", JString("Helsinki")))
    )
    assertEquals(JObject(JField("age", JInt(11))), diff.changed)
    assertEquals(JObject(JField("city", JString("Helsinki"))), diff.added)
    assertEquals(JObject(JField("name", JString("joe"))), diff.deleted)

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
  def navigatesFieldsWithDynamicJsonValues(): Unit = {
    import org.json4s.DynamicJValue.dyn

    val document: JObject = JObject(
      JField("order", JObject(
        JField("id", JString("A-100")),
        JField("items", JArray(List(
          JObject(JField("sku", JString("BK-1")), JField("quantity", JInt(1))),
          JObject(JField("sku", JString("BK-2")), JField("quantity", JInt(3)))
        ))),
        JField("status", JObject(JField("current", JString("fulfilled"))))
      ))
    )

    val dynamicDocument: DynamicJValue = dyn(document)

    assertEquals(JString("A-100"), dynamicDocument.order.id.raw)
    assertEquals(JArray(List(JString("BK-1"), JString("BK-2"))), dynamicDocument.order.items.sku.raw)
    assertEquals(JArray(List(JInt(1), JInt(3))), dynamicDocument.order.items.quantity.raw)
    assertEquals(JString("fulfilled"), dynamicDocument.order.status.current.raw)
    assertEquals(JNothing, dynamicDocument.order.status.previous.raw)
    assertEquals(dynamicDocument.order.status.current, JString("fulfilled"))
    assertEquals(dyn(JString("fulfilled")), dynamicDocument.order.status.current)
  }

  @Test
  def writesReadsAndFormatsValuesWithPublicTypeClasses(): Unit = {
    import org.json4s.DefaultReaders.*
    import org.json4s.DefaultWriters.*

    case class Account(name: String, score: Int)
    implicit val accountWriter: Writer[Account] = Writer.writer2[String, Int, Account](account => (account.name, account.score))("name", "score")
    implicit val accountReader: Reader[Account] = Reader.reader2[String, Int, Account](Account.apply)("name", "score")

    val account: Account = Account("native", 99)
    val accountJson: JValue = Writer[Account].write(account)
    assertEquals(JObject(JField("name", JString("native")), JField("score", JInt(99))), accountJson)
    assertEquals(Right(account), Reader[Account].readEither(accountJson))
    assertTrue(Reader[Account].readEither(JObject(JField("name", JString("native")))).isLeft)

    assertEquals(JArray(List(JInt(1), JInt(2), JInt(3))), Writer[Seq[Int]].write(Seq(1, 2, 3)))
    assertEquals(JNull, Writer[Option[String]].write(None))
    assertEquals(JString("present"), Writer[Option[String]].write(Some("present")))
    assertEquals(Right(List(1, 2, 3)), Reader[List[Int]].readEither(JArray(List(JInt(1), JLong(2), JDouble(3.0d)))))
    assertEquals(Right(Map("a" -> 1, "b" -> 2)), Reader[Map[String, Int]].readEither(JObject(JField("a", JInt(1)), JField("b", JInt(2)))))

    case class Key(id: Int)
    implicit val keyWriter: JsonKeyWriter[Key] = JsonKeyWriter.fromToString[Int].contramap[Key](_.id)
    assertEquals(JObject(JField("7", JString("seven"))), Writer[Map[Key, String]].write(Map(Key(7) -> "seven")))
  }

  @Test
  def buildsJsonObjectsWithDslConversions(): Unit = {
    import org.json4s.JsonDSL.WithBigDecimal.*

    val attributes: Map[String, String] = Map("currency" -> "EUR", "channel" -> "online")
    val invoice: JObject =
      ("name" -> "invoice") ~
        ("paid" -> true) ~
        ("total" -> BigDecimal("19.99")) ~
        ("lineAmounts" -> List(1.25d, 2.50d)) ~
        ("labels" -> List("digital", "priority")) ~
        ("attributes" -> attributes) ~
        ("reference" -> Option.empty[String])

    assertEquals(JString("invoice"), invoice \ "name")
    assertEquals(JBool.True, invoice \ "paid")
    assertEquals(JDecimal(BigDecimal("19.99")), invoice \ "total")
    assertEquals(JArray(List(JDecimal(BigDecimal("1.25")), JDecimal(BigDecimal("2.5")))), invoice \ "lineAmounts")
    assertEquals(JArray(List(JString("digital"), JString("priority"))), invoice \ "labels")
    assertEquals(JString("EUR"), invoice \ "attributes" \ "currency")
    assertEquals(JString("online"), invoice \ "attributes" \ "channel")
    assertTrue(invoice.obj.contains(JField("reference", JNothing)))
  }

  @Test
  def buildsAstAndTextWithJsonWriters(): Unit = {
    val ast: JValue = JsonWriter.ast
      .startObject()
      .startField("message")
      .string("hello")
      .startField("numbers")
      .startArray()
      .int(1)
      .long(2L)
      .double(3.5d)
      .endArray()
      .startField("nested")
      .startObject()
      .startField("ok")
      .boolean(true)
      .endObject()
      .endObject()
      .result

    assertEquals(
      JObject(
        JField("message", JString("hello")),
        JField("numbers", JArray(List(JInt(1), JInt(2), JDouble(3.5d)))),
        JField("nested", JObject(JField("ok", JBool(true))))
      ),
      ast
    )

    val decimalAst: JValue = JsonWriter.bigDecimalAst.startArray().float(1.25f).double(2.5d).bigDecimal(BigDecimal("3.75")).endArray().result
    assertEquals(JArray(List(JDecimal(BigDecimal("1.25")), JDecimal(BigDecimal("2.5")), JDecimal(BigDecimal("3.75")))), decimalAst)

    val compactWriter: StringWriter = new StringWriter()
    JsonWriter.streaming(compactWriter, alwaysEscapeUnicode = false)
      .startObject()
      .startField("line")
      .string("one\ntwo")
      .startField("unicode")
      .string("é")
      .endObject()
    assertEquals("{\"line\":\"one\\ntwo\",\"unicode\":\"é\"}", compactWriter.toString)

    val prettyWriter: StringWriter = new StringWriter()
    JsonWriter.streamingPretty(prettyWriter, alwaysEscapeUnicode = true)
      .startArray()
      .string("é")
      .boolean(false)
      .endArray()
    assertTrue(prettyWriter.toString.contains("\\u00E9"))
    assertTrue(prettyWriter.toString.contains("\n"))
  }

  @Test
  def quotesStringsAndAppliesEmptyValueStrategies(): Unit = {
    assertEquals("quote\\\" slash\\\\ newline\\n", ParserUtil.quote("quote\" slash\\ newline\n", alwaysEscapeUnicode = false))
    assertEquals("caf\\u00E9", ParserUtil.quote("café", alwaysEscapeUnicode = true))

    val withEmptyValues: JObject = JObject(
      JField("present", JString("value")),
      JField("missing", JNothing),
      JField("items", JArray(List(JInt(1), JNothing, JNull)))
    )

    assertEquals(withEmptyValues, EmptyValueStrategy.skip.replaceEmpty(withEmptyValues))
    assertEquals(
      JObject(
        JField("present", JString("value")),
        JField("missing", JNull),
        JField("items", JArray(List(JInt(1), JNull, JNull)))
      ),
      EmptyValueStrategy.preserve.replaceEmpty(withEmptyValues)
    )
    assertEquals(Some(JNull), EmptyValueStrategy.preserve.noneValReplacement)
    assertEquals(None, EmptyValueStrategy.skip.noneValReplacement)
  }

  private def libraryJson: JObject = JObject(
    JField("name", JString("root")),
    JField("store", JObject(
      JField("owner", JObject(JField("name", JString("Julia")))),
      JField("books", JArray(List(
        JObject(
          JField("title", JString("Functional Programming")),
          JField("price", JDecimal(BigDecimal("39.50")))
        ),
        JObject(
          JField("title", JString("Domain Modeling")),
          JField("price", JDecimal(BigDecimal("42.00")))
        )
      )))
    ))
  )
}
