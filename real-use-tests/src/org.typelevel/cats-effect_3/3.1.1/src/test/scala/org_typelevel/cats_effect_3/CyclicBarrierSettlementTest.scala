/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_effect_3

import cats.effect.IO
import cats.effect.std.CyclicBarrier
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import scala.concurrent.duration._

/** Exercises CyclicBarrier phase coordination for parallel regional settlement reports. */
class CyclicBarrierSettlementTest {
  private case class Region(name: String, dailyTotals: List[Int])
  private case class Report(day: Int, region: String, regionalCents: Int, companyCents: Int)

  private def settle(
      region: Region,
      regions: List[Region],
      directory: Path,
      barrier: CyclicBarrier[IO]
  ): IO[List[Report]] = {
    region.dailyTotals.zipWithIndex.traverse { case (regionalCents, day) =>
      for {
        _ <- IO.blocking {
          Files.writeString(directory.resolve(region.name + ".total"), regionalCents.toString, UTF_8)
        }
        // Every region must publish today's total before any report reads the company total.
        _ <- barrier.await
        companyCents <- IO.blocking {
          regions.map { current =>
            Files.readString(directory.resolve(current.name + ".total"), UTF_8).toInt
          }.sum
        }
        report = Report(day + 1, region.name, regionalCents, companyCents)
        // Reuse the barrier: nobody may overwrite a total while another report still reads it.
        _ <- barrier.await
      } yield report
    }
  }

  @Test
  @Timeout(30)
  def regionalReportsUseOneCompleteDailySnapshotAcrossRepeatedSettlementRounds(): Unit = {
    val regions: List[Region] = List(
      Region("north", List(1200, 800, 2200)),
      Region("south", List(750, 1400, 600)),
      Region("west", List(2050, 300, 900))
    )
    val directory: Path = Files.createTempDirectory("barrier-settlement-")

    try {
      val program: IO[List[Report]] = for {
        barrier <- CyclicBarrier[IO](regions.size)
        reports <- regions.parTraverse(region => settle(region, regions, directory, barrier))
      } yield reports.flatten

      val reports: List[Report] = program.unsafeRunTimed(20.seconds).getOrElse {
        throw new AssertionError("Regional settlement did not finish within 20 seconds")
      }

      val expected: List[Report] = List(
        Report(1, "north", 1200, 4000),
        Report(2, "north", 800, 2500),
        Report(3, "north", 2200, 3700),
        Report(1, "south", 750, 4000),
        Report(2, "south", 1400, 2500),
        Report(3, "south", 600, 3700),
        Report(1, "west", 2050, 4000),
        Report(2, "west", 300, 2500),
        Report(3, "west", 900, 3700)
      )
      assertEquals(expected, reports,
        "Each region's report must use all regions' totals from the same settlement day")
      regions.foreach { region =>
        assertEquals(region.dailyTotals.last.toString,
          Files.readString(directory.resolve(region.name + ".total"), UTF_8),
          "The published regional total must belong to the final settlement day")
      }
    } finally {
      regions.foreach(region => Files.deleteIfExists(directory.resolve(region.name + ".total")))
      Files.delete(directory)
    }
  }
}
