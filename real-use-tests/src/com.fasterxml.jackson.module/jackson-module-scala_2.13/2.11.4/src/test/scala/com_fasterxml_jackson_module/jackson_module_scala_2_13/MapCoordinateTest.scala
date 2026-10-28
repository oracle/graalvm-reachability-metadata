/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_scala_2_13

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.module.scala.{DefaultScalaModule, ScalaObjectMapper}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

/** Exercises Scala tuple data binding for positional map coordinates. */
@Timeout(60)
class MapCoordinateTest {
  @Test
  def translatesLocalCoordinateForPublication(): Unit = {
    val mapper: ObjectMapper with ScalaObjectMapper = new ObjectMapper() with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)

    // The map service exchanges coordinates as ordered [x, y] pairs.
    val local: (Double, Double) = mapper.readValue[(Double, Double)]("[12.5,-4.25]")
    val origin: (Double, Double) = mapper.readValue[(Double, Double)]("[100.0,200.0]")
    val (localX, localY) = local
    val (originX, originY) = origin
    assertEquals(12.5, localX, 0.0)
    assertEquals(-4.25, localY, 0.0)

    val translated: (Double, Double) = (originX + localX, originY + localY)
    assertEquals((112.5, 195.75), translated)
    assertEquals((12.5, -4.25), local)

    val published: String = mapper.writeValueAsString(translated)
    val document: JsonNode = mapper.readTree(published)
    assertEquals(mapper.readTree("[112.5,195.75]"), document)

    val received: (Double, Double) = mapper.readValue[(Double, Double)](published)
    val (worldX, worldY) = received
    assertEquals(local, (worldX - originX, worldY - originY))
  }
}
