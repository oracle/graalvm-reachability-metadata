/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_http4s_server_3

import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global
import org.http4s.{Header, Method, Request}
import org.http4s.implicits.{path as _, *}
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import org.typelevel.ci.CIString
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.server.http4s.Http4sServerInterpreter

import scala.concurrent.duration.*

/** Exercises bearer authentication and security logic in Tapir's http4s interpreter. */
class BearerReportAuthorizationTest {
  final case class Analyst(tenant: String)

  @Test
  @Timeout(60)
  def protectsTenantReportsBeforeRunningBusinessLogic(): Unit = {
    val download: Endpoint[String, String, (StatusCode, String), String, Any] = endpoint
      .securityIn(auth.bearer[String]())
      .get
      .in("reports" / path[String]("tenant"))
      .errorOut(statusCode)
      .errorOut(stringBody)
      .out(stringBody)

    val scenario: IO[Unit] = for {
      requestedTenants <- Ref.of[IO, Vector[String]](Vector.empty)
      secured = download.serverSecurityLogic[Analyst, IO] { (token: String) =>
        IO.pure[Either[(StatusCode, String), Analyst]] {
          if (token == "north-analyst-token") Right(Analyst("north"))
          else Left((StatusCode.Unauthorized, "Invalid access token"))
        }
      }.serverLogic { (analyst: Analyst) => (tenant: String) =>
        requestedTenants.update(_ :+ tenant).as[Either[(StatusCode, String), String]] {
          if (analyst.tenant == tenant) Right("tenant,total\nnorth,4200\n")
          else Left((StatusCode.Forbidden, "Report belongs to another tenant"))
        }
      }
      app = Http4sServerInterpreter[IO]().toRoutes(secured).orNotFound
      missing <- app.run(Request[IO](Method.GET, uri"/reports/north"))
      _ <- missing.body.compile.drain
      _ <- IO {
        assertEquals(401, missing.status.code)
        assertTrue(missing.headers.headers.exists { header =>
          header.name == CIString("WWW-Authenticate") && header.value.startsWith("Bearer")
        })
      }
      invalid <- app.run(authorizedRequest("north", "expired-token"))
      invalidBody <- invalid.as[String]
      beforeAuthorized <- requestedTenants.get
      _ <- IO {
        assertEquals(401, invalid.status.code)
        assertEquals("Invalid access token", invalidBody)
        assertEquals(Vector.empty[String], beforeAuthorized)
      }
      allowed <- app.run(authorizedRequest("north", "north-analyst-token"))
      allowedBody <- allowed.as[String]
      forbidden <- app.run(authorizedRequest("south", "north-analyst-token"))
      forbiddenBody <- forbidden.as[String]
      requested <- requestedTenants.get
      _ <- IO {
        assertEquals(200, allowed.status.code)
        assertEquals("tenant,total\nnorth,4200\n", allowedBody)
        assertEquals(403, forbidden.status.code)
        assertEquals("Report belongs to another tenant", forbiddenBody)
        assertEquals(Vector("north", "south"), requested)
      }
    } yield ()

    scenario.timeout(45.seconds).unsafeRunSync()
  }

  private def authorizedRequest(tenant: String, token: String): Request[IO] =
    Request[IO](Method.GET, uri"/reports" / tenant)
      .putHeaders(Header.Raw(CIString("Authorization"), s"Bearer $token"))
}
