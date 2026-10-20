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

/** Exercises Scala immutable collection data binding for a warehouse stock snapshot. */
@Timeout(60)
class WarehouseStockTest {
  @Test
  def adjustsStockAndPublishesSnapshot(): Unit = {
    val mapper: ObjectMapper with ScalaObjectMapper = new ObjectMapper() with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)

    val incoming: String = """{"bolts":[12,8,5],"washers":[40,10],"nuts":[]}"""
    val stock: Map[String, Vector[Int]] = mapper.readValue[Map[String, Vector[Int]]](incoming)

    assertEquals(Map("bolts" -> Vector(12, 8, 5), "washers" -> Vector(40, 10),
      "nuts" -> Vector.empty[Int]), stock)
    assertEquals(25, stock("bolts").sum)

    // A shipment consumes three bolts from the first bin without changing the received snapshot.
    val adjusted: Map[String, Vector[Int]] = stock.updated(
      "bolts", stock("bolts").updated(0, stock("bolts").head - 3))
    assertEquals(Vector(12, 8, 5), stock("bolts"))
    assertEquals(22, adjusted("bolts").sum)

    val published: String = mapper.writeValueAsString(adjusted)
    val document: JsonNode = mapper.readTree(published)
    assertEquals(mapper.readTree("""{"bolts":[9,8,5],"washers":[40,10],"nuts":[]}"""), document)

    val restored: Map[String, Vector[Int]] = mapper.readValue[Map[String, Vector[Int]]](published)
    assertEquals(adjusted, restored)
  }
}
