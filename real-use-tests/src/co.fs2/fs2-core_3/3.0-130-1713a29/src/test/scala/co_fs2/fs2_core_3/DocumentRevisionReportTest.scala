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

/** Exercises FS2 stream zipping to report positional line changes between document revisions. */
class DocumentRevisionReportTest {
  private case class LineChange(number: Long, previous: Option[String], revised: Option[String])

  private def lines(pages: Vector[Vector[String]]): Stream[IO, Option[String]] = Stream
    .emits(pages)
    .covary[IO]
    .flatMap(page => Stream.chunk(Chunk.vector(page)))
    .map(line => Option(line))

  // This is a positional review, not an edit-distance diff: an absent line differs from a blank line.
  private def changes(
      original: Vector[Vector[String]],
      revised: Vector[Vector[String]]
  ): IO[Vector[LineChange]] = lines(original)
    .zipAll(lines(revised))(None, None)
    .zipWithIndex
    .collect {
      case ((previous, current), index) if previous != current =>
        LineChange(index + 1L, previous, current)
    }
    .compile
    .toVector

  @Test
  @Timeout(60)
  def reportsReplacementsAndBothUnequalLengthTailsAcrossPageBoundaries(): Unit = {
    val original: Vector[Vector[String]] = Vector(
      Vector("Dispatch policy", "Region: north", ""),
      Vector("Cutoff: 16:00", "Owner: warehouse")
    )
    val extended: Vector[Vector[String]] = Vector(
      Vector("Dispatch policy"),
      Vector("Region: south", "", "Cutoff: 17:00"),
      Vector("Owner: warehouse", "Approved: operations", "")
    )

    val additions: Vector[LineChange] = changes(original, extended)
      .timeout(20.seconds).unsafeRunSync()

    assertEquals(Vector(
      LineChange(2L, Some("Region: north"), Some("Region: south")),
      LineChange(4L, Some("Cutoff: 16:00"), Some("Cutoff: 17:00")),
      LineChange(6L, None, Some("Approved: operations")),
      LineChange(7L, None, Some(""))
    ), additions)

    // A shorter replacement must still report every removed line from the original document.
    val shortened: Vector[Vector[String]] = Vector(Vector("Dispatch policy", "Region: north"))
    val removals: Vector[LineChange] = changes(original, shortened)
      .timeout(20.seconds).unsafeRunSync()

    assertEquals(Vector(
      LineChange(3L, Some(""), None),
      LineChange(4L, Some("Cutoff: 16:00"), None),
      LineChange(5L, Some("Owner: warehouse"), None)
    ), removals)
  }
}
