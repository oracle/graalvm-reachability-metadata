/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_core_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.Header
import org.http4s.Headers
import org.http4s.Method
import org.http4s.Request
import org.http4s.Response
import org.http4s.Status
import org.http4s.Uri
import org.http4s.headers.`Access-Control-Allow-Credentials`
import org.http4s.headers.`Access-Control-Allow-Headers`
import org.http4s.headers.`Access-Control-Allow-Methods`
import org.http4s.headers.`Access-Control-Expose-Headers`
import org.http4s.headers.`Access-Control-Max-Age`
import org.http4s.headers.`Access-Control-Request-Method`
import org.http4s.headers.Origin
import org.http4s.syntax.literals.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.typelevel.ci.*

import scala.concurrent.duration.*

/**
 * Cross-origin resource sharing with the `Origin` and `Access-Control-*` headers.
 *
 * A comments widget API is embedded by partner sites: it answers browser preflight requests from
 * allow-listed origins with the methods, request headers, and cache lifetime it accepts, decorates
 * actual cross-origin responses with credential and exposed-header grants, and refuses origins,
 * methods, and headers outside its policy. A browser-side check reads the same grants back to
 * decide whether it may send the real request.
 */
class CrossOriginWidgetApiTest {

  @Test
  def answersAPreflightFromAnAllowListedPartner(): Unit = {
    val preflight: Request[IO] = Request[IO](
      method = Method.OPTIONS,
      uri = uri"https://widgets.example.com/threads/7/comments",
      headers = Headers(
        "Origin" -> "https://blog.partner.org",
        "Access-Control-Request-Method" -> "PUT",
        "Access-Control-Request-Headers" -> "content-type, x-widget-token"
      )
    )
    assertEquals(
      Some(Origin.Host(Uri.Scheme.https, Uri.RegName("blog.partner.org"), None)),
      preflight.headers.get[Origin]
    )
    assertEquals(
      Some(Method.PUT),
      preflight.headers.get[`Access-Control-Request-Method`].map(_.method)
    )

    val response: Response[IO] = WidgetApi.serve(preflight)
    assertEquals(Status.NoContent, response.status)
    assertEquals(
      Some("https://blog.partner.org"),
      response.headers.get(ci"Access-Control-Allow-Origin").map(_.head.value)
    )
    assertEquals(
      Some(Set(Method.GET, Method.POST, Method.PUT, Method.DELETE)),
      response.headers.get[`Access-Control-Allow-Methods`].map(_.methods)
    )
    assertEquals(
      Some(List(ci"Content-Type", ci"X-Widget-Token")),
      response.headers.get[`Access-Control-Allow-Headers`].map(_.values)
    )
    val maxAge: `Access-Control-Max-Age` =
      response.headers.get[`Access-Control-Max-Age`].getOrElse(throw new AssertionError("no max age"))
    assertEquals(`Access-Control-Max-Age`.Cache(600L), maxAge)
    assertEquals(Some("600"), response.headers.get(ci"Access-Control-Max-Age").map(_.head.value))
    assertTrue(response.headers.get[`Access-Control-Allow-Credentials`.type].isDefined)
    assertEquals(Some("Origin"), response.headers.get(ci"Vary").map(_.head.value))

    val decision: BrowserDecision = Browser.evaluatePreflight(
      origin = PartnerBlog,
      method = Method.PUT,
      requestHeaders = List(ci"content-type", ci"x-widget-token"),
      preflightResponse = response
    )
    assertEquals(BrowserDecision(allowed = true, cacheFor = Some(10.minutes)), decision)
  }

  @Test
  def decoratesActualCrossOriginResponsesWithGrants(): Unit = {
    val request: Request[IO] = Request[IO](
      method = Method.GET,
      uri = uri"https://widgets.example.com/threads/7/comments"
    ).putHeaders(PartnerShop: Origin)
    assertEquals(Some("https://shop.partner.org:8443"), request.headers.get(ci"Origin").map(_.head.value))

    val response: Response[IO] = WidgetApi.serve(request)
    assertEquals(Status.Ok, response.status)
    assertEquals("""[{"id":1,"text":"first!"}]""", response.as[String].unsafeRunSync())
    assertEquals(
      Some("https://shop.partner.org:8443"),
      response.headers.get(ci"Access-Control-Allow-Origin").map(_.head.value)
    )
    assertEquals(
      Some(List(ci"X-Total-Count", ci"X-Next-Page")),
      response.headers.get[`Access-Control-Expose-Headers`].map(_.values)
    )
    assertEquals(Some("true"), response.headers.get(ci"Access-Control-Allow-Credentials").map(_.head.value))
    assertEquals(Some("1"), response.headers.get(ci"X-Total-Count").map(_.head.value))

    val sameOrigin: Response[IO] =
      WidgetApi.serve(Request[IO](uri = uri"https://widgets.example.com/threads/7/comments"))
    assertEquals(Status.Ok, sameOrigin.status)
    assertTrue(sameOrigin.headers.get(ci"Access-Control-Allow-Origin").isEmpty)
    assertTrue(sameOrigin.headers.get[`Access-Control-Expose-Headers`].isEmpty)
  }

