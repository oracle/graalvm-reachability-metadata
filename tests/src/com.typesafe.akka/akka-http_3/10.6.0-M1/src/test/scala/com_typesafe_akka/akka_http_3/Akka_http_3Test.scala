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
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.FormData
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.MessageEntity
import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.Credentials
import akka.http.scaladsl.unmarshalling.MultipartUnmarshallers._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.SystemMaterializer
import akka.stream.scaladsl.Sink
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.concurrent.Await
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

class AkkaHttp3Test {
  private val timeout: FiniteDuration = 10.seconds

  @Test
  def modelBuildersUriRenderingAndEntityMarshallersRoundTrip(): Unit = withActorSystem { (system: ActorSystem) =>
    implicit val actorSystem: ActorSystem = system
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher
    implicit val materializer: Materializer = SystemMaterializer(system).materializer

    val uri: Uri = Uri("/api/items").withQuery(Uri.Query("q" -> "graal native", "page" -> "1"))
    val request: HttpRequest = HttpRequest(
      method = HttpMethods.POST,
      uri = uri,
      headers = List(RawHeader("X-Correlation-Id", "abc-123")),
      entity = HttpEntity(ContentTypes.`application/json`, """{"message":"hello"}"""))

    assertThat(request.method).isEqualTo(HttpMethods.POST)
    assertThat(request.uri.path.toString()).isEqualTo("/api/items")
    assertThat(request.uri.query().get("q").get).isEqualTo("graal native")
    assertThat(request.headers.head.name()).isEqualTo("X-Correlation-Id")

    val strictRequestEntity: HttpEntity.Strict = await(request.entity.toStrict(timeout))
    assertThat(strictRequestEntity.contentType).isEqualTo(ContentTypes.`application/json`)
    assertThat(strictRequestEntity.data.utf8String).contains("hello")

    val textEntity: MessageEntity = await(Marshal("hello native image").to[MessageEntity])
    assertThat(await(Unmarshal(textEntity).to[String])).isEqualTo("hello native image")

    val formEntity: HttpEntity = FormData("first" -> "one", "space" -> "two words").toEntity
    assertThat(formEntity.contentType.mediaType.value).isEqualTo("application/x-www-form-urlencoded")
    val formBody: String = await(Unmarshal(formEntity).to[String])
    assertThat(formBody).contains("first=one")
    assertThat(formBody).contains("space=two")
  }

