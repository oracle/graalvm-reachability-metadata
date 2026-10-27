/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_3

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.zip.{ZipEntry, ZipFile, ZipOutputStream}
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import org.junit.jupiter.api.io.TempDir
import zio.{Duration, IO, Ref, Runtime, Scope, Unsafe, ZIO, ZPool}

/** Exercises ZIO ZPool resource pooling by serving product descriptions from a reusable catalog archive. */
class PooledCatalogArchiveTest {
  private def createCatalog(path: Path): Unit = {
    val output: ZipOutputStream = new ZipOutputStream(Files.newOutputStream(path))
    try {
      List("coffee.txt" -> "Roasted coffee, 250g", "tea.txt" -> "Green tea, 100g").foreach {
        case (name, description) =>
          output.putNextEntry(new ZipEntry(name))
          output.write(description.getBytes(UTF_8))
          output.closeEntry()
      }
    } finally {
      output.close()
    }
  }

  private def openCatalog(
      path: Path,
      opened: Ref[Int],
      closed: Ref[Int]
  ): ZIO[Scope, Throwable, ZipFile] =
    ZIO.acquireRelease(ZIO.attemptBlocking(new ZipFile(path.toFile))) { archive =>
      ZIO.attemptBlocking(archive.close()).orDie *> closed.update(_ + 1)
    }.tap(_ => opened.update(_ + 1))

  private def description(pool: ZPool[Throwable, ZipFile], name: String): IO[Throwable, String] =
    ZIO.scoped {
      for {
        archive <- pool.get
        entry <- ZIO.attemptBlocking(Option(archive.getEntry(name))).flatMap(
          entry => ZIO.fromOption(entry).orElseFail(new NoSuchElementException(s"Unknown product: $name"))
        )
        input <- ZIO.acquireRelease(ZIO.attemptBlocking(archive.getInputStream(entry)))(
          input => ZIO.attemptBlocking(input.close()).orDie
        )
        contents <- ZIO.attemptBlocking(new String(input.readAllBytes(), UTF_8))
      } yield contents
    }

  @Test
  @Timeout(60)
  def reusesCatalogAfterMissingProductsAndClosesItAtShutdown(@TempDir directory: Path): Unit = {
    val path: Path = directory.resolve("catalog.zip")
    createCatalog(path)
    val program = for {
      opened <- Ref.make(0)
      closed <- Ref.make(0)
      responses <- ZIO.scoped {
        for {
          pool <- ZPool.make(openCatalog(path, opened, closed), 1)
          coffee <- description(pool, "coffee.txt")
          missing <- description(pool, "missing.txt").either
          // The failed request must return its lease so the next request can use the sole handle.
          tea <- description(pool, "tea.txt")
          repeated <- ZIO.foreachPar(List("tea.txt", "coffee.txt", "tea.txt"))(
            name => description(pool, name)
          )
          openCount <- opened.get
          closedCount <- closed.get
        } yield (coffee, missing, tea, repeated, openCount, closedCount)
      }
      closedCount <- closed.get
    } yield (responses, closedCount)

    val ((coffee, missing, tea, repeated, openCount, closedWhileServing), closedAfterShutdown) =
      Unsafe.unsafe { implicit unsafe =>
        Runtime.default.unsafe.run(
          program.timeoutFail(new IllegalStateException("Catalog requests timed out"))(Duration.fromSeconds(30))
        ).getOrThrowFiberFailure()
      }

    assertEquals("Roasted coffee, 250g", coffee)
    assertTrue(missing.left.exists(_.isInstanceOf[NoSuchElementException]))
    assertEquals(Left("Unknown product: missing.txt"), missing.left.map(_.getMessage))
    assertEquals("Green tea, 100g", tea)
    assertEquals(List("Green tea, 100g", "Roasted coffee, 250g", "Green tea, 100g"), repeated)
    assertEquals(1, openCount)
    assertEquals(0, closedWhileServing)
    assertEquals(1, closedAfterShutdown)
  }
}
