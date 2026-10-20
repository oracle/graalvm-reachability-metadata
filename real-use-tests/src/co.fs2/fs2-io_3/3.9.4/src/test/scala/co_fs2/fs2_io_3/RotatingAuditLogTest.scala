/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_fs2.fs2_io_3

import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global
import fs2.Stream
import fs2.io.file.{Files, Flag, Flags, Path}
import fs2.text
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

import java.nio.charset.StandardCharsets.UTF_8
import scala.concurrent.duration.*

/** Exercises rotating file sinks by retaining and replaying a size-bounded UTF-8 audit log. */
class RotatingAuditLogTest {
  @Test
  @Timeout(60)
  def rotateAuditSegmentsWithoutLosingOrDuplicatingEvents(): Unit = {
    val records: List[String] = List(
      "event-001|login|user=Jos\u00e9",
      "event-002|create-order|order=ORD-81|units=12",
      "event-003|reserve-stock|order=ORD-81|warehouse=north",
      "event-004|payment-approved|order=ORD-81|cents=2499",
      "event-005|print-label|order=ORD-81|destination=Z\u00fcrich",
      "event-006|dispatch|order=ORD-81|carrier=parcel",
      "event-007|logout|user=Jos\u00e9"
    )
    val contents: String = records.mkString("", "\n", "\n")
    val segmentLimit: Long = 64L
    val expectedSizes: List[Long] = contents.getBytes(UTF_8).grouped(segmentLimit.toInt)
      .map(_.length.toLong).toList
    val files: Files[IO] = Files[IO]

    files.tempDirectory.use { directory =>
      for {
        sequence <- Ref.of[IO, Int](0)
        // Monotonic names let an audit reader replay segments in their original order.
        nextPath = sequence.getAndUpdate(_ + 1).map(index =>
          directory / f"audit-$index%04d.log"
        )
        _ <- Stream.emits(records).covary[IO].map(_ + "\n")
          .through(text.utf8.encode)
          .through(files.writeRotate(nextPath, segmentLimit, Flags(Flag.Write, Flag.CreateNew)))
          .compile.drain
        segments <- files.list(directory).compile.toList.map(_.sortBy(_.fileName.toString))
        sizes <- Stream.emits(segments).covary[IO].evalMap(files.size).compile.toList
        // Rotation is byte-based: decode after concatenation, not separately per segment.
        replayed <- Stream.emits(segments).covary[IO].flatMap(path =>
          files.readAll(path, 11, Flags.Read)
        ).through(text.utf8.decode).through(text.lines).filter(_.nonEmpty).compile.toList
        _ <- IO {
          val expectedPaths: List[Path] = expectedSizes.indices.map(index =>
            directory / f"audit-$index%04d.log"
          ).toList
          assertEquals(expectedPaths, segments, "Every rotated segment must be discoverable in replay order")
          assertEquals(expectedSizes, sizes, "Segments must honor the byte limit and retain the final partial file")
          assertEquals(records, replayed, "Audit replay must preserve every event, including UTF-8 user data")
        }
      } yield ()
    }.timeout(30.seconds).unsafeRunSync()
  }
}
