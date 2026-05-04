/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_http4s.http4s_circe_3

import cats.data.Kleisli
import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.Stream
import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Encoder
import io.circe.Json
import io.circe.Printer
import io.circe.syntax._
import org.http4s.EntityDecoder
import org.http4s.Header
import org.http4s.InvalidMessageBodyFailure
import org.http4s.Media
import org.http4s.MediaType
import org.http4s.Message
import org.http4s.Method
import org.http4s.ParseFailure
import org.http4s.Request
import org.http4s.Response
import org.http4s.Status
import org.http4s.Uri
import org.http4s.circe.CirceInstances
import org.http4s.circe.DecodingFailures
import org.http4s.circe.accumulatingJsonOf
import org.http4s.circe.decodeUri
import org.http4s.circe.encodeUri
import org.http4s.circe.jsonDecoder
import org.http4s.circe.jsonEncoderOf
import org.http4s.circe.jsonEncoderWithPrinter
import org.http4s.circe.jsonOf
import org.http4s.circe.jsonOfSensitive
import org.http4s.circe.jsonOfWithMedia
import org.http4s.circe.middleware.JsonDebugErrorHandler
import org.http4s.circe.streamJsonArrayDecoder
import org.http4s.circe.streamJsonArrayEncoderOf
import org.http4s.headers.`Content-Type`
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.typelevel.ci._

import scala.concurrent.duration._

class Http4s_circe_3Test {
  private final case class Widget(id: Int, name: String, tags: List[String])

  private given Encoder[Widget] = Encoder.instance { widget =>
    Json.obj(
      "id" -> Json.fromInt(widget.id),
      "name" -> Json.fromString(widget.name),
      "tags" -> widget.tags.asJson
    )
  }

  private given Decoder[Widget] = Decoder.forProduct3("id", "name", "tags")(Widget.apply)

  @Test
  def entityEncoderAndDecoderRoundTripCustomValues(): Unit = {
    val widget: Widget = Widget(7, "bolt", List("hardware", "metric"))
    val request: Media[IO] = Request[IO](Method.POST, parseUri("/widgets"))
      .withEntity(widget)(jsonEncoderOf[IO, Widget])

    assertEquals(Some(`Content-Type`(MediaType.application.json)), request.contentType)
    assertEquals(
      """{"id":7,"name":"bolt","tags":["hardware","metric"]}""",
      bodyText(request)
    )
    assertEquals(widget, decodeAs(request, jsonOf[IO, Widget]))
  }

  @Test
  def jsonEncoderWithPrinterFormatsJsonWhileDecoderReadsItBack(): Unit = {
    val payload: Json = Json.obj(
      "zeta" -> Json.fromInt(2),
      "alpha" -> Json.obj("enabled" -> Json.fromBoolean(true))
    )
    val response: Media[IO] = Response[IO](Status.Ok)
      .withEntity(payload)(jsonEncoderWithPrinter[IO](Printer.spaces2SortKeys))

    val text: String = bodyText(response)

    assertTrue(text.contains("\n  \"alpha\""))
    assertTrue(text.indexOf("\"alpha\"") < text.indexOf("\"zeta\""))
    assertEquals(payload, decodeAs(response, jsonDecoder[IO]))
  }

  @Test
  def uriCirceCodecEncodesRenderedUriAndRejectsInvalidInput(): Unit = {
    val uri: Uri = parseUri("https://user@example.com:8443/a/b?x=1&x=2#frag")
    val json: Json = encodeUri(uri)

    assertEquals(Json.fromString(uri.toString), json)
    assertEquals(Right(uri), decodeUri.decodeJson(json))
    assertTrue(decodeUri.decodeJson(Json.fromString("http://[::1")).isLeft)
  }

  @Test
  def accumulatingJsonOfReportsMultipleFieldFailures(): Unit = {
    val request: Media[IO] = jsonRequest("""{"tags":[]}""")
    val decoded: Either[Throwable, Widget] =
      decodeAttemptAs(request, accumulatingJsonOf[IO, Widget])
    val failure: Throwable = decoded match {
      case Left(value) => value
      case Right(value) => throw new AssertionError(s"expected accumulating decoder failure, got $value")
    }

    assertTrue(failure.isInstanceOf[InvalidMessageBodyFailure])
    assertTrue(failure.getMessage.contains("Could not decode JSON"))
    assertTrue(failure.getCause.isInstanceOf[DecodingFailures])

    val decodingFailures: DecodingFailures = failure.getCause.asInstanceOf[DecodingFailures]
    assertEquals(2, decodingFailures.failures.toList.length)
  }

  @Test
  def sensitiveJsonDecoderRedactsPayloadInDecodeFailure(): Unit = {
    val request: Media[IO] = jsonRequest("""{"id":1,"name":42,"tags":[]}""")
    val decoded: Either[Throwable, Widget] =
      decodeAttemptAs(request, jsonOfSensitive[IO, Widget](_ => "<redacted-json>"))
    val failure: Throwable = decoded match {
      case Left(value) => value
      case Right(value) => throw new AssertionError(s"expected redacted decoder failure, got $value")
    }

    assertTrue(failure.isInstanceOf[InvalidMessageBodyFailure])
    assertTrue(failure.getMessage.contains("Could not decode JSON: <redacted-json>"))
    assertFalse(failure.getMessage.contains("\"name\":42"))
  }

