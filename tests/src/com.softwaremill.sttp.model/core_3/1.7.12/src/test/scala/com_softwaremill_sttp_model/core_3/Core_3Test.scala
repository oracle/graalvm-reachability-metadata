/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_model.core_3

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue}
import org.junit.jupiter.api.Test
import sttp.model.*
import sttp.model.Uri.UriContext
import sttp.model.headers.*
import sttp.model.sse.ServerSentEvent

import java.time.Instant
import scala.concurrent.duration.*

class Core_3Test {
  @Test
  def uriParsingBuildingAndEncodingRoundTripsComponents(): Unit = {
    val parsed: Uri = right(Uri.parse("https://user:secret@example.com:8443/api/v1/search?q=hello+world&flag#top"))

    assertEquals(Some("https"), parsed.scheme)
    assertEquals(Some("user"), parsed.userInfo.map(_.username))
    assertEquals(Some(Some("secret")), parsed.userInfo.map(_.password))
    assertEquals(Some("example.com"), parsed.host)
    assertEquals(Some(8443), parsed.port)
    assertEquals(List("api", "v1", "search"), parsed.path)
    assertEquals(Some("hello world"), parsed.params.get("q"))
    assertEquals(Some(Seq.empty[String]), parsed.params.getMulti("flag"))
    assertEquals(Some("top"), parsed.fragment)

    val updated: Uri = parsed
      .host("api.example.com")
      .port(443)
      .withPath("new section", "item/42")
      .withParam("q", "a+b & c")
      .addParam("lang", "scala")
      .fragment("section 1")

    assertEquals(
      "https://user:secret@api.example.com:443/new%20section/item%2F42?q=a%2Bb+%26+c&lang=scala#section%201",
      updated.toString
    )
    assertEquals("https", updated.schemeToString)
    assertEquals("/new%20section/item%2F42", updated.pathToString)
    assertEquals("q=a%2Bb+%26+c&lang=scala", updated.queryToString)
    assertEquals("section%201", updated.fragmentToString)
    assertTrue(updated.isAbsolute)
    assertFalse(updated.isRelative)

    val relative: Uri = Uri.pathRelative(Seq("child"), Seq(Uri.QuerySegment.KeyValue("x", "1")), Some("frag"))
    assertEquals("child?x=1#frag", relative.toString)
    assertEquals("https://user:secret@example.com:8443/api/v1/child?x=1#frag", parsed.resolve(relative).toString)
  }

  @Test
  def uriInterpolatorEncodesInterpolatedValuesByComponent(): Unit = {
    val pathSegment: String = "team a"
    val queryValue: String = "a+b & c"
    val fragment: String = "section 1"

    val interpolated: Uri = uri"https://api.example.com/files/$pathSegment?filter=$queryValue#$fragment"

    assertEquals("https://api.example.com/files/team%20a?filter=a%2Bb+%26+c#section%201", interpolated.toString)
    assertEquals(List("files", "team a"), interpolated.path)
    assertEquals(Some("a+b & c"), interpolated.params.get("filter"))
    assertEquals(Some("section 1"), interpolated.fragment)
  }

  @Test
  def queryParamsPreserveOrderMultiValuesAndEmptyValues(): Unit = {
    val queryParams: QueryParams = QueryParams
      .fromSeq(Seq("a" -> "1", "b" -> "two words", "a" -> "3"))
      .param("empty", Seq.empty)
      .param("c", "4")

    assertEquals(Seq("a" -> Seq("1", "3"), "b" -> Seq("two words"), "empty" -> Seq.empty[String], "c" -> Seq("4")), queryParams.toMultiSeq)
    assertEquals(Some("1"), queryParams.get("a"))
    assertEquals(Some(Seq("1", "3")), queryParams.getMulti("a"))
    assertEquals("a=1&a=3&b=two+words&empty&c=4", queryParams.toString)
    assertEquals("?a=1&a=3&b=two+words&empty&c=4", queryParams.toString(includeBoundary = true))
  }

