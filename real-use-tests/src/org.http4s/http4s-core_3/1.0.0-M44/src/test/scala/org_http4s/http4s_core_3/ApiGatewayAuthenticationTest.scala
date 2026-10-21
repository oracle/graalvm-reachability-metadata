/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_core_3

import cats.data.NonEmptyList
import cats.effect.IO
import java.nio.charset.StandardCharsets
import org.http4s.AuthScheme
import org.http4s.BasicCredentials
import org.http4s.Challenge
import org.http4s.Credentials
import org.http4s.Headers
import org.http4s.Request
import org.http4s.Response
import org.http4s.Status
import org.http4s.headers.Authorization
import org.http4s.headers.`WWW-Authenticate`
import org.http4s.syntax.literals.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.typelevel.ci.*

/**
 * Authenticates HTTP requests with the credential model: `Authorization` carrying
 * `BasicCredentials` or bearer `Credentials.Token`, and `WWW-Authenticate` challenges.
 *
 * An API gateway accepts operators over HTTP Basic and services over bearer tokens, turns every
 * other request away with a 401 that advertises both schemes, and inspects the raw header values
 * that clients and upstream services send.
 */
class ApiGatewayAuthenticationTest {

  @Test
  def admitsOperatorsWithBasicCredentials(): Unit = {
    val credentials: BasicCredentials = BasicCredentials("aladdin", "opensesame")
    assertEquals("YWxhZGRpbjpvcGVuc2VzYW1l", credentials.token)
    assertEquals(AuthScheme.Basic, Authorization(credentials).credentials.authScheme)

    val request: Request[IO] = Request[IO](uri = uri"/ops/deploy").putHeaders(Authorization(credentials))
    assertEquals(
      Some("Basic YWxhZGRpbjpvcGVuc2VzYW1l"),
      request.headers.get(ci"Authorization").map(_.head.value)
    )

    val response: Response[IO] = gateway(request)
    assertEquals(Status.Ok, response.status)
    assertEquals(Some("operator:aladdin"), response.headers.get(ci"X-Principal").map(_.head.value))

    val wrongPassword: Request[IO] =
      Request[IO](uri = uri"/ops/deploy").putHeaders(Authorization(BasicCredentials("aladdin", "guess")))
    assertEquals(Status.Unauthorized, gateway(wrongPassword).status)

    val decoded: Option[BasicCredentials] = BasicCredentials.fromString("YWxhZGRpbjpvcGVuc2VzYW1l")
    assertEquals(Some("aladdin" -> "opensesame"), decoded.map(c => c.username -> c.password))

    val nonAscii: BasicCredentials = BasicCredentials("jos\u00e9", "p\u00e4ss", StandardCharsets.UTF_8)
    val renderedNonAscii: String = Authorization(nonAscii).credentials.renderString
    assertTrue(renderedNonAscii.startsWith("Basic "), renderedNonAscii)
    assertEquals(
      Some("jos\u00e9" -> "p\u00e4ss"),
      BasicCredentials.fromString(renderedNonAscii.stripPrefix("Basic ")).map(c => c.username -> c.password)
    )
  }

  @Test
  def admitsServicesWithBearerTokens(): Unit = {
    val token: Credentials.Token = Credentials.Token(AuthScheme.Bearer, "svc-billing-7c1e")
    val request: Request[IO] = Request[IO](uri = uri"/internal/invoices").putHeaders(Authorization(token))
    assertEquals(
      Some("Bearer svc-billing-7c1e"),
      request.headers.get(ci"Authorization").map(_.head.value)
    )

    val response: Response[IO] = gateway(request)
    assertEquals(Status.Ok, response.status)
    assertEquals(Some("service:billing"), response.headers.get(ci"X-Principal").map(_.head.value))

    val fromWire: Request[IO] = Request[IO](
      uri = uri"/internal/invoices",
      headers = Headers("Authorization" -> "bearer svc-billing-7c1e")
    )
    assertEquals(Status.Ok, gateway(fromWire).status)

    val revoked: Request[IO] = Request[IO](
      uri = uri"/internal/invoices",
      headers = Headers("Authorization" -> "Bearer svc-retired-0000")
    )
    val rejected: Response[IO] = gateway(revoked)
    assertEquals(Status.Unauthorized, rejected.status)
    val challenges: List[Challenge] =
      rejected.headers.get[`WWW-Authenticate`].toList.flatMap(_.values.toList)
    assertEquals(
      Some(Map("error" -> "invalid_token")),
      challenges.find(_.scheme == "Bearer").map(_.params)
    )
  }

