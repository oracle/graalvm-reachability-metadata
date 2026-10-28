/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_fs2.fs2_core_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.{Chunk, Stream}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

import scala.concurrent.duration.*

/** Exercises FS2 concurrent stream merging to consolidate independent depot dispatch feeds. */
class DepotDispatchMergeTest {
  private case class Dispatch(depot: String, sequence: Int, parcel: String, units: Int)
  private case class DispatchSummary(
      parcelsByDepot: Map[String, Vector[String]],
      unitsByDepot: Map[String, Int],
      processed: Int
  )

  private def feed(pages: Vector[Vector[Dispatch]]): Stream[IO, Dispatch] = Stream
    .emits(pages)
    .covary[IO]
    .flatMap(page => Stream.chunk(Chunk.vector(page)))

  @Test
  @Timeout(60)
  def retainsEachDepotsOrderAndDrainsTheLongerFeedAfterTheOtherCompletes(): Unit = {
    val north: Vector[Vector[Dispatch]] = Vector(
      Vector(Dispatch("north", 1, "N-100", 4)),
      Vector(Dispatch("north", 2, "N-101", 2))
    )
    val south: Vector[Vector[Dispatch]] = Vector(
      Vector(Dispatch("south", 1, "S-200", 3), Dispatch("south", 2, "S-201", 5)),
      Vector(Dispatch("south", 3, "S-202", 1)),
      Vector(Dispatch("south", 4, "S-203", 2), Dispatch("south", 5, "S-204", 6))
    )

    val received: Vector[Dispatch] = feed(north)
      .merge(feed(south))
      .compile
      .toVector
      .timeout(20.seconds)
      .unsafeRunSync()

    // Depot feeds are individually ordered; arrival order between depots is deliberately unspecified.
    val summary: DispatchSummary = received.foldLeft(DispatchSummary(
      Map.empty[String, Vector[String]], Map.empty[String, Int], 0
    )) { (current, dispatch) =>
      val parcels: Vector[String] = current.parcelsByDepot.getOrElse(dispatch.depot, Vector.empty)
      DispatchSummary(
        current.parcelsByDepot.updated(dispatch.depot, parcels :+ dispatch.parcel),
        current.unitsByDepot.updated(
          dispatch.depot, current.unitsByDepot.getOrElse(dispatch.depot, 0) + dispatch.units
        ),
        current.processed + 1
      )
    }

    assertEquals(DispatchSummary(
      Map("north" -> Vector("N-100", "N-101"),
        "south" -> Vector("S-200", "S-201", "S-202", "S-203", "S-204")),
      Map("north" -> 6, "south" -> 17),
      7
    ), summary)
    assertEquals(Vector(1, 2), received.filter(_.depot == "north").map(_.sequence))
    assertEquals(Vector(1, 2, 3, 4, 5), received.filter(_.depot == "south").map(_.sequence))
    assertEquals((north.flatten ++ south.flatten).toSet, received.toSet)
  }
}
