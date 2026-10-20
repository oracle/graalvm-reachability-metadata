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
import sttp.client4.wrappers.DigestAuthenticationBackend
import sttp.model.StatusCode

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

/**
 * Exercises circe JSON exchanges behind HTTP authentication: a warehouse client authenticates
 * with basic credentials, bearer tokens, and digest challenge-response through
 * `DigestAuthenticationBackend` (qop `auth` for reads, qop `auth-int` over the circe-serialized
 * request body for writes), and decodes rejected attempts as typed JSON error bodies.
 */
class WarehouseAuthenticationTest {
  private final case class StockLevel(sku: String, onHand: Int)
  private final case class Adjustment(sku: String, delta: Int, reason: String)
  private final case class AdjustmentReceipt(id: String, sku: String, onHand: Int, approvedBy: String)
  private final case class AuthError(error: String, scheme: String)

  /** One request as the server saw it, kept for assertions on the authentication handshake. */
  private final case class Seen(method: String, path: String, authorization: Option[String], body: String)

  private val Realm: String = "warehouse-api"
  private val Nonce: String = "dcd98b7102dd2f0e8b11d0f600bfb0c093"
  private val Opaque: String = "5ccc069c403ebaf9f0171e9517f40e41"
  private val ClientNonce: String = "0a4f113b"
  private val Users: Map[String, String] = Map("clerk" -> "c1erk:pass", "auditor" -> "aud1t0r")
  private val Token: String = "wh-token-7f3a"

  private var server: HttpServer = null
  private var backend: SyncBackend = null
  private val seen: ConcurrentLinkedQueue[Seen] = new ConcurrentLinkedQueue[Seen]()

  @BeforeEach
  def startServer(): Unit = {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/basic/stock", (exchange: HttpExchange) => handle(exchange, basicStock))
    server.createContext("/bearer/stock", (exchange: HttpExchange) => handle(exchange, bearerStock))
    server.createContext("/digest/stock", (exchange: HttpExchange) => handle(exchange, digestStock))
    server.createContext("/digest/adjustments", (exchange: HttpExchange) => handle(exchange, digestAdjustment))
    server.start()
    backend = DigestAuthenticationBackend(
      DefaultSyncBackend(BackendOptions.connectionTimeout(10.seconds)),
      () => ClientNonce
    )
  }

  @AfterEach
  def stopServer(): Unit = {
    backend.close()
    server.stop(0)
  }

