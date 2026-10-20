/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_fs2.fs2_core_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import fs2.Stream
import fs2.concurrent.Channel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

import scala.concurrent.duration.*

/** Exercises FS2 bounded channels to hand off label-print jobs and drain them during shutdown. */
class LabelPrintQueueTest {
  private case class PrintJob(parcel: String, destination: String, copies: Int)

  @Test
  @Timeout(60)
  def drainsAcceptedPrintJobsInSubmissionOrderWhenTheProducerCloses(): Unit = {
    val jobs: Vector[PrintJob] = Vector(
      PrintJob("parcel-100", "north", 2),
      PrintJob("parcel-101", "south", 1),
      PrintJob("parcel-102", "west", 3),
      PrintJob("parcel-103", "north", 1),
      PrintJob("parcel-104", "east", 2),
      PrintJob("parcel-105", "south", 1)
    )

    val program: IO[Vector[String]] = Channel.bounded[IO, PrintJob](2).flatMap { queue =>
      // A bounded hand-off prevents a large dispatch batch from flooding the print worker.
      val enqueue: Stream[IO, Unit] = Stream.emits(jobs).covary[IO].evalMap { job =>
        queue.send(job).flatMap {
          case Right(_) => IO.unit
          case Left(_) => IO.raiseError[Unit](
            new IllegalStateException(s"Print queue closed before accepting ${job.parcel}")
          )
        }
      }.onFinalize(queue.close.void)

      val labels: Stream[IO, String] = queue.stream.flatMap { job =>
        Stream.emits((1 to job.copies).map { copy =>
          s"${job.parcel}|${job.destination}|$copy/${job.copies}"
        })
      }

      // Closing the input stops new work, not the processing of already accepted jobs.
      labels.concurrently(enqueue).compile.toVector
    }

    val printed: Vector[String] = program.timeout(20.seconds).unsafeRunSync()

    assertEquals(Vector(
      "parcel-100|north|1/2",
      "parcel-100|north|2/2",
      "parcel-101|south|1/1",
      "parcel-102|west|1/3",
      "parcel-102|west|2/3",
      "parcel-102|west|3/3",
      "parcel-103|north|1/1",
      "parcel-104|east|1/2",
      "parcel-104|east|2/2",
      "parcel-105|south|1/1"
    ), printed)
    assertEquals(jobs.map(_.copies).sum, printed.size)
  }
}
