/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_parser_3

import io.circe.{Json, ParsingFailure}
import io.circe.parser.parse
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue}
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/** Exercises JSON value folding to build schema-free search projections from event payloads. */
@Timeout(60)
class EventSearchProjectionTest {
  private final case class SearchProjection(id: String, fields: Map[String, Vector[String]])

  private def scalarFields(value: Json, path: Vector[String]): Vector[(String, String)] =
    value.fold(
      Vector.empty,
      boolean => Vector(path.mkString(".") -> boolean.toString),
      number => Vector(path.mkString(".") -> number.toString),
      text => Vector(path.mkString(".") -> text),
      elements => elements.flatMap(element => scalarFields(element, path)),
      fields => fields.toVector.flatMap { case (name, child) =>
        scalarFields(child, path :+ name)
      }
    )

  private def project(payload: String): Either[io.circe.Error, SearchProjection] =
    for {
      document <- parse(payload)
      id <- document.hcursor.get[String]("id")
      data <- document.hcursor.get[Json]("data")
    } yield SearchProjection(
      id,
      scalarFields(data, Vector.empty).groupMap(_._1)(_._2)
    )

  @Test
  def indexesUnknownNestedFieldsAndRepeatedValuesWithoutInventingNullTerms(): Unit = {
    val event: String =
      """{
        |  "id": "shipment-42",
        |  "data": {
        |    "status": "dispatched",
        |    "insured": true,
        |    "attempt": 2,
        |    "destination": {"city": "Lyon", "instructions": null},
        |    "labels": ["priority", "fragile"],
        |    "packages": [
        |      {"sku": "book-42", "weight": 1.25},
        |      {"sku": "pen-7", "weight": 0.5}
        |    ],
        |    "extensions": {"temperature": {"reading": -4, "unit": "C"}},
        |    "unused": [],
        |    "extra": {}
        |  }
        |}""".stripMargin

    val projection: SearchProjection = project(event).fold(error => throw error, identity)
    assertEquals("shipment-42", projection.id)
    assertEquals(
      Map(
        "status" -> Vector("dispatched"),
        "insured" -> Vector("true"),
        "attempt" -> Vector("2"),
        "destination.city" -> Vector("Lyon"),
        "labels" -> Vector("priority", "fragile"),
        "packages.sku" -> Vector("book-42", "pen-7"),
        "packages.weight" -> Vector("1.25", "0.5"),
        "extensions.temperature.reading" -> Vector("-4"),
        "extensions.temperature.unit" -> Vector("C")
      ),
      projection.fields
    )
    assertFalse(projection.fields.contains("destination.instructions"))
    assertFalse(projection.fields.contains("unused"))
    assertFalse(projection.fields.contains("extra"))

    // A producer can add a field without a consumer schema deployment.
    val evolvedEvent: String = event.replace(
      "\"unit\": \"C\"",
      "\"unit\": \"C\", \"calibrated\": false"
    )
    val evolved: SearchProjection = project(evolvedEvent).fold(error => throw error, identity)
    assertEquals(
      projection.fields.updated("extensions.temperature.calibrated", Vector("false")),
      evolved.fields
    )
  }

  @Test
  def rejectsAnIncompleteEventInsteadOfPublishingAPartialSearchProjection(): Unit = {
    val truncated: String = """{"id":"shipment-42","data":{"status":"dispatched"}"""
    val result: Either[io.circe.Error, SearchProjection] = project(truncated)

    assertTrue(result.swap.exists(_.isInstanceOf[ParsingFailure]))
    assertTrue(project("""{"data":{"status":"dispatched"}}""").isLeft)
    assertEquals(
      Right(SearchProjection("shipment-42", Map.empty)),
      project("""{"id":"shipment-42","data":{}}""")
    )
  }
}
