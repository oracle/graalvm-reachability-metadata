/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_effect_3

import cats.effect.IO
import cats.effect.Resource
import cats.effect.std.Hotswap
import cats.effect.unsafe.implicits.global
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import java.io.BufferedWriter
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import scala.concurrent.duration._

/** Exercises Hotswap resource replacement to rotate an application's buffered audit log. */
class HotswapAuditLogRotationTest {
  private def writer(path: Path): Resource[IO, BufferedWriter] = {
    Resource.fromAutoCloseable(IO.blocking(Files.newBufferedWriter(path, UTF_8)))
  }

  private def append(output: BufferedWriter, event: String): IO[Unit] = {
    IO.blocking(output.write(event + "\n"))
  }

  @Test
  @Timeout(30)
  def rotationFlushesOldLogAndFailedReplacementKeepsCurrentLogUsable(): Unit = {
    val directory: Path = Files.createTempDirectory("hotswap-audit-")
    val firstLog: Path = directory.resolve("audit-001.log")
    val secondLog: Path = directory.resolve("audit-002.log")
    val invalidLog: Path = directory.resolve("unconfigured").resolve("audit.log")

    try {
      val program: IO[(String, Option[Throwable], String)] = for {
        rotationResult <- Hotswap(writer(firstLog)).use { case (rotation, first) =>
          for {
            _ <- append(first, "ORDER-101 accepted")
            _ <- append(first, "ORDER-101 paid")
            second <- rotation.swap(writer(secondLog))
            // The retired writer must be closed and flushed before rotation returns.
            archived <- IO.blocking(Files.readString(firstLog, UTF_8))
            _ <- append(second, "ORDER-102 accepted")
            // A bad destination is a configuration error, not a reason to lose the active log.
            failedRotation <- rotation.swap(writer(invalidLog)).attempt
            _ <- append(second, "ORDER-102 paid")
          } yield (archived, failedRotation.swap.toOption)
        }
        // Service shutdown releases the most recently installed writer as well.
        current <- IO.blocking(Files.readString(secondLog, UTF_8))
      } yield (rotationResult._1, rotationResult._2, current)

      val (archived, failure, current) = program.unsafeRunTimed(20.seconds).getOrElse {
        throw new AssertionError("Audit log rotation did not finish within 20 seconds")
      }

      assertEquals("ORDER-101 accepted\nORDER-101 paid\n", archived,
        "Rotation must flush all earlier events into the archived log")
      assertTrue(failure.exists(_.isInstanceOf[NoSuchFileException]),
        "An unavailable destination must report its acquisition failure")
      assertEquals("ORDER-102 accepted\nORDER-102 paid\n", current,
        "A failed rotation must leave the active log usable until service shutdown")
      assertEquals(archived, Files.readString(firstLog, UTF_8),
        "Events after rotation must not be appended to the retired log")
    } finally {
      Files.deleteIfExists(firstLog)
      Files.deleteIfExists(secondLog)
      Files.delete(directory)
    }
  }
}
