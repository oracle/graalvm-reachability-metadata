/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_effect_3

import cats.effect.IO
import cats.effect.Resource
import cats.effect.unsafe.implicits.global
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import java.io.BufferedWriter
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import scala.concurrent.duration._

/** Exercises Resource lifecycle management for temporary report exports, including failed exports. */
class ResourceReportExportTest {
  private def workspace(root: Path): Resource[IO, Path] = {
    Resource.make(IO.blocking(Files.createDirectory(root.resolve("export")))) { directory =>
      IO.blocking {
        Files.deleteIfExists(directory.resolve("orders.csv"))
        Files.delete(directory)
      }
    }
  }

  private def reportWriter(directory: Path): Resource[IO, BufferedWriter] = {
    Resource.fromAutoCloseable(IO.blocking {
      Files.newBufferedWriter(directory.resolve("orders.csv"), UTF_8)
    })
  }

  private def await[A](program: IO[A]): A = {
    program.unsafeRunTimed(20.seconds).getOrElse {
      throw new AssertionError("Report export did not finish within 20 seconds")
    }
  }

  @Test
  @Timeout(30)
  def completedExportClosesWriterBeforeReadingAndRemovesWorkspace(): Unit = {
    val root: Path = Files.createTempDirectory("resource-report-success-")
    try {
      val program: IO[String] = workspace(root).use { directory =>
        reportWriter(directory).use { writer =>
          IO.blocking {
            writer.write("order,total\n")
            writer.write("A-101,1250\n")
            writer.write("A-102,750\n")
          }
        }.flatMap { _ =>
          // Reading after the inner scope proves that closing the writer flushed the report.
          IO.blocking(Files.readString(directory.resolve("orders.csv"), UTF_8))
        }
      }

      val report: String = await(program)
      assertEquals("order,total\nA-101,1250\nA-102,750\n", report)
      assertFalse(Files.exists(root.resolve("export")), "The completed export must leave no workspace")
    } finally {
      Files.delete(root)
    }
  }

  @Test
  @Timeout(30)
  def rejectedOrderPropagatesFailureAndRemovesPartialExport(): Unit = {
    val root: Path = Files.createTempDirectory("resource-report-failure-")
    val rejectedOrder: IllegalArgumentException = new IllegalArgumentException("Order A-103 has no total")
    try {
      val program: IO[Unit] = workspace(root).use { directory =>
        reportWriter(directory).use { writer =>
          IO.blocking(writer.write("order,total\nA-101,1250\n"))
            .flatMap(_ => IO.raiseError[Unit](rejectedOrder))
        }
      }

      val result: Either[Throwable, Unit] = await(program.attempt)
      assertSame(rejectedOrder, result.swap.toOption.get)
      assertFalse(Files.exists(root.resolve("export")), "A failed export must leave no partial report")
    } finally {
      Files.delete(root)
    }
  }
}
