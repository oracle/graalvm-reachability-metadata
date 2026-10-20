/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_dsl_3

import cats.effect.IO
import cats.effect.Ref
import cats.effect.Resource
import cats.effect.unsafe.implicits.global
import com.comcast.ip4s.*
import org.http4s.HttpDate
import org.http4s.HttpRoutes
import org.http4s.MediaType
import org.http4s.Method
import org.http4s.Request
import org.http4s.Response
import org.http4s.ResponseCookie
import org.http4s.SameSite
import org.http4s.Status
import org.http4s.Uri
import org.http4s.UrlForm
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.Location
import org.http4s.headers.`Content-Type`
import org.http4s.implicits.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * HTML form submission and cookie-based sessions handled by http4s DSL routes.
 *
 * A member portal serves an HTML login page, decodes `application/x-www-form-urlencoded` posts
 * into `UrlForm` with `decodeStrict`, and on valid credentials answers `303 See Other` with a
 * session cookie set through `addCookie`. Later requests are recognised by the cookie the browser
 * sends back, a preferences form carries a repeated field, and logout clears the cookie with
 * `removeCookie`. Missing fields, wrong passwords, a body of the wrong media type, and requests
 * without a session are answered as a browser-facing application would. The portal runs on an
 * Ember server bound to an ephemeral port and is driven by an Ember client that, like a browser,
 * echoes the session cookie it received.
 */
class MemberPortalFormSessionTest {
  import MemberPortalFormSessionTest.*

  @Test
  def loginFormIssuesSessionCookieThatLaterRequestsPresent(): Unit = {
    val outcome: Observed = serveAndCall { (client: Client[IO], base: Uri) =>
      for {
        loginPage <- client.run(Request[IO](Method.GET, base / "login")).use { response =>
          response.as[String].map(body => (response.status, response.contentType, body))
        }
        anonymous <- redirectOf(client, Request[IO](Method.GET, base / "account"))
        missingField <- call(client, Request[IO](Method.POST, base / "login").withEntity(UrlForm("user" -> "ada")))
        wrongPassword <- call(
          client,
          Request[IO](Method.POST, base / "login").withEntity(UrlForm("user" -> "ada", "password" -> "guess"))
        )
        wrongMediaType <- call(
          client,
          Request[IO](Method.POST, base / "login").withEntity("user=ada&password=engine")
        )
        login <- client
          .run(
            Request[IO](Method.POST, base / "login")
              .withEntity(UrlForm("user" -> "ada", "password" -> "analytical engine"))
          )
          .use(response => IO.pure((response.status, response.headers.get[Location], response.cookies)))
        sessionToken = login._3.find(_.name == SessionCookie).map(_.content).getOrElse("")
        account <- call(client, Request[IO](Method.GET, base / "account").addCookie(SessionCookie, sessionToken))
        forged <- redirectOf(client, Request[IO](Method.GET, base / "account").addCookie(SessionCookie, "forged"))
        preferences <- call(
          client,
          Request[IO](Method.POST, base / "account" / "preferences")
            .addCookie(SessionCookie, sessionToken)
            .withEntity(
              UrlForm(
                "topic" -> "mathematics",
                "topic" -> "music & poetry",
                "digest" -> "weekly"
              )
            )
        )
        logout <- client
          .run(Request[IO](Method.POST, base / "logout").addCookie(SessionCookie, sessionToken))
          .use(response => IO.pure((response.status, response.headers.get[Location], response.cookies)))
        afterLogout <- redirectOf(
          client,
          Request[IO](Method.GET, base / "account").addCookie(SessionCookie, sessionToken)
        )
      } yield Observed(
        loginPage,
        anonymous,
        missingField,
        wrongPassword,
        wrongMediaType,
        login,
        account,
        forged,
        preferences,
        logout,
        afterLogout
      )
    }

    assertEquals(Status.Ok, outcome.loginPage._1)
    assertEquals(Some(MediaType.text.html), outcome.loginPage._2.map(_.mediaType))
    assertTrue(outcome.loginPage._3.contains("""<form method="post" action="/login">"""))
    assertEquals((Status.SeeOther, Some(Location(uri"/login"))), outcome.anonymous)
    assertEquals((Status.BadRequest, "user and password are required"), outcome.missingField)
    assertEquals((Status.Forbidden, "invalid credentials for ada"), outcome.wrongPassword)
    assertEquals(Status.UnsupportedMediaType, outcome.wrongMediaType._1)

    assertEquals(Status.SeeOther, outcome.login._1)
    assertEquals(Some(Location(uri"/account")), outcome.login._2)
    val issued: ResponseCookie = outcome.login._3.find(_.name == SessionCookie).get
    assertEquals("session-1", issued.content)
    assertEquals(Some("/"), issued.path)
    assertTrue(issued.httpOnly)
    assertEquals(SameSite.Strict, issued.sameSite.get)

    assertEquals((Status.Ok, "welcome back, ada"), outcome.account)
    assertEquals((Status.SeeOther, Some(Location(uri"/login"))), outcome.forged)
    assertEquals((Status.Ok, "ada follows mathematics, music & poetry (weekly digest)"), outcome.preferences)

    assertEquals(Status.SeeOther, outcome.logout._1)
    assertEquals(Some(Location(uri"/login")), outcome.logout._2)
    val cleared: ResponseCookie = outcome.logout._3.find(_.name == SessionCookie).get
    assertEquals("", cleared.content)
    assertEquals(Some(HttpDate.Epoch), cleared.expires)
    assertEquals((Status.SeeOther, Some(Location(uri"/login"))), outcome.afterLogout)
  }
}

