/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_effect_3

import cats.effect.IO
import cats.effect.std.Random
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import scala.concurrent.duration._

/** Exercises seeded Random shuffling for reproducible, region-balanced experiment allocation. */
class SeededExperimentAllocationTest {
  private case class Participant(id: String, region: String)
  private case class Assignment(participantId: String, region: String, cohort: String)

  private def allocate(
      participants: List[Participant],
      random: Random[IO]
  ): IO[List[Assignment]] = {
    // Canonicalize the roster so replay does not depend on database row order.
    participants.groupBy(_.region).toList.sortBy(_._1).traverse { case (region, roster) =>
      random.shuffleList(roster.sortBy(_.id)).map { shuffled =>
        shuffled.zipWithIndex.map { case (participant, index) =>
          val cohort: String = if (index % 2 == 0) "control" else "new-checkout"
          Assignment(participant.id, region, cohort)
        }
      }
    }.map(_.flatten)
  }

  @Test
  @Timeout(30)
  def experimentRosterCanBeRebuiltFromItsSeedWithoutChangingCohorts(): Unit = {
    val participants: List[Participant] = List("north", "south", "west").flatMap { region =>
      (1 to 8).map(index => Participant(s"$region-$index", region))
    }
    // An experiment stores its seed alongside its enrollment roster for audit and recovery.
    val experimentSeed: Long = 482901L
    val program: IO[(List[Assignment], List[Assignment])] = for {
      random <- Random.scalaUtilRandomSeedLong[IO](experimentSeed)
      published <- allocate(participants, random)
      replayRandom <- Random.scalaUtilRandomSeedLong[IO](experimentSeed)
      rebuilt <- allocate(participants.reverse, replayRandom)
    } yield (published, rebuilt)

    val (published, rebuilt) = program.unsafeRunTimed(20.seconds).getOrElse {
      throw new AssertionError("Experiment allocation did not finish within 20 seconds")
    }

    assertEquals(participants.map(_.id).sorted, published.map(_.participantId).sorted,
      "Every enrolled participant must be assigned exactly once")
    val expectedRegions: Map[String, String] = participants.map(p => p.id -> p.region).toMap
    assertEquals(expectedRegions, published.map(a => a.participantId -> a.region).toMap,
      "Allocation must preserve each participant's region")
    val cohortSizes: Map[(String, String), Int] =
      published.groupBy(a => (a.region, a.cohort)).map { case (key, group) => key -> group.size }
    val expectedSizes: Map[(String, String), Int] = Map(
      ("north", "control") -> 4,
      ("north", "new-checkout") -> 4,
      ("south", "control") -> 4,
      ("south", "new-checkout") -> 4,
      ("west", "control") -> 4,
      ("west", "new-checkout") -> 4
    )
    assertEquals(expectedSizes, cohortSizes, "Both cohorts must be balanced within every region")
    assertEquals(published, rebuilt,
      "Replaying the stored seed must recover the published cohorts despite reordered input")
  }
}
