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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import scala.concurrent.duration.*
import sttp.client4.*
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.model.StatusCode

/** Exercises cookie-based sessions by carrying login response cookies into a protected account request. */
class AccountCookieSessionTest {
  @Test
  @Timeout(60)
  def logsInAndRetrievesAccountWithSessionCookies(): Unit = {
    val server: HttpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    try {
      server.createContext("/login", (exchange: HttpExchange) => {
        val credentials: String = new String(exchange.getRequestBody.readAllBytes(), UTF_8)
        if (exchange.getRequestMethod == "POST" && credentials == "username=alice&password=tea-time") {
          exchange.getResponseHeaders.add("Set-Cookie", "session=account-A17; Path=/; HttpOnly; SameSite=Lax")
          exchange.getResponseHeaders.add("Set-Cookie", "workspace=west; Path=/; SameSite=Lax")
          respond(exchange, 200, "Signed in as alice")
        } else respond(exchange, 401, "Invalid credentials")
      })
      server.createContext("/account", (exchange: HttpExchange) => {
        val cookies: Set[String] = Option(exchange.getRequestHeaders.getFirst("Cookie"))
          .map(_.split(";").iterator.map(_.trim).toSet)
          .getOrElse(Set.empty[String])
        if (cookies == Set("session=account-A17", "workspace=west")) {
          respond(exchange, 200, "Alice: workspace west, 3 open orders")
        } else respond(exchange, 401, "Please sign in")
      })
      server.start()

      val backend: WebSocketSyncBackend = HttpClientSyncBackend(BackendOptions.connectionTimeout(10.seconds))
      try {
        val port: Int = server.getAddress.getPort
        val anonymous: Response[Either[String, String]] = basicRequest
          .get(uri"http://127.0.0.1:$port/account")
          .readTimeout(10.seconds)
          .send(backend)

        assertEquals(StatusCode.Unauthorized, anonymous.code)
        assertEquals(Left("Please sign in"), anonymous.body)

        val login: Response[Either[String, String]] = basicRequest
          .body("username" -> "alice", "password" -> "tea-time")
          .post(uri"http://127.0.0.1:$port/login")
          .readTimeout(10.seconds)
          .send(backend)

        assertEquals(StatusCode.Ok, login.code)
        assertEquals(Right("Signed in as alice"), login.body)
        assertEquals(
          Set(Right("session" -> "account-A17"), Right("workspace" -> "west")),
          login.cookies.map(_.map(cookie => cookie.name -> cookie.value)).toSet
        )

        val account: Response[Either[String, String]] = basicRequest
          .cookies(login)
          .get(uri"http://127.0.0.1:$port/account")
          .readTimeout(10.seconds)
          .send(backend)

        assertEquals(StatusCode.Ok, account.code)
        assertEquals(Right("Alice: workspace west, 3 open orders"), account.body)
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
