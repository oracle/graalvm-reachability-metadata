/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_http_3

import com.typesafe.config.ConfigFactory
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.client.RequestBuilding
import org.apache.pekko.http.scaladsl.coding.Coders
import org.apache.pekko.http.scaladsl.marshalling.Marshal
import org.apache.pekko.http.scaladsl.marshalling.sse.EventStreamMarshalling.*
import org.apache.pekko.http.scaladsl.model.ContentTypes
import org.apache.pekko.http.scaladsl.model.FormData
import org.apache.pekko.http.scaladsl.model.HttpEntity
import org.apache.pekko.http.scaladsl.model.HttpMethods
import org.apache.pekko.http.scaladsl.model.HttpRequest
import org.apache.pekko.http.scaladsl.model.HttpResponse
import org.apache.pekko.http.scaladsl.model.RequestEntity
import org.apache.pekko.http.scaladsl.model.ResponseEntity
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.Uri
import org.apache.pekko.http.scaladsl.model.headers.RawHeader
import org.apache.pekko.http.scaladsl.model.sse.ServerSentEvent
import org.apache.pekko.http.scaladsl.server.Directives
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import org.apache.pekko.http.scaladsl.unmarshalling.sse.EventStreamUnmarshalling.*
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.SystemMaterializer
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.concurrent.Await
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters.*

class Pekko_http_3Test extends Directives {
  private val Timeout: FiniteDuration = 10.seconds

  @Test
  def routeEvaluatesPathQueryHeaderAndEntityDirectives(): Unit =
    withSystem("pekko-http-route-test") { system =>
      implicit val actorSystem: ActorSystem = system
      implicit val materializer: Materializer = SystemMaterializer(system).materializer
      implicit val executionContext: ExecutionContextExecutor = system.dispatcher

      val route: Route = concat(
        path("items" / Segment / IntNumber) { (category: String, id: Int) =>
          get {
            parameters("verbose".as[Boolean].withDefault(false)) { verbose =>
              headerValueByName("X-Trace-Id") { traceId =>
                complete(s"item=$category/$id verbose=$verbose trace=$traceId")
              }
            }
          }
        },
        path("echo") {
          post {
            entity(as[String]) { body =>
              complete(HttpResponse(
                StatusCodes.Created,
                entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, s"echo:$body")))
            }
          }
        })

      val handler: HttpRequest => Future[HttpResponse] = Route.toFunction(route)
      val getRequest: HttpRequest = HttpRequest(uri = Uri("/items/books/42?verbose=true"))
        .withHeaders(RawHeader("X-Trace-Id", "trace-123"))
      val getResponse: HttpResponse = await(handler(getRequest))

      assertThat(getResponse.status).isEqualTo(StatusCodes.OK)
      assertThat(strictText(getResponse.entity)).isEqualTo("item=books/42 verbose=true trace=trace-123")

