/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_http_2_13

import java.net.InetSocketAddress
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.SeqHasAsJava

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.ContentTypes
import org.apache.pekko.http.scaladsl.model.FormData
import org.apache.pekko.http.scaladsl.model.HttpEntity
import org.apache.pekko.http.scaladsl.model.HttpHeader
import org.apache.pekko.http.scaladsl.model.HttpMethods
import org.apache.pekko.http.scaladsl.model.HttpRequest
import org.apache.pekko.http.scaladsl.model.HttpResponse
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.Uri
import org.apache.pekko.http.scaladsl.model.headers.Cookie
import org.apache.pekko.http.scaladsl.model.headers.HttpCookie
import org.apache.pekko.http.scaladsl.model.headers.HttpCookiePair
import org.apache.pekko.http.scaladsl.model.headers.RawHeader
import org.apache.pekko.http.scaladsl.model.headers.`Set-Cookie`
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.PathMatchers.Segment
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.SystemMaterializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Pekko_http_2_13Test {
  @Test
  def parsesAndTransformsUrisWithoutLosingEncodedComponents(): Unit = {
    val uri: Uri = Uri("https://user:pass@example.com:8443/api/items/42?tag=scala&tag=http&empty=#details")
    val updated: Uri = uri
      .withPath(Uri.Path("/api/items/99"))
      .withQuery(Uri.Query("tag" -> "native image", "page" -> "1"))

    assertThat(uri.scheme).isEqualTo("https")
    assertThat(uri.authority.host.address).isEqualTo("example.com")
    assertThat(uri.authority.port).isEqualTo(8443)
    assertThat(uri.path.toString()).isEqualTo("/api/items/42")
    assertThat(uri.query().getAll("tag").asJava).containsExactlyInAnyOrder("scala", "http")
    assertThat(uri.fragment).isEqualTo(Some("details"))
    assertThat(updated.path.toString()).isEqualTo("/api/items/99")
    assertThat(updated.query().get("tag")).isEqualTo(Some("native image"))
    assertThat(updated.query().get("page")).isEqualTo(Some("1"))
    assertThat(updated.fragment).isEqualTo(Some("details"))
  }

  @Test
  def createsRequestsResponsesCookiesAndFormEntities(): Unit = {
    val entity: HttpEntity.Strict = HttpEntity(ContentTypes.`application/json`, """{"name":"pekko","active":true}""")
    val request: HttpRequest = HttpRequest(
      method = HttpMethods.POST,
      uri = Uri("/submit?debug=true"),
      headers = List(RawHeader("X-Trace-Id", "trace-123"), Cookie(HttpCookiePair("theme", "dark"))),
      entity = entity)
    val cookie: HttpCookie = HttpCookie("session", "abc123", path = Some("/"), secure = true, httpOnly = true)
    val response: HttpResponse = HttpResponse(
      status = StatusCodes.Created,
      headers = List(`Set-Cookie`(cookie)),
      entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, "created"))
    val formEntity: HttpEntity.Strict = FormData("name" -> "Pekko HTTP", "symbol" -> "λ").toEntity.asInstanceOf[HttpEntity.Strict]

    assertThat(request.method).isEqualTo(HttpMethods.POST)
    assertThat(request.uri.query().get("debug")).isEqualTo(Some("true"))
    assertThat(request.headers.find(_.is("x-trace-id")).map(_.value)).isEqualTo(Some("trace-123"))
    assertThat(entity.contentType).isEqualTo(ContentTypes.`application/json`)
    assertThat(entity.data.utf8String).contains("\"active\":true")

    assertThat(response.status).isEqualTo(StatusCodes.Created)
    assertThat(response.status.isSuccess).isTrue
    assertThat(response.headers.head.value).contains("session=abc123")
    assertThat(response.headers.head.value).contains("HttpOnly")
    assertThat(response.entity.asInstanceOf[HttpEntity.Strict].data.utf8String).isEqualTo("created")

    assertThat(formEntity.contentType).isEqualTo(ContentTypes.`application/x-www-form-urlencoded`)
    assertThat(formEntity.data.utf8String).contains("name=Pekko+HTTP")
    assertThat(formEntity.data.utf8String).contains("symbol=%CE%BB")
  }

  @Test
  def parsesTypedAndRawHeadersWithErrorsReportedSeparately(): Unit = {
    val accept: HttpHeader = parseHeader("Accept", "application/json;q=0.8, text/plain")
    val cacheControl: HttpHeader = parseHeader("Cache-Control", "no-cache, max-age=60")
    val custom: HttpHeader = parseHeader("X-Request-Mode", "integration")
    val invalidResult: HttpHeader.ParsingResult = HttpHeader.parse("Content-Length", "not-a-number")

    assertThat(accept.name).isEqualTo("Accept")
    assertThat(accept.value).contains("application/json").contains("q=0.8").contains("text/plain")
    assertThat(accept.renderInRequests).isTrue
    assertThat(cacheControl.name).isEqualTo("Cache-Control")
    assertThat(cacheControl.value).contains("max-age=60")
    assertThat(custom).isInstanceOf(classOf[RawHeader])
    assertThat(custom.lowercaseName).isEqualTo("x-request-mode")

    invalidResult match {
      case HttpHeader.ParsingResult.Error(error) =>
        assertThat(error.summary).isNotEmpty
        assertThat(error.formatPretty).isNotEmpty
      case HttpHeader.ParsingResult.Ok(header, errors) =>
        assertThat(header.name).isEqualTo("Content-Length")
        assertThat(errors.asJava).isNotEmpty
        assertThat(errors.map(_.formatPretty).mkString("\n")).isNotEmpty
    }
  }

  @Test
  def materializesStrictAndChunkedEntitiesWithBoundedTimeouts(): Unit = {
    withActorSystem("entities") { implicit system: ActorSystem =>
      implicit val materializer: Materializer = SystemMaterializer(system).materializer
      val chunked: HttpEntity.Chunked = HttpEntity.Chunked.fromData(
        ContentTypes.`text/plain(UTF-8)`,
        Source(List(ByteString("hello "), ByteString("from "), ByteString("chunks"))))
      val strict: HttpEntity.Strict = Await.result(chunked.toStrict(3.seconds), 3.seconds)
      val limited: HttpEntity.Strict = Await.result(strict.withSizeLimit(64).toStrict(3.seconds), 3.seconds)

      assertThat(chunked.isKnownEmpty).isFalse
      assertThat(strict.contentType).isEqualTo(ContentTypes.`text/plain(UTF-8)`)
      assertThat(strict.data.utf8String).isEqualTo("hello from chunks")
      assertThat(limited.contentLengthOption).isEqualTo(Some(17L))
    }
  }

  @Test
  def servesAndConsumesARouteThroughThePublicHttpApi(): Unit = {
    withActorSystem("route") { implicit system: ActorSystem =>
      implicit val materializer: Materializer = SystemMaterializer(system).materializer
      implicit val executionContext: ExecutionContext = system.dispatcher
      val route: Route = path("hello" / Segment) { name =>
        get {
          parameter("shout".?) { shout =>
            val greeting: String = if (shout.contains("true")) s"HELLO ${name.toUpperCase}" else s"Hello $name"
            respondWithHeader(RawHeader("X-Test-Route", "matched")) {
              complete(HttpResponse(entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, greeting)))
            }
          }
        }
      }
      val binding = Await.result(Http().newServerAt("127.0.0.1", 0).bind(route), 10.seconds)

      try {
        val address: InetSocketAddress = binding.localAddress
        val request: HttpRequest = HttpRequest(uri = s"http://${address.getHostString}:${address.getPort}/hello/pekko?shout=true")
        val response: HttpResponse = Await.result(Http().singleRequest(request), 10.seconds)
        val body: HttpEntity.Strict = Await.result(response.entity.toStrict(3.seconds), 3.seconds)

        assertThat(response.status).isEqualTo(StatusCodes.OK)
        assertThat(response.headers.find(_.is("x-test-route")).map(_.value)).isEqualTo(Some("matched"))
        assertThat(body.data.utf8String).isEqualTo("HELLO PEKKO")
      } finally {
        Await.result(binding.unbind().flatMap(_ => Http().shutdownAllConnectionPools()), 10.seconds)
      }
    }
  }

  private def parseHeader(name: String, value: String): HttpHeader = {
    HttpHeader.parse(name, value) match {
      case HttpHeader.ParsingResult.Ok(header, errors) =>
        assertThat(errors.asJava).isEmpty
        header
      case HttpHeader.ParsingResult.Error(error) =>
        throw new AssertionError(s"Expected $name header to parse, but got ${error.formatPretty}")
    }
  }

  private def withActorSystem[T](name: String)(body: ActorSystem => T): T = {
    val system: ActorSystem = ActorSystem(s"pekko-http-$name")
    try {
      body(system)
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }
}
