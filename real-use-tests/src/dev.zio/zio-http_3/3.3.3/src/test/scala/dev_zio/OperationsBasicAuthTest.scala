/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import zio.{Config, Ref, Runtime, Scope, Unsafe, ZIO}
import zio.http.*

/** Exercises ZIO HTTP Basic authentication middleware for a protected operations API. */
class OperationsBasicAuthTest {
  @Test
  @Timeout(60)
  def protectsBannerUpdatesAndHonorsCredentialRevocation(): Unit = {
    val password: String = "operations-test-password"
    val program: ZIO[Scope, Throwable, Unit] = for {
      accounts <- Ref.make(Map("operator" -> Config.Secret(password)))
      banner <- Ref.make("All services operational")
      routes = operationsApi(accounts, banner)
      anonymous <- routes.runZIO(updateRequest("Anonymous update"))
      _ <- assertChallenge(anonymous)
      wrongPassword <- routes.runZIO(
        updateRequest("Wrong password update")
          .addHeader(Header.Authorization.Basic("operator", "incorrect"))
      )
      _ <- assertChallenge(wrongPassword)
      malformed <- routes.runZIO(
        updateRequest("Malformed credential update").addHeader("Authorization", "Basic !!!")
      )
      _ <- assertChallenge(malformed)
      _ <- assertPublicBanner(routes, "All services operational")
      authorized <- routes.runZIO(
        updateRequest("Scheduled database maintenance at 22:00 UTC")
          .addHeader(Header.Authorization.Basic("operator", password))
      )
      receipt <- authorized.body.asString
      _ <- ZIO.succeed {
        assertEquals(Status.Ok, authorized.status)
        assertEquals("Banner updated", receipt)
        assertEquals(None, authorized.rawHeader("WWW-Authenticate"))
      }
      _ <- assertPublicBanner(routes, "Scheduled database maintenance at 22:00 UTC")
      // Disabling an account must affect the next request, not just newly built routes.
      _ <- accounts.update(_ - "operator")
      revoked <- routes.runZIO(
        updateRequest("Revoked account update")
          .addHeader(Header.Authorization.Basic("operator", password))
      )
      _ <- assertChallenge(revoked)
      _ <- assertPublicBanner(routes, "Scheduled database maintenance at 22:00 UTC")
    } yield ()

    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(ZIO.scoped(program)).getOrThrowFiberFailure()
    }
  }

  private def operationsApi(
      accounts: Ref[Map[String, Config.Secret]],
      banner: Ref[String]
  ): Routes[Any, Response] = {
    val authentication: HandlerAspect[Any, Unit] = HandlerAspect.basicAuthZIO { credentials =>
      accounts.get.map(_.get(credentials.uname).contains(credentials.upassword))
    }
    val protectedRoutes: Routes[Any, Response] = Routes(
      Method.PUT / "operations" / "banner" -> handler { (request: Request) =>
        request.body.asString
          .mapError(_ => Response.badRequest)
          .flatMap(message => banner.set(message).as(Response.text("Banner updated")))
      }
    ) @@ authentication
    val publicRoutes: Routes[Any, Nothing] = Routes(
      Method.GET / "status" -> handler {
        banner.get.map(Response.text(_))
      }
    )
    publicRoutes ++ protectedRoutes
  }

  private def updateRequest(message: String): Request =
    // In deployment, the reverse proxy terminates TLS before these routes.
    Request.put("https://status.example/operations/banner", Body.fromString(message))

  private def assertChallenge(response: Response): ZIO[Any, Throwable, Unit] =
    for {
      body <- response.body.asString
      _ <- ZIO.succeed {
        assertEquals(Status.Unauthorized, response.status)
        assertTrue(response.rawHeader("WWW-Authenticate").exists(_.startsWith("Basic")))
        assertEquals("", body)
      }
    } yield ()

  private def assertPublicBanner(
      routes: Routes[Any, Response],
      expected: String
  ): ZIO[Scope, Throwable, Unit] =
    for {
      response <- routes.runZIO(Request.get("https://status.example/status"))
      body <- response.body.asString
      _ <- ZIO.succeed {
        assertEquals(Status.Ok, response.status)
        assertEquals(expected, body)
      }
    } yield ()
}
