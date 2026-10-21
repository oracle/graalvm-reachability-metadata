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

/** Exercises FS2 sliding windows to detect sustained cold-chain temperature excursions. */
class ColdChainTemperatureTest {
  private case class TemperatureReport(sampleNumber: Long, meanTenthsC: Int, alert: Boolean)

  private def monitor(samples: Stream[IO, Int]): Stream[IO, TemperatureReport] = {
    val windowSize: Int = 3
    val limitTenthsC: Int = 70
    samples
      .sliding(windowSize)
      // A partial warm-up window is not enough evidence to raise an excursion alert.
      .filter(_.size == windowSize)
      .zipWithIndex
      .map { case (window, index) =>
        val total: Int = window.toVector.sum
        TemperatureReport(
          index + windowSize.toLong,
          total / windowSize,
          total >= limitTenthsC * windowSize
        )
      }
  }

  @Test
  @Timeout(60)
  def detectsAnExcursionAndRecoveryAcrossSensorBatchBoundaries(): Unit = {
    // Transport batches do not align with the overlapping three-sample monitoring windows.
    val batches: Vector[Vector[Int]] = Vector(
      Vector(30, 40),
      Vector(80),
      Vector(100, 90, 20, 10)
    )
    val samples: Stream[IO, Int] = Stream
      .emits(batches)
      .covary[IO]
      .flatMap(batch => Stream.chunk(Chunk.vector(batch)))

    val reports: Vector[TemperatureReport] = monitor(samples)
      .compile.toVector.timeout(20.seconds).unsafeRunSync()

    assertEquals(Vector(
      TemperatureReport(3L, 50, false),
      TemperatureReport(4L, 73, true),
      TemperatureReport(5L, 90, true),
      TemperatureReport(6L, 70, true),
      TemperatureReport(7L, 40, false)
    ), reports)
    assertEquals(Vector(4L, 5L, 6L), reports.filter(_.alert).map(_.sampleNumber))

    // Even very warm readings must not produce a full-window report before warm-up completes.
    val warmingUp: Vector[TemperatureReport] = monitor(Stream.emits(Vector(100, 110)).covary[IO])
      .compile.toVector.timeout(20.seconds).unsafeRunSync()
    assertEquals(Vector.empty[TemperatureReport], warmingUp)
  }
}