  @Test
  def mediaTypesContentTypeRangesAndAcceptsSelectBestMatch(): Unit = {
    val json: MediaType = right(MediaType.parse("application/json; charset=utf-8; profile=v1"))
    val text: MediaType = MediaType.TextPlainUtf8
    val xml: MediaType = MediaType.ApplicationXml

    assertEquals("application", json.mainType)
    assertEquals("json", json.subType)
    assertEquals(Some("utf-8"), json.charset)
    assertEquals(Map("profile" -> "v1"), json.otherParameters)
    assertTrue(json.isApplication)
    assertFalse(json.isText)
    assertEquals("application/json; charset=utf-8; profile=v1", json.toString)
    assertTrue(json.equalsIgnoreParameters(MediaType.ApplicationJson.charset("UTF-16")))

    val acceptedRanges: Seq[ContentTypeRange] = right(Accepts.parse(Seq(
      Header.accept("text/plain;q=0.5, application/json;profile=v1;q=0.9"),
      Header.acceptCharset("utf-8;q=0.7, iso-8859-1;q=0.1")
    )))

    assertEquals(Some(json), MediaType.bestMatch(Seq(text, xml, json), acceptedRanges))
    assertTrue(json.matches(ContentTypeRange.exact(json)))
    assertTrue(json.matches(ContentTypeRange.AnyApplication))
    assertFalse(xml.matches(ContentTypeRange.AnyText))
    assertEquals("application/*", ContentTypeRange.AnyApplication.toString)
  }

  @Test
  def headersValidateCompareMaskAndExposeTypedAccessors(): Unit = {
    val instant: Instant = Instant.parse("2024-02-03T04:05:06Z")
    val httpDate: String = Header.toHttpDateString(instant)
    val headers: Headers = Headers(Seq(
      Header.contentType(MediaType.ApplicationJson),
      Header.contentLength(128),
      Header.authorization("Bearer", "secret-token"),
      Header.setCookie(CookieWithMeta("sid", "abc", maxAge = Some(60L), path = Some("/"), httpOnly = true)),
      Header.lastModified(instant)
    ))

    assertEquals(Some("application/json"), headers.contentType)
    assertEquals(Some(128L), headers.contentLength)
    assertEquals(Seq("sid=abc; Max-Age=60; Path=/; HttpOnly"), headers.headers(HeaderNames.SetCookie))
    assertEquals(Seq(CookieWithMeta("sid", "abc", maxAge = Some(60L), path = Some("/"), httpOnly = true)), headers.unsafeCookies)
    assertEquals(instant, right(Header.parseHttpDate(httpDate)))
    assertEquals(Header("authorization", "secret-token"), Header("Authorization", "secret-token"))
    assertTrue(Header("CONTENT-TYPE", "application/json").is(HeaderNames.ContentType))
    assertEquals("Authorization: ***", Header.authorization("Bearer", "secret-token").toStringSafe())
    assertTrue(Header.safeApply("bad header", "value").isLeft)
    assertTrue(Header.safeApply("X-Test", "bad\r\nvalue").isLeft)
  }

  @Test
  def methodsStatusCodesStatusTextsAndMetadataExposeHttpSemantics(): Unit = {
    val customMethod: Method = right(Method.safeApply("merge"))
    val response: ResponseMetadata = ResponseMetadata(StatusCode.Created, "Created", Seq(Header.contentLength(0)))
    val request: RequestMetadata = RequestMetadata(Method.POST, Uri("https", "example.com", Seq("submit")), Seq(Header.userAgent("test")))

    assertEquals(Method("MERGE"), customMethod)
    assertTrue(customMethod.is(Method("merge")))
    assertTrue(Method.isSafe(Method.GET))
    assertTrue(Method.isIdempotent(Method.PUT))
    assertFalse(Method.isSafe(Method.POST))
    assertTrue(Method.safeApply("bad method").isLeft)

    assertEquals(201, response.code.code)
    assertFalse(response.is200)
    assertTrue(response.isSuccess)
    assertFalse(response.isRedirect)
    assertEquals(Some(0L), response.contentLength)
    assertEquals(Some("Created"), StatusText.default(StatusCode.Created))
    assertTrue(StatusCode.Continue.isInformational)
    assertTrue(StatusCode.NotFound.isClientError)
    assertTrue(StatusCode.InternalServerError.isServerError)
    assertTrue(StatusCode.safeApply(99).isLeft)

    assertEquals(Method.POST, request.method)
    assertEquals("https://example.com/submit", request.uri.toString)
    assertEquals(Some("test"), request.header(HeaderNames.UserAgent))
    assertTrue(request.toString.contains("RequestMetadata(POST,https://example.com/submit"))
    assertTrue(response.toString.contains("ResponseMetadata(201,Created"))
  }

