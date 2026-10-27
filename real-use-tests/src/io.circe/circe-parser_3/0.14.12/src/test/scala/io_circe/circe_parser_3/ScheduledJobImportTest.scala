/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_circe.circe_parser_3

import io.circe.Decoder
import io.circe.parser.decode
import java.time.{Duration, Instant, OffsetDateTime}
import java.util.UUID
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/** Exercises UUID map-key and Java-time decoding for importing scheduled jobs from JSON. */
@Timeout(60)
class ScheduledJobImportTest {
  private final case class ScheduledJob(
      destination: String,
      scheduledAt: OffsetDateTime,
      retryDelay: Duration
  )

  private given Decoder[ScheduledJob] =
    Decoder.forProduct3("destination", "scheduledAt", "retryDelay")(ScheduledJob.apply)

  @Test
  def importsJobsWithStableIdentifiersAndComputesRetryInstantsAcrossOffsets(): Unit = {
    val jobExport: String =
      """{
        |  "f47ac10b-58cc-4372-a567-0e02b2c3d479": {
        |    "destination": "billing",
        |    "scheduledAt": "2025-04-10T10:15:30.123456789+02:00",
        |    "retryDelay": "PT1M30.5S"
        |  },
        |  "550e8400-e29b-41d4-a716-446655440000": {
        |    "destination": "warehouse",
        |    "scheduledAt": "2025-04-10T04:15:30.123456789-04:00",
        |    "retryDelay": "PT5M"
        |  }
        |}""".stripMargin

    val jobs: Map[UUID, ScheduledJob] =
      decode[Map[UUID, ScheduledJob]](jobExport).fold(error => throw error, identity)
    val billingId: UUID = UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d479")
    val warehouseId: UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")

    assertEquals(Set(billingId, warehouseId), jobs.keySet)
    assertEquals(
      ScheduledJob(
        "billing",
        OffsetDateTime.parse("2025-04-10T10:15:30.123456789+02:00"),
        Duration.ofMillis(90500)
      ),
      jobs(billingId)
    )
    assertEquals("warehouse", jobs(warehouseId).destination)
    assertEquals(Duration.ofMinutes(5), jobs(warehouseId).retryDelay)
    assertEquals(jobs(billingId).scheduledAt.toInstant, jobs(warehouseId).scheduledAt.toInstant)
    assertEquals(7200, jobs(billingId).scheduledAt.getOffset.getTotalSeconds)
    assertEquals(-14400, jobs(warehouseId).scheduledAt.getOffset.getTotalSeconds)

    val retrySchedule: Map[UUID, Instant] = jobs.map { case (id, job) =>
      id -> job.scheduledAt.toInstant.plus(job.retryDelay)
    }
    assertEquals(Instant.parse("2025-04-10T08:17:00.623456789Z"), retrySchedule(billingId))
    assertEquals(Instant.parse("2025-04-10T08:20:30.123456789Z"), retrySchedule(warehouseId))
  }

  @Test
  def rejectsInvalidJobIdentifiersDatesAndDurationsBeforeScheduling(): Unit = {
    val validJob: String =
      """{"destination":"billing","scheduledAt":"2025-04-10T10:15:30+02:00","retryDelay":"PT5M"}"""
    val validId: String = "f47ac10b-58cc-4372-a567-0e02b2c3d479"
    val invalidExports: List[String] = List(
      s"""{"not-a-job-id":$validJob}""",
      s"""{"$validId":${validJob.replace("2025-04-10", "2025-02-30")}}""",
      s"""{"$validId":${validJob.replace("PT5M", "five minutes")}}"""
    )

    invalidExports.foreach { document =>
      assertTrue(
        decode[Map[UUID, ScheduledJob]](document).isLeft,
        "An invalid job export must be rejected before any job is scheduled"
      )
    }
  }
}
