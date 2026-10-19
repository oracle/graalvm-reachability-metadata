/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_circe_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Decoder
import io.circe.Json
import io.circe.generic.semiauto.deriveDecoder
import org.http4s.HttpRoutes
import org.http4s.InvalidMessageBodyFailure
import org.http4s.Uri
import org.http4s.circe.CirceSensitiveDataEntityDecoder._
import org.http4s.circe.jsonEncoder
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import java.io.PrintWriter
import java.io.StringWriter
import scala.concurrent.duration._

/** Exercises automatic body-eliding entity decoder derivation for a secret-lease HTTP client. */
class AutomaticSecretLeaseDecodeTest {
  private final case class SecretLease(token: String, ttlSeconds: Int, mount: String)

  // One import applies the safe diagnostic policy without a per-type HTTP decoder or redactor.
  private given Decoder[SecretLease] = deriveDecoder[SecretLease]

  @Test
  @Timeout(60)
  def logAnIncompatibleLeaseWithoutDisclosingAnyResponseFields(): Unit = {
    val token: String = "lease-token-private-73"
    val bootstrapPassword: String = "future-field-private-84"
    val mount: String = "confidential-tenant-mount"
    val incompatible: Json = Json.obj(
      "token" -> Json.fromString(token),
      "ttlSeconds" -> Json.fromString("one hour"),
      "mount" -> Json.fromString(mount),
      // Even fields unknown to this client's model must never reach its error log.
      "bootstrapPassword" -> Json.fromString(bootstrapPassword)
    )
    val compatible: Json = incompatible.mapObject(_.add("ttlSeconds", Json.fromInt(3600)))
    val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case GET -> Root / "leases" / "legacy" => Ok(incompatible)
      case GET -> Root / "leases" / "current" => Ok(compatible)
    }
    val client: Client[IO] = Client.fromHttpApp(routes.orNotFound)

    val result: IO[Unit] = for {
      rejected <- client.expect[SecretLease](Uri.unsafeFromString("/leases/legacy")).attempt
      _ <- IO {
        rejected match {
          case Left(failure) =>
            assertInstanceOf(classOf[InvalidMessageBodyFailure], failure)
            val diagnostic: String = loggedException(failure)
            assertTrue(diagnostic.contains("<REDACTED>"))
            assertFalse(diagnostic.contains(token))
            assertFalse(diagnostic.contains(bootstrapPassword))
            assertFalse(diagnostic.contains(mount))
            assertFalse(diagnostic.contains("one hour"))
          case Right(_) => throw new AssertionError("An incompatible lease must not be accepted")
        }
      }
      lease <- client.expect[SecretLease](Uri.unsafeFromString("/leases/current"))
      _ <- IO {
        // Redaction affects diagnostics, not the credentials returned to application code.
        assertEquals(SecretLease(token, 3600, mount), lease)
      }
    } yield ()

    result.timeout(30.seconds).unsafeRunSync()
  }

  private def loggedException(failure: Throwable): String = {
    val output: StringWriter = new StringWriter()
    val writer: PrintWriter = new PrintWriter(output)
    failure.printStackTrace(writer)
    writer.flush()
    output.toString
  }
}