  @Test
  def cookiesAndCookieMetadataParseValidateAndRenderDirectives(): Unit = {
    val cookies: List[Cookie] = right(Cookie.parse("theme=light; session=abc123; flag"))
    assertEquals(List(Cookie("theme", "light"), Cookie("session", "abc123"), Cookie("flag", "")), cookies)
    assertEquals("theme=light; session=abc123; flag=", Cookie.toString(cookies))
    assertTrue(Cookie.safeApply("bad name", "value").isLeft)

    val expires: Instant = Instant.parse("2024-01-01T00:00:00Z")
    val cookie: CookieWithMeta = CookieWithMeta("session", "abc", expires = Some(expires), maxAge = Some(3600L),
      domain = Some("example.com"), path = Some("/app"), secure = true, httpOnly = true,
      sameSite = Some(Cookie.SameSite.Strict), otherDirectives = Map("Priority" -> Some("High"), "Partitioned" -> None))

    val rendered: String = cookie.toString
    assertTrue(rendered.startsWith("session=abc; Expires=Mon, 01 Jan 2024 00:00:00 GMT; Max-Age=3600"))
    assertTrue(rendered.contains("; Domain=example.com; Path=/app; Secure; HttpOnly; SameSite=Strict"))
    assertTrue(rendered.contains("; Priority=High"))
    assertTrue(rendered.contains("; Partitioned"))

    val parsed: CookieWithMeta = right(CookieWithMeta.parse(rendered))
    assertEquals("session", parsed.name)
    assertEquals("abc", parsed.value)
    assertEquals(Some(expires), parsed.expires)
    assertEquals(Some(3600L), parsed.maxAge)
    assertEquals(Some(Cookie.SameSite.Strict), parsed.sameSite)
    assertTrue(CookieWithMeta.safeApply("bad name", "value").isLeft)
  }

  @Test
  def cacheAcceptRangeContentRangeAndEntityHeadersRoundTrip(): Unit = {
    val acceptEncoding: AcceptEncoding = right(AcceptEncoding.parse("gzip, br;q=0.8"))
    assertEquals(List(AcceptEncoding.WeightedEncoding("gzip", None), AcceptEncoding.WeightedEncoding("br", Some(BigDecimal("0.8")))), acceptEncoding.encodings)
    assertEquals("gzip,br;q=0.8", acceptEncoding.toString)
    assertEquals(Header(HeaderNames.AcceptEncoding, "gzip,br;q=0.8"), Header.acceptEncoding(acceptEncoding))
    assertTrue(AcceptEncoding.parse("gzip;q=1.5").isLeft)

    val directives: List[CacheDirective] = CacheDirective.unsafeParse("max-age=60, no-cache, stale-if-error=30")
    assertEquals(List(CacheDirective.MaxAge(60.seconds), CacheDirective.NoCache, CacheDirective.StaleIfError(30.seconds)), directives)
    assertEquals(Header(HeaderNames.CacheControl, "max-age=60, no-cache, stale-if-error=30"), Header.cacheControl(directives))

    val ranges: List[Range] = right(Range.parse("bytes=0-99, 200-299"))
    assertEquals(List(Range(Some(0L), Some(99L), ContentRangeUnits.Bytes), Range(Some(200L), Some(299L), ContentRangeUnits.Bytes)), ranges)
    assertTrue(ranges.head.isValid(1000))
    assertEquals(100L, ranges.head.contentLength)
    assertEquals(ContentRange(ContentRangeUnits.Bytes, Some(0L -> 99L), Some(1000L)), ranges.head.toContentRange(1000L))
    assertEquals("bytes 0-99/1000", Header.contentRange(ranges.head.toContentRange(1000L)).value)
    assertTrue(Range.parse("bytes=9-1").isLeft)

    val contentRange: ContentRange = right(ContentRange.parse("items 10-20/*"))
    assertEquals(ContentRange("items", Some(10L -> 20L), None), contentRange)
    assertEquals("items 10-20/*", contentRange.toString)
    assertTrue(ContentRange.safeApply(ContentRangeUnits.Bytes, Some(20L -> 10L), Some(100L)).isLeft)
  }

  @Test
  def hostHeaderParsingSeparatesPortsAndBracketedIpv6Literals(): Unit = {
    assertEquals(("example.com", Some(8443)), Host.parseHostAndPort("example.com:8443"))
    assertEquals(("example.com", None), Host.parseHostAndPort("example.com"))
    assertEquals(("2001:db8:cafe::17", Some(443)), Host.parseHostAndPort("[2001:db8:cafe::17]:443"))
    assertEquals(("2001:db8:cafe::17", None), Host.parseHostAndPort("[2001:db8:cafe::17]"))
    assertEquals(("example.com", None), Host.parseHostAndPort("example.com:not-a-port"))
  }

