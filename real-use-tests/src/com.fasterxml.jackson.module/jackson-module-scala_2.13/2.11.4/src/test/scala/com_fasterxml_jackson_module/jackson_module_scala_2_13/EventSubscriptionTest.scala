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

/** Exercises Scala Symbol data binding for event-topic subscriptions. */
@Timeout(60)
class EventSubscriptionTest {
  @Test
  def validatesTopicNamesAndPublishesSubscriptionAcknowledgment(): Unit = {
    val mapper: ObjectMapper with ScalaObjectMapper = new ObjectMapper() with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)

    // The application uses symbolic topic identifiers internally and strings on the wire.
    val requested: List[Symbol] = mapper.readValue[List[Symbol]](
      """["order-created","stock-low","order-created","unknown-topic"]""")
    assertEquals(List(Symbol("order-created"), Symbol("stock-low"),
      Symbol("order-created"), Symbol("unknown-topic")), requested)

    val available: Set[Symbol] = Set(Symbol("order-created"), Symbol("stock-low"))
    val (accepted, rejected) = requested.distinct.partition(available.contains)
    assertEquals(List(Symbol("order-created"), Symbol("stock-low")), accepted)
    assertEquals(List(Symbol("unknown-topic")), rejected)

    // Dispatch decisions use the decoded symbols, not JSON strings or tree nodes.
    val handlers: Map[Symbol, String] = Map(
      Symbol("order-created") -> "fulfillment", Symbol("stock-low") -> "replenishment")
    val destinations: List[String] = accepted.map(handlers)
    assertEquals(List("fulfillment", "replenishment"), destinations)

    val acknowledgment: Map[String, List[Symbol]] = Map(
      "subscribed" -> accepted, "rejected" -> rejected)
    val published: String = mapper.writeValueAsString(acknowledgment)
    val document: JsonNode = mapper.readTree(published)
    assertEquals(mapper.readTree(
      """{
        |  "subscribed": ["order-created", "stock-low"],
        |  "rejected": ["unknown-topic"]
        |}""".stripMargin), document)

    val received: Map[String, List[Symbol]] =
      mapper.readValue[Map[String, List[Symbol]]](published)
    assertEquals(acknowledgment, received)
    assertEquals(destinations, received("subscribed").map(handlers))
  }
}
