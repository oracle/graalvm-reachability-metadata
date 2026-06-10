/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_generic_3

import cats.data.Validated
import io.circe.Codec
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Encoder
import io.circe.Json
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveCodec
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

final case class Address(street: String, city: String, zip: String)

object Address {
  given Codec.AsObject[Address] = deriveCodec[Address]
}

final case class Person(
    name: String,
    age: Int,
    address: Address,
    aliases: List[String],
    attributes: Map[String, Int],
    spouse: Option[String]
)

object Person {
  given Encoder.AsObject[Person] = deriveEncoder[Person]

  given Decoder[Person] = deriveDecoder[Person]
}

final case class Page[A](items: List[A], total: Long, next: Option[String])

object Page {
  given [A: Encoder]: Encoder.AsObject[Page[A]] = deriveEncoder[Page[A]]

  given [A: Decoder]: Decoder[Page[A]] = deriveDecoder[Page[A]]
}

sealed trait Command

object Command {
  final case class Create(id: String, owner: Person) extends Command

  final case class Pause(id: String, reason: Option[String]) extends Command

  given Encoder[Command] = deriveEncoder[Command]

  given Decoder[Command] = deriveDecoder[Command]
}

final case class AutoPoint(x: Int, y: Int)

final case class AutoSegment(name: String, points: List[AutoPoint], closed: Boolean)

class Circe_generic_3Test {
  @Test
  def semiautomaticDerivationEncodesAndDecodesNestedProducts(): Unit = {
    val person: Person = samplePerson

    val json: Json = person.asJson
    val cursor = json.hcursor

    assertEquals(Right("Ada Lovelace"), cursor.get[String]("name"))
    assertEquals(Right(36), cursor.get[Int]("age"))
    assertEquals(Right("London"), cursor.downField("address").get[String]("city"))
    assertEquals(Right(List("analyst", "programmer")), cursor.get[List[String]]("aliases"))
    assertEquals(Right(Map("published" -> 1, "machines" -> 2)), cursor.get[Map[String, Int]]("attributes"))
    assertEquals(Right(None), cursor.get[Option[String]]("spouse"))
    assertEquals(Right(person), json.as[Person])
  }

  @Test
  def derivedObjectEncodersCanBeComposedAsJsonObjects(): Unit = {
    val encodedObject: JsonObject = summon[Encoder.AsObject[Person]].encodeObject(samplePerson)
    val augmented: Json = encodedObject
      .add("kind", Json.fromString("person"))
      .add("verified", Json.fromBoolean(true))
      .toJson

    assertEquals(Right("person"), augmented.hcursor.get[String]("kind"))
    assertEquals(Right(true), augmented.hcursor.get[Boolean]("verified"))
    assertEquals(Right(samplePerson), augmented.as[Person])
  }

  @Test
  def semiautomaticDerivationReportsFieldLevelDecodeFailures(): Unit = {
    val invalid: Json = Json.obj(
      "name" -> Json.fromString("Grace Hopper"),
      "age" -> Json.fromString("not-a-number"),
      "address" -> Json.obj(
        "street" -> Json.fromString("Arlington"),
        "city" -> Json.fromString("Arlington"),
        "zip" -> Json.fromString("22207")
      ),
      "aliases" -> Json.arr(Json.fromString("compiler")),
      "attributes" -> Json.obj("rank" -> Json.fromInt(1)),
      "spouse" -> Json.Null
    )

    invalid.as[Person] match {
      case Left(failure: DecodingFailure) =>
        assertFalse(failure.message.isBlank)
        assertTrue(failure.history.nonEmpty)
      case Left(error) =>
        fail[Unit](s"Expected a decoding failure for the age field, but got: $error")
      case Right(person) =>
        fail[Unit](s"Expected invalid JSON to fail, but decoded: $person")
    }
  }

  @Test
  def derivedDecodersAccumulateIndependentProductFailures(): Unit = {
    val invalid: Json = Json.obj(
      "name" -> Json.fromInt(1),
      "age" -> Json.fromString("old"),
      "address" -> Json.obj(
        "street" -> Json.fromBoolean(true),
        "city" -> Json.fromString("Paris"),
        "zip" -> Json.fromInt(75000)
      ),
      "aliases" -> Json.arr(Json.fromInt(1), Json.fromString("valid")),
      "attributes" -> Json.obj("score" -> Json.fromString("high")),
      "spouse" -> Json.fromBoolean(false)
    )

    summon[Decoder[Person]].decodeAccumulating(invalid.hcursor) match {
      case Validated.Invalid(errors) =>
        assertTrue(errors.toList.length >= 5, s"Expected several accumulated failures, but got: ${errors.toList}")
      case Validated.Valid(person) =>
        fail[Unit](s"Expected invalid JSON to fail, but decoded: $person")
    }
  }

  @Test
  def derivesEncodersAndDecodersForParameterizedProducts(): Unit = {
    val page: Page[Person] = Page(items = List(samplePerson), total = 1L, next = Some("cursor-2"))

    val json: Json = page.asJson

    assertEquals(Right(1L), json.hcursor.get[Long]("total"))
    assertEquals(Right(Some("cursor-2")), json.hcursor.get[Option[String]]("next"))
    assertEquals(Right("Ada Lovelace"), json.hcursor.downField("items").downArray.get[String]("name"))
    assertEquals(Right(page), json.as[Page[Person]])
  }

  @Test
  def derivesEncodersAndDecodersForSealedTraitHierarchies(): Unit = {
    val commands: List[Command] = List(
      Command.Create("create-1", samplePerson),
      Command.Pause("pause-1", Some("maintenance"))
    )

    val json: Json = commands.asJson
    val compact: String = json.noSpaces

    assertTrue(json.isArray)
    assertTrue(compact.contains("Create"), s"Expected encoded coproduct JSON to contain the Create case: $compact")
    assertTrue(compact.contains("Pause"), s"Expected encoded coproduct JSON to contain the Pause case: $compact")
    assertEquals(Right(commands), json.as[List[Command]])
  }

  @Test
  def automaticDerivationProvidesCodecsInImportScope(): Unit = {
    import io.circe.generic.auto.*

    val segment: AutoSegment = AutoSegment(
      name = "boundary",
      points = List(AutoPoint(0, 0), AutoPoint(10, 5)),
      closed = false
    )

    val json: Json = segment.asJson

    assertEquals(Right("boundary"), json.hcursor.get[String]("name"))
    assertEquals(Right(10), json.hcursor.downField("points").downN(1).get[Int]("x"))
    assertEquals(Right(false), json.hcursor.get[Boolean]("closed"))
    assertEquals(Right(segment), json.as[AutoSegment])
  }

  private def samplePerson: Person = {
    Person(
      name = "Ada Lovelace",
      age = 36,
      address = Address("St. James's Square", "London", "SW1Y"),
      aliases = List("analyst", "programmer"),
      attributes = Map("published" -> 1, "machines" -> 2),
      spouse = None
    )
  }
}
