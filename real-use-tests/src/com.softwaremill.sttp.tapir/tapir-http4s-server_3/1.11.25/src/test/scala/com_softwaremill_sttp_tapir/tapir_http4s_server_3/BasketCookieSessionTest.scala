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
import sttp.model.StatusCode
import sttp.model.headers.{Cookie, CookieValueWithMeta, CookieWithMeta}
import sttp.tapir.*
import sttp.tapir.server.http4s.Http4sServerInterpreter

import scala.concurrent.duration.*

/** Exercises cookie decoding and Set-Cookie encoding for basket sessions in Tapir's http4s interpreter. */
class BasketCookieSessionTest {
  @Test
  @Timeout(60)
  def issuesUsesAndRevokesABrowserBasketSession(): Unit = {
    val sessionCookie: CookieValueWithMeta = CookieValueWithMeta.unsafeApply(
      "basket-1",
      maxAge = Some(1800L),
      path = Some("/basket"),
      secure = true,
      httpOnly = true,
      sameSite = Some(Cookie.SameSite.Lax)
    )
    val create: PublicEndpoint[Unit, Unit, (CookieValueWithMeta, String), Any] = endpoint.post
      .in("basket")
      .out(statusCode(StatusCode.Created))
      .out(setCookie("basket-session"))
      .out(stringBody)
    val view: PublicEndpoint[Option[String], String, String, Any] = endpoint.get
      .in("basket")
      .in(cookie[Option[String]]("basket-session"))
      .errorOut(statusCode(StatusCode.NotFound))
      .errorOut(stringBody)
      .out(stringBody)
    val discard: PublicEndpoint[String, Unit, CookieValueWithMeta, Any] = endpoint.delete
      .in("basket")
      .in(cookie[String]("basket-session"))
      .out(statusCode(StatusCode.NoContent))
      .out(setCookie("basket-session"))

    val scenario: IO[Unit] = for {
      baskets <- Ref.of[IO, Map[String, String]](Map.empty)
      createRoute = create.serverLogicSuccess[IO] { (_: Unit) =>
        baskets.update(_ + (sessionCookie.value -> "Your basket has 0 items"))
          .as((sessionCookie, "Basket created"))
      }
      viewRoute = view.serverLogic[IO] { (token: Option[String]) =>
        baskets.get.map { saved => token.flatMap(saved.get).toRight("No active basket") }
      }
      discardRoute = discard.serverLogicSuccess[IO] { (token: String) =>
        baskets.update(_ - token).as(sessionCookie.copy(value = "", maxAge = Some(0L)))
      }
      app = Http4sServerInterpreter[IO]().toRoutes(List(createRoute, viewRoute, discardRoute)).orNotFound
      anonymous <- app.run(Request[IO](Method.GET, uri"/basket"))
      anonymousBody <- anonymous.as[String]
      _ <- IO {
        assertEquals(404, anonymous.status.code)
        assertEquals("No active basket", anonymousBody)
      }
      created <- app.run(Request[IO](Method.POST, uri"/basket"))
      createdBody <- created.as[String]
      issued <- IO(readCookie(created))
      _ <- IO {
        assertEquals(201, created.status.code)
        assertEquals("Basket created", createdBody)
        assertEquals("basket-session", issued.name)
        assertEquals(sessionCookie, issued.valueWithMeta)
        assertTrue(issued.secure)
        assertTrue(issued.httpOnly)
      }
      browserCookie = Header.Raw(CIString("Cookie"), s"theme=dark; ${issued.name}=${issued.value}")
      current <- app.run(Request[IO](Method.GET, uri"/basket").putHeaders(browserCookie))
      currentBody <- current.as[String]
      _ <- IO {
        assertEquals(200, current.status.code)
        assertEquals("Your basket has 0 items", currentBody)
      }
      discarded <- app.run(Request[IO](Method.DELETE, uri"/basket").putHeaders(browserCookie))
      _ <- discarded.body.compile.drain
      cleared <- IO(readCookie(discarded))
      _ <- IO {
        assertEquals(204, discarded.status.code)
        assertEquals("basket-session", cleared.name)
        assertEquals("", cleared.value)
        assertEquals(Some(0L), cleared.maxAge)
        assertEquals(issued.path, cleared.path)
      }
      replayed <- app.run(Request[IO](Method.GET, uri"/basket").putHeaders(browserCookie))
      replayedBody <- replayed.as[String]
      _ <- IO {
        assertEquals(404, replayed.status.code)
        assertEquals("No active basket", replayedBody)
      }
    } yield ()

    scenario.timeout(45.seconds).unsafeRunSync()
  }

  private def readCookie(response: Response[IO]): CookieWithMeta = {
    val header: Option[String] = response.headers.headers
      .find(_.name == CIString("Set-Cookie")).map(_.value)
    assertTrue(header.isDefined, "The browser must receive a Set-Cookie header")
    CookieWithMeta.unsafeParse(header.get)
  }
}
