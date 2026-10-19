/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_core_3

import cats.data.Ior
import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.kernel.Semigroup
import java.util.UUID
import org.http4s.AuthScheme
import org.http4s.Credentials
import org.http4s.Header
import org.http4s.Headers
import org.http4s.HttpVersion
import org.http4s.Method
import org.http4s.ParseFailure
import org.http4s.ParseResult
import org.http4s.Request
import org.http4s.Response
import org.http4s.Status
import org.http4s.headers.Authorization
import org.http4s.syntax.literals.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.typelevel.ci.*

/**
 * Defines application-specific typed headers with the `Header` type class (`Header.create`,
 * `Header.Single`, `Header.Recurring`, `Header.Select`) and reads, merges, replaces, and redacts
 * them through `Headers` and the message header operations.
 *
 * A multi-tenant API gateway admits requests only with a valid `X-Tenant-Id`, merges repeated
 * `X-Feature-Flags` lines, stamps an `X-Request-Id` on the upstream request, strips internal
 * headers, and logs the request with credentials redacted.
 */
class TenantGatewayCustomHeadersTest {
  @Test
  def rendersAndParsesTheCustomHeadersThroughTheirTypeClass(): Unit = {
    val requestId: RequestId = RequestId(UUID.fromString("5d0c6a1e-8f53-4c9a-9d1e-2b7f4e0a9c31"))
    val request: Request[IO] = Request[IO](uri = uri"/v1/invoices")
      .putHeaders(TenantId("acme-eu"), requestId, FeatureFlags(List("dark-mode", "beta-export")))

    assertEquals(ci"X-Tenant-Id", Header[TenantId].name)
    assertEquals("acme-eu", request.headers.get(ci"x-tenant-id").map(_.head.value).getOrElse(""))
    assertEquals(
      "dark-mode, beta-export",
      request.headers.get(ci"X-Feature-Flags").map(_.head.value).getOrElse("")
    )
    assertEquals(Some(TenantId("acme-eu")), request.headers.get[TenantId])
    assertEquals(Some(requestId), request.headers.get[RequestId])
    assertTrue(request.headers.contains[FeatureFlags])
    assertEquals(Right(TenantId("globex")), Header[TenantId].parse("  Globex "))
  }

  @Test
  def mergesRepeatedFeatureFlagLinesAndReportsTheMalformedOnes(): Unit = {
    val request: Request[IO] = Request[IO](
      uri = uri"/v1/invoices",
      headers = Headers(
        "X-Tenant-Id" -> "acme-eu",
        "X-Feature-Flags" -> "dark-mode, beta-export",
        "x-feature-flags" -> "Bulk-Delete",
        "X-Feature-Flags" -> "dark-mode,"
      )
    )
    assertEquals(
      Some(FeatureFlags(List("dark-mode", "beta-export", "bulk-delete"))),
      request.headers.get[FeatureFlags]
    )

    val messy: Headers = Headers(
      "X-Feature-Flags" -> "dark-mode",
      "X-Feature-Flags" -> "no spaces allowed",
      "X-Feature-Flags" -> "beta-export"
    )
    messy.getWithWarnings[FeatureFlags] match {
      case Some(Ior.Both(failures, flags)) =>
        assertEquals(1, failures.size)
        assertEquals("Invalid X-Feature-Flags header", failures.head.sanitized)
        assertTrue(failures.head.details.contains("no spaces allowed"), failures.head.details)
        assertEquals(FeatureFlags(List("dark-mode", "beta-export")), flags)
      case other => throw new AssertionError(s"unexpected selection: $other")
    }
    assertEquals(None, Headers("X-Tenant-Id" -> "acme-eu").getWithWarnings[FeatureFlags])
  }

  @Test
  def refusesRequestsWithoutAValidTenant(): Unit = {
    val missing: Response[IO] = Gateway.admit(Request[IO](uri = uri"/v1/invoices")).swap.toOption.get
    assertEquals(Status.BadRequest, missing.status)
    assertEquals("Missing X-Tenant-Id header", bodyOf(missing))

    val malformed: Request[IO] =
      Request[IO](uri = uri"/v1/invoices", headers = Headers("X-Tenant-Id" -> "acme_eu!"))
    malformed.headers.getWithWarnings[TenantId] match {
      case Some(Ior.Left(failures)) => assertEquals("Invalid X-Tenant-Id header", failures.head.sanitized)
      case other => throw new AssertionError(s"unexpected selection: $other")
    }
    val rejected: Response[IO] = Gateway.admit(malformed).swap.toOption.get
    assertEquals(Status.BadRequest, rejected.status)
    assertEquals("Invalid X-Tenant-Id header", bodyOf(rejected))

    val admitted: Request[IO] =
      Request[IO](uri = uri"/v1/invoices", headers = Headers("X-Tenant-Id" -> "ACME-EU"))
    assertEquals(Right(TenantId("acme-eu")), Gateway.admit(admitted))
  }

