/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_client4.circe_3

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.circe.generic.auto.*
import io.circe.parser.decode
import io.circe.syntax.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import sttp.client4.*
import sttp.client4.circe.*
import sttp.model.StatusCode
import sttp.model.headers.Cookie
import sttp.model.headers.CookieWithMeta

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

/**
 * Exercises cookie-based sessions around circe JSON calls: a shop client logs in with
 * `asJson` credentials, reads the `Set-Cookie` attributes of the reply, forwards the session
 * with `cookies(response)`, `cookie(name, value)` and a `CookieWithMeta` list while adding
 * cart items as JSON, and decodes the typed 401 bodies of missing and expired sessions with
 * `asJsonEither`.
 */
class ShoppingCartCookieSessionTest {
  private final case class Credentials(login: String, password: String)
  private final case class LoginResult(user: String, roles: List[String])
  private final case class CartItem(sku: String, quantity: Int)
  private final case class Cart(owner: String, items: List[CartItem], itemCount: Int)
  private final case class ApiError(code: String, message: String)

  private var server: HttpServer = null
  private var backend: SyncBackend = null
  private val sessions: ConcurrentHashMap[String, String] = new ConcurrentHashMap[String, String]()
  private val carts: ConcurrentHashMap[String, List[CartItem]] = new ConcurrentHashMap[String, List[CartItem]]()
  private val cartCookieHeaders: ConcurrentLinkedQueue[String] = new ConcurrentLinkedQueue[String]()

  @BeforeEach
  def startServer(): Unit = {
    val tokens: AtomicLong = new AtomicLong(7000L)
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext(
      "/session",
      (exchange: HttpExchange) => {
        try {
          exchange.getRequestMethod match {
            case "POST" =>
              decode[Credentials](readBody(exchange)) match {
                case Right(Credentials("ada", "analytical-engine")) =>
                  val token: String = s"s-${tokens.incrementAndGet()}"
                  sessions.put(token, "ada")
                  exchange.getResponseHeaders.add(
                    "Set-Cookie",
                    s"SESSION=$token; Path=/; Max-Age=3600; HttpOnly; SameSite=Strict"
                  )
                  exchange.getResponseHeaders.add("Set-Cookie", "locale=en-GB; Path=/")
                  respond(exchange, 200, LoginResult("ada", List("customer", "beta")).asJson.noSpaces)
                case Right(_) =>
                  respond(exchange, 401, ApiError("invalid_credentials", "Login or password is wrong").asJson.noSpaces)
                case Left(error) =>
                  respond(exchange, 400, ApiError("malformed", error.getMessage).asJson.noSpaces)
              }
            case "DELETE" =>
              sessionToken(exchange).foreach(token => sessions.remove(token))
              exchange.getResponseHeaders.add("Set-Cookie", "SESSION=; Path=/; Max-Age=0")
              respond(exchange, 204, "")
            case _ =>
              respond(exchange, 405, "")
          }
        } finally {
          exchange.close()
        }
      }
    )
    server.createContext(
      "/cart",
      (exchange: HttpExchange) => {
        try {
          cartCookieHeaders.add(
            Option(exchange.getRequestHeaders.getFirst("Cookie"))
              .map(_.split(";").map(_.trim).sorted.mkString("; "))
              .getOrElse("<none>")
          )
          sessionToken(exchange).flatMap(token => Option(sessions.get(token))) match {
            case None =>
              respond(exchange, 401, ApiError("session_required", "Log in to use the cart").asJson.noSpaces)
            case Some(owner) =>
              exchange.getRequestMethod match {
                case "POST" =>
                  val item: CartItem = decode[CartItem](readBody(exchange)).fold(error => throw error, identity)
                  val items: List[CartItem] = carts.getOrDefault(owner, Nil) :+ item
                  carts.put(owner, items)
                  respond(exchange, 201, Cart(owner, items, items.map(_.quantity).sum).asJson.noSpaces)
                case "GET" =>
                  val items: List[CartItem] = carts.getOrDefault(owner, Nil)
                  respond(exchange, 200, Cart(owner, items, items.map(_.quantity).sum).asJson.noSpaces)
                case _ =>
                  respond(exchange, 405, "")
              }
          }
        } finally {
          exchange.close()
        }
      }
    )
    server.start()
    backend = DefaultSyncBackend(BackendOptions.connectionTimeout(10.seconds))
  }

  @AfterEach
  def stopServer(): Unit = {
    backend.close()
    server.stop(0)
  }

