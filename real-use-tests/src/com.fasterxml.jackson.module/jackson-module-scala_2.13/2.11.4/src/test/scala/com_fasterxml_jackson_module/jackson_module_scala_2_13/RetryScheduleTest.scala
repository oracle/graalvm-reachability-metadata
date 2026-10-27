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

import scala.collection.immutable.SortedSet

/** Exercises Scala sorted-set data binding for a delivery retry schedule. */
@Timeout(60)
class RetryScheduleTest {
  @Test
  def normalizesRetryOffsetsAndPublishesRemainingAttempts(): Unit = {
    val mapper: ObjectMapper with ScalaObjectMapper = new ObjectMapper() with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)

    // Configuration lists offsets in seconds after the original delivery attempt.
    val configured: SortedSet[Int] = mapper.readValue[SortedSet[Int]](
      "[300,30,120,60,30,300]")
    assertEquals(List(30, 60, 120, 300), configured.toList)
    assertEquals(4, configured.size)
    assertEquals(30, configured.head)

    // At 90 seconds, retain future attempts and add the operator's final retry.
    val elapsedSeconds: Int = 90
    val remaining: SortedSet[Int] = configured.filter(_ > elapsedSeconds) + 600
    assertEquals(List(120, 300, 600), remaining.toList)
    assertEquals(120, remaining.head)

    val published: String = mapper.writeValueAsString(remaining)
    assertEquals(mapper.readTree("[120,300,600]"), mapper.readTree(published))

    // A restarted worker recovers the same ordered, duplicate-free schedule.
    val restored: SortedSet[Int] = mapper.readValue[SortedSet[Int]](published)
    assertEquals(remaining, restored)
    assertEquals(List(30, 210, 510), restored.toList.map(_ - elapsedSeconds))
    assertEquals(List(120, 300, 600), (restored + 300).toList)

    val completed: SortedSet[Int] = restored.filter(_ > 600)
    val completedJson: String = mapper.writeValueAsString(completed)
    assertEquals(mapper.readTree("[]"), mapper.readTree(completedJson))
    assertEquals(SortedSet.empty[Int], mapper.readValue[SortedSet[Int]](completedJson))
  }
}
