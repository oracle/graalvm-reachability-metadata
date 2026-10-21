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
import fs2.io.file.{CopyFlag, CopyFlags, Files, Path}
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}

import scala.concurrent.duration.*

/** Exercises symbolic links by activating and rolling back versioned application releases. */
class ReleaseSymlinkTest {
  private def activate(files: Files[IO], root: Path, release: String): IO[Unit] = {
    val pending: Path = root / "current.next"
    for {
      // Relative targets keep the deployment tree relocatable.
      _ <- files.createSymbolicLink(pending, Path("releases") / release)
      _ <- files.move(pending, root / "current",
        CopyFlags(CopyFlag.AtomicMove, CopyFlag.ReplaceExisting))
    } yield ()
  }

  @Test
  @Timeout(60)
  def switchReleaseAndRollbackWithoutChangingReleaseContents(): Unit = {
    val files: Files[IO] = Files[IO]
    val previous: String = "service=catalog\nrelease=blue\nfeature.search=false\n"
    val candidate: String = "service=catalog\nrelease=green\nfeature.search=true\n"

    files.tempDirectory.use { root =>
      val blue: Path = root / "releases" / "blue"
      val green: Path = root / "releases" / "green"
      val current: Path = root / "current"
      val activeConfig: Path = current / "application.conf"
      for {
        _ <- files.createDirectories(blue)
        _ <- files.createDirectories(green)
        _ <- Stream.emit(previous).covary[IO]
          .through(files.writeUtf8(blue / "application.conf")).compile.drain
        _ <- Stream.emit(candidate).covary[IO]
          .through(files.writeUtf8(green / "application.conf")).compile.drain
        _ <- activate(files, root, "blue")
        initial <- files.readUtf8(activeConfig).compile.string
        _ <- IO(assertEquals(previous, initial, "Consumers initially read the stable release"))
        _ <- activate(files, root, "green")
        upgraded <- files.readUtf8(activeConfig).compile.string
        greenSelected <- files.isSameFile(current, green)
        _ <- IO {
          assertEquals(candidate, upgraded, "The stable path must expose the newly activated configuration")
          assertTrue(greenSelected, "Activation must select the candidate release directory")
        }
        // An application health check rejects the candidate; restore the previous release.
        _ <- activate(files, root, "blue")
        restored <- files.readUtf8(activeConfig).compile.string
        resolved <- files.realPath(activeConfig)
        expected <- files.realPath(blue / "application.conf")
        remainsLink <- files.isSymbolicLink(current)
        pendingExists <- files.exists(root / "current.next", followLinks = false)
        savedBlue <- files.readUtf8(blue / "application.conf").compile.string
        savedGreen <- files.readUtf8(green / "application.conf").compile.string
        _ <- IO {
          assertEquals(previous, restored, "Rollback must restore the configuration at the same consumer path")
          assertEquals(expected, resolved, "The rolled-back path must resolve into the stable release")
          assertTrue(remainsLink, "Deployment must retain indirection rather than copying a release")
          assertFalse(pendingExists, "Activation must not leave an unpublished link behind")
          assertEquals(previous, savedBlue, "Switching releases must not mutate the stable release")
          assertEquals(candidate, savedGreen, "The rejected release must remain intact for investigation")
        }
      } yield ()
    }.timeout(30.seconds).unsafeRunSync()
  }
}
