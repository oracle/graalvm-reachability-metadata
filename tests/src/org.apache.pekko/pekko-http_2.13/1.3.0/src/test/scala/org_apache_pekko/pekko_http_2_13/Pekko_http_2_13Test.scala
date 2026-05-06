/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_http_2_13

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.client.RequestBuilding._
import org.apache.pekko.http.scaladsl.common.EntityStreamingSupport
import org.apache.pekko.http.scaladsl.marshalling.Marshal
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.headers._
import org.apache.pekko.http.scaladsl.model.sse.ServerSentEvent
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.settings.RoutingSettings
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import org.apache.pekko.stream.{Materializer, SystemMaterializer}
import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.Test

import scala.concurrent.Await
import scala.concurrent.duration._

class Pekko_http_2_13Test {
  @Test
  def uriParsingRequestModelAndTransformersComposeCorrectly(): Unit = {
    val uri = Uri("https://user@example.com:8443/search?q=pekko%20http&lang=en#top")

    assertTrue(uri.isAbsolute)
    assertEquals("https", uri.scheme)
    assertEquals("user", uri.authority.userinfo)
    assertEquals("example.com", uri.authority.host.address)
    assertEquals(8443, uri.effectivePort)
    assertEquals("/search", uri.path.toString())
    assertEquals(Some("pekko http"), uri.query().get("q"))
    assertEquals(Some("en"), uri.query().get("lang"))
    assertEquals(Some("top"), uri.fragment)

    val traceKey = AttributeKey[String]("trace-id")
    val baseRequest = HttpRequest(HttpMethods.PUT, uri = Uri("/items/42"))
      .withEntity(ContentTypes.`application/json`, "{\"name\":\"pekko\"}")
    val addRequestMetadata = addHeader(RawHeader("X-Request-Id", "abc-123")) ~>
      addCredentials(BasicHttpCredentials("user", "secret")) ~>
      addAttribute(traceKey, "trace-1")
    val transformed = addRequestMetadata(baseRequest)

    assertEquals(HttpMethods.PUT, transformed.method)
    assertEquals(Some("trace-1"), transformed.attributes.get(traceKey))
    assertEquals(Some("abc-123"), transformed.header[RawHeader].map(_.value))
    assertEquals(Some("Basic"), transformed.header[Authorization].map(_.credentials.scheme))
    assertTrue(transformed.entity.isStrict)
    assertEquals("{\"name\":\"pekko\"}", strictUtf8(transformed.entity))
  }

  @Test
  def headersCookiesContentTypesAndStatusCodesRenderAndParse(): Unit = {
    val parsedHeader = HttpHeader.parse("Accept", "application/json, text/plain;q=0.5")
    parsedHeader match {
      case HttpHeader.ParsingResult.Ok(header: Accept, errors) =>
        assertTrue(errors.isEmpty)
        assertFalse(header.acceptsAll)
        assertEquals("accept", header.lowercaseName)
        assertTrue(header.value.contains("application/json"))
      case other => throw new AssertionError(s"Expected parsed Accept header, got $other")
    }

    val cookie = HttpCookie("session", "abc")
      .withPath("/")
      .withHttpOnly(true)
      .withSecure(true)
      .withSameSite(SameSite.Lax)
    val setCookie = `Set-Cookie`(cookie)
    assertEquals("Set-Cookie", setCookie.name)
    assertTrue(setCookie.value.contains("session=abc"))
    assertTrue(setCookie.value.contains("HttpOnly"))
    assertTrue(setCookie.value.contains("SameSite=Lax"))

    val request = HttpRequest(headers = List(Cookie(HttpCookiePair("theme", "dark"), HttpCookiePair("mode", "test"))))
    assertEquals(Seq("theme" -> "dark", "mode" -> "test"), request.cookies.map(pair => pair.name -> pair.value))

    val parsedContentType = ContentType.parse("application/json; charset=UTF-8") match {
      case Right(contentType) => contentType
      case Left(errors)      => throw new AssertionError(errors.map(_.formatPretty).mkString("; "))
    }
    assertEquals(MediaTypes.`application/json`, parsedContentType.mediaType)
    assertEquals(Some(HttpCharsets.`UTF-8`), parsedContentType.charsetOption)

    val customType = MediaType.customBinary("application", "x-test-format", MediaType.NotCompressible, List("xtf"))
    assertTrue(customType.isApplication)
    assertFalse(customType.isCompressible)
    assertEquals(ContentType.Binary(customType), customType.toContentType)

    val customStatus = StatusCodes.custom(299, "Nearly Complete", "accepted by upstream", isSuccess = true, allowsEntity = true)
    assertEquals(299, customStatus.intValue)
    assertTrue(customStatus.isSuccess)
    assertTrue(customStatus.allowsEntity)
  }

  @Test
  def requestBuildingMarshalsFormDataAndRemovesModeledHeaders(): Unit = {
    withActorSystem("pekko-http-request-building") { implicit system =>
      implicit val materializer: Materializer = SystemMaterializer(system).materializer
      implicit val executionContext = system.dispatcher

      val request = Post("/submit?dryRun=true", FormData("name" -> "Apache Pekko", "kind" -> "http").toEntity)
      val withHeaders = addHeaders(Host("example.test", 8080), RawHeader("X-Trace", "trace-9"))(request)
      val withoutHost = removeHeader("Host")(withHeaders)

      assertEquals(HttpMethods.POST, withoutHost.method)
      assertEquals(Some("true"), withoutHost.uri.query().get("dryRun"))
      assertFalse(withoutHost.header[Host].isDefined)
      assertEquals(Some("trace-9"), withoutHost.header[RawHeader].map(_.value))
      assertEquals(ContentTypes.`application/x-www-form-urlencoded`, withoutHost.entity.contentType)
      assertEquals("name=Apache+Pekko&kind=http", strictEntityText(withoutHost.entity))
    }
  }

