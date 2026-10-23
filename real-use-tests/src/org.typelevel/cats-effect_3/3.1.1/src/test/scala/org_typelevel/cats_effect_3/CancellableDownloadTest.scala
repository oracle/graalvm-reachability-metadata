/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_effect_3

import cats.effect.Deferred
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import scala.concurrent.duration._

/** Exercises IO.race cancellation for downloads that publish a file only after receiving all data. */
class CancellableDownloadTest {
  private case class DownloadSnapshot(
      result: Option[Path],
      published: Option[String],
      partialExists: Boolean
  )

  private def partialFile(destination: Path): Path = {
    destination.resolveSibling(destination.getFileName.toString + ".part")
  }

  private def receive(
      destination: Path,
      remainingData: IO[String],
      firstChunkWritten: IO[Unit]
  ): IO[Path] = {
    val partial: Path = partialFile(destination)
    // Only receiving more data is cancelable; file creation and publication must finish atomically
    // with respect to cancellation so cleanup never races an outstanding filesystem operation.
    IO.uncancelable { poll =>
      for {
        _ <- IO.blocking(Files.writeString(partial, "item,quantity\n", UTF_8))
        remaining <- poll(firstChunkWritten *> remainingData)
        _ <- IO.blocking(Files.writeString(partial, remaining, UTF_8, APPEND))
        published <- IO.blocking(Files.move(partial, destination))
      } yield published
    }.onCancel(IO.blocking(Files.deleteIfExists(partial)).void)
  }

  private def downloadOrCancel(
      destination: Path,
      remainingData: IO[String],
      firstChunkWritten: IO[Unit],
      cancellation: IO[Unit]
  ): IO[Option[Path]] = {
    IO.race(receive(destination, remainingData, firstChunkWritten), cancellation).map {
      case Left(published) => Some(published)
      case Right(_) => None
    }
  }

  private def attempt(destination: Path, cancelRequested: Boolean): IO[DownloadSnapshot] = {
    for {
      started <- Deferred[IO, Unit]
      remaining <- Deferred[IO, String]
      cancellation <- Deferred[IO, Unit]
      // The driver waits for a real partial file before releasing data or requesting cancellation.
      transfer <- IO.both(
        downloadOrCancel(destination, remaining.get, started.complete(()).void, cancellation.get),
        started.get *> (if (cancelRequested) cancellation.complete(()).void
          else remaining.complete("coffee,12\ntea,8\n").void)
      )
      snapshot <- IO.blocking {
        val published: Option[String] =
          if (Files.exists(destination)) Some(Files.readString(destination, UTF_8)) else None
        DownloadSnapshot(transfer._1, published, Files.exists(partialFile(destination)))
      }
    } yield snapshot
  }

  @Test
  @Timeout(30)
  def cancelledDownloadCleansUpBeforeReturningAndCanBeRetriedSuccessfully(): Unit = {
    val directory: Path = Files.createTempDirectory("cancellable-download-")
    val destination: Path = directory.resolve("inventory.csv")
    try {
      val program: IO[(DownloadSnapshot, DownloadSnapshot)] = for {
        cancelled <- attempt(destination, cancelRequested = true)
        retried <- attempt(destination, cancelRequested = false)
      } yield (cancelled, retried)

      val (cancelled, retried) = program.unsafeRunTimed(20.seconds).getOrElse {
        throw new AssertionError("Download cancellation and retry did not finish within 20 seconds")
      }

      assertEquals(None, cancelled.result, "A cancelled download must not report a published file")
      assertEquals(None, cancelled.published, "Partial data must never appear at the final path")
      assertFalse(cancelled.partialExists, "The race must finish cancellation cleanup before returning")
      assertEquals(Some(destination), retried.result)
      assertEquals(Some("item,quantity\ncoffee,12\ntea,8\n"), retried.published,
        "Retry must publish exactly one complete download, without data from the cancelled attempt")
      assertFalse(retried.partialExists, "Successful publication must consume the staging file")
    } finally {
      Files.deleteIfExists(partialFile(destination))
      Files.deleteIfExists(destination)
      Files.delete(directory)
    }
  }
}
