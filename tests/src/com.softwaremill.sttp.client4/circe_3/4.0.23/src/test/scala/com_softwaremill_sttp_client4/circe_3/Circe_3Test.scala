/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_client4.circe_3

import io.circe.Decoder
import io.circe.Encoder
import io.circe.HCursor
import io.circe.Json
import io.circe.Printer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sttp.client4.*
import sttp.client4.ResponseException.DeserializationException
import sttp.client4.ResponseException.UnexpectedStatusCode
import sttp.client4.circe.*
import sttp.client4.testing.ResponseStub
import sttp.client4.testing.SyncBackendStub
import sttp.model.HeaderNames
import sttp.model.MediaType
import sttp.model.StatusCode

class Circe_3Test {
  private final case class Widget(id: Int, name: String, active: Boolean)
  private final case class ApiError(code: String, message: String)

  private given Encoder[Widget] = Encoder.instance { widget =>
    Json.obj(
      "id" -> Json.fromInt(widget.id),
      "name" -> Json.fromString(widget.name),
      "active" -> Json.fromBoolean(widget.active)
    )
  }

  private given Decoder[Widget] = Decoder.instance { (cursor: HCursor) =>
    for {
      id <- cursor.downField("id").as[Int]
      name <- cursor.downField("name").as[String]
      active <- cursor.downField("active").as[Boolean]
    } yield Widget(id, name, active)
  }

  private given Decoder[ApiError] = Decoder.instance { (cursor: HCursor) =>
    for {
      code <- cursor.downField("code").as[String]
      message <- cursor.downField("message").as[String]
    } yield ApiError(code, message)
  }

  private given eitherDecoder[L: Decoder, R: Decoder]: Decoder[Either[L, R]] =
    summon[Decoder[L]].either(summon[Decoder[R]])

  @Test
  def asJsonSerializesRequestBodyUsingEncoderAndPrinter(): Unit = {
    val widget: Widget = Widget(7, "demo widget", active = true)
    val body: StringBody = asJson(widget)(using summon[Encoder[Widget]], Printer.spaces2)

    assertTrue(body.s.contains("\n"))
    assertTrue(body.s.contains("\"id\" : 7"))
    assertTrue(body.s.contains("\"name\" : \"demo widget\""))
    assertEquals("utf-8", body.encoding)
    assertEquals(MediaType.ApplicationJson, body.defaultContentType)

    val request = basicRequest
      .body(body)
      .post(uri"http://example.com/widgets")

    val contentType = request.headers.find(_.is(HeaderNames.ContentType)).map(_.value)
    val contentLength = request.headers.find(_.is(HeaderNames.ContentLength)).map(_.value)
    assertEquals(Some("application/json"), contentType)
    assertEquals(Some(body.s.getBytes("utf-8").length.toString), contentLength)
  }

  @Test
  def deserializeJsonDecodesValidPayloadsAndReportsCirceErrors(): Unit = {
    val decoded: Either[io.circe.Error, Widget] = deserializeJson[Widget].apply(
      """{"id":11,"name":"direct","active":false}"""
    )
    assertEquals(Right(Widget(11, "direct", active = false)), decoded)

    val invalid: Either[io.circe.Error, Widget] = deserializeJson[Widget].apply("""{"id":"bad"}""")
    assertTrue(invalid.isLeft)
    assertTrue(invalid.swap.toOption.get.getMessage.contains("Int"))
  }

  @Test
  def asJsonReadsSuccessfulResponsesIntoRightAndKeepsHttpErrorsAsStrings(): Unit = {
    val successBackend: SyncBackendStub = SyncBackendStub.whenAnyRequest.thenRespondAdjust(
      """{"id":1,"name":"ok","active":true}"""
    )
    val success = basicRequest
      .response(asJson[Widget])
      .get(uri"http://example.com/success")
      .send(successBackend)

    assertEquals(Right(Widget(1, "ok", active = true)), success.body)
    assertEquals(StatusCode.Ok, success.code)

    val errorBackend: SyncBackendStub = SyncBackendStub.whenAnyRequest.thenRespondAdjust(
      """{"code":"missing","message":"not found"}""",
      StatusCode.NotFound
    )
    val error = basicRequest
      .response(asJson[Widget])
      .get(uri"http://example.com/missing")
      .send(errorBackend)
      .body

    val unexpected: UnexpectedStatusCode[_] = assertInstanceOf(
      classOf[UnexpectedStatusCode[_]],
      error.swap.toOption.get
    )
    assertEquals("""{"code":"missing","message":"not found"}""", unexpected.body)
    assertEquals(StatusCode.NotFound, unexpected.response.code)
  }