  @Test
  def marshalAndUnmarshalStrictEntitiesRequestsAndResponses(): Unit = {
    withActorSystem("pekko-http-marshalling") { implicit system =>
      implicit val materializer: Materializer = SystemMaterializer(system).materializer
      implicit val executionContext = system.dispatcher

      val entity = Await.result(Marshal("plain text").to[MessageEntity], 5.seconds)
      assertEquals(ContentTypes.`text/plain(UTF-8)`, entity.contentType)
      assertEquals("plain text", strictEntityText(entity))

      val unmarshalled = Await.result(Unmarshal(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "round trip")).to[String], 5.seconds)
      assertEquals("round trip", unmarshalled)

      val response = Await.result(Marshal(StatusCodes.Created -> "created body").to[HttpResponse], 5.seconds)
      assertEquals(StatusCodes.Created, response.status)
      assertEquals("created body", strictUtf8(response.entity))
    }
  }

  @Test
  def sealedRoutesApplyPathParametersQueryParametersEntityUnmarshallingAndRejectionHandling(): Unit = {
    withActorSystem("pekko-http-routing") { implicit system =>
      implicit val executionContext = system.dispatcher

      val route: Route =
        path("items" / IntNumber) { itemId =>
          get {
            parameters("verbose".as[Boolean].?(false)) { verbose =>
              respondWithHeader(RawHeader("X-Item-Id", itemId.toString)) {
                complete(StatusCodes.OK, s"item=$itemId verbose=$verbose")
              }
            }
          } ~
            post {
              entity(as[String]) { body =>
                complete(StatusCodes.Created, body.reverse)
              }
            }
        }

      val handler = Route.toFunction(Route.seal(route))
      val getResponse = Await.result(handler(HttpRequest(uri = "/items/7?verbose=true")), 5.seconds)
      assertEquals(StatusCodes.OK, getResponse.status)
      assertEquals(Some("7"), getResponse.header[RawHeader].map(_.value))
      assertEquals("item=7 verbose=true", strictUtf8(getResponse.entity))

      val postResponse = Await.result(handler(HttpRequest(HttpMethods.POST, uri = "/items/7", entity = HttpEntity("abc"))), 5.seconds)
      assertEquals(StatusCodes.Created, postResponse.status)
      assertEquals("cba", strictUtf8(postResponse.entity))

      val rejectedResponse = Await.result(handler(HttpRequest(uri = "/items/not-a-number")), 5.seconds)
      assertEquals(StatusCodes.NotFound, rejectedResponse.status)
    }
  }

  @Test
  def streamingSupportSettingsAndServerSentEventsExposeExpectedPublicConfiguration(): Unit = {
    val jsonStreaming = EntityStreamingSupport.json(4096)
    assertEquals(ContentTypes.`application/json`, jsonStreaming.contentType)
    assertEquals(1, jsonStreaming.parallelism)
    assertFalse(jsonStreaming.unordered)

    val csvStreaming = EntityStreamingSupport.csv(1024).withParallelMarshalling(parallelism = 2, unordered = true)
    assertEquals(MediaTypes.`text/csv`, csvStreaming.contentType.mediaType)
    assertEquals(2, csvStreaming.parallelism)
    assertTrue(csvStreaming.unordered)

    val settings = RoutingSettings("""
      pekko.http.routing.verbose-error-messages = on
      pekko.http.routing.render-vanity-footer = off
      pekko.http.routing.range-count-limit = 4
      pekko.http.routing.decode-max-size = 1048576
      """)
    assertTrue(settings.verboseErrorMessages)
    assertFalse(settings.renderVanityFooter)
    assertEquals(4, settings.rangeCountLimit)
    assertEquals(1048576L, settings.decodeMaxSize)

    val event = ServerSentEvent("line one\nline two", "update", "id-1")
    assertEquals("line one\nline two", event.data)
    assertEquals(Some("update"), event.eventType)
    assertEquals(Some("id-1"), event.id)
    assertEquals(None, event.retry)
    assertEquals(Some(2500), ServerSentEvent("retry later", 2500).retry)
  }

  private def withActorSystem[T](name: String)(body: ActorSystem => T): T = {
    val system = ActorSystem(name)
    try {
      body(system)
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }

  private def strictEntityText(entity: HttpEntity): String = {
    entity match {
      case HttpEntity.Strict(_, data) => data.utf8String
      case other                      => throw new AssertionError(s"Expected strict entity, got $other")
    }
  }

  private def strictUtf8(entity: ResponseEntity): String = {
    entity match {
      case HttpEntity.Strict(_, data) => data.utf8String
      case other                      => throw new AssertionError(s"Expected strict entity, got $other")
    }
  }

  private def strictUtf8(entity: RequestEntity): String = {
    entity match {
      case HttpEntity.Strict(_, data) => data.utf8String
      case other                      => throw new AssertionError(s"Expected strict entity, got $other")
    }
  }
}