  @Test
  def basicCredentialsUnlockStockAndWrongPasswordDecodesAuthError(): Unit = {
    val granted: Response[Either[ResponseException[AuthError], List[StockLevel]]] =
      basicRequest
        .get(uri"http://127.0.0.1:$port/basic/stock")
        .auth
        .basic("clerk", "c1erk:pass")
        .response(asJsonEither[AuthError, List[StockLevel]])
        .readTimeout(10.seconds)
        .send(backend)
    assertThat(granted.code).isEqualTo(StatusCode.Ok)
    assertThat(granted.body).isEqualTo(Right(List(StockLevel("PAL-1", 40), StockLevel("CRT-9", 7))))
    val expectedHeader: String =
      "Basic " + Base64.getEncoder.encodeToString("clerk:c1erk:pass".getBytes(StandardCharsets.UTF_8))
    assertThat(seen.asScala.toList.map(_.authorization)).isEqualTo(List(Some(expectedHeader)))

    val denied: Response[Either[ResponseException[AuthError], List[StockLevel]]] =
      basicRequest
        .get(uri"http://127.0.0.1:$port/basic/stock")
        .auth
        .basic("clerk", "guess")
        .response(asJsonEither[AuthError, List[StockLevel]])
        .readTimeout(10.seconds)
        .send(backend)
    assertThat(denied.code).isEqualTo(StatusCode.Unauthorized)
    assertThat(denied.header("WWW-Authenticate")).isEqualTo(Some(s"""Basic realm="$Realm""""))
    denied.body match {
      case Left(ResponseException.UnexpectedStatusCode(error, metadata)) =>
        assertThat(error).isEqualTo(AuthError("invalid_credentials", "Basic"))
        assertThat(metadata.code).isEqualTo(StatusCode.Unauthorized)
      case other =>
        throw new AssertionError(s"Expected a typed 401 body, got $other")
    }
  }

  @Test
  def bearerTokenIsSentVerbatimAndMissingTokenIsRejected(): Unit = {
    val levels: List[StockLevel] =
      basicRequest
        .get(uri"http://127.0.0.1:$port/bearer/stock?sku=PAL-1")
        .auth
        .bearer(Token)
        .response(asJsonOrFail[List[StockLevel]])
        .readTimeout(10.seconds)
        .send(backend)
        .body
    assertThat(levels).isEqualTo(List(StockLevel("PAL-1", 40)))
    assertThat(seen.peek().authorization).isEqualTo(Some(s"Bearer $Token"))

    val anonymous: Response[Either[ResponseException[AuthError], List[StockLevel]]] =
      basicRequest
        .get(uri"http://127.0.0.1:$port/bearer/stock")
        .response(asJsonEither[AuthError, List[StockLevel]])
        .readTimeout(10.seconds)
        .send(backend)
    assertThat(anonymous.code).isEqualTo(StatusCode.Unauthorized)
    anonymous.body match {
      case Left(ResponseException.UnexpectedStatusCode(error, _)) =>
        assertThat(error).isEqualTo(AuthError("missing_token", "Bearer"))
      case other =>
        throw new AssertionError(s"Expected a typed 401 body, got $other")
    }
  }

  @Test
  def digestChallengeIsAnsweredForJsonReads(): Unit = {
    // The challenge must reach the wrapper as a response, so the handler cannot fail on a 401.
    val response: Response[Either[ResponseException[AuthError], List[StockLevel]]] =
      basicRequest
        .get(uri"http://127.0.0.1:$port/digest/stock?sku=CRT-9&warehouse=north")
        .auth
        .digest("auditor", "aud1t0r")
        .response(asJsonEither[AuthError, List[StockLevel]])
        .readTimeout(10.seconds)
        .send(backend)
    assertThat(response.code).isEqualTo(StatusCode.Ok)
    assertThat(response.body).isEqualTo(Right(List(StockLevel("CRT-9", 7))))

    val attempts: List[Seen] = seen.asScala.toList
    assertThat(attempts.map(_.authorization.isDefined)).isEqualTo(List(false, true))
    val params: Map[String, String] = digestParams(attempts(1).authorization.get)
    assertThat(params("username")).isEqualTo("auditor")
    assertThat(params("realm")).isEqualTo(Realm)
    assertThat(params("uri")).isEqualTo("/digest/stock?sku=CRT-9&warehouse=north")
    assertThat(params("qop")).isEqualTo("auth")
    assertThat(params("nc")).isEqualTo("00000001")
    assertThat(params("cnonce")).isEqualTo(ClientNonce)
    assertThat(params("opaque")).isEqualTo(Opaque)
  }

  @Test
  def digestAuthIntProtectsTheCirceSerializedRequestBody(): Unit = {
    val adjustment: Adjustment = Adjustment("PAL-1", -5, "damaged in transit")
    val receipt: Either[ResponseException[AuthError], AdjustmentReceipt] =
      basicRequest
        .post(uri"http://127.0.0.1:$port/digest/adjustments")
        .auth
        .digest("clerk", "c1erk:pass")
        .body(asJson(adjustment))
        .response(asJsonEither[AuthError, AdjustmentReceipt])
        .readTimeout(10.seconds)
        .send(backend)
        .body
    assertThat(receipt).isEqualTo(Right(AdjustmentReceipt("ADJ-PAL-1", "PAL-1", 35, "clerk")))

    val attempts: List[Seen] = seen.asScala.toList
    assertThat(attempts.map(_.method)).isEqualTo(List("POST", "POST"))
    assertThat(attempts.map(attempt => decode[Adjustment](attempt.body)))
      .isEqualTo(List(Right(adjustment), Right(adjustment)))
    assertThat(digestParams(attempts(1).authorization.get)("qop")).isEqualTo("auth-int")
  }

  @Test
  def wrongDigestPasswordReturnsTheSecondChallengeAsTypedError(): Unit = {
    val response: Response[Either[ResponseException[AuthError], List[StockLevel]]] =
      basicRequest
        .get(uri"http://127.0.0.1:$port/digest/stock")
        .auth
        .digest("auditor", "not-it")
        .response(asJsonEither[AuthError, List[StockLevel]])
        .readTimeout(10.seconds)
        .send(backend)
    assertThat(response.code).isEqualTo(StatusCode.Unauthorized)
    response.body match {
      case Left(ResponseException.UnexpectedStatusCode(error, _)) =>
        assertThat(error).isEqualTo(AuthError("invalid_digest", "Digest"))
      case other =>
        throw new AssertionError(s"Expected a typed 401 body, got $other")
    }
    assertThat(seen.asScala.toList.map(_.authorization.isDefined)).isEqualTo(List(false, true))
  }

  private def port: Int = server.getAddress.getPort

  private val allStock: List[StockLevel] = List(StockLevel("PAL-1", 40), StockLevel("CRT-9", 7))

  private def stockFor(exchange: HttpExchange): List[StockLevel] = {
    val query: Option[String] = Option(exchange.getRequestURI.getRawQuery)
    val sku: Option[String] =
      query.toList.flatMap(_.split('&').toList).collectFirst { case s"sku=$value" => value }
    sku.fold(allStock)(wanted => allStock.filter(_.sku == wanted))
  }

  private def basicStock(exchange: HttpExchange, request: Seen): Unit = {
    val authorized: Boolean = request.authorization.exists {
      case s"Basic $encoded" =>
        new String(Base64.getDecoder.decode(encoded), StandardCharsets.UTF_8) match {
          case s"$user:$password" => Users.get(user).contains(password)
          case _ => false
        }
      case _ => false
    }
    if (authorized) {
      respond(exchange, 200, stockFor(exchange).asJson.noSpaces)
    } else {
      exchange.getResponseHeaders.set("WWW-Authenticate", s"""Basic realm="$Realm"""")
      respond(exchange, 401, AuthError("invalid_credentials", "Basic").asJson.noSpaces)
    }
  }

  private def bearerStock(exchange: HttpExchange, request: Seen): Unit = {
    if (request.authorization.contains(s"Bearer $Token")) {
      respond(exchange, 200, stockFor(exchange).asJson.noSpaces)
    } else {
      exchange.getResponseHeaders.set("WWW-Authenticate", s"""Bearer realm="$Realm"""")
      respond(exchange, 401, AuthError("missing_token", "Bearer").asJson.noSpaces)
    }
  }

  private def digestStock(exchange: HttpExchange, request: Seen): Unit = {
    if (digestValid(request, exchange, "auth").isDefined) {
      respond(exchange, 200, stockFor(exchange).asJson.noSpaces)
    } else {
      challenge(exchange, "auth")
    }
  }

  private def digestAdjustment(exchange: HttpExchange, request: Seen): Unit = {
    digestValid(request, exchange, "auth-int") match {
      case Some(user) =>
        val adjustment: Adjustment = decode[Adjustment](request.body).fold(throw _, identity)
        val onHand: Int = allStock.find(_.sku == adjustment.sku).map(_.onHand).getOrElse(0) + adjustment.delta
        val receipt: AdjustmentReceipt = AdjustmentReceipt(s"ADJ-${adjustment.sku}", adjustment.sku, onHand, user)
        respond(exchange, 200, receipt.asJson.noSpaces)
      case None =>
        challenge(exchange, "auth-int")
    }
  }

  private def challenge(exchange: HttpExchange, qop: String): Unit = {
    exchange.getResponseHeaders.set(
      "WWW-Authenticate",
      s"""Digest realm="$Realm", qop="$qop", nonce="$Nonce", opaque="$Opaque", algorithm=MD5"""
    )
    respond(exchange, 401, AuthError("invalid_digest", "Digest").asJson.noSpaces)
  }

  /** Verifies an RFC 7616 digest response and returns the authenticated user. */
  private def digestValid(request: Seen, exchange: HttpExchange, qop: String): Option[String] =
    request.authorization.collect { case header @ s"Digest $_" => digestParams(header) }.flatMap { params =>
      val user: String = params.getOrElse("username", "")
      val uriMatches: Boolean = params.get("uri").contains(exchange.getRequestURI.toString)
      val expected: Option[String] = Users.get(user).map { password =>
        val ha1: String = md5Hex(s"$user:$Realm:$password")
        val ha2: String =
          if (qop == "auth-int") md5Hex(s"${request.method}:${params("uri")}:${md5Hex(request.body)}")
          else md5Hex(s"${request.method}:${params("uri")}")
        md5Hex(s"$ha1:$Nonce:${params("nc")}:${params("cnonce")}:$qop:$ha2")
      }
      val fresh: Boolean = params.get("nonce").contains(Nonce) && params.get("qop").contains(qop)
      if (uriMatches && fresh && expected.isDefined && params.get("response") == expected) Some(user) else None
    }

  private def digestParams(header: String): Map[String, String] = {
    val pattern = """(\w+)=(?:"([^"]*)"|([^,\s]*))""".r
    pattern
      .findAllMatchIn(header.stripPrefix("Digest "))
      .map(m => m.group(1) -> Option(m.group(2)).getOrElse(m.group(3)))
      .toMap
  }

  private def md5Hex(value: String): String =
    MessageDigest
      .getInstance("MD5")
      .digest(value.getBytes(StandardCharsets.UTF_8))
      .map(b => f"${b & 0xff}%02x")
      .mkString

  private def handle(exchange: HttpExchange, route: (HttpExchange, Seen) => Unit): Unit = {
    try {
      val body: String = new String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
      val request: Seen = Seen(
        exchange.getRequestMethod,
        exchange.getRequestURI.getPath,
        Option(exchange.getRequestHeaders.getFirst("Authorization")),
        body
      )
      seen.add(request)
      route(exchange, request)
    } finally {
      exchange.close()
    }
  }

  private def respond(exchange: HttpExchange, status: Int, body: String): Unit = {
    val bytes: Array[Byte] = body.getBytes(StandardCharsets.UTF_8)
    exchange.getResponseHeaders.set("Content-Type", "application/json")
    exchange.sendResponseHeaders(status, bytes.length.toLong)
    exchange.getResponseBody.write(bytes)
  }
}
