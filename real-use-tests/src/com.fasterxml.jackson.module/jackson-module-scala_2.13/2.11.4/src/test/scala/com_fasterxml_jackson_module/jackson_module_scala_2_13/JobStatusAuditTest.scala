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

/** Exercises Scala Enumeration serialization for a batch-job status audit feed. */
@Timeout(60)
class JobStatusAuditTest {
  object JobStatus extends Enumeration {
    val Queued: Value = Value("queued")
    val Completed: Value = Value("completed")
    val Failed: Value = Value("failed")
  }

  @Test
  def publishesNamedJobOutcomesForAnAuditConsumer(): Unit = {
    val mapper: ObjectMapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)

    val initial: Map[String, JobStatus.Value] = Map(
      "import-41" -> JobStatus.Queued,
      "import-42" -> JobStatus.Queued,
      "import-43" -> JobStatus.Queued)
    val outcomes: Map[String, JobStatus.Value] = initial
      .updated("import-41", JobStatus.Completed)
      .updated("import-42", JobStatus.Failed)
    assertEquals(JobStatus.Queued, initial("import-41"))

    // The module's default enum format carries both the enum identity and its named value.
    val published: String = mapper.writeValueAsString(outcomes)
    val document: JsonNode = mapper.readTree(published)
    val enumIdentity: String =
      "com_fasterxml_jackson_module.jackson_module_scala_2_13.JobStatusAuditTest$JobStatus"
    val expectedNames: Map[String, String] = Map(
      "import-41" -> "completed", "import-42" -> "failed", "import-43" -> "queued")
    assertEquals(3, document.size())
    expectedNames.foreach { case (jobId, name) =>
      val entry: JsonNode = document.path(jobId)
      assertEquals(2, entry.size())
      assertEquals(enumIdentity, entry.path("enumClass").asText())
      assertEquals(name, entry.path("value").asText())
    }

    // A dashboard consumes the feed without loading Scala enum classes.
    val failedJobs: List[String] = expectedNames.keys.toList.sorted.filter { jobId: String =>
      document.path(jobId).path("value").asText() == "failed"
    }
    assertEquals(List("import-42"), failedJobs)
    val completedJobs: Int = expectedNames.keys.count { jobId: String =>
      document.path(jobId).path("value").asText() == "completed"
    }
    assertEquals(1, completedJobs)

    // Retrying the failed import publishes its new status, not an ordinal number.
    val retried: Map[String, JobStatus.Value] = outcomes.updated("import-42", JobStatus.Completed)
    val nextDocument: JsonNode = mapper.readTree(mapper.writeValueAsString(retried))
    assertEquals("completed", nextDocument.path("import-42").path("value").asText())
    assertEquals(document.path("import-41"), nextDocument.path("import-41"))
    assertEquals(document.path("import-43"), nextDocument.path("import-43"))
  }
}