  @Test
  def carriesSessionCookiesAcrossJsonCartCalls(): Unit = {
    val baseUri: String = s"http://127.0.0.1:${server.getAddress.getPort}"

    val rejected: Response[Either[ResponseException[ApiError], LoginResult]] = basicRequest
      .post(uri"$baseUri/session")
      .body(asJson(Credentials("ada", "difference-engine")))
      .response(asJsonEither[ApiError, LoginResult])
      .readTimeout(10.seconds)
      .send(backend)
    assertThat(rejected.code).isEqualTo(StatusCode.Unauthorized)
    assertThat(rejected.unsafeCookies.isEmpty).isTrue()
    rejected.body match {
      case Left(ResponseException.UnexpectedStatusCode(error, _)) => assertThat(error.code).isEqualTo("invalid_credentials")
      case other                                => throw new AssertionError(s"Unexpected login result: $other")
    }

    val login: Response[Either[ResponseException[ApiError], LoginResult]] = basicRequest
      .post(uri"$baseUri/session")
      .body(asJson(Credentials("ada", "analytical-engine")))
      .response(asJsonEither[ApiError, LoginResult])
      .readTimeout(10.seconds)
      .send(backend)
    assertThat(login.code).isEqualTo(StatusCode.Ok)
    assertThat(login.body).isEqualTo(Right(LoginResult("ada", List("customer", "beta"))))

    val cookies: Map[String, CookieWithMeta] = login.unsafeCookies.map(cookie => cookie.name -> cookie).toMap
    assertThat(cookies.keySet.asJava).containsExactlyInAnyOrder("SESSION", "locale")
    val session: CookieWithMeta = cookies("SESSION")
    assertThat(session.value).isEqualTo("s-7001")
    assertThat(session.path).isEqualTo(Some("/"))
    assertThat(session.maxAge).isEqualTo(Some(3600L))
    assertThat(session.httpOnly).isTrue()
    assertThat(session.secure).isFalse()
    assertThat(session.sameSite).isEqualTo(Some(Cookie.SameSite.Strict))
    assertThat(cookies("locale").value).isEqualTo("en-GB")
    assertThat(cookies("locale").httpOnly).isFalse()

    val anonymous: Response[Either[ResponseException[ApiError], Cart]] = basicRequest
      .get(uri"$baseUri/cart")
      .response(asJsonEither[ApiError, Cart])
      .readTimeout(10.seconds)
      .send(backend)
    assertThat(anonymous.code).isEqualTo(StatusCode.Unauthorized)
    anonymous.body match {
      case Left(ResponseException.UnexpectedStatusCode(error, _)) =>
        assertThat(error).isEqualTo(ApiError("session_required", "Log in to use the cart"))
      case other => throw new AssertionError(s"Unexpected anonymous cart result: $other")
    }

    val firstAdd: Cart = basicRequest
      .post(uri"$baseUri/cart")
      .cookies(login)
      .body(asJson(CartItem("TEA-ASSAM", 2)))
      .response(asJsonOrFail[Cart])
      .readTimeout(10.seconds)
      .send(backend)
      .body
    assertThat(firstAdd).isEqualTo(Cart("ada", List(CartItem("TEA-ASSAM", 2)), 2))

    val secondAdd: Cart = basicRequest
      .post(uri"$baseUri/cart")
      .cookie("SESSION", session.value)
      .body(asJson(CartItem("MUG-BLUE", 1)))
      .response(asJsonOrFail[Cart])
      .readTimeout(10.seconds)
      .send(backend)
      .body
    assertThat(secondAdd.items.map(_.sku)).isEqualTo(List("TEA-ASSAM", "MUG-BLUE"))
    assertThat(secondAdd.itemCount).isEqualTo(3)

    val current: Response[Either[ResponseException[ApiError], Cart]] = basicRequest
      .get(uri"$baseUri/cart")
      .cookies(List(session))
      .response(asJsonEither[ApiError, Cart])
      .readTimeout(10.seconds)
      .send(backend)
    assertThat(current.code).isEqualTo(StatusCode.Ok)
    assertThat(current.body).isEqualTo(Right(secondAdd))

    val logout: Response[Either[String, String]] = basicRequest
      .delete(uri"$baseUri/session")
      .cookies(List(session))
      .readTimeout(10.seconds)
      .send(backend)
    assertThat(logout.code).isEqualTo(StatusCode.NoContent)
    val cleared: CookieWithMeta = logout.unsafeCookies.find(_.name == "SESSION").get
    assertThat(cleared.value).isEmpty()
    assertThat(cleared.maxAge).isEqualTo(Some(0L))

    val expired: Response[Either[ResponseException[ApiError], Cart]] = basicRequest
      .get(uri"$baseUri/cart")
      .cookies(login)
      .response(asJsonEither[ApiError, Cart])
      .readTimeout(10.seconds)
      .send(backend)
    assertThat(expired.code).isEqualTo(StatusCode.Unauthorized)
    expired.body match {
      case Left(ResponseException.UnexpectedStatusCode(error, _)) => assertThat(error.code).isEqualTo("session_required")
      case other                                => throw new AssertionError(s"Unexpected expired cart result: $other")
    }

    assertThat(cartCookieHeaders.asScala.toList.asJava).containsExactly(
      "<none>",
      "SESSION=s-7001; locale=en-GB",
      "SESSION=s-7001",
      "SESSION=s-7001",
      "SESSION=s-7001; locale=en-GB"
    )
  }

  private def sessionToken(exchange: HttpExchange): Option[String] =
    Option(exchange.getRequestHeaders.getFirst("Cookie")).toList
      .flatMap(_.split(";").toList)
      .map(_.trim)
      .collectFirst { case pair if pair.startsWith("SESSION=") => pair.stripPrefix("SESSION=") }
      .filter(_.nonEmpty)

  private def readBody(exchange: HttpExchange): String =
    new String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)

  private def respond(exchange: HttpExchange, status: Int, body: String): Unit = {
    val bytes: Array[Byte] = body.getBytes(StandardCharsets.UTF_8)
    exchange.getResponseHeaders.set("Content-Type", "application/json")
    exchange.sendResponseHeaders(status, if (bytes.isEmpty) -1 else bytes.length.toLong)
    if (bytes.nonEmpty) {
      exchange.getResponseBody.write(bytes)
    }
  }
}