      val postRequest: HttpRequest = HttpRequest(
        method = HttpMethods.POST,
        uri = Uri("/echo"),
        entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, "payload"))
      val postResponse: HttpResponse = await(handler(postRequest))

      assertThat(postResponse.status).isEqualTo(StatusCodes.Created)
      assertThat(strictText(postResponse.entity)).isEqualTo("echo:payload")
    }

  @Test
  def requestBuildingMarshallingAndUnmarshallingRoundTripFormsAndText(): Unit =
    withSystem("pekko-http-marshalling-test") { system =>
      implicit val actorSystem: ActorSystem = system
      implicit val materializer: Materializer = SystemMaterializer(system).materializer
      implicit val executionContext: ExecutionContextExecutor = system.dispatcher

      val form: FormData = FormData("name" -> "Apache Pekko", "feature" -> "http routing")
      val request: HttpRequest = RequestBuilding.Post("/submit?dryRun=true", form)
        .withHeaders(RawHeader("X-Client", "request-builder"))

      assertThat(request.method).isEqualTo(HttpMethods.POST)
      assertThat(request.uri.path.toString()).isEqualTo("/submit")
      assertThat(request.uri.query().get("dryRun")).isEqualTo(Some("true"))
      assertThat(request.getHeader("X-Client").isPresent).isTrue()

      val unmarshalledForm: FormData = await(Unmarshal(request.entity).to[FormData])
      assertThat(unmarshalledForm.fields.get("name")).isEqualTo(Some("Apache Pekko"))
      assertThat(unmarshalledForm.fields.get("feature")).isEqualTo(Some("http routing"))

      val marshalledEntity: RequestEntity = await(Marshal("plain response").to[RequestEntity])
      assertThat(marshalledEntity.contentType).isEqualTo(ContentTypes.`text/plain(UTF-8)`)
      assertThat(await(Unmarshal(marshalledEntity).to[String])).isEqualTo("plain response")
    }

  @Test
  def gzipCoderCompressesEntitiesAndRestoresOriginalBytes(): Unit =
    withSystem("pekko-http-coding-test") { system =>
      implicit val actorSystem: ActorSystem = system
      implicit val materializer: Materializer = SystemMaterializer(system).materializer

      val payloadText: String = List.fill(8)("pekko-http gzip payload").mkString
      val payload: ByteString = ByteString(payloadText)
      val encoded: ByteString = await(Coders.Gzip.encodeAsync(payload))
      val decoded: ByteString = await(Coders.Gzip.decode(encoded))

      assertThat(encoded).isNotEqualTo(payload)
      assertThat(decoded.utf8String).isEqualTo(payload.utf8String)

      val response: HttpResponse = HttpResponse(entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, payload))
      val encodedResponse: HttpResponse = Coders.Gzip.encodeMessage(response)
      assertThat(encodedResponse.headers.map(_.lowercaseName()).asJava).contains("content-encoding")

      val decodedResponse: HttpResponse = Coders.Gzip.decodeMessage(encodedResponse)
      assertThat(decodedResponse.headers.map(_.lowercaseName()).asJava).doesNotContain("content-encoding")
      assertThat(strictText(decodedResponse.entity)).isEqualTo(payload.utf8String)
    }

  @Test
  def serverSentEventsMarshalToEventStreamAndUnmarshalBack(): Unit =
    withSystem("pekko-http-sse-test") { system =>
      implicit val actorSystem: ActorSystem = system
      implicit val materializer: Materializer = SystemMaterializer(system).materializer
      implicit val executionContext: ExecutionContextExecutor = system.dispatcher

      val events: Seq[ServerSentEvent] = Seq(
        ServerSentEvent("first line\nsecond line", "created", "event-1"),
        ServerSentEvent("retry later", 2500))

      val entity: ResponseEntity = await(Marshal(Source(events)).to[ResponseEntity])
      assertThat(entity.contentType.mediaType.value).isEqualTo("text/event-stream")

      val strictEntity: HttpEntity.Strict = await(entity.toStrict(Timeout))
      val rendered: String = strictEntity.data.utf8String
      assertThat(rendered).contains("event:created")
      assertThat(rendered).contains("id:event-1")
      assertThat(rendered).contains("retry:2500")

      val parsedSource: Source[ServerSentEvent, NotUsed] =
        await(Unmarshal(strictEntity).to[Source[ServerSentEvent, NotUsed]])
      val parsedEvents: Seq[ServerSentEvent] = await(parsedSource.runWith(Sink.seq))
      assertThat(parsedEvents.asJava).containsExactlyElementsOf(events.asJava)
    }

  private def withSystem[T](name: String)(test: ActorSystem => T): T = {
    val config = ConfigFactory.parseString("""
      pekko.loglevel = "WARNING"
      pekko.stdout-loglevel = "WARNING"
      pekko.actor.default-dispatcher.shutdown-timeout = 1s
      """)
    val system: ActorSystem = ActorSystem(name, config)
    try test(system)
    finally await(system.terminate())
  }

  private def strictText(entity: HttpEntity)(implicit materializer: Materializer): String =
    await(entity.toStrict(Timeout)).data.utf8String

  private def await[T](future: Future[T]): T =
    Await.result(future, Timeout)
}
