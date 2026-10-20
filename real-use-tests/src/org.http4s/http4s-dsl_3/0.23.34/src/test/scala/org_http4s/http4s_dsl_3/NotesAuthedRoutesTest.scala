/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_dsl_3

import cats.data.Kleisli
import cats.data.OptionT
import cats.effect.IO
import cats.effect.Ref
import cats.effect.Resource
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import com.comcast.ip4s.*
import org.http4s.AuthScheme
import org.http4s.AuthedRoutes
import org.http4s.Challenge
import org.http4s.Credentials
import org.http4s.HttpRoutes
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.Authorization
import org.http4s.headers.`WWW-Authenticate`
import org.http4s.implicits.*
import org.http4s.server.AuthMiddleware
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Authenticated routing with `AuthedRoutes` and the DSL `as` extractor behind an `AuthMiddleware`.
 *
 * A personal notes service resolves the caller from a bearer token before any route runs. Its
 * routes are written as `AuthedRoutes`, matched with `case ... as user` to receive the
 * authenticated account next to the request, and read the request body through
 * `AuthedRequest.req`. Missing or unknown tokens are answered by the middleware's failure routes
 * with `401 Unauthorized` and a `WWW-Authenticate` challenge, an administrator-only route refuses
 * other users with `403 Forbidden`, and a public health route stays reachable without a token.
 * The service runs on an Ember server and is called by an Ember client.
 */
class NotesAuthedRoutesTest {
  import NotesAuthedRoutesTest.*

  @Test
  def notesServiceAuthenticatesCallersAndScopesDataPerUser(): Unit = {
    val outcome: Observed = serveAndCall { (client: Client[IO], base: Uri) =>
      val notes: Uri = base / "notes"
      def signed(token: String, request: Request[IO]): Request[IO] =
        request.putHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, token)))

      for {
        health <- call(client, Request[IO](Method.GET, base / "health"))
        anonymous <- client.run(Request[IO](Method.GET, notes)).use { response =>
          response.as[String].map { body =>
            (response.status, response.headers.get[`WWW-Authenticate`].map(_.values.head.scheme), body)
          }
        }
        forged <- call(client, signed("forged", Request[IO](Method.GET, notes)))
        _ <- call(client, signed(aliceToken, Request[IO](Method.POST, notes).withEntity("buy milk")))
        _ <- call(client, signed(aliceToken, Request[IO](Method.POST, notes).withEntity("call bob")))
        bobAdded <- call(client, signed(bobToken, Request[IO](Method.POST, notes).withEntity("fix bike")))
        aliceNotes <- call(client, signed(aliceToken, Request[IO](Method.GET, notes)))
        bobNotes <- call(client, signed(bobToken, Request[IO](Method.GET, notes)))
        whoAmI <- call(client, signed(bobToken, Request[IO](Method.GET, base / "me")))
        bobAudit <- call(client, signed(bobToken, Request[IO](Method.GET, base / "admin" / "notes")))
        aliceAudit <- call(client, signed(aliceToken, Request[IO](Method.GET, base / "admin" / "notes")))
        unknownRoute <- call(client, signed(aliceToken, Request[IO](Method.GET, base / "settings")))
      } yield Observed(
        health,
        anonymous,
        forged,
        bobAdded,
        aliceNotes,
        bobNotes,
        whoAmI,
        bobAudit,
        aliceAudit,
        unknownRoute._1
      )
    }

    assertEquals((Status.Ok, "up"), outcome.health)
    assertEquals((Status.Unauthorized, Some("Bearer"), "missing bearer token"), outcome.anonymous)
    assertEquals((Status.Unauthorized, "unknown token"), outcome.forged)
    assertEquals((Status.Created, "note 1 for bob"), outcome.bobAdded)
    assertEquals((Status.Ok, "buy milk\ncall bob"), outcome.aliceNotes)
    assertEquals((Status.Ok, "fix bike"), outcome.bobNotes)
    assertEquals((Status.Ok, "bob (member)"), outcome.whoAmI)
    assertEquals((Status.Forbidden, "bob is not an administrator"), outcome.bobAudit)
    assertEquals((Status.Ok, "alice: 2\nbob: 1"), outcome.aliceAudit)
    assertEquals(Status.NotFound, outcome.unknownRoute)
  }
}

