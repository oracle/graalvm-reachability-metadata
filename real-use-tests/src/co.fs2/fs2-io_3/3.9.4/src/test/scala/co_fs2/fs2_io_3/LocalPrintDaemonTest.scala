/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_fs2.fs2_io_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import fs2.Stream
import fs2.io.file.{Files, Path}
import fs2.io.net.unixsocket.{UnixSocketAddress, UnixSockets}
import fs2.text
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse}
import org.junit.jupiter.api.{Test, Timeout}

import scala.concurrent.duration.*

/** Exercises Unix-domain sockets by submitting print jobs to a local daemon and reading queue receipts. */
class LocalPrintDaemonTest {
  private case class PrintJob(id: String, document: String, copies: Int) {
    def request: String = s"$id|$document|$copies\n"
    def receipt: String = s"QUEUED:$id:$copies\n"
  }

  private def awaitEndpoint(files: Files[IO], endpoint: Path): IO[Unit] =
    files.exists(endpoint).flatMap { exists =>
      if (exists) IO.unit else IO.cede *> awaitEndpoint(files, endpoint)
    }

  @Test
  @Timeout(60)
  def submitJobsThroughLocalDaemonAndRemoveEndpointOnShutdown(): Unit = {
    val jobs: List[PrintJob] = List(
      PrintJob("job-41", "invoice-41.pdf", 2),
      PrintJob("job-42", "shipping-label-42.pdf", 1)
    )
    val files: Files[IO] = Files[IO]
    val sockets: UnixSockets[IO] = UnixSockets[IO]

    files.tempDirectory.use { directory =>
      val endpoint: Path = directory / "print.sock"
      val address: UnixSocketAddress = UnixSocketAddress(endpoint.toString)
      val daemon: IO[List[PrintJob]] = sockets.server(address).take(jobs.size.toLong)
        .evalMap { socket =>
          for {
            request <- socket.reads.through(text.utf8.decode).compile.string
            job <- IO {
              val fields: Array[String] = request.trim.split("\\|", -1)
              require(fields.length == 3, "Print requests contain an id, document, and copy count")
              PrintJob(fields(0), fields(1), fields(2).toInt)
            }
            _ <- Stream.emit(job.receipt).through(text.utf8.encode)
              .through(socket.writes).compile.drain
          } yield job
        }.compile.toList

      val submit: IO[List[String]] = awaitEndpoint(files, endpoint).timeout(10.seconds) *>
        jobs.traverse { job =>
          sockets.client(address).use { socket =>
            for {
              _ <- Stream.emit(job.request).through(text.utf8.encode)
                .through(socket.writes).compile.drain
              // Half-close delimits a complete request while leaving the receipt channel open.
              _ <- socket.endOfOutput
              receipt <- socket.reads.through(text.utf8.decode).compile.string
            } yield receipt
          }
        }

      for {
        result <- daemon.both(submit).timeout(20.seconds)
        endpointRemains <- files.exists(endpoint)
        _ <- IO {
          assertEquals(jobs, result._1, "The daemon must receive every job with its document and copy count")
          assertEquals(jobs.map(_.receipt), result._2, "Each client must receive its own queue receipt")
          assertFalse(endpointRemains, "Stopping the daemon must release its filesystem endpoint")
        }
      } yield ()
    }.timeout(30.seconds).unsafeRunSync()
  }
}