  @Test
  def asJsonDecodesEmptyBodiesAsNoneWhenEitherSideIsOptional(): Unit = {
    val leftOptionalBackend: SyncBackendStub = SyncBackendStub.whenAnyRequest.thenRespondAdjust("")
    val leftOptional = basicRequest
      .response(asJson[Either[Option[ApiError], Widget]])
      .get(uri"http://example.com/optional-left")
      .send(leftOptionalBackend)
      .body

    val leftExpected: Either[ResponseException[String], Either[Option[ApiError], Widget]] = Right(Left(None))
    assertEquals(leftExpected, leftOptional)

    val rightOptionalBackend: SyncBackendStub = SyncBackendStub.whenAnyRequest.thenRespondAdjust("")
    val rightOptional = basicRequest
      .response(asJson[Either[ApiError, Option[Widget]]])
      .get(uri"http://example.com/optional-right")
      .send(rightOptionalBackend)
      .body

    val rightExpected: Either[ResponseException[String], Either[ApiError, Option[Widget]]] = Right(Right(None))
    assertEquals(rightExpected, rightOptional)
  }

  @Test
  def asJsonReportsDeserializationErrorsForSuccessfulInvalidJson(): Unit = {
    val backend: SyncBackendStub = SyncBackendStub.whenAnyRequest.thenRespondAdjust("""{"id":"oops"}""")
    val result: Either[ResponseException[String], Widget] = basicRequest
      .response(asJson[Widget])
      .get(uri"http://example.com/invalid")
      .send(backend)
      .body

    val failure: DeserializationException = assertInstanceOf(
      classOf[DeserializationException],
      result.swap.toOption.get
    )
    assertEquals("""{"id":"oops"}""", failure.body)
    assertEquals(StatusCode.Ok, failure.response.code)
    assertTrue(failure.cause.getMessage.contains("Int"))
  }

  @Test
  def asJsonOrFailReturnsBodyOrThrowsResponseException(): Unit = {
    val successBackend: SyncBackendStub = SyncBackendStub.whenAnyRequest.thenRespondAdjust(
      """{"id":2,"name":"strict","active":false}"""
    )
    val success = basicRequest
      .response(asJsonOrFail[Widget])
      .get(uri"http://example.com/strict")
      .send(successBackend)

    assertEquals(Widget(2, "strict", active = false), success.body)

    val failingBackend: SyncBackendStub = SyncBackendStub.whenAnyRequest.thenRespondAdjust(
      """{"id":2,"name":"strict","active":false}""",
      StatusCode.BadRequest
    )
    val thrown: SttpClientException.ResponseHandlingException[_] = assertThrows(
      classOf[SttpClientException.ResponseHandlingException[_]],
      () =>
        basicRequest
          .response(asJsonOrFail[Widget])
          .get(uri"http://example.com/strict-error")
          .send(failingBackend)
    )
    val unexpected: UnexpectedStatusCode[_] = assertInstanceOf(
      classOf[UnexpectedStatusCode[_]],
      thrown.responseException
    )
    assertEquals(StatusCode.BadRequest, unexpected.response.code)
    assertTrue(unexpected.body.toString.contains("strict"))
  }

  @Test
  def asJsonAlwaysDecodesRegardlessOfStatusAndSanitizesEmptyOptionResponses(): Unit = {
    val errorBackend: SyncBackendStub = SyncBackendStub.whenAnyRequest.thenRespondAdjust(
      """{"id":3,"name":"from-error","active":true}""",
      StatusCode.InternalServerError
    )
    val decodedFromError = basicRequest
      .response(asJsonAlways[Widget])
      .get(uri"http://example.com/error-body")
      .send(errorBackend)

    assertEquals(Right(Widget(3, "from-error", active = true)), decodedFromError.body)
    assertEquals(StatusCode.InternalServerError, decodedFromError.code)

    val emptyBackend: SyncBackendStub = SyncBackendStub.whenAnyRequest.thenRespondAdjust("")
    val emptyOption = basicRequest
      .response(asJsonAlways[Option[Widget]])
      .get(uri"http://example.com/empty")
      .send(emptyBackend)
      .body

    assertEquals(Right(None), emptyOption)
  }