  @Test
  def circeInstancesBuilderUsesCustomJsonDecodeErrors(): Unit = {
    val request: Media[IO] = jsonRequest("""{"id":12,"name":false,"tags":[]}""")
    val customDecoder: EntityDecoder[IO, Widget] = CirceInstances.builder
      .withJsonDecodeError { (_: Json, failures: NonEmptyList[DecodingFailure]) =>
        InvalidMessageBodyFailure(s"custom widget decode error: ${failures.head.message}")
      }
      .build
      .jsonOf[IO, Widget]
    val decoded: Either[Throwable, Widget] = decodeAttemptAs(request, customDecoder)
    val failure: Throwable = decoded match {
      case Left(value) => value
      case Right(value) => throw new AssertionError(s"expected custom decoder failure, got $value")
    }

    assertTrue(failure.isInstanceOf[InvalidMessageBodyFailure])
    assertTrue(failure.getMessage.contains("custom widget decode error"))
    assertFalse(failure.getMessage.contains("Could not decode JSON"))
  }

  @Test
  def jsonOfWithMediaAcceptsConfiguredVendorMediaType(): Unit = {
    val vendorMediaType: MediaType = parseMediaType("application/vnd.widget+json")
    val request: Media[IO] = Request[IO](Method.POST, parseUri("/vendor"))
      .withEntity("""{"id":11,"name":"adapter","tags":["vendor"]}""")
      .withContentType(`Content-Type`(vendorMediaType))

    val decoded: Widget =
      decodeAs(request, jsonOfWithMedia[IO, Widget](vendorMediaType))

    assertEquals(Widget(11, "adapter", List("vendor")), decoded)
  }

  @Test
  def streamJsonArrayEncoderAndDecoderRoundTripJsonArrays(): Unit = {
    val widgets: List[Widget] = List(
      Widget(1, "one", List("small")),
      Widget(2, "two", List("medium", "blue"))
    )
    val response: Media[IO] = Response[IO](Status.Ok)
      .withEntity(Stream.emits(widgets).covary[IO])(streamJsonArrayEncoderOf[IO, Widget])

    assertEquals(
      """[{"id":1,"name":"one","tags":["small"]},{"id":2,"name":"two","tags":["medium","blue"]}]""",
      bodyText(response)
    )

    val decodedJson: List[Json] =
      run(decodeAs(response, streamJsonArrayDecoder[IO]).compile.toList)
    val decodedWidgets: List[Widget] = decodedJson.map(_.as[Widget].toOption.get)

    assertEquals(widgets, decodedWidgets)
  }

  @Test
  def messageSyntaxDecodesJsonAndDomainValuesFromRequestBody(): Unit = {
    import org.http4s.circe._

    val request: Message[IO] = Request[IO](Method.POST, parseUri("/syntax"))
      .withEntity("""{"id":21,"name":"syntax","tags":["extension"]}""")
      .withContentType(`Content-Type`(MediaType.application.json))

    val json: Json = run(request.json)

    assertEquals(Right(21), json.hcursor.downField("id").as[Int])
    assertEquals(Widget(21, "syntax", List("extension")), run(request.decodeJson[Widget]))
  }

  @Test
  def jsonDebugErrorHandlerProducesJsonFailureResponseWithRedactedHeaders(): Unit = {
    val service: Kleisli[IO, Request[IO], Response[IO]] = Kleisli { _ =>
      IO.raiseError[Response[IO]](new IllegalStateException("service exploded"))
    }
    val request: Request[IO] = Request[IO](Method.GET, parseUri("/debug?token=visible"))
      .putHeaders(Header.Raw(ci"Authorization", "Bearer super-secret"))

    val response: Response[IO] = run(JsonDebugErrorHandler[IO, IO](service).run(request))
    val text: String = bodyText(response)
    val json: Json = decodeAs(response, jsonDecoder[IO])

    assertEquals(Status.InternalServerError, response.status)
    assertTrue(text.contains("Authorization"))
    assertFalse(text.contains("Bearer super-secret"))
    assertEquals(
      Right("GET"),
      json.hcursor.downField("request").downField("method").as[String]
    )
    assertEquals(
      Right("service exploded"),
      json.hcursor.downField("throwable").downField("message").as[String]
    )
  }

  private def jsonRequest(json: String): Media[IO] =
    Request[IO](Method.POST, parseUri("/decode"))
      .withEntity(json)
      .withContentType(`Content-Type`(MediaType.application.json))

  private def bodyText(message: Media[IO]): String =
    run(message.bodyText.compile.string)

  private def decodeAs[A](message: Media[IO], decoder: EntityDecoder[IO, A]): A = {
    given EntityDecoder[IO, A] = decoder
    run(message.as[A])
  }

  private def decodeAttemptAs[A](message: Media[IO], decoder: EntityDecoder[IO, A]): Either[Throwable, A] = {
    given EntityDecoder[IO, A] = decoder
    run(message.as[A].attempt)
  }

  private def parseUri(value: String): Uri =
    Uri.fromString(value).fold(throwParseFailure, identity)

  private def parseMediaType(value: String): MediaType =
    MediaType.parse(value).fold(throwParseFailure, identity)

  private def throwParseFailure(parseFailure: ParseFailure): Nothing =
    throw new IllegalArgumentException(parseFailure.message)

  private def run[A](io: IO[A]): A =
    io.timeout(5.seconds).unsafeRunSync()
}
