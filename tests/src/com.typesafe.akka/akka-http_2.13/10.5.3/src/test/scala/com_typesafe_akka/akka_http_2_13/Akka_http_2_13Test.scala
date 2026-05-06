/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_http_2_13

import scala.concurrent.Await
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.FormData
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpMethod
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.MediaRanges
import akka.http.scaladsl.model.MessageEntity
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.model.ws.WebSocketRequest
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.headers.Allow
import akka.http.scaladsl.model.headers.Cookie
import akka.http.scaladsl.model.headers.HttpCookie
import akka.http.scaladsl.model.headers.HttpCookiePair
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.SystemMaterializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Akka_http_2_13Test extends Directives {
  @Test
  def buildsRequestsResponsesUrisAndModeledHeaders(): Unit = {
    val uri: Uri = Uri("https://example.test:8443/inventory/widgets")
      .withQuery(Uri.Query("page" -> "2", "filter" -> "scala http"))
    val acceptHeader: Accept = Accept(MediaRanges.`application/*`)
    val request: HttpRequest = HttpRequest(
      method = HttpMethods.POST,
      uri = uri,
      headers = List(acceptHeader, RawHeader("X-Trace-Id", "trace-123")),
      entity = HttpEntity(ContentTypes.`application/json`, """{"name":"akka","count":2}"""))

    assertThat(request.method).isEqualTo(HttpMethods.POST)
    assertThat(request.uri.scheme).isEqualTo("https")
    assertThat(request.uri.authority.host.address).isEqualTo("example.test")
    assertThat(request.uri.effectivePort).isEqualTo(8443)
    assertThat(request.uri.path.toString()).isEqualTo("/inventory/widgets")
    assertThat(request.uri.query().get("filter")).isEqualTo(Some("scala http"))
    assertThat(request.header[Accept].get.mediaRanges).isEqualTo(List(MediaRanges.`application/*`))
    assertThat(headerValue(request.headers, "X-Trace-Id")).isEqualTo(Some("trace-123"))
    assertThat(request.entity.contentType).isEqualTo(ContentTypes.`application/json`)

    val response: HttpResponse = HttpResponse(StatusCodes.Created)
      .withHeaders(Location(uri), RawHeader("X-Response-Id", "response-456"))
      .withEntity(ContentTypes.`text/plain(UTF-8)`, "created")

    assertThat(response.status).isEqualTo(StatusCodes.Created)
    assertThat(response.status.isSuccess).isTrue
    assertThat(response.header[Location].get.uri).isEqualTo(uri)
    assertThat(headerValue(response.headers, "X-Response-Id")).isEqualTo(Some("response-456"))
  }

  @Test
  def parsesCommonModelValuesAndCookies(): Unit = {
    val parsedUri: Uri = Uri("/search/books?q=akka%20http&tag=scala&tag=native#top")
    val customMethod: HttpMethod = HttpMethod.custom("PROPFIND")
    val sessionCookie: HttpCookie = HttpCookie("session", "abc123")
      .withPath("/")
      .withHttpOnly(true)
      .withSecure(true)
    val cookieHeader: Cookie = Cookie("theme", "dark")

    assertThat(parsedUri.path.toString()).isEqualTo("/search/books")
    assertThat(parsedUri.query().get("q")).isEqualTo(Some("akka http"))
    assertThat(parsedUri.fragment).isEqualTo(Some("top"))
    assertThat(HttpMethods.getForKey("PATCH")).isEqualTo(Some(HttpMethods.PATCH))
    assertThat(customMethod.value).isEqualTo("PROPFIND")
    assertThat(customMethod.isEntityAccepted).isTrue
    assertThat(sessionCookie.path).isEqualTo(Some("/"))
    assertThat(sessionCookie.httpOnly).isTrue
    assertThat(sessionCookie.secure).isTrue
    assertThat(sessionCookie.pair.name).isEqualTo("session")
    assertThat(sessionCookie.pair.value).isEqualTo("abc123")
    assertThat(cookieHeader.cookies.map(pair => pair.name -> pair.value).toMap).isEqualTo(Map("theme" -> "dark"))
  }

  @Test
  def marshalsAndUnmarshalsTextAndFormEntities(): Unit = {
    withActorSystem("akka-http-marshalling") { (system: ActorSystem, materializer: Materializer) =>
      implicit val actorSystem: ActorSystem = system
      implicit val mat: Materializer = materializer
      implicit val executionContext: ExecutionContextExecutor = system.dispatcher

      val marshalledText: MessageEntity = Await.result(Marshal("Akka HTTP").to[MessageEntity], OperationTimeout)
      val unmarshalledText: String = Await.result(Unmarshal(marshalledText).to[String], OperationTimeout)
      val encodedForm: FormData = FormData("language" -> "Scala", "library" -> "Akka HTTP")
      val decodedForm: FormData = Await.result(Unmarshal(encodedForm.toEntity).to[FormData], OperationTimeout)

      assertThat(marshalledText.contentType).isEqualTo(ContentTypes.`text/plain(UTF-8)`)
      assertThat(unmarshalledText).isEqualTo("Akka HTTP")
      assertThat(decodedForm.fields.toMap).isEqualTo(Map("language" -> "Scala", "library" -> "Akka HTTP"))
    }
  }

  @Test
  def encodesAndDecodesGzipPayloadsAndMessages(): Unit = {
    withActorSystem("akka-http-gzip-coding") { (_, materializer: Materializer) =>
      implicit val mat: Materializer = materializer

      val plainText: String = "Akka HTTP supports transparent entity compression"
      val compressedData: ByteString = Gzip.encode(ByteString(plainText))
      val decompressedData: ByteString = Await.result(Gzip.decode(compressedData), OperationTimeout)
      val response: HttpResponse = HttpResponse(entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, plainText))
      val encodedResponse: HttpResponse = Gzip.encodeMessage(response).asInstanceOf[HttpResponse]
      val decodedResponse: HttpResponse = Gzip.decodeMessage(encodedResponse).asInstanceOf[HttpResponse]

      assertThat(compressedData).isNotEqualTo(ByteString(plainText))
      assertThat(decompressedData.utf8String).isEqualTo(plainText)
      assertThat(headerValue(encodedResponse.headers, "Content-Encoding")).isEqualTo(Some("gzip"))
      assertThat(decodedResponse.headers.exists(header => header.name().equalsIgnoreCase("Content-Encoding"))).isFalse
      assertThat(entityText(decodedResponse.entity)).isEqualTo(plainText)
    }
  }

  @Test
  def consumesChunkedStreamingEntities(): Unit = {
    withActorSystem("akka-http-streaming-entity") { (system: ActorSystem, materializer: Materializer) =>
      implicit val mat: Materializer = materializer

      val chunks: Source[ByteString, akka.NotUsed] = Source(List(ByteString("chunk-1"), ByteString("-chunk-2")))
      val entity: HttpEntity.Chunked = HttpEntity.Chunked.fromData(ContentTypes.`text/plain(UTF-8)`, chunks)
      val strictEntity: HttpEntity.Strict = Await.result(entity.toStrict(OperationTimeout), OperationTimeout)

      assertThat(system.name).isEqualTo("akka-http-streaming-entity")
      assertThat(strictEntity.contentType).isEqualTo(ContentTypes.`text/plain(UTF-8)`)
      assertThat(strictEntity.data.utf8String).isEqualTo("chunk-1-chunk-2")
    }
  }

  @Test
  def appliesRoutingDirectivesToRequestsWithoutBindingASocket(): Unit = {
    withActorSystem("akka-http-directives") { (system: ActorSystem, materializer: Materializer) =>
      implicit val mat: Materializer = materializer
      implicit val executionContext: ExecutionContextExecutor = system.dispatcher

      val route: Route =
        pathPrefix("api" / "items" / IntNumber) { id: Int =>
          parameter("verbose".as[Boolean].?) { verbose: Option[Boolean] =>
            headerValueByName("X-Request-Id") { requestId: String =>
              post {
                entity(as[String]) { body: String =>
                  val responseBody: String = s"id=$id,verbose=${verbose.getOrElse(false)},request=$requestId,body=$body"
                  complete(HttpResponse(status = StatusCodes.Accepted, entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, responseBody)))
                }
              }
            }
          }
        } ~
          path("session") {
            cookie("session") { session: HttpCookiePair =>
              complete(session.value)
            }
          }
      val handler: HttpRequest => Future[HttpResponse] = Route.toFunction(Route.seal(route))(system)

      val postRequest: HttpRequest = HttpRequest(
        method = HttpMethods.POST,
        uri = "/api/items/42?verbose=true",
        headers = List(RawHeader("X-Request-Id", "req-1")),
        entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, "payload"))
      val postResponse: HttpResponse = Await.result(handler(postRequest), OperationTimeout)
      val cookieResponse: HttpResponse = Await.result(
        handler(HttpRequest(uri = "/session", headers = List(Cookie("session", "abc123")))),
        OperationTimeout)

      assertThat(postResponse.status).isEqualTo(StatusCodes.Accepted)
      assertThat(entityText(postResponse.entity)).isEqualTo("id=42,verbose=true,request=req-1,body=payload")
      assertThat(cookieResponse.status).isEqualTo(StatusCodes.OK)
      assertThat(entityText(cookieResponse.entity)).isEqualTo("abc123")
    }
  }

  @Test
  def sealsRouteRejectionsIntoHttpResponses(): Unit = {
    withActorSystem("akka-http-rejections") { (system: ActorSystem, materializer: Materializer) =>
      implicit val mat: Materializer = materializer

      val route: Route = path("only-get") {
        get {
          complete("read")
        }
      }
      val handler: HttpRequest => Future[HttpResponse] = Route.toFunction(Route.seal(route))(system)
      val notFound: HttpResponse = Await.result(handler(HttpRequest(uri = "/missing")), OperationTimeout)
      val wrongMethod: HttpResponse = Await.result(
        handler(HttpRequest(method = HttpMethods.POST, uri = "/only-get")),
        OperationTimeout)
      val allowHeader: Allow = wrongMethod.header[Allow].get

      assertThat(notFound.status).isEqualTo(StatusCodes.NotFound)
      assertThat(entityText(notFound.entity)).contains("The requested resource could not be found")
      assertThat(wrongMethod.status).isEqualTo(StatusCodes.MethodNotAllowed)
      assertThat(allowHeader.methods).isEqualTo(List(HttpMethods.GET))
    }
  }

  @Test
  def exchangesStrictTextMessagesOverWebSockets(): Unit = {
    withActorSystem("akka-http-websocket") { (system: ActorSystem, materializer: Materializer) =>
      implicit val actorSystem: ActorSystem = system
      implicit val mat: Materializer = materializer
      implicit val executionContext: ExecutionContextExecutor = system.dispatcher

      val route: Route = path("ws" / Segment) { prefix: String =>
        val websocketFlow: Flow[Message, Message, akka.NotUsed] = Flow[Message].collect {
          case TextMessage.Strict(text) => TextMessage.Strict(s"$prefix:${text.reverse}")
        }
        handleWebSocketMessages(websocketFlow)
      }
      val binding: Http.ServerBinding = Await.result(Http().newServerAt("127.0.0.1", 0).bind(route), OperationTimeout)

      try {
        val port: Int = binding.localAddress.getPort
        val incomingMessage: Future[String] = Source
          .single(TextMessage.Strict("scala"))
          .via(Http().webSocketClientFlow(WebSocketRequest(s"ws://127.0.0.1:$port/ws/native")))
          .collect { case TextMessage.Strict(text) => text }
          .runWith(Sink.head)

        assertThat(Await.result(incomingMessage, OperationTimeout)).isEqualTo("native:alacs")
      } finally {
        Await.result(binding.terminate(OperationTimeout), OperationTimeout)
      }
    }
  }

  @Test
  def servesAndConsumesLoopbackRequestsWithTheHighLevelApi(): Unit = {
    withActorSystem("akka-http-loopback") { (system: ActorSystem, materializer: Materializer) =>
      implicit val actorSystem: ActorSystem = system
      implicit val mat: Materializer = materializer
      implicit val executionContext: ExecutionContextExecutor = system.dispatcher

      val route: Route = path("greet" / Segment) { name: String =>
        get {
          parameter("title".?) { title: Option[String] =>
            respondWithHeader(RawHeader("X-Test-Server", "akka-http")) {
              complete(s"hello ${title.fold("")(value => value + " ")}$name")
            }
          }
        }
      }
      val binding: Http.ServerBinding = Await.result(Http().newServerAt("127.0.0.1", 0).bind(route), OperationTimeout)

      try {
        val port: Int = binding.localAddress.getPort
        val response: HttpResponse = Await.result(
          Http().singleRequest(HttpRequest(uri = Uri(s"http://127.0.0.1:$port/greet/Scala?title=Dr"))),
          OperationTimeout)

        assertThat(response.status).isEqualTo(StatusCodes.OK)
        assertThat(headerValue(response.headers, "X-Test-Server")).isEqualTo(Some("akka-http"))
        assertThat(entityText(response.entity)).isEqualTo("hello Dr Scala")
      } finally {
        Await.result(binding.terminate(OperationTimeout), OperationTimeout)
      }
    }
  }

  private def withActorSystem[T](name: String)(body: (ActorSystem, Materializer) => T): T = {
    val system: ActorSystem = ActorSystem(name)
    val materializer: Materializer = SystemMaterializer(system).materializer
    try {
      body(system, materializer)
    } finally {
      Await.result(system.terminate(), OperationTimeout)
    }
  }

  private def entityText(entity: HttpEntity)(implicit materializer: Materializer): String = {
    Await.result(entity.toStrict(OperationTimeout), OperationTimeout).data.utf8String
  }

  private def headerValue(headers: Seq[HttpHeader], name: String): Option[String] = {
    headers.find(header => header.name().equalsIgnoreCase(name)).map(_.value())
  }

  private val OperationTimeout: FiniteDuration = 5.seconds
}
