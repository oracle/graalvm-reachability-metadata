/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_3

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{FileAlreadyExistsException, Files, NoSuchFileException, Path}
import java.nio.file.StandardOpenOption.{CREATE_NEW, WRITE}
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import org.junit.jupiter.api.io.TempDir
import zio.{Duration, IO, Ref, Runtime, Schedule, Unsafe, ZIO}

/** Exercises ZIO Schedule retries by publishing reports under unique names without overwriting existing reports. */
class ReportPublicationRetryTest {
  private def publish(
      directory: Path,
      stem: String,
      contents: String,
      nextSuffix: Ref[Int]
  ): IO[Throwable, Path] = {
    val attempt: IO[Throwable, Path] = for {
      suffix <- nextSuffix.getAndUpdate(_ + 1)
      filename = if (suffix == 0) s"$stem.csv" else s"$stem-$suffix.csv"
      path <- ZIO.attemptBlocking(
        Files.writeString(directory.resolve(filename), contents, UTF_8, CREATE_NEW, WRITE)
      )
    } yield path

    // Only name collisions are recoverable; retry immediately, at most twice.
    attempt.retry(
      Schedule.recurs(2) && Schedule.recurWhile[Throwable](_.isInstanceOf[FileAlreadyExistsException])
    )
  }

  @Test
  @Timeout(60)
  def retriesNameCollisionsButStopsAtTheBudgetOrAnUnrecoverableFailure(@TempDir directory: Path): Unit = {
    val previous: String = "invoice_id,total_cents\nINV-OLD,1200\n"
    val current: String = "invoice_id,total_cents\nINV-NEW,4500\n"
    Files.writeString(directory.resolve("invoices.csv"), previous, UTF_8)
    Files.writeString(directory.resolve("invoices-1.csv"), previous, UTF_8)

    val program = for {
      firstSuffix <- Ref.make(0)
      published <- publish(directory, "invoices", current, firstSuffix)
      successfulAttempts <- firstSuffix.get
      // The first publication filled the last name available within the retry budget.
      secondSuffix <- Ref.make(0)
      exhausted <- publish(directory, "invoices", "must not replace reports", secondSuffix).either
      exhaustedAttempts <- secondSuffix.get
      missingSuffix <- Ref.make(0)
      missing <- publish(directory.resolve("missing"), "invoices", current, missingSuffix).either
      failedAttempts <- missingSuffix.get
    } yield (published, successfulAttempts, exhausted, exhaustedAttempts, missing, failedAttempts)

    val (published, successfulAttempts, exhausted, exhaustedAttempts, missing, failedAttempts) =
      Unsafe.unsafe { implicit unsafe =>
        Runtime.default.unsafe.run(
          program.timeoutFail(new IllegalStateException("Report publication timed out"))(Duration.fromSeconds(30))
        ).getOrThrowFiberFailure()
      }

    assertEquals(directory.resolve("invoices-2.csv"), published)
    assertEquals(3, successfulAttempts)
    assertEquals(current, Files.readString(published, UTF_8))
    assertEquals(previous, Files.readString(directory.resolve("invoices.csv"), UTF_8))
    assertEquals(previous, Files.readString(directory.resolve("invoices-1.csv"), UTF_8))
    assertTrue(exhausted.left.exists(_.isInstanceOf[FileAlreadyExistsException]))
    assertEquals(3, exhaustedAttempts)
    assertFalse(Files.exists(directory.resolve("invoices-3.csv")))
    assertTrue(missing.left.exists(_.isInstanceOf[NoSuchFileException]))
    assertEquals(1, failedAttempts)
    assertFalse(Files.exists(directory.resolve("missing")))
  }
}
