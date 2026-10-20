/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio

import java.time.Duration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import zio.{NonEmptyChunk, Runtime, Scope, Unsafe, ZIO}
import zio.http.*

/** Exercises ZIO HTTP CORS middleware for a cross-origin browser preferences API. */
class BrowserPreferencesCorsTest {
  private val dashboardOrigin: String = "https://dashboard.example"

  @Test
  @Timeout(60)
  def permitsDashboardPreflightAndExposesItsUpdateReceipt(): Unit = {
    val cors: Middleware.CorsConfig = Middleware.CorsConfig(
      allowedOrigin = origin =>
        if (origin == Header.Origin("https", "dashboard.example")) {
          Some(Header.AccessControlAllowOrigin("https", "dashboard.example"))
        } else None,
      allowedMethods = Header.AccessControlAllowMethods(Method.POST),
      allowedHeaders = Header.AccessControlAllowHeaders("content-type", "x-request-id"),
      allowCredentials = Header.AccessControlAllowCredentials.Allow,
      exposedHeaders = Header.AccessControlExposeHeaders.Some(NonEmptyChunk("x-request-id")),
      maxAge = Some(Header.AccessControlMaxAge(Duration.ofMinutes(10)))
    )
    val routes: Routes[Any, Response] = Routes(
      Method.POST / "preferences" -> handler { (request: Request) =>
        request.body.asString
          .mapError(_ => Response.badRequest)
          .map { email =>
            Response.text(s"Notifications will be sent to $email")
              .addHeader("x-request-id", "preferences-42")
          }
      }
    ) @@ Middleware.cors(cors)

    val program: ZIO[Scope, Throwable, Unit] = for {
      preflight <- routes.runZIO(preflightRequest(dashboardOrigin, "POST"))
      preflightBody <- preflight.body.asString
      _ <- ZIO.succeed {
        assertEquals(Status.NoContent, preflight.status)
        assertEquals("", preflightBody)
        assertEquals(Some(dashboardOrigin), preflight.rawHeader("Access-Control-Allow-Origin"))
        assertEquals(Some("true"), preflight.rawHeader("Access-Control-Allow-Credentials"))
        assertEquals(Set("POST"), headerTokens(preflight, "Access-Control-Allow-Methods"))
        assertEquals(
          Set("content-type", "x-request-id"),
          headerTokens(preflight, "Access-Control-Allow-Headers")
        )
        assertEquals(Some("600"), preflight.rawHeader("Access-Control-Max-Age"))
      }
      update <- routes.runZIO(
        Request.post("/preferences", Body.fromString("alice@example.com"))
          .addHeader("Origin", dashboardOrigin)
          .addHeader("Content-Type", "text/plain")
          .addHeader("x-request-id", "preferences-42")
      )
      receipt <- update.body.asString
      _ <- ZIO.succeed {
        assertEquals(Status.Ok, update.status)
        assertEquals("Notifications will be sent to alice@example.com", receipt)
        assertEquals(Some(dashboardOrigin), update.rawHeader("Access-Control-Allow-Origin"))
        assertEquals(Some("true"), update.rawHeader("Access-Control-Allow-Credentials"))
        assertEquals(Set("x-request-id"), headerTokens(update, "Access-Control-Expose-Headers"))
        assertEquals(Some("preferences-42"), update.rawHeader("x-request-id"))
      }
      foreignOrigin <- routes.runZIO(preflightRequest("https://untrusted.example", "POST"))
      forbiddenMethod <- routes.runZIO(preflightRequest(dashboardOrigin, "DELETE"))
      _ <- ZIO.succeed {
        assertEquals(Status.NotFound, foreignOrigin.status)
        assertEquals(None, foreignOrigin.rawHeader("Access-Control-Allow-Origin"))
        assertEquals(Status.NotFound, forbiddenMethod.status)
        assertEquals(None, forbiddenMethod.rawHeader("Access-Control-Allow-Origin"))
      }
      // CORS is a browser policy, not a requirement on same-origin or non-browser clients.
      direct <- routes.runZIO(
        Request.post("/preferences", Body.fromString("bob@example.com"))
      )
      directReceipt <- direct.body.asString
      _ <- ZIO.succeed {
        assertEquals(Status.Ok, direct.status)
        assertEquals("Notifications will be sent to bob@example.com", directReceipt)
        assertEquals(None, direct.rawHeader("Access-Control-Allow-Origin"))
      }
    } yield ()

    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(ZIO.scoped(program)).getOrThrowFiberFailure()
    }
  }

  private def preflightRequest(origin: String, method: String): Request =
    Request.options("/preferences")
      .addHeader("Origin", origin)
      .addHeader("Access-Control-Request-Method", method)
      .addHeader("Access-Control-Request-Headers", "content-type,x-request-id")

  private def headerTokens(response: Response, name: String): Set[String] =
    response.rawHeader(name).toSet.flatMap(_.split(",").map(_.trim))
}
