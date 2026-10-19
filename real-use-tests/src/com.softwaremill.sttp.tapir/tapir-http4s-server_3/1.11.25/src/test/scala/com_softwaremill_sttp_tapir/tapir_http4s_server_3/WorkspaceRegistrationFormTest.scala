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
import org.http4s.implicits.*
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import org.typelevel.ci.CIString
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.server.http4s.Http4sServerInterpreter

import scala.concurrent.duration.*

/** Exercises URL-encoded form decoding and validation in Tapir's http4s interpreter. */
class WorkspaceRegistrationFormTest {
  final case class Registration(displayName: String, email: String, seats: Int)

  private given Schema[Registration] = Schema.derived[Registration]
    .modify(_.seats)(_.validate(Validator.min(1)))

  @Test
  @Timeout(60)
  def registersAWorkspaceFromABrowserFormAndRedirectsToConfirmation(): Unit = {
    val register: PublicEndpoint[Registration, Unit, String, Any] = endpoint.post
      .in("workspaces" / "register")
      .in(formBody[Registration])
      .out(statusCode(StatusCode.SeeOther))
      .out(header[String]("Location"))

    val scenario: IO[Unit] = for {
      registrations <- Ref.of[IO, Vector[Registration]](Vector.empty)
      app = Http4sServerInterpreter[IO]().toRoutes(
        register.serverLogicSuccess { (registration: Registration) =>
          registrations.update(_ :+ registration).as("/workspaces/registration-complete")
        }
      ).orNotFound
      accepted <- app.run(formRequest(
        "displayName=Zo%C3%AB+%26+Sons&email=procurement%2Bwest%40example.test&seats=3"
      ))
      _ <- accepted.body.compile.drain
      saved <- registrations.get
      _ <- IO {
        assertEquals(303, accepted.status.code)
        assertEquals(
          Some("/workspaces/registration-complete"),
          accepted.headers.headers.find(_.name == CIString("Location")).map(_.value)
        )
        assertEquals(Vector(Registration("Zo\u00eb & Sons", "procurement+west@example.test", 3)), saved)
      }
      invalid <- app.run(formRequest("displayName=North&email=north%40example.test&seats=0"))
      invalidBody <- invalid.as[String]
      incomplete <- app.run(formRequest("displayName=North&seats=2"))
      incompleteBody <- incomplete.as[String]
      afterRejected <- registrations.get
      _ <- IO {
        assertEquals(400, invalid.status.code)
        assertTrue(invalidBody.nonEmpty)
        assertEquals(400, incomplete.status.code)
        assertTrue(incompleteBody.nonEmpty)
        assertEquals(saved, afterRejected)
      }
    } yield ()

    scenario.timeout(45.seconds).unsafeRunSync()
  }

  private def formRequest(body: String): Request[IO] =
    Request[IO](Method.POST, uri"/workspaces/register")
      .withEntity(body)
      .putHeaders(Header.Raw(CIString("Content-Type"), "application/x-www-form-urlencoded; charset=UTF-8"))
}
