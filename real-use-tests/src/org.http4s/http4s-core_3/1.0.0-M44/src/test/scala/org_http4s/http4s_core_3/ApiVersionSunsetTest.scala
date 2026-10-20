/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_core_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.Headers
import org.http4s.HttpDate
import org.http4s.MediaType
import org.http4s.Request
import org.http4s.Response
import org.http4s.Status
import org.http4s.Uri
import org.http4s.headers.Deprecation
import org.http4s.headers.Link
import org.http4s.headers.LinkValue
import org.http4s.headers.Location
import org.http4s.headers.Sunset
import org.http4s.syntax.literals.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.typelevel.ci.*

/**
 * Retires an API version with the `Deprecation`, `Sunset`, `Link`, and `Location` headers.
 *
 * A partner orders API keeps answering its deprecated v1 routes until the sunset date while
 * pointing callers at the successor version and the migration guide, answers `410 Gone` once the
 * sunset has passed, and permanently redirects renamed routes. A partner client reads the same
 * headers back to plan its migration.
 */
class ApiVersionSunsetTest {

  @Test
  def announcesTheDeprecationOnEveryV1Response(): Unit = {
    val request: Request[IO] = Request[IO](uri = uri"https://api.example.com/v1/orders/42")
    val response: Response[IO] = OrdersApi.serve(request, BeforeSunset)

    assertEquals(Status.Ok, response.status)
    assertEquals("order 42", response.as[String].unsafeRunSync())
    assertEquals(Some(Deprecation(DeprecatedOn)), response.headers.get[Deprecation])
    assertEquals(Some(Sunset(SunsetOn)), response.headers.get[Sunset])
    assertEquals(
      Some("Sat, 01 Aug 2026 00:00:00 GMT"),
      response.headers.get(ci"Sunset").map(_.head.value)
    )

    val links: List[LinkValue] =
      response.headers.get[Link].toList.flatMap(_.values.toList)
    assertEquals(List(Some("successor-version"), Some("deprecation")), links.map(_.rel))
    assertEquals(uri"/v2/orders/42", links.head.uri)
    assertEquals(
      Some(
        "</v2/orders/42>; rel=successor-version, " +
          "<https://docs.example.com/migrate-v2>; rel=deprecation; type=text/html"
      ),
      response.headers.get(ci"Link").map(_.head.value)
    )
    assertEquals(Some(MediaType.text.html), links(1).`type`)

    val current: Response[IO] =
      OrdersApi.serve(Request[IO](uri = uri"https://api.example.com/v2/orders/42"), BeforeSunset)
    assertEquals(Status.Ok, current.status)
    assertTrue(current.headers.get[Deprecation].isEmpty)
    assertTrue(current.headers.get[Sunset].isEmpty)
    assertTrue(current.headers.get[Link].isEmpty)
  }

  @Test
  def partnerClientPlansItsMigrationFromReceivedHeaders(): Unit = {
    val request: Request[IO] = Request[IO](uri = uri"https://api.example.com/v1/orders/42")
    val received: Response[IO] = Response[IO](
      status = Status.Ok,
      headers = Headers(
        "Deprecation" -> "Wed, 01 Jul 2026 00:00:00 GMT",
        "Sunset" -> "Sat, 01 Aug 2026 00:00:00 GMT",
        "Link" -> "</v2/orders/42>; rel=\"successor-version\"",
        "Link" -> "<https://docs.example.com/migrate-v2>; rel=\"deprecation\"; type=\"text/html\""
      )
    )

    val notice: MigrationNotice = PartnerClient.migrationNotice(request, received, ClientClock)
    assertEquals(DeprecatedOn, notice.deprecatedOn)
    assertEquals(17L, notice.daysLeft)
    assertEquals("https://api.example.com/v2/orders/42", notice.successor.renderString)
    assertEquals(Some(uri"https://docs.example.com/migrate-v2"), notice.guide)

    val parsed: Link = Link
      .parse("</v2/orders>; rel=\"successor-version\", </docs>; rel=\"deprecation\"")
      .fold(failure => throw failure, identity)
    assertEquals(List(uri"/v2/orders", uri"/docs"), parsed.values.toList.map(_.uri))
  }

  @Test
  def answersGoneAfterTheSunsetAndRedirectsRenamedRoutes(): Unit = {
    val retired: Response[IO] =
      OrdersApi.serve(Request[IO](uri = uri"https://api.example.com/v1/orders/42"), AfterSunset)
    assertEquals(Status.Gone, retired.status)
    assertEquals("v1 was retired on Sat, 01 Aug 2026 00:00:00 GMT", retired.as[String].unsafeRunSync())
    assertEquals(
      List(uri"/v2/orders/42"),
      retired.headers.get[Link].toList.flatMap(_.values.toList).map(_.uri)
    )

    val renamedRequest: Request[IO] =
      Request[IO](uri = uri"https://api.example.com/v2/purchase-orders/42?expand=lines")
    val moved: Response[IO] = OrdersApi.serve(renamedRequest, AfterSunset)
    assertEquals(Status.PermanentRedirect, moved.status)
    val location: Location = moved.headers.get[Location].getOrElse(throw new AssertionError("no Location"))
    assertEquals("/v2/orders/42?expand=lines", location.uri.renderString)
    assertEquals(
      "https://api.example.com/v2/orders/42?expand=lines",
      renamedRequest.uri.resolve(location.uri).renderString
    )

    val unknown: Response[IO] =
      OrdersApi.serve(Request[IO](uri = uri"https://api.example.com/v3/orders/42"), AfterSunset)
    assertEquals(Status.NotFound, unknown.status)
  }

