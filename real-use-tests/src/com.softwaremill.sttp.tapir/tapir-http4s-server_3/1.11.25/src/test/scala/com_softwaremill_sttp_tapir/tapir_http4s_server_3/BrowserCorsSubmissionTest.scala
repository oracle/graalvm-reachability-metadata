/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_http4s_server_3

import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global
import org.http4s.{Header, Method, Request, Response}
import org.http4s.implicits.*
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import org.typelevel.ci.CIString
import sttp.model.{Method as CorsMethod, StatusCode}
import sttp.tapir.*
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}
import sttp.tapir.server.interceptor.cors.{CORSConfig, CORSInterceptor}

import java.util.Locale
import scala.concurrent.duration.*

/** Exercises CORS preflight and response interception in Tapir's http4s interpreter. */
class BrowserCorsSubmissionTest {
  @Test
  @Timeout(60)
  def permitsTheWorkspacePortalToSubmitNotesAndReadTheReceiptHeader(): Unit = {
    val portal: String = "https://portal.example.test"
    val cors: CORSConfig = CORSConfig.default
      .allowMatchingOrigins(_ == portal)
      .allowCredentials
      .allowMethods(CorsMethod.POST)
      .allowHeaders("Content-Type", "X-Workspace")
      .exposeHeaders("X-Receipt")
      .maxAge(10.minutes)
      .preflightResponseStatusCode(StatusCode.NoContent)
    val options: Http4sServerOptions[IO] = Http4sServerOptions.customiseInterceptors[IO]
      .corsInterceptor(CORSInterceptor.customOrThrow[IO](cors))
      .options
    val submit: PublicEndpoint[(String, String), Unit, (String, String), Any] = endpoint.post
      .in("notes")
      .in(header[String]("X-Workspace"))
      .in(stringBody)
      .out(header[String]("X-Receipt"))
      .out(stringBody)

    val scenario: IO[Unit] = for {
      notes <- Ref.of[IO, Vector[(String, String)]](Vector.empty)
      app = Http4sServerInterpreter[IO](options).toRoutes(
        submit.serverLogicSuccess { (workspace: String, note: String) =>
          notes.update(_ :+ (workspace, note)).as(("note-1", "Note saved"))
        }
      ).orNotFound
      preflight <- app.run(preflightRequest(portal))
      _ <- preflight.body.compile.drain
      beforeSubmission <- notes.get
      _ <- IO {
        assertEquals(204, preflight.status.code)
        assertEquals(Some(portal), headerValue(preflight, "Access-Control-Allow-Origin"))
        assertEquals(Some("true"), headerValue(preflight, "Access-Control-Allow-Credentials"))
        assertTrue(headerTokens(preflight, "Access-Control-Allow-Methods").contains("post"))
        assertTrue(headerTokens(preflight, "Access-Control-Allow-Headers").contains("x-workspace"))
        assertEquals(Some("600"), headerValue(preflight, "Access-Control-Max-Age"))
        assertEquals(Vector.empty[(String, String)], beforeSubmission)
      }
      response <- app.run(
        Request[IO](Method.POST, uri"/notes")
          .withEntity("Prepare the quarterly review")
          .putHeaders(
            Header.Raw(CIString("Origin"), portal),
            Header.Raw(CIString("X-Workspace"), "north")
          )
      )
      body <- response.as[String]
      saved <- notes.get
      _ <- IO {
        assertEquals(200, response.status.code)
        assertEquals("Note saved", body)
        assertEquals(Some("note-1"), headerValue(response, "X-Receipt"))
        assertEquals(Some(portal), headerValue(response, "Access-Control-Allow-Origin"))
        assertEquals(Some("true"), headerValue(response, "Access-Control-Allow-Credentials"))
        assertTrue(headerTokens(response, "Access-Control-Expose-Headers").contains("x-receipt"))
        assertEquals(Vector(("north", "Prepare the quarterly review")), saved)
      }
      untrusted <- app.run(preflightRequest("https://untrusted.example.test"))
      _ <- untrusted.body.compile.drain
      afterPreflight <- notes.get
      _ <- IO {
        assertEquals(None, headerValue(untrusted, "Access-Control-Allow-Origin"))
        assertEquals(saved, afterPreflight)
      }
    } yield ()

    scenario.timeout(45.seconds).unsafeRunSync()
  }

  private def preflightRequest(origin: String): Request[IO] =
    Request[IO](Method.OPTIONS, uri"/notes").putHeaders(
      Header.Raw(CIString("Origin"), origin),
      Header.Raw(CIString("Access-Control-Request-Method"), "POST"),
      Header.Raw(CIString("Access-Control-Request-Headers"), "content-type,x-workspace")
    )

  private def headerValue(response: Response[IO], name: String): Option[String] =
    response.headers.headers.find(_.name == CIString(name)).map(_.value)

  private def headerTokens(response: Response[IO], name: String): Set[String] =
    headerValue(response, name).toList.flatMap(_.split(",")).map(_.trim.toLowerCase(Locale.ROOT)).toSet
}
