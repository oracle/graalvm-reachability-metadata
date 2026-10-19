/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_core_3

import cats.effect.IO
import org.http4s.CacheDirective
import org.http4s.Charset
import org.http4s.EntityTag
import org.http4s.Headers
import org.http4s.MediaType
import org.http4s.Method
import org.http4s.QValue
import org.http4s.Request
import org.http4s.Response
import org.http4s.Status
import org.http4s.headers.Accept
import org.http4s.headers.ETag
import org.http4s.headers.`Cache-Control`
import org.http4s.headers.`Content-Length`
import org.http4s.headers.`Content-Type`
import org.http4s.headers.`If-None-Match`
import org.http4s.syntax.literals.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.typelevel.ci.*

import scala.concurrent.duration.*

/**
 * Negotiates content and conditional caching with the typed header model in `org.http4s.headers`.
 *
 * A reporting service reads the raw `Accept` and `If-None-Match` headers of a download request,
 * picks the best representation it can produce, and answers with typed `Content-Type`, `ETag`,
 * and `Cache-Control` headers, or with `304 Not Modified` when the client copy is current.
 */
class ReportDownloadHeadersTest {

  @Test
  def picksTheHighestRankedRepresentationTheServiceCanProduce(): Unit = {
    val request: Request[IO] = Request[IO](
      method = Method.GET,
      uri = uri"/reports/2026-q3",
      headers = Headers(
        "Accept" -> "text/html;q=0.4, application/xml;q=0.9, text/csv;q=0.8, */*;q=0.1"
      )
    )

    val accept: Accept = request.headers.get[Accept].getOrElse(throw new AssertionError("no Accept"))
    assertEquals(4, accept.values.size)
    assertEquals(MediaType.text.csv, chooseRepresentation(accept))

    val jsonFirst: Accept =
      Accept.parse("application/json, text/csv;q=0.5").fold(failure => throw failure, identity)
    assertEquals(MediaType.application.json, chooseRepresentation(jsonFirst))

    val onlyWildcard: Accept = Accept.parse("*/*").fold(failure => throw failure, identity)
    assertEquals(Producible.head, chooseRepresentation(onlyWildcard))
  }

  @Test
  def answersAFreshDownloadWithTypedRepresentationHeaders(): Unit = {
    val request: Request[IO] = Request[IO](uri = uri"/reports/2026-q3")
      .putHeaders(Accept(MediaType.text.csv))
    val response: Response[IO] = serve(request, ReportVersion)

    assertEquals(Status.Ok, response.status)
    val contentType: `Content-Type` =
      response.headers.get[`Content-Type`].getOrElse(throw new AssertionError("no Content-Type"))
    assertEquals(MediaType.text.csv, contentType.mediaType)
    assertEquals(Some(Charset.`UTF-8`), contentType.charset)
    assertEquals(Some(EntityTag(ReportVersion)), response.headers.get[ETag].map(_.tag))

    val cacheControl: `Cache-Control` =
      response.headers.get[`Cache-Control`].getOrElse(throw new AssertionError("no Cache-Control"))
    assertTrue(cacheControl.values.toList.contains(CacheDirective.`max-age`(5.minutes)))
    assertTrue(cacheControl.values.toList.contains(CacheDirective.`private`()))

    assertEquals(Some("text/csv; charset=UTF-8"), rawValue(response.headers, ci"Content-Type"))
    assertEquals(Some("\"" + ReportVersion + "\""), rawValue(response.headers, ci"ETag"))
    assertEquals(Some("max-age=300, private"), rawValue(response.headers, ci"cache-control"))
  }

  @Test
  def answersNotModifiedWhenTheClientHoldsTheCurrentVersion(): Unit = {
    val revalidation: Request[IO] = Request[IO](
      uri = uri"/reports/2026-q3",
      headers = Headers(
        "Accept" -> "text/csv",
        "If-None-Match" -> s"W/\"draft-7\", \"$ReportVersion\""
      )
    )
    val notModified: Response[IO] = serve(revalidation, ReportVersion)
    assertEquals(Status.NotModified, notModified.status)
    assertEquals(Some(EntityTag(ReportVersion)), notModified.headers.get[ETag].map(_.tag))
    assertTrue(notModified.headers.get[`Content-Type`].isEmpty)

    val stale: Request[IO] = Request[IO](uri = uri"/reports/2026-q3")
      .putHeaders(`If-None-Match`(EntityTag("v41")), Accept(MediaType.text.csv))
    assertEquals(Status.Ok, serve(stale, ReportVersion).status)

    val anyVersion: Request[IO] = Request[IO](uri = uri"/reports/2026-q3")
      .putHeaders(`If-None-Match`.`*`)
    assertEquals(Status.NotModified, serve(anyVersion, ReportVersion).status)
  }

  @Test
  def reportsMalformedHeadersInsteadOfFailingTheRequest(): Unit = {
    val headers: Headers = Headers("Content-Length" -> "-5", "ETag" -> "unquoted")

    assertTrue(headers.getWithWarnings[`Content-Length`].exists(_.isLeft))
    assertTrue(headers.get[ETag].isEmpty)
    assertEquals(Some("unquoted"), rawValue(headers, ci"etag"))
    assertTrue(ETag.parse("\"r-2026-q3\"").isRight)
  }

  /** The entity tag of the report revision the service currently serves. */
  val ReportVersion: String = "r-2026-q3-v42"

  /** The representations the reporting service can render, in its own order of preference. */
  val Producible: List[MediaType] =
    List(MediaType.application.json, MediaType.text.csv, MediaType.application.pdf)

  /** Picks the producible media type the client ranks highest, or the service default. */
  def chooseRepresentation(accept: Accept): MediaType = {
    val ranked = for {
      produced <- Producible
      ranges = accept.values.toList.filter(range => produced.satisfies(range.mediaRange))
      if ranges.nonEmpty
      best = ranges.maxBy(_.qValue)
      if best.qValue.isAcceptable
    } yield (produced, best.qValue)
    ranked.sortBy(_._2)(using Ordering[QValue].reverse).headOption.map(_._1).getOrElse(Producible.head)
  }

  /** Serves the report, honouring content negotiation and `If-None-Match` revalidation. */
  def serve(request: Request[IO], version: String): Response[IO] = {
    val tag: EntityTag = EntityTag(version)
    val clientHasCurrent: Boolean = request.headers.get[`If-None-Match`].exists { ifNoneMatch =>
      ifNoneMatch.tags.forall(_.exists(_.tag == tag.tag))
    }
    if (clientHasCurrent) {
      Response[IO](Status.NotModified).putHeaders(ETag(tag))
    } else {
      val mediaType: MediaType =
        request.headers.get[Accept].map(chooseRepresentation).getOrElse(Producible.head)
      Response[IO](Status.Ok).putHeaders(
        `Content-Type`(mediaType, Charset.`UTF-8`),
        ETag(tag),
        `Cache-Control`(CacheDirective.`max-age`(5.minutes), CacheDirective.`private`())
      )
    }
  }

  /** Returns the rendered value of a header the way it goes out on the wire. */
  def rawValue(headers: Headers, name: CIString): Option[String] =
    headers.get(name).map(_.head.value)
}