  @Test
  def rejectsMalformedLifecycleHeaders(): Unit = {
    assertTrue(Sunset.parse("next tuesday").isLeft)
    assertTrue(Deprecation.parse("").isLeft)
    assertTrue(Link.parse("/v2/orders; rel=successor-version").isLeft)

    val garbled: Response[IO] =
      Response[IO](Status.Ok, headers = Headers("Sunset" -> "soon", "Link" -> "nowhere"))
    assertTrue(garbled.headers.get[Sunset].isEmpty)
    assertTrue(garbled.headers.get[Link].isEmpty)
    assertEquals(Some("soon"), garbled.headers.get(ci"Sunset").map(_.head.value))
  }

  /** The day v1 was announced as deprecated. */
  val DeprecatedOn: HttpDate = HttpDate.unsafeFromString("Wed, 01 Jul 2026 00:00:00 GMT")

  /** The day v1 stops answering. */
  val SunsetOn: HttpDate = HttpDate.unsafeFromString("Sat, 01 Aug 2026 00:00:00 GMT")

  /** A server clock reading inside the deprecation window. */
  val BeforeSunset: HttpDate = HttpDate.unsafeFromString("Wed, 15 Jul 2026 09:30:00 GMT")

  /** A server clock reading after v1 was retired. */
  val AfterSunset: HttpDate = HttpDate.unsafeFromString("Mon, 03 Aug 2026 12:00:00 GMT")

  /** The partner client's clock when it reads a deprecated response. */
  val ClientClock: HttpDate = HttpDate.unsafeFromString("Tue, 14 Jul 2026 18:00:00 GMT")

  /** What a partner learns from a deprecated response. */
  final case class MigrationNotice(
      deprecatedOn: HttpDate,
      daysLeft: Long,
      successor: Uri,
      guide: Option[Uri]
  )

  /** The orders API, which serves v2 and keeps v1 alive until its sunset date. */
  object OrdersApi {

    /** Answers one request as the service would at the given clock reading. */
    def serve(request: Request[IO], now: HttpDate): Response[IO] =
      request.uri.path.segments.map(_.decoded()).toList match {
        case List("v2", "orders", id) =>
          Response[IO](Status.Ok).withEntity(s"order $id")
        case List("v2", "purchase-orders", id) =>
          val target: Uri =
            Uri(path = Uri.Path.unsafeFromString(s"/v2/orders/$id"), query = request.uri.query)
          Response[IO](Status.PermanentRedirect).putHeaders(Location(target))
        case List("v1", "orders", id) if now.epochSecond < SunsetOn.epochSecond =>
          Response[IO](Status.Ok)
            .withEntity(s"order $id")
            .putHeaders(
              Deprecation(DeprecatedOn),
              Sunset(SunsetOn),
              Link(successorLink(id), guideLink)
            )
        case List("v1", "orders", id) =>
          Response[IO](Status.Gone)
            .withEntity(s"v1 was retired on ${SunsetOn.renderString}")
            .putHeaders(Link(successorLink(id)))
        case _ =>
          Response[IO](Status.NotFound)
      }

    def successorLink(id: String): LinkValue =
      LinkValue(Uri.unsafeFromString(s"/v2/orders/$id"), rel = Some("successor-version"))

    val guideLink: LinkValue = LinkValue(
      uri"https://docs.example.com/migrate-v2",
      rel = Some("deprecation"),
      `type` = Some(MediaType.text.html)
    )
  }

  /** A partner integration that watches for deprecation notices on the responses it receives. */
  object PartnerClient {

    /** Builds a migration notice from a deprecated response to the given request. */
    def migrationNotice(request: Request[IO], response: Response[IO], now: HttpDate): MigrationNotice = {
      val deprecation: Deprecation =
        response.headers.get[Deprecation].getOrElse(throw new AssertionError("not deprecated"))
      val sunset: Sunset = response.headers.get[Sunset].getOrElse(throw new AssertionError("no sunset"))
      val links: List[LinkValue] = response.headers.get[Link].toList.flatMap(_.values.toList)
      val successor: LinkValue = links
        .find(_.rel.contains("successor-version"))
        .getOrElse(throw new AssertionError("no successor"))
      val secondsLeft: Long = sunset.date.epochSecond - now.epochSecond
      MigrationNotice(
        deprecatedOn = deprecation.date,
        daysLeft = secondsLeft / 86400L,
        successor = request.uri.resolve(successor.uri),
        guide = links.find(_.rel.contains("deprecation")).map(_.uri)
      )
    }
  }
}
