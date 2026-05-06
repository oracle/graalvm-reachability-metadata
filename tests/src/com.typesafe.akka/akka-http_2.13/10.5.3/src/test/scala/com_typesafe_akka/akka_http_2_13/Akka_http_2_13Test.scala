/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_http_2_13

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.FormData
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.model.MessageEntity
import akka.http.scaladsl.model.RequestEntity
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.headers.HttpCookie
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.headers.`Set-Cookie`
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.SystemMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Akka_http_2_13Test {
  private val Timeout: FiniteDuration = 10.seconds

  private implicit val system: ActorSystem = ActorSystem("akka-http-reachability-test")
  private implicit val materializer: Materializer = SystemMaterializer(system).materializer
  private implicit val executionContext: ExecutionContext = system.dispatcher

  @AfterAll
  def shutdownActorSystem(): Unit = {
    Await.result(system.terminate(), Timeout)
  }

  @Test
  def routesBindToALocalServerAndHandleClientRequests(): Unit = {
    val route: Route =
      pathPrefix("items" / Segment) { itemId =>
        concat(
          get {
            parameter("echo".?) { echo =>
              respondWithHeader(RawHeader("X-Item-Id", itemId)) {
                val body: String = s"""{"id":"$itemId","echo":"${echo.getOrElse("")}"}"""
                complete(HttpEntity(ContentTypes.`application/json`, body))
              }
            }
          },
          post {
            entity(as[String]) { body =>
              complete(StatusCodes.Created -> s"$itemId:$body")
            }
          }
        )
      }

    val binding = Await.result(Http().newServerAt("127.0.0.1", 0).bind(route), Timeout)
    try {
      val baseUri: String = s"http://${binding.localAddress.getHostString}:${binding.localAddress.getPort}"

      val getResponse = Await.result(Http().singleRequest(HttpRequest(uri = s"$baseUri/items/42?echo=hello")), Timeout)
      val getBody = strictBody(getResponse)
      assertEquals(StatusCodes.OK, getResponse.status)
      assertEquals("42", headerValue(getResponse, "x-item-id"))
      assertTrue(getBody.contains("\"id\":\"42\""))
      assertTrue(getBody.contains("\"echo\":\"hello\""))

      val postEntity: RequestEntity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, "payload")
      val postRequest = HttpRequest(
        uri = s"$baseUri/items/42",
        method = HttpMethods.POST,
        entity = postEntity
      )
      val postResponse = Await.result(Http().singleRequest(postRequest), Timeout)
      val postBody = strictBody(postResponse)
      assertEquals(StatusCodes.Created, postResponse.status)
      assertEquals("42:payload", postBody)

      val notFoundResponse = Await.result(Http().singleRequest(HttpRequest(uri = s"$baseUri/missing")), Timeout)
      val notFoundBody = strictBody(notFoundResponse)
      assertEquals(StatusCodes.NotFound, notFoundResponse.status)
      assertThat(notFoundBody).contains("could not be found")
    } finally {
      Await.result(binding.terminate(2.seconds), Timeout)
    }
  }

  @Test
  def uriModelParsesAuthoritiesPathsQueriesAndFragments(): Unit = {
    val uri: Uri = Uri("https://example.org:8443/api/items/42?name=Akka%20HTTP&enabled=true#section")

    assertEquals("https", uri.scheme)
    assertEquals("example.org", uri.authority.host.address)
    assertEquals(8443, uri.authority.port)
    assertEquals("/api/items/42", uri.path.toString)
    assertEquals(Some("Akka HTTP"), uri.query().get("name"))
    assertEquals(Some("true"), uri.query().get("enabled"))
    assertEquals(Some("section"), uri.fragment)
  }

  @Test
  def marshalAndUnmarshalMessageEntities(): Unit = {
    val textEntity: MessageEntity = Await.result(Marshal("plain text").to[MessageEntity], Timeout)
    val decodedText: String = Await.result(Unmarshal(textEntity).to[String], Timeout)
    assertEquals("plain text", decodedText)

    val jsonEntity: RequestEntity = HttpEntity(ContentTypes.`application/json`, """{"library":"akka-http"}""")
    val decodedJson: String = Await.result(Unmarshal(jsonEntity).to[String], Timeout)
    assertEquals("""{"library":"akka-http"}""", decodedJson)
  }

  @Test
  def formDataProducesUrlEncodedRequestEntities(): Unit = {
    val formData: FormData = FormData("name" -> "Akka HTTP", "symbols" -> "a+b=c")
    val strictEntity = Await.result(formData.toEntity.toStrict(Timeout), Timeout)

    assertEquals(MediaTypes.`application/x-www-form-urlencoded`, strictEntity.contentType.mediaType)
    val encodedForm: String = strictEntity.data.utf8String
    assertTrue(encodedForm.contains("name=Akka+HTTP"))
    assertTrue(encodedForm.contains("symbols=a%2Bb%3Dc"))
  }

  @Test
  def chunkedEntitiesMaterializeIntoStrictEntities(): Unit = {
    val data = Source(List(ByteString("first "), ByteString("second")))
    val entity: HttpEntity.Chunked = HttpEntity.Chunked.fromData(ContentTypes.`text/plain(UTF-8)`, data)

    assertThat(entity.isStrict).isFalse()
    val strictEntity = Await.result(entity.toStrict(Timeout), Timeout)

    assertEquals(ContentTypes.`text/plain(UTF-8)`, strictEntity.contentType)
    assertEquals("first second", strictEntity.data.utf8String)
  }

  @Test
  def parseAndModelHttpHeaders(): Unit = {
    val parsedHeader: HttpHeader = HttpHeader.parse("X-Correlation-Id", "abc-123") match {
      case HttpHeader.ParsingResult.Ok(header, _) => header
      case HttpHeader.ParsingResult.Error(error) => fail(s"Expected header parsing to succeed, but failed with $error")
    }
    val setCookie: `Set-Cookie` = `Set-Cookie`(HttpCookie("session", "abc", path = Some("/"), httpOnly = true))

    assertEquals("x-correlation-id", parsedHeader.lowercaseName())
    assertEquals("abc-123", parsedHeader.value())
    assertEquals("session", setCookie.cookie.name)
    assertEquals("abc", setCookie.cookie.value)
    assertThat(setCookie.cookie.httpOnly).isTrue()
    assertEquals(Some("/"), setCookie.cookie.path)
  }

  private def strictBody(response: HttpResponse): String = {
    Await.result(response.entity.toStrict(Timeout).map(_.data.utf8String), Timeout)
  }

  private def headerValue(response: HttpResponse, lowercaseName: String): String = {
    response.headers.find(_.lowercaseName() == lowercaseName).map(_.value()).getOrElse(fail(s"Missing response header: $lowercaseName"))
  }
}
