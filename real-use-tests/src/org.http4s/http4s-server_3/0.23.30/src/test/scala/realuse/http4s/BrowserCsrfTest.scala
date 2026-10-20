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
import org.http4s.{Header, HttpApp, HttpRoutes, Method, Request, RequestCookie, Status, Uri}
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import org.http4s.server.middleware.CSRF
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import org.typelevel.ci.*

/** Exercises CSRF middleware protecting browser profile updates with signed double-submit cookies. */
class BrowserCsrfTest {
  @Test
  @Timeout(60)
  def acceptsSameOriginUpdatesOnlyWithMatchingSignedTokens(): Unit = {
    val origin: String = "https://accounts.example.test"
    val endpoint: Uri = Uri.unsafeFromString("/profile/display-name")
    val update: Request[IO] = Request[IO](method = Method.POST, uri = endpoint)
      .putHeaders(Header.Raw(ci"Origin", origin))
      .withEntity("Alice Cooper")

    def withCookie(request: Request[IO], token: String): Request[IO] =
      request.addCookie(RequestCookie("csrf-token", token))

    def withToken(request: Request[IO], token: String): Request[IO] =
      request.putHeaders(Header.Raw(ci"X-Csrf-Token", token))

    val checks: IO[Unit] = for {
      profileName <- Ref.of[IO, String]("Alice")
      signingKey <- CSRF.generateSigningKey[IO]()
      csrf: CSRF[IO, IO] = CSRF.withDefaultOriginCheck[IO, IO](
        signingKey, "accounts.example.test", Uri.Scheme.https, None
      ).withCookieSecure(true).withCookieHttpOnly(false).build
      routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
        case GET -> Root / "profile" / "display-name" => profileName.get.flatMap(Ok(_))
        case request @ POST -> Root / "profile" / "display-name" =>
          request.as[String].flatMap(name => profileName.set(name) *> Ok(s"Saved: $name"))
      }
      application: HttpApp[IO] = csrf.validate()(routes.orNotFound)
      page <- application.run(Request[IO](uri = endpoint))
      initialName <- page.as[String]
      cookie <- IO.fromOption(page.cookies.find(_.name == "csrf-token"))(
        new IllegalStateException("The profile page must issue a CSRF cookie")
      )
      otherBrowser <- application.run(Request[IO](uri = endpoint))
      otherCookie <- IO.fromOption(otherBrowser.cookies.find(_.name == "csrf-token"))(
        new IllegalStateException("A second browser must receive its own CSRF cookie")
      )
      authenticatedUpdate: Request[IO] = withToken(withCookie(update, cookie.content), cookie.content)
      rejected <- List(
        update,
        withCookie(update, cookie.content),
        withToken(update, cookie.content),
        withToken(withCookie(update, cookie.content), otherCookie.content),
        withToken(withCookie(update, "forged-token"), "forged-token"),
        authenticatedUpdate.putHeaders(Header.Raw(ci"Origin", "https://untrusted.example.test"))
      ).traverse(application.run)
      nameAfterRejections <- profileName.get
      accepted <- application.run(authenticatedUpdate)
      confirmation <- accepted.as[String]
      refreshedCookie <- IO.fromOption(accepted.cookies.find(_.name == "csrf-token"))(
        new IllegalStateException("A successful update must refresh the CSRF cookie")
      )
      reloaded <- application.run(withCookie(Request[IO](uri = endpoint), refreshedCookie.content))
      savedName <- reloaded.as[String]
    } yield {
      assertEquals(Status.Ok, page.status)
      assertEquals("Alice", initialName)
      assertTrue(cookie.secure)
      assertFalse(cookie.httpOnly)
      assertEquals(Some("/"), cookie.path)
      rejected.foreach(response => assertEquals(Status.Forbidden, response.status))
      assertEquals("Alice", nameAfterRejections)
      assertEquals(Status.Ok, accepted.status)
      assertEquals("Saved: Alice Cooper", confirmation)
      assertEquals(Status.Ok, reloaded.status)
      assertEquals("Alice Cooper", savedName)
    }

    checks.unsafeRunSync()
  }
}
