/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_zio_3

import scala.concurrent.duration.DurationInt

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue}
import org.junit.jupiter.api.Test
import sttp.client4.*
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.model.headers.{Cookie, CookieValueWithMeta, CookieWithMeta}
import sttp.model.{Header, Method, StatusCode}
import sttp.tapir.Schema
import sttp.tapir.json.zio.*
import sttp.tapir.server.interceptor.cors.{CORSConfig, CORSInterceptor}
import sttp.tapir.server.ziohttp.{ZioHttpInterpreter, ZioHttpServerOptions}
import sttp.tapir.ztapir.*
import zio.http.Server
import zio.json.{DecoderOps, DeriveJsonCodec, JsonCodec}
import zio.{IO, Ref, Runtime, Task, UIO, Unsafe, ZIO, ZLayer}

/**
 * Cross-origin cookie sessions through the CORS interceptor that `ZioHttpServerOptions` installs.
 *
 * A storefront API is called from browser pages on its own `*.shop.example` origins. It signs a
 * shopper in with a `setCookie` output, reads the session back through a `cookie` input in
 * `zServerLogic` that takes its carts from the ZIO environment, exposes the cart version header
 * to scripts and signs out by expiring the cookie. `CORSInterceptor.customOrThrow` allows only
 * the storefront origins with credentials, a fixed method and header list and a ten-minute
 * preflight cache. The test sends the requests a browser would send, preflights included, and
 * checks the status, CORS headers, cookies and bodies a page would observe.
 */
class StorefrontZioCorsCookieSessionTest {
  import StorefrontZioCorsCookieSessionTest.*

  @Test
  def corsInterceptorGuardsCredentialedCookieSessionsFromStorefrontOrigins(): Unit = {
    val program: ZIO[Server & Carts, Throwable, Replies] = for {
      port <- Server.install(ZioHttpInterpreter(serverOptions).toHttp(serverEndpoints))
      replies <- ZIO.scoped(HttpClientZioBackend.scoped().flatMap(backend => exercise(port, backend)))
    } yield replies

    val replies: Replies = Unsafe.unsafe { implicit u =>
      Runtime.default.unsafe.run(program.provide(Server.defaultWithPort(0), InMemoryCarts.layer))
        .getOrThrowFiberFailure()
    }

    val preflight: Reply = replies.preflight
    assertEquals(StatusCode.NoContent, preflight.code)
    assertEquals(Some(Storefront), preflight.header("Access-Control-Allow-Origin"))
    assertEquals(Some("true"), preflight.header("Access-Control-Allow-Credentials"))
    assertEquals(Set("GET", "POST", "PUT", "DELETE"), preflight.list("Access-Control-Allow-Methods"))
    assertEquals(Set("content-type", "x-request-id"), preflight.list("Access-Control-Allow-Headers").map(_.toLowerCase))
    assertEquals(Some("600"), preflight.header("Access-Control-Max-Age"))
    assertTrue(preflight.list("Vary").contains("Origin"), preflight.toString)
    assertEquals("", preflight.body)

    val foreignPreflight: Reply = replies.foreignPreflight
    assertEquals(None, foreignPreflight.header("Access-Control-Allow-Origin"))

    val signIn: Reply = replies.signIn
    assertEquals(StatusCode.Ok, signIn.code)
    assertEquals(Shopper("ada", 0), decode[Shopper](signIn.body))
    assertEquals(Some(Checkout), signIn.header("Access-Control-Allow-Origin"))
    assertEquals(Some("true"), signIn.header("Access-Control-Allow-Credentials"))
    val session: CookieWithMeta = sessionCookie(signIn)
    assertEquals("sid-ada-1", session.value)
    assertEquals(Some("/"), session.path)
    assertTrue(session.httpOnly, session.toString)
    assertTrue(session.secure, session.toString)
    assertEquals(Some(Cookie.SameSite.None), session.sameSite)

    assertEquals(StatusCode.Ok, replies.addApple.code)
    val added: Reply = replies.addPear
    assertEquals(StatusCode.Ok, added.code)
    assertEquals(Cart("ada", List(Line("apple", 2), Line("pear", 1)), 2), decode[Cart](added.body))
    assertEquals(Some("2"), added.header("X-Cart-Version"))
    assertEquals(Set("X-Cart-Version"), added.list("Access-Control-Expose-Headers"))
    assertEquals(Some(Storefront), added.header("Access-Control-Allow-Origin"))

    // The browser enforces CORS: the server answers, but without an allowed origin the page cannot read it.
    val foreignRead: Reply = replies.foreignRead
    assertEquals(StatusCode.Ok, foreignRead.code)
    assertEquals(2, decode[Cart](foreignRead.body).lines.size)
    assertEquals(None, foreignRead.header("Access-Control-Allow-Origin"))

    val anonymous: Reply = replies.anonymous
    assertEquals(StatusCode.Unauthorized, anonymous.code)
    assertEquals(Problem("signed-out", "no session"), decode[Problem](anonymous.body))
    assertEquals(Some(Storefront), anonymous.header("Access-Control-Allow-Origin"))

    val signOut: Reply = replies.signOut
    assertEquals(StatusCode.NoContent, signOut.code)
    val expired: CookieWithMeta = sessionCookie(signOut)
    assertEquals("", expired.value)
    assertEquals(Some(0L), expired.maxAge)
    assertEquals(Some(Storefront), signOut.header("Access-Control-Allow-Origin"))

    val afterSignOut: Reply = replies.afterSignOut
    assertEquals(StatusCode.Unauthorized, afterSignOut.code)
    assertEquals(Problem("signed-out", "unknown session"), decode[Problem](afterSignOut.body))
    assertFalse(afterSignOut.headers.exists(_.is("Set-Cookie")), afterSignOut.toString)
  }

