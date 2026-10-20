/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_parser_3

import io.circe.{Decoder, DecodingFailure, Encoder, Error, Json}
import io.circe.parser.decode
import io.circe.syntax.*
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/** Exercises alternative-shape JSON decoders for migrating saved delivery-route coordinates. */
@Timeout(60)
class DeliveryRouteMigrationTest {
  private final case class GeoPoint(latitude: Double, longitude: Double)
  private final case class Stop(label: String, location: GeoPoint)

  private val objectPointDecoder: Decoder[GeoPoint] =
    Decoder.forProduct2("latitude", "longitude")(GeoPoint.apply)
  private val legacyPointDecoder: Decoder[GeoPoint] =
    Decoder[(Double, Double)].map { case (latitude, longitude) => GeoPoint(latitude, longitude) }

  // Old route files stored a point as [latitude, longitude]; newer files name the axes.
  private given Decoder[GeoPoint] = objectPointDecoder.or(legacyPointDecoder).emap { point =>
    Either.cond(
      point.latitude >= -90 && point.latitude <= 90 &&
        point.longitude >= -180 && point.longitude <= 180,
      point,
      "Coordinates are outside the geographic range"
    )
  }
  private given Decoder[Stop] = Decoder.forProduct2("label", "location")(Stop.apply)
  private given Encoder[GeoPoint] =
    Encoder.forProduct2("latitude", "longitude")(point => (point.latitude, point.longitude))
  private given Encoder[Stop] = Encoder.forProduct2("label", "location")(stop => (stop.label, stop.location))

  private def migrate(savedRoute: String): Either[Error, String] =
    decode[Vector[Stop]](savedRoute).map(_.asJson.noSpaces)

  @Test
  def readsMixedGenerationsAndSavesEveryStopInTheCurrentFormat(): Unit = {
    val savedRoute: String =
      """[
        |  {"label":"Depot","location":[48.85,2.35]},
        |  {"label":"Collection","location":{"latitude":48.86,"longitude":2.36}},
        |  {"label":"Return","location":[48.85,2.35]}
        |]""".stripMargin
    val expectedStops: Vector[Stop] = Vector(
      Stop("Depot", GeoPoint(48.85, 2.35)),
      Stop("Collection", GeoPoint(48.86, 2.36)),
      Stop("Return", GeoPoint(48.85, 2.35))
    )

    assertEquals(Right(expectedStops), decode[Vector[Stop]](savedRoute))

    val migrated: String = migrate(savedRoute).fold(error => throw error, identity)
    val expectedDocument: String =
      """[
        |  {"label":"Depot","location":{"latitude":48.85,"longitude":2.35}},
        |  {"label":"Collection","location":{"latitude":48.86,"longitude":2.36}},
        |  {"label":"Return","location":{"latitude":48.85,"longitude":2.35}}
        |]""".stripMargin

    assertEquals(decode[Json](expectedDocument), decode[Json](migrated))
    assertEquals(Right(expectedStops), decode[Vector[Stop]](migrated))
    assertEquals(Right(migrated), migrate(migrated))
  }

  @Test
  def rejectsInvalidCoordinatesInEitherFormatWithoutReplacingTheSavedRoute(): Unit = {
    val publishedRoute: String =
      """[{"label":"Depot","location":{"latitude":48.85,"longitude":2.35}}]"""
    val invalidLocations: Vector[String] = Vector(
      "[91,2.35]",
      """{"latitude":48.85,"longitude":181}""",
      "[48.85]",
      "[48.85,2.35,100]",
      """{"latitude":48.85}""",
      """{"latitude":false,"longitude":2.35}"""
    )

    invalidLocations.foreach { location =>
      val candidate: String =
        s"""[{"label":"Depot","location":[48.85,2.35]},{"label":"Delivery","location":$location}]"""
      val replacement: Either[Error, String] = migrate(candidate)

      assertTrue(
        replacement.swap.exists(_.isInstanceOf[DecodingFailure]),
        "An invalid stop must reject the entire route: " + location
      )
      assertEquals(publishedRoute, replacement.getOrElse(publishedRoute))
    }
  }
}
