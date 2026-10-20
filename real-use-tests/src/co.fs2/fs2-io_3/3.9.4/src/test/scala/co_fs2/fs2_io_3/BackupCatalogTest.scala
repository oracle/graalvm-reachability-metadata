/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_fs2.fs2_io_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.Stream
import fs2.io.file.{Files, Path}
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

import scala.concurrent.duration.*

/** Exercises recursive directory traversal by building a backup catalog of completed customer exports. */
class BackupCatalogTest {
  private case class CatalogEntry(relativePath: Path, bytes: Long)

  @Test
  @Timeout(60)
  def catalogNestedExportsWithoutDirectoriesOrPartialUploads(): Unit = {
    val files: Files[IO] = Files[IO]
    val fixtures: List[(Path, String)] = List(
      Path("README.txt") -> "Backup fixture\n",
      Path("customers/eu/orders.csv") -> "order,total\nA-1,1200\n",
      Path("customers/us/orders.csv") -> "order,total\nB-2,350\n",
      Path("customers/us/empty.txt") -> "",
      Path("customers/us/upload.part") -> "incomplete"
    )
    val expected: Map[Path, Long] = Map(
      Path("README.txt") -> 15L,
      Path("customers/eu/orders.csv") -> 21L,
      Path("customers/us/orders.csv") -> 20L,
      Path("customers/us/empty.txt") -> 0L
    )

    files.tempDirectory.use { root =>
      for {
        _ <- files.createDirectories(root / "customers" / "eu")
        _ <- files.createDirectories(root / "customers" / "us")
        // A directory that looks like an export must never become a backup entry.
        _ <- files.createDirectories(root / "pending.csv")
        _ <- Stream.emits(fixtures).covary[IO].evalMap { case (relative, contents) =>
          Stream.emit(contents).covary[IO]
            .through(files.writeUtf8(root / relative)).compile.drain
        }.compile.drain
        entries <- files.walk(root)
          .evalMap { path =>
            files.getBasicFileAttributes(path).map(attributes => (path, attributes))
          }
          .collect {
            case (path, attributes)
                if attributes.isRegularFile && !path.fileName.toString.endsWith(".part") =>
              CatalogEntry(root.relativize(path), attributes.size)
          }
          .compile.toList
        _ <- IO {
          // Directory iteration order is not part of the catalog's contract.
          assertEquals(expected, entries.map(entry => entry.relativePath -> entry.bytes).toMap)
          assertEquals(4, entries.size, "Include each completed file, including empty files, exactly once")
          assertEquals(56L, entries.map(_.bytes).sum, "Report the storage required for the backup")
        }
      } yield ()
    }.timeout(30.seconds).unsafeRunSync()
  }
}