  private def exercise(port: Int, backend: Backend[Task]): Task[Replies] = {
    val base: String = s"http://localhost:$port"
    def send(request: Request[Either[String, String]]): Task[Reply] =
      request.send(backend).map(r => Reply(r.code, r.headers.toList, r.body.fold(identity, identity)))
    def preflight(origin: String): Task[Reply] =
      send(
        basicRequest.options(uri"$base/cart/items/apple").header("Origin", origin)
          .header("Access-Control-Request-Method", "PUT")
          .header("Access-Control-Request-Headers", "content-type, x-request-id")
      )
    def addItem(sid: String, sku: String, quantity: Int): Task[Reply] =
      send(
        basicRequest.put(uri"$base/cart/items/$sku").header("Origin", Storefront).cookie(SessionCookie, sid)
          .contentType("application/json").body(s"""{"quantity":$quantity}""")
      )
    def readCart(origin: String, sid: Option[String]): Task[Reply] = {
      val request = basicRequest.get(uri"$base/cart").header("Origin", origin)
      send(sid.fold(request)(request.cookie(SessionCookie, _)))
    }
    for {
      preflightReply <- preflight(Storefront)
      foreignPreflight <- preflight(Foreign)
      signIn <- send(
        basicRequest.post(uri"$base/session").header("Origin", Checkout).contentType("application/json")
          .body("""{"shopper":"ada"}""")
      )
      sid = sessionCookie(signIn).value
      addApple <- addItem(sid, "apple", 2)
      addPear <- addItem(sid, "pear", 1)
      foreignRead <- readCart(Foreign, Some(sid))
      anonymous <- readCart(Storefront, None)
      signOut <- send(basicRequest.delete(uri"$base/session").header("Origin", Storefront).cookie(SessionCookie, sid))
      afterSignOut <- readCart(Storefront, Some(sid))
    } yield Replies(preflightReply, foreignPreflight, signIn, addApple, addPear, foreignRead, anonymous, signOut,
      afterSignOut)
  }

  private def sessionCookie(reply: Reply): CookieWithMeta = {
    val cookies: List[CookieWithMeta] = reply.headers.filter(_.is("Set-Cookie"))
      .map(h => CookieWithMeta.parse(h.value).fold(e => throw new AssertionError(e), identity))
    assertEquals(List(SessionCookie), cookies.map(_.name), reply.toString)
    cookies.head
  }

  private def decode[A: JsonCodec](body: String): A =
    body.fromJson[A].fold(error => throw new AssertionError(s"$error in $body"), identity)
}

object StorefrontZioCorsCookieSessionTest {
  val Storefront: String = "https://www.shop.example"
  val Checkout: String = "https://checkout.shop.example"
  val Foreign: String = "https://coupons.example"
  val SessionCookie: String = "SID"

  final case class SignIn(shopper: String) derives Schema
  final case class Shopper(name: String, items: Int) derives Schema
  final case class Quantity(quantity: Int) derives Schema
  final case class Line(sku: String, quantity: Int) derives Schema
  final case class Cart(shopper: String, lines: List[Line], version: Int) derives Schema
  final case class Problem(kind: String, detail: String) derives Schema

  given JsonCodec[SignIn] = DeriveJsonCodec.gen[SignIn]
  given JsonCodec[Shopper] = DeriveJsonCodec.gen[Shopper]
  given JsonCodec[Quantity] = DeriveJsonCodec.gen[Quantity]
  given JsonCodec[Line] = DeriveJsonCodec.gen[Line]
  given JsonCodec[Cart] = DeriveJsonCodec.gen[Cart]
  given JsonCodec[Problem] = DeriveJsonCodec.gen[Problem]

  /** One response as a browser receives it, before it applies the CORS rules. */
  final case class Reply(code: StatusCode, headers: List[Header], body: String) {
    def header(name: String): Option[String] = headers.find(_.is(name)).map(_.value)

    /** The comma-separated values of every header with this name. */
    def list(name: String): Set[String] =
      headers.filter(_.is(name)).flatMap(_.value.split(",")).map(_.trim).filter(_.nonEmpty).toSet
  }

  final case class Replies(
      preflight: Reply,
      foreignPreflight: Reply,
      signIn: Reply,
      addApple: Reply,
      addPear: Reply,
      foreignRead: Reply,
      anonymous: Reply,
      signOut: Reply,
      afterSignOut: Reply
  )

