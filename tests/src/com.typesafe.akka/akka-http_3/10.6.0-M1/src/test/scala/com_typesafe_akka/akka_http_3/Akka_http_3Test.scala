/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_http_3

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.*
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials, RawHeader}
import akka.http.scaladsl.server.Directives.*
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.util.ByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.jdk.CollectionConverters.*

class Akka_http_3Test {
  private val TestTimeout: FiniteDuration = 10.seconds

  @Test
  def modelParsesUrisHeadersAndBuildsRequests(): Unit = {
    val uri: Uri = Uri("https://example.com:8443/api/v1/search?name=Akka%20HTTP&tag=a&tag=b#section")
    val query: Uri.Query = uri.query()
    val authorization: Authorization = Authorization(BasicHttpCredentials("alice", "secret"))
    val request: HttpRequest = HttpRequest(
      method = HttpMethods.POST,
      uri = uri,
      entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, "payload")
    ).withHeaders(authorization, RawHeader("X-Correlation-Id", "corr-123"))

    assertThat(uri.scheme).isEqualTo("https")
    assertThat(uri.authority.host.address).isEqualTo("example.com")
    assertThat(uri.authority.port).isEqualTo(8443)
    assertThat(uri.path.toString).isEqualTo("/api/v1/search")
    assertThat(query.get("name").getOrElse("missing")).isEqualTo("Akka HTTP")
    assertThat(query.getAll("tag").asJava).containsExactlyInAnyOrder("a", "b")
    assertThat(uri.fragment.getOrElse("missing")).isEqualTo("section")
    assertThat(request.method).isEqualTo(HttpMethods.POST)
    assertThat(request.entity.contentType).isEqualTo(ContentTypes.`text/plain(UTF-8)`)
    assertThat(request.headers.find(_.is("x-correlation-id")).map(_.value).getOrElse("missing"))
      .isEqualTo("corr-123")
    assertThat(request.headers.exists(_.is("authorization"))).isTrue