  @Test
  def asJsonEitherDecodesErrorAndSuccessBodiesUsingTheirOwnDecoders(): Unit = {
    val successBackend: SyncBackendStub = SyncBackendStub.whenAnyRequest.thenRespondAdjust(
      """{"id":4,"name":"either-success","active":true}"""
    )
    val successBody = basicRequest
      .response(asJsonEither[ApiError, Widget])
      .get(uri"http://example.com/either-success")
      .send(successBackend)
      .body

    assertEquals(Right(Widget(4, "either-success", active = true)), successBody)

    val errorBackend: SyncBackendStub = SyncBackendStub.whenAnyRequest.thenRespondAdjust(
      """{"code":"invalid","message":"input rejected"}""",
      StatusCode.UnprocessableEntity
    )
    val errorBody = basicRequest
      .response(asJsonEither[ApiError, Widget])
      .get(uri"http://example.com/either-error")
      .send(errorBackend)
      .body

    val unexpected: UnexpectedStatusCode[_] = assertInstanceOf(
      classOf[UnexpectedStatusCode[_]],
      errorBody.swap.toOption.get
    )
    assertEquals(ApiError("invalid", "input rejected"), unexpected.body)
    assertEquals(StatusCode.UnprocessableEntity, unexpected.response.code)
  }

  @Test
  def asJsonEitherReportsErrorBodyDeserializationFailures(): Unit = {
    val backend: SyncBackendStub = SyncBackendStub.whenAnyRequest.thenRespondAdjust(
      """{"code":123,"message":"not a string code"}""",
      StatusCode.BadRequest
    )
    val result = basicRequest
      .response(asJsonEither[ApiError, Widget])
      .get(uri"http://example.com/bad-error-json")
      .send(backend)
      .body

    val failure: DeserializationException = assertInstanceOf(
      classOf[DeserializationException],
      result.swap.toOption.get
    )
    assertEquals("""{"code":123,"message":"not a string code"}""", failure.body)
    assertEquals(StatusCode.BadRequest, failure.response.code)
    assertTrue(failure.cause.getMessage.toLowerCase.contains("string"))
  }

  @Test
  def asJsonEitherOrFailReturnsTypedEitherOrThrowsOnInvalidJson(): Unit = {
    val errorBackend: SyncBackendStub = SyncBackendStub.whenAnyRequest.thenRespondAdjust(
      """{"code":"conflict","message":"already exists"}""",
      StatusCode.Conflict
    )
    val errorResult = basicRequest
      .response(asJsonEitherOrFail[ApiError, Widget])
      .get(uri"http://example.com/conflict")
      .send(errorBackend)
      .body

    assertEquals(Left(ApiError("conflict", "already exists")), errorResult)

    val successBackend: SyncBackendStub = SyncBackendStub.whenAnyRequest.thenRespondAdjust(
      """{"id":5,"name":"created","active":true}""",
      StatusCode.Created
    )
    val successResult = basicRequest
      .response(asJsonEitherOrFail[ApiError, Widget])
      .post(uri"http://example.com/widgets")
      .send(successBackend)
      .body

    assertEquals(Right(Widget(5, "created", active = true)), successResult)

    val invalidBackend: SyncBackendStub = SyncBackendStub.whenAnyRequest.thenRespondAdjust(
      """{"id":"not-an-int"}""",
      StatusCode.Created
    )
    val thrown: SttpClientException.ResponseHandlingException[_] = assertThrows(
      classOf[SttpClientException.ResponseHandlingException[_]],
      () =>
        basicRequest
          .response(asJsonEitherOrFail[ApiError, Widget])
          .post(uri"http://example.com/invalid-success")
          .send(invalidBackend)
    )
    val deserialization: DeserializationException = assertInstanceOf(
      classOf[DeserializationException],
      thrown.responseException
    )
    assertEquals(StatusCode.Created, deserialization.response.code)
    assertTrue(deserialization.cause.getMessage.contains("Int"))
  }

  @Test
  def requestSerializationAndResponseDeserializationWorkTogetherWithBackendStub(): Unit = {
    val backend: SyncBackendStub = SyncBackendStub.whenAnyRequest.thenRespondF { request =>
      val requestBody: StringBody = assertInstanceOf(classOf[StringBody], request.body)
      assertEquals("""{"id":8,"name":"roundtrip","active":true}""", requestBody.s)
      assertTrue(
        request.headers.exists(header => header.is(HeaderNames.ContentType) && header.value == "application/json")
      )
      ResponseStub.adjust("""{"id":9,"name":"roundtrip-response","active":false}""")
    }

    val response = basicRequest
      .body(asJson(Widget(8, "roundtrip", active = true)))
      .response(asJsonOrFail[Widget])
      .post(uri"http://example.com/roundtrip")
      .send(backend)

    assertEquals(Widget(9, "roundtrip-response", active = false), response.body)
  }
}
