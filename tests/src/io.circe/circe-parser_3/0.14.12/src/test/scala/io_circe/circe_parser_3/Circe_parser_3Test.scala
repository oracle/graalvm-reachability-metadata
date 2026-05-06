/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_parser_3

import cats.data.Validated
import io.circe.Decoder
import io.circe.Json
import io.circe.parser.decode
import io.circe.parser.decodeAccumulating
import io.circe.parser.parse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

class Circe_parser_3Test {
  private final case class Person(name: String, age: Int, tags: List[String])

  private given Decoder[Person] = Decoder.instance { cursor =>
    for {
      name <- cursor.get[String]("name")
      age <- cursor.get[Int]("age")
      tags <- cursor.get[List[String]]("tags")
    } yield Person(name, age, tags)
  }

  @Test
  def parsesNestedJsonDocumentAndNavigatesWithCursors(): Unit = {
    val input: String =
      """
        |{
        |  "name": "Leia",
        |  "age": 19,
        |  "active": true,
        |  "roles": ["general", "princess"],
        |  "profile": {
        |    "height": 1.5,
        |    "nickname": null
        |  }
        |}
        |""".stripMargin

    val root: Json = parsed(input)
    val cursor = root.hcursor

    assertEquals("Leia", expectRight(cursor.get[String]("name")))
    assertEquals(19, expectRight(cursor.get[Int]("age")))
    assertTrue(expectRight(cursor.get[Boolean]("active")))
    assertEquals(List("general", "princess"), expectRight(cursor.get[List[String]]("roles")))
    assertEquals(BigDecimal("1.5"), expectRight(cursor.downField("profile").get[BigDecimal]("height")))
    assertTrue(cursor.downField("profile").downField("nickname").focus.exists(_.isNull))
  }

  @Test
  def parsesEscapedStringsUnicodeAndPreciseNumbers(): Unit = {
    val input: String =
      """{"text":"line\nsnowman \u2603 quote \" slash /","decimal":12345678901234567890.12345,"exp":-1.25e2}"""

    val cursor = parsed(input).hcursor

    assertEquals("line\nsnowman ☃ quote \" slash /", expectRight(cursor.get[String]("text")))
    assertEquals(BigDecimal("12345678901234567890.12345"), expectRight(cursor.get[BigDecimal]("decimal")))
    assertEquals(BigDecimal("-125"), expectRight(cursor.get[BigDecimal]("exp")))
  }

  @Test
  def parsesTopLevelScalarJsonValuesSurroundedByWhitespace(): Unit = {
    val nullJson: Json = parsed(" null ")
    val booleanJson: Json = parsed("\ntrue\t")
    val stringJson: Json = parsed("\"standalone\"")
    val numberJson: Json = parsed("42.125")

    assertTrue(nullJson.isNull)
    assertEquals(Some(true), booleanJson.asBoolean)
    assertEquals(Some("standalone"), stringJson.asString)
    assertTrue(numberJson.asNumber.exists(_.toBigDecimal.contains(BigDecimal("42.125"))))
  }

  @Test
  def decodesStandardScalaCollectionsAndOptionalValues(): Unit = {
    val decodedMap = decode[Map[String, List[Int]]]("""{"even":[2,4,6],"odd":[1,3,5]}""")
    val decodedOptions = decode[List[Option[Boolean]]]("""[true,null,false]""")

    assertEquals(Map("even" -> List(2, 4, 6), "odd" -> List(1, 3, 5)), expectRight(decodedMap))
    assertEquals(List(Some(true), None, Some(false)), expectRight(decodedOptions))
  }

  @Test
  def decodesDomainObjectsThroughPublicDecoderApi(): Unit = {
    val input: String = """{"name":"Ada","age":36,"tags":["math","compiler"]}"""

    val decoded = decode[Person](input)

    assertEquals(Person("Ada", 36, List("math", "compiler")), expectRight(decoded))
  }

  @Test
  def reportsMalformedJsonAsParsingFailure(): Unit = {
    val result = parse("""{"name":"broken",}""")

    result match {
      case Left(failure) =>
        assertFalse(failure.message.isBlank)
        assertNotNull(failure.underlying)
      case Right(json) =>
        fail[Unit](s"Expected malformed JSON to fail, but parsed: $json")
    }
  }

  @Test
  def accumulatesMultipleElementDecodingFailures(): Unit = {
    val result = decodeAccumulating[List[Int]]("""["one", true, 3, null]""")

    result match {
      case Validated.Invalid(errors) =>
        val errorList = errors.toList
        assertTrue(errorList.length >= 3, s"Expected multiple decoding errors, but got: $errorList")
      case Validated.Valid(values) =>
        fail[Unit](s"Expected invalid integer list to fail, but decoded: $values")
    }
  }

  @Test
  def preservesJsonTransformationsAfterParsing(): Unit = {
    val root = parsed("""{"items":[{"id":1},{"id":2}],"meta":{"source":"test"}}""")

    val ids = root.hcursor.downField("items").as[List[Json]].map { items =>
      items.map(item => expectRight(item.hcursor.get[Int]("id")))
    }
    val compact: String = root.noSpaces

    assertEquals(List(1, 2), expectRight(ids))
    assertTrue(compact.contains("\"source\":\"test\""))
    assertTrue(compact.startsWith("{"))
  }

  private def parsed(input: String): Json = {
    expectRight(parse(input))
  }

  private def expectRight[A](result: Either[?, A]): A = {
    result match {
      case Right(value) => value
      case Left(error) => fail[A](s"Expected successful result, but got: $error")
    }
  }
}