    HttpHeader.parse("Cache-Control", "no-cache") match {
      case ParsingResult.Ok(header, errors) =>
        assertThat(header.name).isEqualTo("Cache-Control")
        assertThat(header.value).isEqualTo("no-cache")
        assertThat(errors.isEmpty).isTrue
      case ParsingResult.Error(error) =>
        throw new AssertionError(s"Expected header to parse successfully, but failed with: ${error.summary}")
    }
  }

  @Test
  def marshalsUnmarshalsEntitiesAndFormData(): Unit = withActorSystem { (_, materializer, executionContext) =>
    given Materializer = materializer
    given ExecutionContext = executionContext

    val jsonEntity: ResponseEntity = HttpEntity(
      ContentTypes.`application/json`,
      ByteString("""{"message":"hello","count":2}""")
    )
    val strictJson: HttpEntity.Strict = await(jsonEntity.toStrict(TestTimeout))
    val marshalledText: RequestEntity = await(Marshal("plain text body").to[RequestEntity])
    val unmarshalledText: String = await(Unmarshal(marshalledText).to[String])
    val formEntity: RequestEntity = await(Marshal(FormData(Map("color" -> "blue", "size" -> "large"))).to[RequestEntity])
    val strictForm: HttpEntity.Strict = await(formEntity.toStrict(TestTimeout))

    assertThat(strictJson.contentType).isEqualTo(ContentTypes.`application/json`)
    assertThat(strictJson.data.utf8String).contains("\"message\":\"hello\"")
    assertThat(unmarshalledText).isEqualTo("plain text body")
    assertThat(strictForm.contentType.mediaType).isEqualTo(MediaTypes.`application/x-www-form-urlencoded`)
    assertThat(strictForm.data.utf8String).contains("color=blue")
    assertThat(strictForm.data.utf8String).contains("size=large")
  }

  @Test
  def marshalsAndUnmarshalsMultipartFormData(): Unit = withActorSystem { (_, materializer, executionContext) =>
    given Materializer = materializer
    given ExecutionContext = executionContext

    val descriptionEntity: HttpEntity.Strict = HttpEntity(
      ContentTypes.`text/plain(UTF-8)`,
      "quarterly report"
    )
    val fileEntity: HttpEntity.Strict = HttpEntity(
      ContentTypes.`application/octet-stream`,
      ByteString("file-content")
    )
    val descriptionPart: Multipart.FormData.BodyPart.Strict =
      Multipart.FormData.BodyPart.Strict("description", descriptionEntity)
    val uploadPart: Multipart.FormData.BodyPart.Strict =
      Multipart.FormData.BodyPart.Strict("upload", fileEntity, Map("filename" -> "report.txt"))
    val multipart: Multipart.FormData = Multipart.FormData(descriptionPart, uploadPart)
    val requestEntity: RequestEntity = multipart.toEntity("native-test-boundary")
    val rawBody: String = await(Unmarshal(requestEntity).to[String])
    val parsedForm: Multipart.FormData = await(Unmarshal(requestEntity).to[Multipart.FormData])
    val strictForm: Multipart.FormData.Strict = await(parsedForm.toStrict(TestTimeout))
    val partsByName: Map[String, Multipart.FormData.BodyPart.Strict] =
      strictForm.strictParts.map((part: Multipart.FormData.BodyPart.Strict) => part.name -> part).toMap

    assertThat(requestEntity.contentType.mediaType.isMultipart).isTrue
    assertThat(requestEntity.contentType.mediaType.mainType).isEqualTo("multipart")
    assertThat(requestEntity.contentType.mediaType.subType).isEqualTo("form-data")
    assertThat(rawBody).contains("name=\"description\"")
    assertThat(rawBody).contains("name=\"upload\"")
    assertThat(rawBody).contains("filename=\"report.txt\"")
    assertThat(partsByName.keySet.asJava).containsExactlyInAnyOrder("description", "upload")
    assertThat(partsByName("description").entity.contentType).isEqualTo(ContentTypes.`text/plain(UTF-8)`)
    assertThat(partsByName("description").entity.data.utf8String).isEqualTo("quarterly report")
    assertThat(partsByName("upload").additionalDispositionParams.getOrElse("filename", "missing"))
      .isEqualTo("report.txt")
    assertThat(partsByName("upload").entity.contentType).isEqualTo(ContentTypes.`application/octet-stream`)
    assertThat(partsByName("upload").entity.data.utf8String).isEqualTo("file-content")
  }

  @Test
  def routesRequestsWithDirectivesAndHandlesRejections(): Unit = withActorSystem { (system, materializer, executionContext) =>
    given Materializer = materializer
    given ExecutionContext = executionContext

    val route: Route = concat(
      path("health") {
        get {
          complete(StatusCodes.OK -> "healthy")
        }
      },
      path("sum") {
        get {
          parameters("a".as[Int], "b".as[Int]) { (a: Int, b: Int) =>
            complete((a + b).toString)
          }
        }
      },
      path("echo" / Segment) { (id: String) =>
        post {
          headerValueByName("X-Token") { (token: String) =>
            entity(as[String]) { (body: String) =>
              respondWithHeader(RawHeader("X-Echoed-Id", id)) {
                complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, s"$id:$token:$body"))
              }
            }
          }
        }
      }
    )
    val handler: HttpRequest => Future[HttpResponse] = Route.toFunction(Route.seal(route))(system)

    val healthResponse: HttpResponse = await(handler(HttpRequest(uri = "/health")))
    val healthBody: String = await(Unmarshal(healthResponse.entity).to[String])
    val sumResponse: HttpResponse = await(handler(HttpRequest(uri = "/sum?a=7&b=5")))
    val sumBody: String = await(Unmarshal(sumResponse.entity).to[String])
    val echoResponse: HttpResponse = await(handler(HttpRequest(
      method = HttpMethods.POST,
      uri = "/echo/42",
      headers = List(RawHeader("X-Token", "route-token")),
      entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, "route-body")
    )))
    val echoBody: String = await(Unmarshal(echoResponse.entity).to[String])
    val missingHeaderResponse: HttpResponse = await(handler(HttpRequest(
      method = HttpMethods.POST,
      uri = "/echo/42",
      entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, "route-body")
    )))

    assertThat(healthResponse.status).isEqualTo(StatusCodes.OK)
    assertThat(healthBody).isEqualTo("healthy")
    assertThat(sumResponse.status).isEqualTo(StatusCodes.OK)
    assertThat(sumBody).isEqualTo("12")
    assertThat(echoResponse.status).isEqualTo(StatusCodes.OK)
    assertThat(echoResponse.headers.find(_.is("x-echoed-id")).map(_.value).getOrElse("missing"))
      .isEqualTo("42")
    assertThat(echoBody).isEqualTo("42:route-token:route-body")
    assertThat(missingHeaderResponse.status).isEqualTo(StatusCodes.BadRequest)
    await(missingHeaderResponse.discardEntityBytes()(materializer).future())
  }

  @Test
  def localServerAndClientRoundTripStrictEntities(): Unit = withActorSystem { (system, materializer, executionContext) =>
    given Materializer = materializer
    given ExecutionContext = executionContext

    val handler: HttpRequest => Future[HttpResponse] = { (request: HttpRequest) =>
      request.entity.toStrict(TestTimeout).map { (strictEntity: HttpEntity.Strict) =>
        val responseText: String = s"${request.method.value}:${request.uri.path}:${strictEntity.data.utf8String}"
        HttpResponse(
          status = StatusCodes.Created,
          headers = List(RawHeader("X-Server-Test", "akka-http")),
          entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, responseText)
        )
      }
    }
    val http = Http(system)
    val binding = await(http.newServerAt("127.0.0.1", 0).withMaterializer(materializer).bind(handler))

    try {
      val endpoint: String = s"http://127.0.0.1:${binding.localAddress.getPort}/round-trip"
      val response: HttpResponse = await(http.singleRequest(HttpRequest(
        method = HttpMethods.PUT,
        uri = endpoint,
        entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, "client-body")
      )))
      val responseBody: String = await(Unmarshal(response.entity).to[String])

      assertThat(response.status).isEqualTo(StatusCodes.Created)
      assertThat(response.headers.find(_.is("x-server-test")).map(_.value).getOrElse("missing"))
        .isEqualTo("akka-http")
      assertThat(responseBody).isEqualTo("PUT:/round-trip:client-body")
    } finally {
      await(binding.unbind())
    }
  }

  private def withActorSystem(testCode: (ActorSystem, Materializer, ExecutionContext) => Unit): Unit = {
    val system: ActorSystem = ActorSystem(s"akka-http-3-test-${System.nanoTime()}")
    val materializer: Materializer = Materializer(system)
    try {
      testCode(system, materializer, system.dispatcher)
    } finally {
      materializer.shutdown()
      Await.result(system.terminate(), TestTimeout)
    }
  }

  private def await[T](future: Future[T]): T = Await.result(future, TestTimeout)
}
