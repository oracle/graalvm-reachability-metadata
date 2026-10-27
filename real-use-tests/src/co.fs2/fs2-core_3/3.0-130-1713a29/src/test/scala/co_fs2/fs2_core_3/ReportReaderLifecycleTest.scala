/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_fs2.fs2_core_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.Stream
import org.junit.jupiter.api.Assertions.{assertEquals, assertThrows, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}

import java.io.{BufferedReader, IOException, StringReader}
import scala.concurrent.duration.*

/** Exercises FS2 resource bracketing to close report readers after completion, preview, or failure. */
class ReportReaderLifecycleTest {
  private case class SalesRow(store: String, totalCents: Long)

  // Ownership of the supplied reader passes to the stream, including when a caller takes a preview.
  private def salesReport(reader: BufferedReader): Stream[IO, SalesRow] = Stream
    .bracket(IO.pure(reader))(openReader => IO.blocking(openReader.close()))
    .flatMap { openReader =>
      Stream.repeatEval(IO.blocking(Option(openReader.readLine()))).unNoneTerminate
    }
    .map { line =>
      val fields: Array[String] = line.split(",", -1)
      require(fields.length == 2, s"Invalid sales row: $line")
      SalesRow(fields(0), fields(1).toLong)
    }

  private def assertClosed(reader: BufferedReader): Unit = {
    assertThrows(classOf[IOException], () => { reader.readLine(); () })
  }

  @Test
  @Timeout(60)
  def releasesReportReadersEvenWhenTheConsumerStopsOrRejectsARow(): Unit = {
    val completedReader: BufferedReader = new BufferedReader(new StringReader(
      "north,1250\nsouth,2400\nnorth,750\n"
    ))
    val completed: Vector[SalesRow] = salesReport(completedReader)
      .compile.toVector.timeout(20.seconds).unsafeRunSync()

    assertEquals(Vector(
      SalesRow("north", 1250L), SalesRow("south", 2400L), SalesRow("north", 750L)
    ), completed)
    assertEquals(Map("north" -> 2000L, "south" -> 2400L),
      completed.groupMapReduce(_.store)(_.totalCents)(_ + _))
    assertClosed(completedReader)

    // The preview must not parse the invalid third row, but must still release its input.
    val previewReader: BufferedReader = new BufferedReader(new StringReader(
      "north,1250\nsouth,2400\ninvalid-row\n"
    ))
    val preview: Vector[SalesRow] = salesReport(previewReader)
      .take(2).compile.toVector.timeout(20.seconds).unsafeRunSync()

    assertEquals(completed.take(2), preview)
    assertClosed(previewReader)

    val rejectedReader: BufferedReader = new BufferedReader(new StringReader(
      "north,1250\ninvalid-row\nsouth,2400\n"
    ))
    val rejected: Vector[Either[Throwable, SalesRow]] = salesReport(rejectedReader)
      .attempt.compile.toVector.timeout(20.seconds).unsafeRunSync()

    assertEquals(2, rejected.size)
    assertEquals(Right(SalesRow("north", 1250L)), rejected.head)
    assertTrue(rejected.last.left.exists(error =>
      error.isInstanceOf[IllegalArgumentException] &&
        error.getMessage.contains("Invalid sales row: invalid-row")))
    assertClosed(rejectedReader)
  }
}