  @Test
  def forwardingOriginsEtagsAndAuthenticationChallengesParseAndRender(): Unit = {
    val forwarded: List[Forwarded] = right(Forwarded.parse(List(
      "for=192.0.2.60;proto=https;host=example.com",
      "for=\"[2001:db8:cafe::17]\";by=203.0.113.43"
    )))

    assertEquals(Some("192.0.2.60"), forwarded.head.`for`)
    assertEquals(Some("https"), forwarded.head.proto)
    assertEquals(Some("[2001:db8:cafe::17]"), forwarded(1).`for`)
    assertEquals("for=192.0.2.60;host=example.com;proto=https,by=203.0.113.43;for=\"[2001:db8:cafe::17]\"", Forwarded.toString(forwarded))
    assertTrue(Forwarded.parse("broken").isLeft)

    assertEquals("null", Origin.Null.toString)
    assertEquals("https://example.com:8443", Origin.Host("https", "example.com", Some(8443)).toString)
    assertEquals(Header(HeaderNames.Origin, "https://example.com:8443"), Header.origin(Origin.Host("https", "example.com", Some(8443))))

    val eTags: List[ETag] = right(ETag.parseList("\"abc\", W/\"weak,value\""))
    assertEquals(List(ETag("abc"), ETag("weak,value", weak = true)), eTags)
    assertEquals("\"abc\", W/\"weak,value\"", ETag.toString(eTags))
    assertTrue(ETag.parse("abc").isLeft)

    val challenge: WWWAuthenticateChallenge = right(WWWAuthenticateChallenge.parseSingle("Bearer realm=\"api\""))
      .charset("UTF-8")
      .addParam("error", "invalid_token")
    assertEquals("Bearer", challenge.scheme)
    assertEquals(Some("api"), challenge.realm)
    assertEquals(Some("UTF-8"), challenge.charset)
    assertEquals("Bearer realm=\"api\", charset=\"UTF-8\", error=\"invalid_token\"", challenge.toString)
    assertTrue(WWWAuthenticateChallenge.parseSingle("Unsupported realm=\"api\"").isLeft)
  }

  @Test
  def multipartPartsExposeHeadersDispositionAndEscaping(): Unit = {
    val part: Part[String] = Part("upload", "content", contentType = Some(MediaType.TextPlain), fileName = Some("report \"final\".txt"))
      .dispositionParam("creation-date", "2024-01-01")
      .header("X-Part", "one")
      .header("X-Part", "two")
      .header(Header.contentType(MediaType.ApplicationJson), replaceExisting = true)

    assertEquals("upload", part.name)
    assertEquals("content", part.body)
    assertEquals(Some("report \"final\".txt"), part.fileName)
    assertEquals(Some("application/json"), part.contentType)
    assertEquals(Seq("one", "two"), part.headers("X-Part"))
    val disposition: String = part.contentDispositionHeaderValue
    assertTrue(disposition.startsWith("form-data; name=\"upload\"; "))
    assertTrue(disposition.contains("filename=\"report \\\"final\\\".txt\""))
    assertTrue(disposition.contains("creation-date=\"2024-01-01\""))
  }

  @Test
  def serverSentEventsParseAndRenderMultilineData(): Unit = {
    val event: ServerSentEvent = ServerSentEvent.parse(List(
      "event: update",
      "id: 42",
      "retry: 1500",
      "data: first line",
      "data: second line",
      "ignored: field"
    ))

    assertEquals(Some("update"), event.eventType)
    assertEquals(Some("42"), event.id)
    assertEquals(Some(1500), event.retry)
    assertEquals(Some("first line\nsecond line"), event.data)
    assertEquals("data: first line\ndata: second line\nevent: update\nid: 42\nretry: 1500", event.toString)

    val emptyFields: ServerSentEvent = ServerSentEvent.parse(List("data", "event", "id", "retry: not-a-number"))
    assertEquals(Some(""), emptyFields.data)
    assertEquals(Some(""), emptyFields.eventType)
    assertEquals(Some(""), emptyFields.id)
    assertEquals(None, emptyFields.retry)
  }

  @Test
  def httpVersionsAndHeaderFactoriesExposeConstants(): Unit = {
    assertEquals(HttpVersion.HTTP_1_1, HttpVersion.HTTP_1_1)
    assertEquals("GET, POST", Header.accessControlAllowMethods(Method.GET, Method.POST).value)
    assertEquals("X-Token, X-Trace", Header.accessControlAllowHeaders("X-Token", "X-Trace").value)
    assertEquals("client, proxy", Header.xForwardedFor("client", "proxy").value)
    assertEquals("Basic realm=\"admin\"", Header.wwwAuthenticate(WWWAuthenticateChallenge.basic("admin")).value)
    assertEquals(List(Header(HeaderNames.WwwAuthenticate, "Basic"), Header(HeaderNames.WwwAuthenticate, "Bearer")),
      Header.wwwAuthenticate(WWWAuthenticateChallenge.basic, WWWAuthenticateChallenge.bearer))
    assertEquals("/next", Header.location(Uri.relative(Seq("next"))).value)
  }

  private def right[A](either: Either[String, A]): A = either match {
    case Right(value) => value
    case Left(error)  => throw new AssertionError(s"Expected Right but got Left($error)")
  }
}
