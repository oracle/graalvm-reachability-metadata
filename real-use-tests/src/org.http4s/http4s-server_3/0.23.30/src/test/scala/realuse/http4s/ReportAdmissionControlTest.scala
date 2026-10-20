/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package realuse.http4s

import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import org.http4s.{Header, HttpApp, HttpRoutes, Request, Response, Status, Uri}
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import org.http4s.server.middleware.MaxActiveRequests
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import org.typelevel.ci.*

/** Exercises maximum-active-request admission control for report downloads. */
class ReportAdmissionControlTest {
  @Test
  @Timeout(60)
  def rejectsExtraDownloadsUntilTheActiveResponseBodyCompletes(): Unit = {
    val busyMessage: String = "A report download is active; please retry."
    val busyResponse: Response[IO] = Response[IO](Status.ServiceUnavailable)
      .withEntity(busyMessage)
      .putHeaders(Header.Raw(ci"Retry-After", "10"))

    def download(name: String): Request[IO] =
      Request[IO](uri = Uri.unsafeFromString(s"/reports/$name"))

    def report(name: String): String = s"report,sku,quantity\n$name,book-42,3\n"

    val checks: IO[Unit] = for {
      generated <- Ref.of[IO, Vector[String]](Vector.empty)
      routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
        case GET -> Root / "reports" / name =>
          generated.update(_ :+ name) *> Ok(report(name))
      }
      admission <- MaxActiveRequests.forHttpApp[IO](maxActive = 1L, defaultResp = busyResponse)
      application: HttpApp[IO] = admission(routes.orNotFound)
      active <- application.run(download("daily"))
      // Headers have arrived, but the first client has not consumed its download yet.
      rejected <- application.run(download("monthly"))
      rejectedBody <- rejected.as[String]
      rejectedAgain <- application.run(download("yearly"))
      rejectedAgainBody <- rejectedAgain.as[String]
      generatedWhileBusy <- generated.get
      completedBody <- active.as[String]
      retried <- application.run(download("monthly"))
      retriedBody <- retried.as[String]
      next <- application.run(download("yearly"))
      nextBody <- next.as[String]
      allGenerated <- generated.get
    } yield {
      assertEquals(Status.Ok, active.status)
      assertEquals(Vector("daily"), generatedWhileBusy)
      for ((response, body) <- List(rejected -> rejectedBody, rejectedAgain -> rejectedAgainBody)) {
        assertEquals(Status.ServiceUnavailable, response.status)
        assertEquals(busyMessage, body)
        assertEquals(Some("10"), response.headers.get(ci"Retry-After").map(_.head.value))
      }
      assertEquals(report("daily"), completedBody)
      assertEquals(Status.Ok, retried.status)
      assertEquals(report("monthly"), retriedBody)
      assertEquals(None, retried.headers.get(ci"Retry-After"))
      assertEquals(Status.Ok, next.status)
      assertEquals(report("yearly"), nextBody)
      assertEquals(Vector("daily", "monthly", "yearly"), allGenerated)
    }

    checks.unsafeRunSync()
  }
}
