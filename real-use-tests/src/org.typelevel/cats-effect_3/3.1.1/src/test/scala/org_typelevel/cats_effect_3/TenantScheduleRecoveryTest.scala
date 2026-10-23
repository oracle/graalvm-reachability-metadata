/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_effect_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import scala.concurrent.duration._

/** Exercises IO selective error recovery for tenant digest schedules with a default configuration. */
class TenantScheduleRecoveryTest {
  private case class Schedule(hour: Int)
  private class InvalidSchedule(tenant: String)
      extends IllegalArgumentException(s"$tenant: digest hour must be between 0 and 23")

  private def readSchedule(path: Path, tenant: String): IO[Schedule] = {
    IO.blocking(Files.readString(path, UTF_8)).flatMap { text =>
      text.trim.toIntOption match {
        case Some(hour) if hour >= 0 && hour <= 23 => IO.pure(Schedule(hour))
        case _ => IO.raiseError[Schedule](new InvalidSchedule(tenant))
      }
    }
  }

  private def loadSchedule(root: Path, tenant: String): IO[Schedule] = {
    readSchedule(root.resolve(tenant + ".hour"), tenant).handleErrorWith {
      // Only an absent override permits fallback; a malformed override is an operator error.
      case _: NoSuchFileException => readSchedule(root.resolve("default.hour"), tenant)
      case error => IO.raiseError[Schedule](error)
    }
  }

  @Test
  @Timeout(30)
  def batchRecoversMissingOverridesButReportsInvalidSchedulesWithoutLosingOtherTenants(): Unit = {
    val root: Path = Files.createTempDirectory("tenant-schedules-")
    val defaults: Path = root.resolve("default.hour")
    val custom: Path = root.resolve("north.hour")
    val invalid: Path = root.resolve("west.hour")

    try {
      Files.writeString(defaults, "8\n", UTF_8)
      Files.writeString(custom, "17\n", UTF_8)
      Files.writeString(invalid, "25\n", UTF_8)

      val program: IO[(Map[String, Either[Throwable, Schedule]], Schedule,
          Either[Throwable, Schedule])] = for {
        // Each tenant has its own error boundary, so one bad override does not abort the batch.
        outcomes <- List("north", "south", "west").traverse { tenant =>
          loadSchedule(root, tenant).attempt.map(tenant -> _)
        }
        _ <- IO.blocking(Files.delete(defaults))
        // A valid override remains usable even when the fallback is unavailable.
        independent <- loadSchedule(root, "north")
        missing <- loadSchedule(root, "south").attempt
      } yield (outcomes.toMap, independent, missing)

      val (outcomes, independent, missing) = program.unsafeRunTimed(20.seconds).getOrElse {
        throw new AssertionError("Tenant schedule loading did not finish within 20 seconds")
      }

      val summarized: Map[String, Either[String, Schedule]] = outcomes.map { case (tenant, result) =>
        tenant -> result.left.map(_.getMessage)
      }
      val expected: Map[String, Either[String, Schedule]] = Map(
        "north" -> Right(Schedule(17)),
        "south" -> Right(Schedule(8)),
        "west" -> Left("west: digest hour must be between 0 and 23")
      )
      assertEquals(expected, summarized,
        "Missing overrides must use defaults, while malformed overrides must be reported")
      assertTrue(outcomes("west").swap.toOption.exists(_.isInstanceOf[InvalidSchedule]))
      assertEquals(Schedule(17), independent, "A successful override must not evaluate the fallback")
      assertTrue(missing.swap.toOption.exists {
        case error: NoSuchFileException => error.getFile == defaults.toString
        case _ => false
      }, "Failure to read the fallback must propagate with the default file's path")
    } finally {
      Files.deleteIfExists(defaults)
      Files.deleteIfExists(custom)
      Files.deleteIfExists(invalid)
      Files.delete(root)
    }
  }
}