  @Test
  def refusesOriginsMethodsAndHeadersOutsideThePolicy(): Unit = {
    val stranger: Response[IO] = WidgetApi.serve(
      Request[IO](method = Method.OPTIONS, uri = uri"https://widgets.example.com/threads/7/comments")
        .putHeaders(
          Origin.Host(Uri.Scheme.https, Uri.RegName("evil.example.net"), None): Origin,
          `Access-Control-Request-Method`(Method.POST)
        )
    )
    assertEquals(Status.Forbidden, stranger.status)
    assertTrue(stranger.headers.get(ci"Access-Control-Allow-Origin").isEmpty)
    assertFalse(
      Browser.evaluatePreflight(
        Origin.Host(Uri.Scheme.https, Uri.RegName("evil.example.net"), None),
        Method.POST,
        Nil,
        stranger
      ).allowed
    )

    val patchPreflight: Request[IO] =
      Request[IO](method = Method.OPTIONS, uri = uri"https://widgets.example.com/threads/7/comments")
        .putHeaders(PartnerBlog: Origin, `Access-Control-Request-Method`(Method.PATCH))
    val patch: Response[IO] = WidgetApi.serve(patchPreflight)
    assertEquals(Status.Forbidden, patch.status)
    assertEquals("method PATCH is not allowed", patch.as[String].unsafeRunSync())

    val customHeader: Response[IO] = WidgetApi.serve(
      patchPreflight
        .putHeaders(`Access-Control-Request-Method`(Method.POST))
        .putHeaders(Header.Raw(ci"Access-Control-Request-Headers", "X-Debug-Trace"))
    )
    assertEquals(Status.Forbidden, customHeader.status)
    assertEquals("header x-debug-trace is not allowed", customHeader.as[String].unsafeRunSync())

    val sandboxed: Response[IO] = WidgetApi.serve(
      Request[IO](method = Method.OPTIONS, uri = uri"https://widgets.example.com/threads/7/comments")
        .putHeaders(Origin.`null`: Origin, `Access-Control-Request-Method`(Method.GET))
    )
    assertEquals(Status.Forbidden, sandboxed.status)
    assertEquals("opaque origins are not allowed", sandboxed.as[String].unsafeRunSync())

    val accepted: Response[IO] =
      WidgetApi.serve(patchPreflight.putHeaders(`Access-Control-Request-Method`(Method.DELETE)))
    assertFalse(
      Browser.evaluatePreflight(PartnerBlog, Method.PATCH, Nil, accepted).allowed
    )
    assertFalse(
      Browser.evaluatePreflight(PartnerBlog, Method.DELETE, List(ci"X-Debug-Trace"), accepted).allowed
    )
  }

  @Test
  def parsesAndRejectsWireValues(): Unit = {
    assertEquals(Right(Origin.`null`), Origin.parse("null"))
    assertEquals(
      Right(Origin.Host(Uri.Scheme.http, Uri.RegName("localhost"), Some(3000))),
      Origin.parse("http://localhost:3000")
    )
    assertEquals(uri"https://shop.partner.org:8443", PartnerShop.toUri)
    assertTrue(Origin.parse("blog.partner.org").isLeft)

    assertEquals(
      Right(Set(Method.GET, Method.HEAD)),
      `Access-Control-Allow-Methods`.parse("GET, HEAD").map(_.methods)
    )
    assertEquals(Right(`Access-Control-Max-Age`.NoCaching), `Access-Control-Max-Age`.parse("-1"))
    assertEquals(
      Some(5.seconds),
      `Access-Control-Max-Age`.unsafeFromLong(5L) match {
        case cache: `Access-Control-Max-Age`.Cache => cache.duration
        case `Access-Control-Max-Age`.NoCaching => None
      }
    )
    assertTrue(`Access-Control-Max-Age`.fromLong(-7L).isLeft)
    assertTrue(`Access-Control-Request-Method`.parse("NOT A METHOD").isLeft)
    assertTrue(`Access-Control-Allow-Credentials`.parse("false").isLeft)
  }

  /** A partner blog that embeds the widget. */
  val PartnerBlog: Origin.Host = Origin.Host(Uri.Scheme.https, Uri.RegName("blog.partner.org"), None)