  @Test
  def preparesTheUpstreamRequestWithReplacedAddedAndStrippedHeaders(): Unit = {
    val generated: RequestId = RequestId(UUID.fromString("0f8e2d4c-1b3a-4e5f-9a7b-6c5d4e3f2a10"))
    val incoming: Request[IO] = Request[IO](
      method = Method.POST,
      uri = uri"/v1/invoices",
      headers = Headers(
        "X-Tenant-Id" -> "acme-eu",
        "X-Request-Id" -> "not-a-uuid",
        "X-Feature-Flags" -> "dark-mode",
        "X-Internal-Debug" -> "trace=all"
      )
    )
    val upstream: Request[IO] = Gateway.toUpstream(incoming, generated)

    assertEquals(Some(generated), upstream.headers.get[RequestId])
    assertEquals(1, upstream.headers.get(ci"X-Request-Id").map(_.size).getOrElse(0))
    assertEquals(None, upstream.headers.get(ci"X-Internal-Debug"))
    assertEquals(2, upstream.headers.get(ci"X-Feature-Flags").map(_.size).getOrElse(0))
    assertEquals(Some(FeatureFlags(List("dark-mode", "gateway-v2"))), upstream.headers.get[FeatureFlags])

    val traced: RequestId = RequestId(UUID.fromString("7a6b5c4d-3e2f-4a1b-8c9d-0e1f2a3b4c5d"))
    val alreadyTraced: Request[IO] = incoming.putHeaders(traced)
    assertEquals(Some(traced), Gateway.toUpstream(alreadyTraced, generated).headers.get[RequestId])
    assertTrue(Gateway.toUpstream(alreadyTraced, generated).headers.contains[TenantId])
  }

  @Test
  def logsRequestHeadersWithCredentialsRedacted(): Unit = {
    val request: Request[IO] = Request[IO](
      uri = uri"/v1/invoices",
      headers = Headers(
        Authorization(Credentials.Token(AuthScheme.Bearer, "eyJhbGciOi")),
        "X-Api-Key" -> "k-12345",
        TenantId("acme-eu")
      )
    )
    val logged: List[Header.Raw] = Gateway.loggableHeaders(request).headers
    assertEquals(
      List(
        Header.Raw(ci"Authorization", "<REDACTED>"),
        Header.Raw(ci"X-Api-Key", "<REDACTED>"),
        Header.Raw(ci"X-Tenant-Id", "acme-eu")
      ),
      logged
    )
    assertEquals(Some("k-12345"), request.headers.get(ci"X-Api-Key").map(_.head.value))
  }

  /** The tenant a request belongs to: a lower-case slug of letters, digits, and dashes. */
  final case class TenantId(value: String)

  object TenantId {
    private val Slug: String = "[a-z0-9]+(-[a-z0-9]+)*"

    given Header[TenantId, Header.Single] = Header.create(
      ci"X-Tenant-Id",
      _.value,
      raw => {
        val normalized: String = raw.trim.toLowerCase
        if (normalized.matches(Slug)) Right(TenantId(normalized))
        else Left(ParseFailure("Invalid X-Tenant-Id header", s"not a tenant slug: '$raw'"))
      }
    )
  }

  /** The correlation id the gateway propagates to every upstream service. */
  final case class RequestId(value: UUID)

  object RequestId {
    given Header[RequestId, Header.Single] = Header.create(
      ci"X-Request-Id",
      _.value.toString,
      raw =>
        ParseResult.fromTryCatchNonFatal("Invalid X-Request-Id header")(RequestId(UUID.fromString(raw.trim)))
    )
  }

  /** Feature flags a client opts into; the header may repeat and its lines merge. */
  final case class FeatureFlags(names: List[String])

  object FeatureFlags {
    private val Flag: String = "[a-z0-9-]+"

    given Header[FeatureFlags, Header.Recurring] = Header.create(
      ci"X-Feature-Flags",
      _.names.mkString(", "),
      raw => {
        val names: List[String] = raw.split(',').toList.map(_.trim.toLowerCase).filter(_.nonEmpty)
        if (names.nonEmpty && names.forall(_.matches(Flag))) Right(FeatureFlags(names))
        else Left(ParseFailure("Invalid X-Feature-Flags header", s"bad flag list: '$raw'"))
      }
    )

    given Semigroup[FeatureFlags] =
      Semigroup.instance((left, right) => FeatureFlags((left.names ++ right.names).distinct))
  }

  /** The gateway's header handling, as an application writes it around its routes. */
  object Gateway {
    private val InternalHeaders: Set[CIString] = Set(ci"X-Internal-Debug")
    private val LoggedSecrets: Set[CIString] = Headers.SensitiveHeaders + ci"X-Api-Key"

    def admit(request: Request[IO]): Either[Response[IO], TenantId] =
      request.headers.getWithWarnings[TenantId] match {
        case None => Left(Response[IO](Status.BadRequest).withEntity("Missing X-Tenant-Id header"))
        case Some(Ior.Right(tenant)) => Right(tenant)
        case Some(Ior.Both(_, tenant)) => Right(tenant)
        case Some(Ior.Left(failures)) => Left(reject(failures))
      }

    def toUpstream(request: Request[IO], generated: RequestId): Request[IO] = {
      val requestId: RequestId = request.headers.get[RequestId].getOrElse(generated)
      request
        .filterHeaders(raw => !InternalHeaders.contains(raw.name))
        .removeHeader[RequestId]
        .putHeaders(requestId)
        .addHeader(FeatureFlags(List("gateway-v2")))
    }

    def loggableHeaders(request: Request[IO]): Headers =
      request.headers.redactSensitive(LoggedSecrets.contains)

    private def reject(failures: NonEmptyList[ParseFailure]): Response[IO] =
      failures.head.toHttpResponse[IO](HttpVersion.`HTTP/1.1`)
  }

  def bodyOf(response: Response[IO]): String = response.as[String].unsafeRunSync()
}