  /** Sessions and the cart each one holds. */
  trait Carts {
    def open(shopper: String): UIO[String]
    def close(sid: String): UIO[Boolean]
    def cart(sid: String): IO[Problem, Cart]
    def put(sid: String, sku: String, quantity: Int): IO[Problem, Cart]
  }

  final class InMemoryCarts(sessions: Ref[Map[String, Cart]], issued: Ref[Int]) extends Carts {
    def open(shopper: String): UIO[String] =
      for {
        n <- issued.updateAndGet(_ + 1)
        sid = s"sid-$shopper-$n"
        _ <- sessions.update(_.updated(sid, Cart(shopper, Nil, 0)))
      } yield sid

    def close(sid: String): UIO[Boolean] = sessions.modify(s => (s.contains(sid), s - sid))

    def cart(sid: String): IO[Problem, Cart] =
      sessions.get.map(_.get(sid)).someOrFail(Problem("signed-out", "unknown session"))

    def put(sid: String, sku: String, quantity: Int): IO[Problem, Cart] =
      sessions.modify { s =>
        s.get(sid) match {
          case None => (Left(Problem("signed-out", "unknown session")), s)
          case Some(cart) =>
            val lines: List[Line] = cart.lines.filterNot(_.sku == sku) :+ Line(sku, quantity)
            val updated: Cart = cart.copy(lines = lines, version = cart.version + 1)
            (Right(updated), s.updated(sid, updated))
        }
      }.absolve
  }

  object InMemoryCarts {
    val layer: ZLayer[Any, Nothing, Carts] =
      ZLayer.fromZIO(Ref.make(Map.empty[String, Cart]).zipWith(Ref.make(0))(new InMemoryCarts(_, _)))
  }

  private val signedOut = statusCode(StatusCode.Unauthorized).and(jsonBody[Problem])

  private val signInEndpoint =
    endpoint.post.in("session").in(jsonBody[SignIn]).out(setCookie(SessionCookie)).out(jsonBody[Shopper])

  private val signOutEndpoint =
    endpoint.delete.in("session").in(cookie[Option[String]](SessionCookie)).out(statusCode(StatusCode.NoContent))
      .out(setCookie(SessionCookie))

  private val cartEndpoint =
    endpoint.get.in("cart").in(cookie[Option[String]](SessionCookie)).errorOut(signedOut)
      .out(header[String]("X-Cart-Version")).out(jsonBody[Cart])

  private val putItemEndpoint =
    endpoint.put.in("cart" / "items" / path[String]("sku")).in(cookie[Option[String]](SessionCookie))
      .in(jsonBody[Quantity]).errorOut(signedOut).out(header[String]("X-Cart-Version")).out(jsonBody[Cart])

  /** The session cookie a browser keeps for cross-origin calls to the API. */
  private def sessionValue(sid: String): CookieValueWithMeta =
    CookieValueWithMeta.unsafeApply(sid, path = Some("/"), secure = true, httpOnly = true, sameSite = Some(
      Cookie.SameSite.None))

  private def signedIn(sid: Option[String]): IO[Problem, String] =
    ZIO.fromOption(sid).orElseFail(Problem("signed-out", "no session"))

  val serverEndpoints: List[ZServerEndpoint[Carts, Any]] = List(
    signInEndpoint.zServerLogic[Carts] { in =>
      ZIO.serviceWithZIO[Carts](_.open(in.shopper)).map(sid => (sessionValue(sid), Shopper(in.shopper, 0)))
    },
    signOutEndpoint.zServerLogic[Carts] { sid =>
      ZIO.foreachDiscard(sid)(s => ZIO.serviceWithZIO[Carts](_.close(s)))
        .as(CookieValueWithMeta.unsafeApply("", maxAge = Some(0L), path = Some("/"), secure = true, httpOnly = true))
    },
    cartEndpoint.zServerLogic[Carts] { sid =>
      signedIn(sid).flatMap(s => ZIO.serviceWithZIO[Carts](_.cart(s))).map(c => (c.version.toString, c))
    },
    putItemEndpoint.zServerLogic[Carts] { (sku, sid, quantity) =>
      signedIn(sid).flatMap(s => ZIO.serviceWithZIO[Carts](_.put(s, sku, quantity.quantity)))
        .map(c => (c.version.toString, c))
    }
  )

  private val corsConfig: CORSConfig =
    CORSConfig.default
      .allowMatchingOrigins(origin => origin.startsWith("https://") && origin.endsWith(".shop.example"))
      .allowCredentials
      .allowMethods(Method.GET, Method.POST, Method.PUT, Method.DELETE)
      .allowHeaders("Content-Type", "X-Request-Id")
      .exposeHeaders("X-Cart-Version")
      .maxAge(10.minutes)

  val serverOptions: ZioHttpServerOptions[Carts] =
    ZioHttpServerOptions.customiseInterceptors[Carts]
      .corsInterceptor(CORSInterceptor.customOrThrow(corsConfig))
      .options
}