  @Test
  def routesHandleParametersEntitiesHeadersAndValidationRejections(): Unit = withActorSystem { (system: ActorSystem) =>
    implicit val actorSystem: ActorSystem = system
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher
    implicit val materializer: Materializer = SystemMaterializer(system).materializer

    val route: Route = concat(
      path("greet") {
        get {
          parameter("name".withDefault("anonymous")) { name =>
            respondWithHeader(RawHeader("X-Greeting", "generated")) {
              complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, s"hello $name"))
            }
          }
        }
      },
      path("echo") {
        post {
          entity(as[String]) { body =>
            complete(HttpResponse(entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, body.reverse)))
          }
        }
      },
      path("sum") {
        get {
          parameters("a".as[Int], "b".as[Int]) { (a: Int, b: Int) =>
            complete((a + b).toString)
          }
        }
      })

    val handler: HttpRequest => Future[HttpResponse] = Route.toFunction(route)

    val greeting: HttpResponse = await(handler(HttpRequest(uri = "/greet?name=Akka")))
    assertThat(greeting.status).isEqualTo(StatusCodes.OK)
    assertThat(greeting.headers.find(_.is("x-greeting")).map(_.value()).get).isEqualTo("generated")
    assertThat(responseText(greeting)).isEqualTo("hello Akka")

    val echo: HttpResponse = await(handler(HttpRequest(
      method = HttpMethods.POST,
      uri = "/echo",
      entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, "abcdef"))))
    assertThat(echo.status).isEqualTo(StatusCodes.OK)
    assertThat(responseText(echo)).isEqualTo("fedcba")

    val sum: HttpResponse = await(handler(HttpRequest(uri = "/sum?a=7&b=5")))
    assertThat(sum.status).isEqualTo(StatusCodes.OK)
    assertThat(responseText(sum)).isEqualTo("12")

    val malformedParameter: HttpResponse = await(handler(HttpRequest(uri = "/sum?a=seven&b=5")))
    assertThat(malformedParameter.status).isEqualTo(StatusCodes.BadRequest)
    assertThat(responseText(malformedParameter)).contains("a")
  }

  @Test
  def multipartFormDataRouteParsesFieldsAndFileMetadata(): Unit = withActorSystem { (system: ActorSystem) =>
    implicit val actorSystem: ActorSystem = system
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher
    implicit val materializer: Materializer = SystemMaterializer(system).materializer

    val route: Route = path("upload") {
      post {
        entity(as[Multipart.FormData]) { formData =>
          val strictParts: Future[Seq[Multipart.FormData.BodyPart.Strict]] =
            formData.parts.mapAsync(1)(_.toStrict(timeout)).runWith(Sink.seq)
          onSuccess(strictParts) { parts =>
            val summary: String = parts
              .sortBy(_.name)
              .map { part =>
                val fileName: String = part.filename.getOrElse("field")
                s"${part.name}:$fileName:${part.entity.data.utf8String}"
              }
              .mkString("|")
            complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, summary))
          }
        }
      }
    }
    val handler: HttpRequest => Future[HttpResponse] = Route.toFunction(route)
    val multipart: Multipart.FormData.Strict = Multipart.FormData.Strict(Seq(
      Multipart.FormData.BodyPart.Strict("description", HttpEntity("native multipart upload")),
      Multipart.FormData.BodyPart.Strict(
        "file",
        HttpEntity(ContentTypes.`text/plain(UTF-8)`, "file-contents"),
        Map("filename" -> "notes.txt"))))

    val response: HttpResponse = await(handler(HttpRequest(
      method = HttpMethods.POST,
      uri = "/upload",
      entity = multipart.toEntity)))

    assertThat(response.status).isEqualTo(StatusCodes.OK)
    assertThat(responseText(response))
      .isEqualTo("description:field:native multipart upload|file:notes.txt:file-contents")
  }

  @Test
  def basicAuthenticationDirectiveAcceptsCredentialsAndRejectsUnauthorizedRequests(): Unit =
    withActorSystem { (system: ActorSystem) =>
      implicit val actorSystem: ActorSystem = system
      implicit val executionContext: ExecutionContextExecutor = system.dispatcher
      implicit val materializer: Materializer = SystemMaterializer(system).materializer

      val authenticator: Credentials => Option[String] = {
        case provided: Credentials.Provided
            if provided.identifier == "alice" && provided.verify("wonderland") =>
          Some(provided.identifier)
        case _ => None
      }
      val route: Route = path("secure") {
        authenticateBasic("secure-area", authenticator) { (user: String) =>
          complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, s"authenticated:$user"))
        }
      }
      val handler: HttpRequest => Future[HttpResponse] = Route.toFunction(route)

      val authenticated: HttpResponse = await(handler(HttpRequest(
        uri = "/secure",
        headers = List(Authorization(BasicHttpCredentials("alice", "wonderland"))))))
      assertThat(authenticated.status).isEqualTo(StatusCodes.OK)
      assertThat(responseText(authenticated)).isEqualTo("authenticated:alice")

      val missingCredentials: HttpResponse = await(handler(HttpRequest(uri = "/secure")))
      assertThat(missingCredentials.status).isEqualTo(StatusCodes.Unauthorized)
      val challenge: String = missingCredentials.headers.find(_.is("www-authenticate")).map(_.value()).get
      assertThat(challenge).contains("Basic")
      assertThat(challenge).contains("secure-area")
      val missingCredentialsBody: String = responseText(missingCredentials)
      assertThat(missingCredentialsBody).isNotEmpty()

      val rejectedCredentials: HttpResponse = await(handler(HttpRequest(
        uri = "/secure",
        headers = List(Authorization(BasicHttpCredentials("alice", "wrong-password"))))))
      assertThat(rejectedCredentials.status).isEqualTo(StatusCodes.Unauthorized)
      val rejectedCredentialsBody: String = responseText(rejectedCredentials)
      assertThat(rejectedCredentialsBody).isNotEmpty()
    }

  @Test
  def boundServerAndClientExchangeStrictHttpResponses(): Unit = withActorSystem { (system: ActorSystem) =>
    implicit val actorSystem: ActorSystem = system
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher
    implicit val materializer: Materializer = SystemMaterializer(system).materializer

    val route: Route = concat(
      path("ping") {
        get {
          complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "pong"))
        }
      },
      path("created" / Segment) { id =>
        post {
          respondWithHeader(RawHeader("X-Created-Id", id)) {
            complete(StatusCodes.Created)
          }
        }
      })

    val http = Http()
    val binding: Http.ServerBinding = await(http.newServerAt("127.0.0.1", 0).bind(Route.toFunction(route)))
    try {
      val port: Int = binding.localAddress.getPort

      val pingResponse: HttpResponse = await(http.singleRequest(HttpRequest(uri = s"http://127.0.0.1:$port/ping")))
      assertThat(pingResponse.status).isEqualTo(StatusCodes.OK)
      assertThat(responseText(pingResponse)).isEqualTo("pong")

      val createdResponse: HttpResponse = await(http.singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"http://127.0.0.1:$port/created/42")))
      assertThat(createdResponse.status).isEqualTo(StatusCodes.Created)
      assertThat(createdResponse.headers.find(_.is("x-created-id")).map(_.value()).get).isEqualTo("42")
      await(createdResponse.discardEntityBytes().future())
    } finally {
      await(binding.unbind())
      await(http.shutdownAllConnectionPools())
    }
  }

  private def responseText(response: HttpResponse)(implicit
    executionContext: ExecutionContextExecutor,
    materializer: Materializer): String =
    await(Unmarshal(response.entity).to[String])

  private def withActorSystem(test: ActorSystem => Unit): Unit = {
    val system: ActorSystem = ActorSystem("akka-http-3-reachability-test")
    try {
      test(system)
    } finally {
      Await.result(system.terminate(), timeout)
    }
  }

  private def await[A](future: Future[A]): A = Await.result(future, timeout)
}