object MemberPortalFormSessionTest {

  /** Name of the cookie that carries the session token. */
  val SessionCookie: String = "PORTAL_SESSION"

  /** Accounts the portal accepts, keyed by user name. */
  val Passwords: Map[String, String] = Map("ada" -> "analytical engine", "charles" -> "difference engine")

  /** What the client saw at each step of the portal visit. */
  final case class Observed(
      loginPage: (Status, Option[`Content-Type`], String),
      anonymous: (Status, Option[Location]),
      missingField: (Status, String),
      wrongPassword: (Status, String),
      wrongMediaType: (Status, String),
      login: (Status, Option[Location], List[ResponseCookie]),
      account: (Status, String),
      forged: (Status, Option[Location]),
      preferences: (Status, String),
      logout: (Status, Option[Location], List[ResponseCookie]),
      afterLogout: (Status, Option[Location])
  )

  /** Open sessions keyed by token; tokens are numbered so the issued value is predictable. */
  final case class Sessions(issued: Int, users: Map[String, String])

  val LoginPage: String =
    """<!doctype html>
      |<html><body>
      |<form method="post" action="/login">
      |<input name="user"><input name="password" type="password"><button>Sign in</button>
      |</form>
      |</body></html>""".stripMargin

  /** The portal routes an application would mount at the root of its site. */
  def portalRoutes(sessions: Ref[IO, Sessions]): HttpRoutes[IO] = {
    def userOf(request: Request[IO]): IO[Option[String]] =
      request.cookies.find(_.name == SessionCookie) match {
        case Some(cookie) => sessions.get.map(_.users.get(cookie.content))
        case None => IO.pure(None)
      }

    def withUser(request: Request[IO])(respond: String => IO[Response[IO]]): IO[Response[IO]] =
      userOf(request).flatMap(_.fold(SeeOther(Location(uri"/login")))(respond))

    HttpRoutes.of[IO] {
      case GET -> Root / "login" =>
        Ok(LoginPage, `Content-Type`(MediaType.text.html))

      case request @ POST -> Root / "login" =>
        request.decodeStrict[UrlForm] { form =>
          (form.getFirst("user"), form.getFirst("password")) match {
            case (Some(user), Some(password)) if Passwords.get(user).contains(password) =>
              for {
                token <- sessions.modify { state =>
                  val token: String = s"session-${state.issued + 1}"
                  (Sessions(state.issued + 1, state.users + (token -> user)), token)
                }
                redirect <- SeeOther(Location(uri"/account"))
              } yield redirect.addCookie(
                ResponseCookie(
                  SessionCookie,
                  token,
                  path = Some("/"),
                  httpOnly = true,
                  sameSite = Some(SameSite.Strict)
                )
              )
            case (Some(user), Some(_)) => Forbidden(s"invalid credentials for $user")
            case _ => BadRequest("user and password are required")
          }
        }

      case request @ GET -> Root / "account" =>
        withUser(request)(user => Ok(s"welcome back, $user"))

      case request @ POST -> Root / "account" / "preferences" =>
        withUser(request) { user =>
          request.decodeStrict[UrlForm] { form =>
            val topics: List[String] = form.get("topic").toList
            val digest: String = form.getFirstOrElse("digest", "no")
            Ok(s"$user follows ${topics.mkString(", ")} ($digest digest)")
          }
        }

      case request @ POST -> Root / "logout" =>
        val token: Option[String] = request.cookies.find(_.name == SessionCookie).map(_.content)
        for {
          _ <- sessions.update(state => state.copy(users = token.fold(state.users)(state.users - _)))
          redirect <- SeeOther(Location(uri"/login"))
        } yield redirect.removeCookie(SessionCookie)
    }
  }

  /** Starts the portal on an ephemeral loopback port and runs `scenario` against it. */
  def serveAndCall[A](scenario: (Client[IO], Uri) => IO[A]): A = {
    val running: Resource[IO, (Client[IO], Uri)] = for {
      sessions <- Resource.eval(Ref.of[IO, Sessions](Sessions(0, Map.empty)))
      server <- EmberServerBuilder
        .default[IO]
        .withHost(ipv4"127.0.0.1")
        .withPort(port"0")
        .withHttpApp(portalRoutes(sessions).orNotFound)
        .build
      client <- EmberClientBuilder.default[IO].build
    } yield (client, Uri.unsafeFromString(s"http://127.0.0.1:${server.address.getPort}"))
    running.use((client, base) => scenario(client, base)).unsafeRunSync()
  }

  /** Sends `request` and reads the whole response body as text. */
  def call(client: Client[IO], request: Request[IO]): IO[(Status, String)] =
    client.run(request).use(response => response.as[String].map(body => (response.status, body)))

  /** Sends `request` and reports where the portal redirected it. */
  def redirectOf(client: Client[IO], request: Request[IO]): IO[(Status, Option[Location])] =
    client.run(request).use(response => IO.pure((response.status, response.headers.get[Location])))
}
