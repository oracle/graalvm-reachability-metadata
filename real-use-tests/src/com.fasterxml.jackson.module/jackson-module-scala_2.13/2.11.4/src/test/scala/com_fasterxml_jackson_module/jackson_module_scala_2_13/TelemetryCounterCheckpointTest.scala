/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_scala_2_13

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.module.scala.{DefaultScalaModule, ScalaObjectMapper}
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}

/** Exercises Scala BigInt data binding for cumulative telemetry counter checkpoints. */
@Timeout(60)
class TelemetryCounterCheckpointTest {
  @Test
  def computesUsageAndPersistsCountersBeyondTheLongRange(): Unit = {
    val mapper: ObjectMapper with ScalaObjectMapper = new ObjectMapper() with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)

    // Collectors report cumulative byte counters as JSON integers, not floating-point values.
    val checkpoint: Map[String, BigInt] = mapper.readValue[Map[String, BigInt]](
      """{"edge-east":18446744073709551616,"edge-west":9223372036854775808}""")
    val observed: Map[String, BigInt] = mapper.readValue[Map[String, BigInt]](
      """{"edge-east":18446744073709551741,"edge-west":9223372036854775815}""")
    assertEquals(BigInt("18446744073709551616"), checkpoint("edge-east"))
    assertEquals(BigInt("9223372036854775815"), observed("edge-west"))

    val usage: Map[String, BigInt] = observed.map { case (collector, counter) =>
      collector -> (counter - checkpoint(collector))
    }
    assertEquals(Map("edge-east" -> BigInt(125), "edge-west" -> BigInt(7)), usage)
    assertEquals(BigInt(132), usage.values.sum)
    assertEquals(BigInt("27670116110564327556"), observed.values.sum)

    // Persist the new high-water marks so a restarted aggregator cannot double-count usage.
    val published: String = mapper.writeValueAsString(observed)
    val document: JsonNode = mapper.readTree(published)
    assertTrue(document.path("edge-east").isIntegralNumber)
    assertTrue(document.path("edge-west").isIntegralNumber)
    assertEquals("18446744073709551741", document.path("edge-east").asText())
    assertEquals("9223372036854775815", document.path("edge-west").asText())

    val restored: Map[String, BigInt] = mapper.readValue[Map[String, BigInt]](published)
    assertEquals(observed, restored)
    val replayedUsage: BigInt = observed.iterator.map { case (collector, counter) =>
      counter - restored(collector)
    }.sum
    assertEquals(BigInt(0), replayedUsage)
  }
}
