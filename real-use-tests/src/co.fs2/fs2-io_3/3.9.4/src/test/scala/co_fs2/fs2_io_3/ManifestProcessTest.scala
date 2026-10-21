/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_fs2.fs2_io_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import fs2.Stream
import fs2.io.process.ProcessBuilder
import fs2.text
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}

import scala.concurrent.duration.*

/** Exercises subprocess streaming by sending shipment manifests to a shell validator and collecting its report. */
class ManifestProcessTest {
  private case class ValidationReport(output: String, errors: String, exitCode: Int)

  // A shell-based warehouse tool consumes CSV on stdin and separates reports from diagnostics.
  private val validator: String = """
    |count=0
    |total=0
    |status=0
    |while IFS=, read -r sku quantity; do
    |  case "$quantity" in
    |    ''|*[!0-9]*)
    |      printf 'Rejected %s: quantity must be numeric\n' "$sku" >&2
    |      status=2
    |      ;;
    |    *)
    |      count=$((count + 1))
    |      total=$((total + quantity))
    |      ;;
    |  esac
    |done
    |printf '%s: accepted=%s units=%s\n' "$MANIFEST_ID" "$count" "$total"
    |exit "$status"
    |""".stripMargin

  private def validate(id: String, rows: List[String]): IO[ValidationReport] = {
    ProcessBuilder("sh", "-c", validator)
      .withExtraEnv(Map("MANIFEST_ID" -> id))
      .spawn[IO].use { process =>
        val send: IO[Unit] = Stream.emits(rows).covary[IO]
          .map(_ + "\n")
          .through(text.utf8.encode)
          .through(process.stdin)
          .compile.drain
        val output: IO[String] = process.stdout.through(text.utf8.decode).compile.string
        val errors: IO[String] = process.stderr.through(text.utf8.decode).compile.string

        // Drain both output pipes while writing input, then wait for the actual exit status.
        (send, output, errors, process.exitValue).parTupled.map {
          case (_, stdout, stderr, exitCode) => ValidationReport(stdout, stderr, exitCode)
        }
      }
  }

  @Test
  @Timeout(60)
  def validateShipmentManifestsThroughExternalTool(): Unit = {
    val reports: IO[(ValidationReport, ValidationReport)] = for {
      accepted <- validate("shipment-81", List("SKU-42,12", "SKU-7,5"))
      rejected <- validate("shipment-82", List("SKU-9,4", "SKU-3,unknown"))
    } yield (accepted, rejected)

    val (accepted, rejected) = reports.timeout(30.seconds).unsafeRunSync()
    assertEquals(ValidationReport("shipment-81: accepted=2 units=17\n", "", 0), accepted)
    assertEquals(ValidationReport(
      "shipment-82: accepted=1 units=4\n",
      "Rejected SKU-3: quantity must be numeric\n",
      2
    ), rejected, "A rejected manifest must retain its report, diagnostics, and nonzero exit status")
  }
}