  @Test
  def challengesAnonymousRequestsWithBothSchemes(): Unit = {
    val response: Response[IO] = gateway(Request[IO](uri = uri"/ops/deploy"))
    assertEquals(Status.Unauthorized, response.status)

    val header: `WWW-Authenticate` = response.headers.get[`WWW-Authenticate`]
      .getOrElse(throw new AssertionError("no WWW-Authenticate header"))
    assertEquals(List("Basic", "Bearer"), header.values.toList.map(_.scheme))
    assertEquals(List(Realm, Realm), header.values.toList.map(_.realm))
    assertEquals(Map("charset" -> "UTF-8"), header.values.head.params)

    val rendered: String = response.headers.get(ci"WWW-Authenticate").map(_.head.value)
      .getOrElse(throw new AssertionError("no raw WWW-Authenticate header"))
    assertTrue(rendered.startsWith("Basic realm=\"ops-gateway\""), rendered)
    assertTrue(rendered.contains("charset=\"UTF-8\""), rendered)
    assertTrue(rendered.contains("Bearer realm=\"ops-gateway\""), rendered)

    val malformed: Request[IO] = Request[IO](
      uri = uri"/ops/deploy",
      headers = Headers("Authorization" -> "Basic")
    )
    assertEquals(Status.Unauthorized, gateway(malformed).status)
  }

  @Test
  def parsesAuthenticationHeadersFromTheWire(): Unit = {
    val digest: Authorization = Authorization
      .parse("Digest username=\"mufasa\", realm=\"ops-gateway\", nonce=\"dcd98b7102dd\"")
      .fold(failure => throw failure, identity)
    digest.credentials match {
      case Credentials.AuthParams(scheme, params) =>
        assertEquals(AuthScheme.Digest, scheme)
        assertEquals(
          List("username" -> "mufasa", "realm" -> "ops-gateway", "nonce" -> "dcd98b7102dd"),
          params.toList
        )
      case other => throw new AssertionError(s"unexpected credentials: $other")
    }

    val basic: Authorization =
      Authorization.parse("Basic YWxhZGRpbjpvcGVuc2VzYW1l").fold(failure => throw failure, identity)
    basic.credentials match {
      case BasicCredentials(user, password) =>
        assertEquals("aladdin", user)
        assertEquals("opensesame", password)
      case other => throw new AssertionError(s"unexpected credentials: $other")
    }

    val upstream: `WWW-Authenticate` = `WWW-Authenticate`
      .parse("Bearer realm=\"partner\", error=\"insufficient_scope\", scope=\"invoices:write\"")
      .fold(failure => throw failure, identity)
    val challenge: Challenge = upstream.values.head
    assertEquals("Bearer", challenge.scheme)
    assertEquals("partner", challenge.realm)
    assertEquals(Some("invoices:write"), challenge.params.get("scope"))
    assertEquals(Some("insufficient_scope"), challenge.params.get("error"))

    val built: Credentials.AuthParams =
      Credentials.AuthParams(ci"Digest", "username" -> "mufasa", "nonce" -> "dcd98b7102dd")
    assertTrue(Authorization.parse(Authorization(built).credentials.renderString).isRight)
    assertTrue(Authorization.parse("").isLeft)
  }

  /** The protection space the gateway advertises in its challenges. */
  val Realm: String = "ops-gateway"

  /** Operator accounts allowed over HTTP Basic, by user name. */
  val Operators: Map[String, String] = Map("aladdin" -> "opensesame")

  /** Service tokens allowed as bearer credentials, mapped to the service they identify. */
  val ServiceTokens: Map[String, String] = Map("svc-billing-7c1e" -> "billing")

  /** Resolves the principal behind a request's credentials, if they are valid. */
  def principal(credentials: Credentials): Option[String] =
    credentials match {
      case BasicCredentials(user, password) if Operators.get(user).contains(password) =>
        Some(s"operator:$user")
      case Credentials.Token(scheme, token) if scheme == AuthScheme.Bearer =>
        ServiceTokens.get(token).map(service => s"service:$service")
      case _ => None
    }

  /** Answers a request with 200 for a known principal and a 401 challenge otherwise. */
  def gateway(request: Request[IO]): Response[IO] = {
    val credentials: Option[Credentials] = request.headers.get[Authorization].map(_.credentials)
    credentials.flatMap(principal) match {
      case Some(name) => Response[IO](Status.Ok).putHeaders("X-Principal" -> name)
      case None =>
        val bearerParams: Map[String, String] =
          if (credentials.exists(_.authScheme == AuthScheme.Bearer)) Map("error" -> "invalid_token")
          else Map.empty
        Response[IO](Status.Unauthorized).putHeaders(
          `WWW-Authenticate`(
            NonEmptyList.of(
              Challenge("Basic", Realm, Map("charset" -> "UTF-8")),
              Challenge("Bearer", Realm, bearerParams)
            )
          )
        )
    }
  }
}
