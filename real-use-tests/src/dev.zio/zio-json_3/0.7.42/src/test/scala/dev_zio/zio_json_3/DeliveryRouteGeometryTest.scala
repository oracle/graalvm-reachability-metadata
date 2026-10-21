/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_json_3

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import zio.json.*
import zio.json.ast.Json

/** Exercises tuple codecs for importing and publishing GeoJSON delivery routes. */
class DeliveryRouteGeometryTest {
  final case class LineString(
    @jsonField("type") geometryType: String,
    coordinates: List[(Double, Double)],
    bbox: Option[(Double, Double, Double, Double)] = None
  )

  given JsonCodec[LineString] = DeriveJsonCodec.gen[LineString]

  @Test
  @Timeout(30)
  def importRouteAndPublishItsMapViewport(): Unit = {
    val incoming: String = """
      {
        "type": "LineString",
        "coordinates": [
          [-122.75, 45.5],
          [-122.5, 45.75],
          [-122.25, 45.25]
        ]
      }
      """
    val route: LineString = incoming.fromJson[LineString].fold(
      error => fail[LineString](error),
      identity
    )
    assertEquals("LineString", route.geometryType)
    assertEquals(
      List((-122.75, 45.5), (-122.5, 45.75), (-122.25, 45.25)),
      route.coordinates,
      "Coordinate pairs must preserve longitude/latitude order and route order"
    )
    assertEquals(None, route.bbox)

    val longitudes: List[Double] = route.coordinates.map(_._1)
    val latitudes: List[Double] = route.coordinates.map(_._2)
    val bounds: (Double, Double, Double, Double) = (
      longitudes.min, latitudes.min, longitudes.max, latitudes.max
    )
    assertEquals((-122.75, 45.25, -122.25, 45.75), bounds)
    val preview: LineString = route.copy(bbox = Some(bounds))
    val published: String = preview.toJson
    val expected: String = """
      {
        "type": "LineString",
        "coordinates": [[-122.75,45.5],[-122.5,45.75],[-122.25,45.25]],
        "bbox": [-122.75,45.25,-122.25,45.75]
      }
      """
    assertEquals(
      expected.fromJson[Json],
      published.fromJson[Json],
      "The map client requires positional JSON arrays, not objects with tuple member names"
    )
    assertEquals(Right(preview), published.fromJson[LineString])

    val invalidRoutes: List[String] = List(
      """{"type":"LineString","coordinates":[[-122.75]]}""",
      """{"type":"LineString","coordinates":[[-122.75,45.5,10.0]]}""",
      """{"type":"LineString","coordinates":[[-122.75,"north"]]}""",
      """{"type":"LineString","coordinates":[[-122.75,45.5]],"bbox":[-123,45,-122]}"""
    )
    invalidRoutes.foreach { document =>
      assertTrue(
        document.fromJson[LineString].isLeft,
        s"Malformed coordinate or bounding-box arrays must not be published: $document"
      )
    }
  }
}
