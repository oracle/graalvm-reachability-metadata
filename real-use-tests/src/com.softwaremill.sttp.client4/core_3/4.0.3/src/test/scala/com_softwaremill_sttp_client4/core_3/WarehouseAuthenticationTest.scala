/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_client4.core_3

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import scala.concurrent.duration.*
import sttp.client4.*
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.model.StatusCode

/** Exercises HTTP authentication by exchanging Basic credentials for a Bearer token to retrieve warehouse stock. */
class WarehouseAuthenticationTest {
  @Test
  @Timeout(60)
  def obtainsAccessTokenAndReadsProtectedStock(): Unit = {
    val credentials: String = "Basic " + Base64.getEncoder.encodeToString("stock-reader:shelf:west+17".getBytes(UTF_8))
    val token: String = "warehouse-access-A17"
    val server: HttpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    try {
      server.createContext("/access-token", (exchange: HttpExchange) => {
        if (exchange.getRequestMethod == "POST" && exchange.getRequestHeaders.getFirst("Authorization") == credentials) {
          respond(exchange, 200, token)
        } else {
          exchange.getResponseHeaders.set("WWW-Authenticate", "Basic realm=\"warehouse-token\"")
          respond(exchange, 401, "Invalid client credentials")
        }
      })
      server.createContext("/stock", (exchange: HttpExchange) => {
        if (exchange.getRequestMethod == "GET" && exchange.getRequestHeaders.getFirst("Authorization") == s"Bearer $token") {
          respond(exchange, 200, "tea=42;coffee=17")
        } else {
          exchange.getResponseHeaders.set("WWW-Authenticate", "Bearer realm=\"warehouse-stock\"")
          respond(exchange, 401, "Access token required")
        }
      })
      server.start()

      val backend: WebSocketSyncBackend = HttpClientSyncBackend(BackendOptions.connectionTimeout(10.seconds))
      try {
        val port: Int = server.getAddress.getPort
        val anonymous: Response[Either[String, String]] = basicRequest
          .get(uri"http://127.0.0.1:$port/stock")
          .readTimeout(10.seconds)
          .send(backend)

        assertEquals(StatusCode.Unauthorized, anonymous.code)
        assertEquals(Left("Access token required"), anonymous.body)
        assertEquals(Some("Bearer realm=\"warehouse-stock\""), anonymous.header("WWW-Authenticate"))

        val rejected: Response[Either[String, String]] = basicRequest
          .auth.basic("stock-reader", "wrong-password")
          .post(uri"http://127.0.0.1:$port/access-token")
          .readTimeout(10.seconds)
          .send(backend)

        assertEquals(StatusCode.Unauthorized, rejected.code)
        assertEquals(Left("Invalid client credentials"), rejected.body)
        assertEquals(Some("Basic realm=\"warehouse-token\""), rejected.header("WWW-Authenticate"))

        val authenticated: Response[Either[String, String]] = basicRequest
          .auth.basic("stock-reader", "shelf:west+17")
          .post(uri"http://127.0.0.1:$port/access-token")
          .readTimeout(10.seconds)
          .send(backend)

        assertEquals(StatusCode.Ok, authenticated.code)
        assertEquals(Right(token), authenticated.body)
        val accessToken: String = authenticated.body.toOption.get

        val stock: Response[Either[String, String]] = basicRequest
          .auth.bearer(accessToken)
          .get(uri"http://127.0.0.1:$port/stock")
          .readTimeout(10.seconds)
          .send(backend)

        assertEquals(StatusCode.Ok, stock.code)
        assertEquals(Right("tea=42;coffee=17"), stock.body)
      } finally backend.close()
    } finally server.stop(0)
  }

  private def respond(exchange: HttpExchange, status: Int, text: String): Unit = {
    try {
      val bytes: Array[Byte] = text.getBytes(UTF_8)
      exchange.getResponseHeaders.set("Content-Type", "text/plain; charset=UTF-8")
      exchange.sendResponseHeaders(status, bytes.length.toLong)
      exchange.getResponseBody.write(bytes)
    } finally exchange.close()
  }
}
