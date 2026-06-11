/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_literal_3

import io.circe.Encoder
import io.circe.Json
import io.circe.KeyEncoder
import io.circe.jawn.parse
import io.circe.literal.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import scala.compiletime.testing.Error
import scala.compiletime.testing.typeCheckErrors

class Circe_literal_3Test {
  @Test
  def buildsNestedJsonLiteralsAtCompileTime(): Unit = {
    val literal: Json = json"""
      {
        "name": "circe-literal",
        "versions": [0, 14, 15],
        "flags": { "literal": true, "native": null },
        "escaped": "line\nquote\"slash\\",
        "threshold": -1.25e2
      }
    """

    val expected: Json = parseJson("""
      {
        "name": "circe-literal",
        "versions": [0, 14, 15],
        "flags": { "literal": true, "native": null },
        "escaped": "line\nquote\"slash\\",
        "threshold": -1.25e2
      }
    """)

    assertEquals(expected, literal)
    assertEquals(Right("circe-literal"), literal.hcursor.downField("name").as[String])
    assertEquals(Right(List(0, 14, 15)), literal.hcursor.downField("versions").as[List[Int]])
    assertEquals(Right(true), literal.hcursor.downField("flags").downField("literal").as[Boolean])
  }

  @Test
  def createsTopLevelPrimitiveLiterals(): Unit = {
    assertEquals(Json.Null, json"null")
    assertEquals(Json.True, json"true")
    assertEquals(Json.False, json"false")
    assertEquals(Json.fromString("literal string"), json""""literal string"""")
    assertEquals(Json.fromInt(12345), json"12345")
    assertEquals(Json.fromBigDecimal(BigDecimal("0.0001")), json"0.0001")
  }

  @Test
  def interpolatesStandardScalaValuesInJsonValuePositions(): Unit = {
    val count: Int = 3
    val enabled: Boolean = true
    val tags: List[String] = List("scala", "json")
    val present: Option[String] = Some("included")
    val absent: Option[Int] = None

    val literal: Json = json"""
      {
        "count": $count,
        "enabled": $enabled,
        "tags": $tags,
        "present": $present,
        "absent": $absent,
        "static": [1.0, "abc"]
      }
    """

    val expected: Json = parseJson("""
      {
        "count": 3,
        "enabled": true,
        "tags": ["scala", "json"],
        "present": "included",
        "absent": null,
        "static": [1.0, "abc"]
      }
    """)

    assertEquals(expected, literal)
  }

  @Test
  def interpolatesTopLevelScalaValues(): Unit = {
    val text: String = "quotes \" and unicode snowman ☃"
    val numbers: List[Int] = List(1, 2, 3)
    val jsonObject: Json = Json.obj("ok" -> Json.True, "answer" -> Json.fromInt(42))

    assertEquals(Json.fromString(text), json"$text")
    assertEquals(Json.arr(Json.fromInt(1), Json.fromInt(2), Json.fromInt(3)), json"$numbers")
    assertEquals(jsonObject, json"$jsonObject")
  }

  @Test
  def interpolatesStandardScalaValuesInJsonKeyPositions(): Unit = {
    val stringKey: String = "space key \"quoted\""
    val intKey: Int = 42
    val objectValue: Json = Json.obj("ok" -> Json.True)

    val literal: Json = json"""{ $stringKey: "text", $intKey: $objectValue }"""
    val expected: Json = Json.obj(
      stringKey -> Json.fromString("text"),
      intKey.toString -> objectValue
    )

    assertEquals(expected, literal)
  }

  @Test
  def interpolatesInlineExpressionsInJsonValueAndKeyPositions(): Unit = {
    val literal: Json = json"""{ ${40 + 2}: ${List("forty", "two").mkString("-")} }"""
    val expected: Json = Json.obj("42" -> Json.fromString("forty-two"))

    assertEquals(expected, literal)
  }

  @Test
  def usesCustomEncodersForInterpolatedValuesAndKeys(): Unit = {
    final case class Pet(name: String, age: Int)

    given Encoder[Pet] = Encoder.instance { pet =>
      Json.obj(
        "name" -> Json.fromString(pet.name),
        "age" -> Json.fromInt(pet.age)
      )
    }
    given KeyEncoder[Pet] with {
      override def apply(pet: Pet): String = s"${pet.name}:${pet.age}"
    }

    val pet: Pet = Pet("Rover", 5)
    val encodedPet: Json = Json.obj(
      "name" -> Json.fromString("Rover"),
      "age" -> Json.fromInt(5)
    )

    val literal: Json = json"""{ $pet: $pet, "pets": [$pet] }"""
    val expected: Json = Json.obj(
      "Rover:5" -> encodedPet,
      "pets" -> Json.arr(encodedPet)
    )

    assertEquals(expected, literal)
  }

  @Test
  def rejectsInvalidJsonLiteralsAtCompileTime(): Unit = {
    val errors: List[Error] = typeCheckErrors("""
      import io.circe.literal.*
      val invalid = json"{ \"missingColon\" true }"
    """)

    assertFalse(errors.isEmpty)
    assertTrue(errors.exists(_.message.nonEmpty))
  }

  private def parseJson(input: String): Json = {
    parse(input) match {
      case Right(json) => json
      case Left(failure) => throw new AssertionError(s"Expected JSON fixture to parse: ${failure.message}")
    }
  }
}