  /** A partner shop that serves its storefront on a non-default port. */
  val PartnerShop: Origin.Host = Origin.Host(Uri.Scheme.https, Uri.RegName("shop.partner.org"), Some(8443))

  /** What a browser concludes from a preflight response. */
  final case class BrowserDecision(allowed: Boolean, cacheFor: Option[FiniteDuration])

  /** The widget API and the cross-origin policy it enforces. */
  object WidgetApi {

    val allowedOrigins: Set[Origin] = Set(PartnerBlog, PartnerShop)
    val allowedMethods: Set[Method] = Set(Method.GET, Method.POST, Method.PUT, Method.DELETE)
    val allowedHeaders: List[CIString] = List(ci"Content-Type", ci"X-Widget-Token")
    val exposedHeaders: List[CIString] = List(ci"X-Total-Count", ci"X-Next-Page")

    /** Answers one request as the service would. */
    def serve(request: Request[IO]): Response[IO] =
      request.headers.get[Origin] match {
        case None => comments(request)
        case Some(origin) if request.method == Method.OPTIONS => preflight(request, origin)
        case Some(origin) if allowedOrigins.contains(origin) =>
          comments(request).putHeaders(
            allowOrigin(origin),
            `Access-Control-Allow-Credentials`,
            `Access-Control-Expose-Headers`(exposedHeaders),
            "Vary" -> "Origin"
          )
        case Some(_) => comments(request)
      }

    def preflight(request: Request[IO], origin: Origin): Response[IO] = {
      val requestedHeaders: List[CIString] = request.headers
        .get(ci"Access-Control-Request-Headers")
        .toList
        .flatMap(_.toList)
        .flatMap(_.value.split(",").toList)
        .map(_.trim)
        .filter(_.nonEmpty)
        .map(CIString(_))
      val requestedMethod: Option[Method] =
        request.headers.get[`Access-Control-Request-Method`].map(_.method)
      origin match {
        case Origin.`null` =>
          Response[IO](Status.Forbidden).withEntity("opaque origins are not allowed")
        case host if !allowedOrigins.contains(host) =>
          Response[IO](Status.Forbidden)
        case host =>
          requestedMethod.filterNot(allowedMethods.contains) match {
            case Some(method) =>
              Response[IO](Status.Forbidden).withEntity(s"method ${method.name} is not allowed")
            case None =>
              requestedHeaders.find(header => !allowedHeaders.contains(header)) match {
                case Some(header) =>
                  Response[IO](Status.Forbidden)
                    .withEntity(s"header ${header.toString.toLowerCase} is not allowed")
                case None =>
                  Response[IO](Status.NoContent).putHeaders(
                    allowOrigin(host),
                    `Access-Control-Allow-Methods`(allowedMethods),
                    `Access-Control-Allow-Headers`(allowedHeaders),
                    `Access-Control-Max-Age`.unsafeFromDuration(10.minutes),
                    `Access-Control-Allow-Credentials`,
                    "Vary" -> "Origin"
                  )
              }
          }
      }
    }

    def allowOrigin(origin: Origin): Header.Raw = origin match {
      case host: Origin.Host => Header.Raw(ci"Access-Control-Allow-Origin", host.renderString)
      case Origin.`null` => Header.Raw(ci"Access-Control-Allow-Origin", "null")
    }

    def comments(request: Request[IO]): Response[IO] =
      Response[IO](Status.Ok)
        .withEntity("""[{"id":1,"text":"first!"}]""")
        .putHeaders("X-Total-Count" -> "1")
  }

  /** The checks a browser runs on a preflight response before sending the real request. */
  object Browser {

    def evaluatePreflight(
        origin: Origin.Host,
        method: Method,
        requestHeaders: List[CIString],
        preflightResponse: Response[IO]
    ): BrowserDecision = {
      val originGranted: Boolean = preflightResponse.headers
        .get(ci"Access-Control-Allow-Origin")
        .exists(_.head.value == origin.renderString)
      val methodGranted: Boolean = preflightResponse.headers
        .get[`Access-Control-Allow-Methods`]
        .exists(_.methods.contains(method))
      val grantedHeaders: List[CIString] =
        preflightResponse.headers.get[`Access-Control-Allow-Headers`].toList.flatMap(_.values)
      val headersGranted: Boolean = requestHeaders.forall(grantedHeaders.contains)
      val cacheFor: Option[FiniteDuration] =
        preflightResponse.headers.get[`Access-Control-Max-Age`].flatMap {
          case cache: `Access-Control-Max-Age`.Cache => cache.duration
          case `Access-Control-Max-Age`.NoCaching => None
        }
      BrowserDecision(originGranted && methodGranted && headersGranted, cacheFor)
    }
  }
}
