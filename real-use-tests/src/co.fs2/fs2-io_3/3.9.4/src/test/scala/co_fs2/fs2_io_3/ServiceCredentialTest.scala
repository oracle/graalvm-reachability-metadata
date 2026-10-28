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
import fs2.io.file.{CopyFlag, CopyFlags, Files, Path, PosixPermission, PosixPermissions}
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse}
import org.junit.jupiter.api.{Test, Timeout}

import scala.concurrent.duration.*

/** Exercises POSIX file permissions by provisioning and rotating an owner-only service credential. */
class ServiceCredentialTest {
  private val privateDirectory: PosixPermissions = PosixPermissions(
    PosixPermission.OwnerRead, PosixPermission.OwnerWrite, PosixPermission.OwnerExecute
  )
  private val stagingPermissions: PosixPermissions = PosixPermissions(
    PosixPermission.OwnerRead, PosixPermission.OwnerWrite
  )
  private val publishedPermissions: PosixPermissions = PosixPermissions(PosixPermission.OwnerRead)

  private def publish(files: Files[IO], directory: Path, credential: String): IO[Unit] = {
    val staging: Path = directory / "service.token.pending"
    val active: Path = directory / "service.token"
    for {
      // Set permissions at creation, before any secret bytes are written.
      _ <- files.createFile(staging, Some(stagingPermissions))
      initialPermissions <- files.getPosixPermissions(staging)
      _ <- IO(assertEquals(stagingPermissions, initialPermissions,
        "A staged credential must never be readable by group or other users"))
      _ <- Stream.emit(credential).covary[IO]
        .through(files.writeUtf8(staging)).compile.drain
      // Publish a read-only credential by replacing its directory entry, not modifying it in place.
      _ <- files.setPosixPermissions(staging, publishedPermissions)
      _ <- files.move(staging, active, CopyFlags(CopyFlag.AtomicMove, CopyFlag.ReplaceExisting))
    } yield ()
  }

  @Test
  @Timeout(60)
  def rotateCredentialWithoutBroadeningAccess(): Unit = {
    val files: Files[IO] = Files[IO]
    val original: String = "service=catalog\ntoken=development-credential-001\n"
    val replacement: String = "service=catalog\ntoken=rotated-002\n"

    files.tempDirectory.use { root =>
      val secrets: Path = root / "secrets"
      val active: Path = secrets / "service.token"
      for {
        _ <- files.createDirectory(secrets, Some(privateDirectory))
        _ <- publish(files, secrets, original)
        firstContents <- files.readUtf8(active).compile.string
        firstPermissions <- files.getPosixPermissions(active)
        _ <- IO {
          assertEquals(original, firstContents, "The service must read the provisioned credential")
          assertEquals(publishedPermissions, firstPermissions,
            "The published credential must be owner-readable only")
        }
        _ <- publish(files, secrets, replacement)
        rotatedContents <- files.readUtf8(active).compile.string
        rotatedPermissions <- files.getPosixPermissions(active)
        directoryPermissions <- files.getPosixPermissions(secrets)
        stagingRemains <- files.exists(secrets / "service.token.pending")
        remainingFiles <- files.list(secrets).compile.toList
        _ <- IO {
          assertEquals(replacement, rotatedContents,
            "Rotation must expose the new credential without retaining bytes from the longer old value")
          assertEquals(publishedPermissions, rotatedPermissions,
            "Replacing the credential must preserve the owner-only read policy")
          assertEquals(privateDirectory, directoryPermissions,
            "The containing directory must remain inaccessible to group and other users")
          assertFalse(stagingRemains, "Publication must consume the staged credential")
          assertEquals(List(active), remainingFiles, "No obsolete credential may remain in the secrets directory")
        }
      } yield ()
    }.timeout(30.seconds).unsafeRunSync()
  }
}