object NotesAuthedRoutesTest {

  /** What the client saw for each call. */
  final case class Observed(
      health: (Status, String),
      anonymous: (Status, Option[String], String),
      forged: (Status, String),
      bobAdded: (Status, String),
      aliceNotes: (Status, String),
      bobNotes: (Status, String),
      whoAmI: (Status, String),
      bobAudit: (Status, String),
      aliceAudit: (Status, String),
      unknownRoute: Status
  )

  /** An authenticated account. */
  final case class User(name: String, admin: Boolean)

  val aliceToken: String = "tok-alice-7f3a"
  val bobToken: String = "tok-bob-91c2"

  /** The token directory the middleware consults. */
  val accounts: Map[String, User] = Map(
    aliceToken -> User("alice", admin = true),
    bobToken -> User("bob", admin = false)
  )

  /** Resolves the caller from the `Authorization` header or explains why it cannot. */
  val authenticate: Kleisli[IO, Request[IO], Either[String, User]] = Kleisli { (request: Request[IO]) =>
    IO.pure {
      request.headers.get[Authorization] match {
        case Some(Authorization(Credentials.Token(AuthScheme.Bearer, token))) =>
          accounts.get(token).toRight("unknown token")
        case _ => Left("missing bearer token")
      }
    }
  }

  /** Answers every failed authentication with a bearer challenge and the reason. */
  val onFailure: AuthedRoutes[String, IO] = Kleisli { authed =>
    OptionT.liftF(Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "notes")), authed.context))
  }

  val requireUser: AuthMiddleware[IO, User] = AuthMiddleware(authenticate, onFailure)

  /** The notes API, which only ever sees authenticated callers. */
  def notesRoutes(store: Ref[IO, Map[String, Vector[String]]]): AuthedRoutes[User, IO] =
    AuthedRoutes.of[User, IO] {
      case GET -> Root / "notes" as user =>
        store.get.flatMap(all => Ok(all.getOrElse(user.name, Vector.empty).mkString("\n")))
      case authed @ POST -> Root / "notes" as user =>
        for {
          text <- authed.req.as[String]
          count <- store.modify { all =>
            val updated: Vector[String] = all.getOrElse(user.name, Vector.empty) :+ text
            (all.updated(user.name, updated), updated.size)
          }
          response <- Created(s"note $count for ${user.name}")
        } yield response
      case GET -> Root / "me" as user =>
        Ok(s"${user.name} (${if (user.admin) "administrator" else "member"})")
      case GET -> Root / "admin" / "notes" as user =>
        if (!user.admin) Forbidden(s"${user.name} is not an administrator")
        else
          store.get.flatMap { all =>
            Ok(all.toList.sortBy(_._1).map((name, list) => s"$name: ${list.size}").mkString("\n"))
          }
    }

  /** Routes that need no credentials. */
  val publicRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] { case GET -> Root / "health" =>
    Ok("up")
  }

  /** Starts the service on an ephemeral loopback port and runs `scenario` against it. */
  def serveAndCall[A](scenario: (Client[IO], Uri) => IO[A]): A = {
    val running: Resource[IO, (Client[IO], Uri)] = for {
      store <- Resource.eval(Ref.of[IO, Map[String, Vector[String]]](Map.empty))
      server <- EmberServerBuilder
        .default[IO]
        .withHost(ipv4"127.0.0.1")
        .withPort(port"0")
        .withHttpApp((publicRoutes <+> requireUser(notesRoutes(store))).orNotFound)
        .build
      client <- EmberClientBuilder.default[IO].build
    } yield (client, Uri.unsafeFromString(s"http://127.0.0.1:${server.address.getPort}"))
    running.use((client, base) => scenario(client, base)).unsafeRunSync()
  }

  /** Sends `request` and reads the whole response body as text. */
  def call(client: Client[IO], request: Request[IO]): IO[(Status, String)] =
    client.run(request).use(response => response.as[String].map(body => (response.status, body)))
}
