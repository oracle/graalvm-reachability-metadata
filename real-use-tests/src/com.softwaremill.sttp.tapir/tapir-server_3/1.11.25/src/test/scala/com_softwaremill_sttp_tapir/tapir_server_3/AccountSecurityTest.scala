/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_server_3

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import sttp.model.StatusCode
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.jdkhttp.JdkHttpServer

/** Exercises server endpoint security logic for bearer-authenticated account access. */
class AccountSecurityTest {
  private case class Principal(accountId: String)

  @Test
  @Timeout(60)
  def authenticatesBeforeServingAccountDetails(): Unit = {
    val account: ServerEndpoint[Any, Identity] = endpoint.get
      .securityIn(auth.bearer[String]())
      .in("accounts" / path[String]("accountId"))
      .errorOut(statusCode.and(stringBody))
      .out(stringBody)
      .serverSecurityLogicPure[Principal, Identity] { token =>
        if (token == "alice-session") Right(Principal("alice"))
        else Left((StatusCode.Unauthorized, "Invalid session"))
      }
      .serverLogic { principal => requestedAccount =>
        if (principal.accountId == requestedAccount) Right(s"Account: $requestedAccount; plan: standard")
        else Left((StatusCode.Forbidden, "This account belongs to another customer"))
      }

    val server = JdkHttpServer().host("127.0.0.1").port(0).addEndpoint(account).start()
    try {
      val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
      try {
        val baseUri: String = s"http://127.0.0.1:${server.getAddress.getPort}/accounts/"

        def getAccount(id: String, token: Option[String]): HttpResponse[String] = {
          val request: HttpRequest.Builder = HttpRequest.newBuilder(URI.create(baseUri + id))
            .timeout(Duration.ofSeconds(10)).GET()
          token.foreach(value => request.header("Authorization", s"Bearer $value"))
          client.send(request.build(), HttpResponse.BodyHandlers.ofString())
        }

        val missing: HttpResponse[String] = getAccount("alice", None)
        assertEquals(401, missing.statusCode())
        assertTrue(missing.headers().firstValue("WWW-Authenticate").orElse("").startsWith("Bearer"))

        val invalid: HttpResponse[String] = getAccount("alice", Some("expired-session"))
        assertEquals(401, invalid.statusCode())
        assertEquals("Invalid session", invalid.body())

        val ownAccount: HttpResponse[String] = getAccount("alice", Some("alice-session"))
        assertEquals(200, ownAccount.statusCode())
        assertEquals("Account: alice; plan: standard", ownAccount.body())

        val otherAccount: HttpResponse[String] = getAccount("bob", Some("alice-session"))
        assertEquals(403, otherAccount.statusCode())
        assertEquals("This account belongs to another customer", otherAccount.body())
      } finally client.close()
    } finally server.stop(0)
  }
}
