/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package realuse.tapircirce

import cats.effect.IO
import cats.effect.Ref
import cats.effect.unsafe.implicits.global
import io.circe.Codec
import io.circe.Json
import io.circe.parser.parse
import org.http4s.Header
import org.http4s.HttpApp
import org.http4s.MediaType
import org.http4s.Method
import org.http4s.Request
import org.http4s.Status
import org.http4s.Uri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.typelevel.ci.CIString
import sttp.tapir.*
import sttp.tapir.json.circe.TapirJsonCirce
import sttp.tapir.server.http4s.Http4sServerInterpreter

import scala.concurrent.duration.*

/** Exercises Circe JSON response content negotiation for a queue-depth monitoring endpoint. */
class QueueDepthNegotiationTest extends TapirJsonCirce {
  case class QueueDepth(pending: Int) derives Codec.AsObject, Schema

  @Test
  @Timeout(60)
  def servesJsonToDashboardsAndPlainTextToMonitoringClients(): Unit = {
    val pending: Ref[IO, Int] = Ref.of[IO, Int](17).unsafeRunSync()
    val monitoring = endpoint.get
      .in("queue-depth")
      .out(oneOfBody(
        jsonBody[QueueDepth],
        stringBody.map[QueueDepth](text => QueueDepth(text.toInt))(_.pending.toString)
      ))
      .serverLogicSuccess[IO] { _ => pending.get.map(QueueDepth.apply) }
    val app: HttpApp[IO] = Http4sServerInterpreter[IO]().toRoutes(monitoring).orNotFound

    def fetch(accept: Option[String]): (Status, Option[MediaType], String) = {
      val request: Request[IO] = Request[IO](Method.GET, Uri.unsafeFromString("/queue-depth"))
      val negotiated: Request[IO] = accept.fold(request) { value =>
        request.putHeaders(Header.Raw(CIString("Accept"), value))
      }
      (for {
        response <- app.run(negotiated)
        text <- response.bodyText.compile.string
      } yield (response.status, response.contentType.map(_.mediaType), text))
        .timeout(15.seconds).unsafeRunSync()
    }

    def assertJson(accept: Option[String], expectedPending: Int): Unit = {
      val result: (Status, Option[MediaType], String) = fetch(accept)
      val expected: Json = Json.obj("pending" -> Json.fromInt(expectedPending))
      assertEquals(Status.Ok, result._1)
      assertEquals(Some(MediaType.application.json), result._2)
      assertEquals(expected, parse(result._3).fold(throw _, identity))
    }

    def assertText(accept: String, expectedPending: Int): Unit = {
      val result: (Status, Option[MediaType], String) = fetch(Some(accept))
      assertEquals(Status.Ok, result._1)
      assertEquals(Some(MediaType.text.plain), result._2)
      assertEquals(expectedPending.toString, result._3)
    }

    assertJson(Some("application/json"), 17)
    assertText("text/plain", 17)
    assertJson(Some("text/plain;q=0.2, application/json;q=0.9"), 17)
    assertText("application/json;q=0.2, text/plain;q=0.9", 17)
    assertJson(None, 17)
    assertJson(Some("*/*"), 17)
    assertEquals(Status.NotAcceptable, fetch(Some("application/xml"))._1)

    // Both representations read the current queue, including a drained queue's zero value.
    pending.set(0).unsafeRunSync()
    assertJson(Some("application/json"), 0)
    assertText("text/plain", 0)
  }
}
