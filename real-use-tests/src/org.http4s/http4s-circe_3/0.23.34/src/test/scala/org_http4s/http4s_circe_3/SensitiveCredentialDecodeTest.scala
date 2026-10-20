/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_circe_3

import cats.effect.IO
import cats.effect.Ref
import cats.effect.unsafe.implicits.global
import io.circe.Decoder
import io.circe.Json
import io.circe.generic.semiauto.deriveDecoder
import org.http4s.EntityDecoder
import org.http4s.HttpRoutes
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri
import org.http4s.circe.jsonDecoder
import org.http4s.circe.jsonEncoder
import org.http4s.circe.jsonOfSensitive
import org.http4s.dsl.io._
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import scala.concurrent.duration._

/** Exercises sensitive-data JSON decoding for a credential API with redacted validation diagnostics. */
class SensitiveCredentialDecodeTest {
  private final case class Credentials(account: String, password: String, token: String, ttl: Int)

  private given Decoder[Credentials] = deriveDecoder[Credentials]
  private given EntityDecoder[IO, Credentials] = jsonOfSensitive[IO, Credentials] { document =>
    document.mapObject(_.add("password", Json.fromString("[REDACTED]"))
      .add("token", Json.fromString("[REDACTED]"))).noSpaces
  }

  @Test
  @Timeout(60)
  def redactRejectedCredentialsWithoutChangingValidSubmissions(): Unit = {
    val password: String = "private-password-73"
    val token: String = "private-token-84"
    val invalid: Json = Json.obj(
      "account" -> Json.fromString("service-reports"),
      "password" -> Json.fromString(password),
      "token" -> Json.fromString(token),
      "ttl" -> Json.fromString("one hour")
    )
    val valid: Json = invalid.mapObject(_.add("ttl", Json.fromInt(3600)))

    val result: IO[Unit] = for {
      diagnostics <- Ref.of[IO, List[String]](Nil)
      stored <- Ref.of[IO, List[Credentials]](Nil)
      routes = HttpRoutes.of[IO] {
        case request @ POST -> Root / "credentials" =>
          request.attemptAs[Credentials].value.flatMap {
            case Left(failure) =>
              diagnostics.update(_ :+ failure.getMessage) *>
                BadRequest(Json.obj("error" -> Json.fromString("Invalid credentials document")))
            case Right(credentials) =>
              stored.update(_ :+ credentials) *>
                Created(Json.obj("account" -> Json.fromString(credentials.account)))
          }
      }
      rejected <- routes.orNotFound.run(submission(invalid))
      rejection <- rejected.as[Json]
      logs <- diagnostics.get
      afterRejection <- stored.get
      _ <- IO {
        assertEquals(Status.BadRequest, rejected.status)
        assertEquals(Json.obj("error" -> Json.fromString("Invalid credentials document")), rejection)
        assertEquals(List.empty[Credentials], afterRejection)
        assertEquals(1, logs.size)
        assertTrue(logs.head.contains("service-reports"))
        assertTrue(logs.head.contains("one hour"))
        assertTrue(logs.head.contains("\"password\":\"[REDACTED]\""))
        assertTrue(logs.head.contains("\"token\":\"[REDACTED]\""))
        assertFalse(logs.head.contains(password))
        assertFalse(logs.head.contains(token))
      }
      accepted <- routes.orNotFound.run(submission(valid))
      receipt <- accepted.as[Json]
      saved <- stored.get
      finalLogs <- diagnostics.get
      _ <- IO {
        assertEquals(Status.Created, accepted.status)
        assertEquals(Json.obj("account" -> Json.fromString("service-reports")), receipt)
        assertEquals(List(Credentials("service-reports", password, token, 3600)), saved)
        assertEquals(logs, finalLogs)
      }
    } yield ()

    result.timeout(30.seconds).unsafeRunSync()
  }

  private def submission(document: Json): Request[IO] =
    Request[IO](Method.POST, Uri.unsafeFromString("/credentials")).withEntity(document)
}
