/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_scala_2_13

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

/** Exercises Scala Iterator serialization for a filtered dispatch manifest export. */
@Timeout(60)
class DispatchManifestTest {
  case class Shipment(orderId: String, destination: String, packageCount: Int)

  @Test
  def exportsReadyShipmentsInDispatchOrder(): Unit = {
    val mapper: ObjectMapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)

    val shipments: Vector[Shipment] = Vector(
      Shipment("order-41", "North depot", 2),
      Shipment("order-42", "Central depot", 0),
      Shipment("order-43", "South \"express\" depot", 3))

    // Feed the filtered iterator directly to Jackson without materializing another collection.
    def exportManifest(rows: Iterator[Shipment]): String =
      mapper.writeValueAsString(rows.filter(_.packageCount > 0))

    val published: String = exportManifest(shipments.iterator)
    val manifest: JsonNode = mapper.readTree(published)
    val expected: JsonNode = mapper.readTree(
      """[
        |  {"orderId":"order-41","destination":"North depot","packageCount":2},
        |  {"orderId":"order-43","destination":"South \"express\" depot","packageCount":3}
        |]""".stripMargin)
    assertEquals(expected, manifest)

    // The carrier sees dispatch order and the total number of packages to collect.
    assertEquals("order-41", manifest.get(0).path("orderId").asText())
    assertEquals("order-43", manifest.get(1).path("orderId").asText())
    val packageTotal: Int = (0 until manifest.size()).map { index: Int =>
      manifest.get(index).path("packageCount").asInt()
    }.sum
    assertEquals(5, packageTotal)

    // A batch containing only unready orders is still a valid, empty JSON manifest.
    val emptyBatch: Iterator[Shipment] = Iterator(Shipment("order-44", "West depot", 0))
    assertEquals(mapper.readTree("[]"), mapper.readTree(exportManifest(emptyBatch)))
  }
}
