/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_scala_2_13

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.{DefaultScalaModule, ScalaObjectMapper}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

import scala.collection.immutable.SortedMap

/** Exercises Scala sorted-map data binding for a versioned database migration plan. */
@Timeout(60)
class MigrationPlanTest {
  @Test
  def resumesMigrationsInNumericVersionOrder(): Unit = {
    val mapper: ObjectMapper with ScalaObjectMapper = new ObjectMapper() with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)

    // JSON object keys are strings, but migration versions must sort numerically.
    val incoming: String =
      """{"10":"add-audit-index","2":"add-customer-email","1":"create-customers","3":"backfill-email"}"""
    val plan: SortedMap[Int, String] = mapper.readValue[SortedMap[Int, String]](incoming)
    assertEquals(List(1, 2, 3, 10), plan.keys.toList)
    assertEquals("create-customers", plan(1))

    // A persisted checkpoint means versions 1 and 2 have already completed.
    val checkpoint: Int = 2
    val pending: SortedMap[Int, String] = plan.rangeFrom(checkpoint + 1)
      .updated(11, "record-schema-version")
    assertEquals(List(3, 10, 11), pending.keys.toList)
    assertEquals(List("backfill-email", "add-audit-index", "record-schema-version"),
      pending.values.toList)
    assertEquals(List(1, 2, 3, 10), plan.keys.toList)

    val published: String = mapper.writeValueAsString(pending)
    assertEquals(mapper.readTree(
      """{"3":"backfill-email","10":"add-audit-index","11":"record-schema-version"}"""),
      mapper.readTree(published))

    // A restarted runner recovers numeric keys and chooses the next migration.
    val restored: SortedMap[Int, String] = mapper.readValue[SortedMap[Int, String]](published)
    assertEquals(pending, restored)
    assertEquals(3, restored.firstKey)
    assertEquals("backfill-email", restored(restored.firstKey))
    val afterCompletion: SortedMap[Int, String] = restored.removed(restored.firstKey)
    assertEquals(List(10, 11), afterCompletion.keys.toList)
    assertEquals("add-audit-index", afterCompletion(afterCompletion.firstKey))

    val finished: SortedMap[Int, String] = restored.rangeFrom(12)
    val finishedJson: String = mapper.writeValueAsString(finished)
    assertEquals(mapper.readTree("{}"), mapper.readTree(finishedJson))
    assertEquals(SortedMap.empty[Int, String],
      mapper.readValue[SortedMap[Int, String]](finishedJson))
  }
}
