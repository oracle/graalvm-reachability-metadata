/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_client4.json_common_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sttp.client4.*
import sttp.client4.ResponseException.UnexpectedStatusCode
import sttp.client4.json.*
import sttp.client4.testing.SyncBackendStub
import sttp.model.StatusCode

class Json_common_3Test {
  @Test
  def isOptionRecognizesOptionalResponseShapes(): Unit = {
    assertTrue(summon[IsOption[Option[String]]].isOption)
    assertTrue(summon[IsOption[Either[Option[String], Int]]].isOption)
    assertTrue(summon[IsOption[Either[String, Option[Int]]]].isOption)

    assertFalse(summon[IsOption[String]].isOption)
    assertFalse(summon[IsOption[Either[String, Int]]].isOption)
    assertFalse(summon[IsOption[List[String]]].isOption)
  }

  @Test
  def isOptionCompanionProvidesExplicitOptionalityInstances(): Unit = {
    val optional: IsOption[Option[String]] = IsOption.optionIsOption[String]
    val leftOptional: IsOption[Either[Option[String], ?]] = IsOption.leftOptionIsOption[String]
    val rightOptional: IsOption[Either[?, Option[Int]]] = IsOption.rightOptionIsOption[Int]
    val required: IsOption[String] = IsOption.otherIsNotOption[String]

    assertTrue(optional.isOption)
    assertTrue(leftOptional.isOption)
    assertTrue(rightOptional.isOption)
    assertFalse(required.isOption)
  }

  @Test
  def jsonResponseExtensionsUseStableDisplayNames(): Unit = {
    assertEquals("either(as string, as json)", asString.showAsJson.show)
    assertEquals("as json", asStringAlways.showAsJsonAlways.show)
    assertEquals("as json or fail", asString.orFail.showAsJsonOrFail.show)
    assertEquals(
      "either(as json, as json)",
      asEither(asStringAlways, asStringAlways).showAsJsonEither.show
    )
    assertEquals(
      "either(as json, as json) or fail",
      asEither(asStringAlways, asStringAlways).orFail.showAsJsonEitherOrFail.show
    )
  }

  @Test
  def showAsJsonPreservesStatusBasedStringHandling(): Unit = {
    val successBackend: SyncBackendStub = SyncBackendStub.whenAnyRequest.thenRespondAdjust(
      "success-json",
      StatusCode.Ok
    )
    val success = basicRequest
      .response(asString.showAsJson)
      .get(uri"http://example.com/success")
      .send(successBackend)
      .body

    assertEquals(Right("success-json"), success)

    val errorBackend: SyncBackendStub = SyncBackendStub.whenAnyRequest.thenRespondAdjust(
      "error-json",
      StatusCode.BadRequest
    )
    val error = basicRequest
      .response(asString.showAsJson)
      .get(uri"http://example.com/error")
      .send(errorBackend)
      .body

    assertEquals(Left("error-json"), error)
  }

  @Test
  def showAsJsonAlwaysPreservesBodyForErrorResponses(): Unit = {
    val backend: SyncBackendStub = SyncBackendStub.whenAnyRequest.thenRespondAdjust(
      "always-json",
      StatusCode.InternalServerError
    )
    val response = basicRequest
      .response(asStringAlways.showAsJsonAlways)
      .get(uri"http://example.com/always")
      .send(backend)

    assertEquals(StatusCode.InternalServerError, response.code)
    assertEquals("always-json", response.body)
  }

  @Test
  def showAsJsonEitherPreservesDifferentErrorAndSuccessHandlers(): Unit = {
    val responseAs = asEither(
      asStringAlways.map(error => s"error:$error"),
      asStringAlways.map(success => s"success:$success")
    ).showAsJsonEither

    val successBackend: SyncBackendStub = SyncBackendStub.whenAnyRequest.thenRespondAdjust(
      "body",
      StatusCode.Created
    )
    val success = basicRequest
      .response(responseAs)
      .post(uri"http://example.com/either")
      .send(successBackend)
      .body

    assertEquals(Right("success:body"), success)

    val errorBackend: SyncBackendStub = SyncBackendStub.whenAnyRequest.thenRespondAdjust(
      "body",
      StatusCode.NotFound
    )
    val error = basicRequest
      .response(responseAs)
      .post(uri"http://example.com/either")
      .send(errorBackend)
      .body

    assertEquals(Left("error:body"), error)
  }

  @Test
  def showAsJsonOrFailPreservesFailingResponseSemantics(): Unit = {
    val successBackend: SyncBackendStub = SyncBackendStub.whenAnyRequest.thenRespondAdjust(
      "ok-json",
      StatusCode.Ok
    )
    val success = basicRequest
      .response(asString.orFail.showAsJsonOrFail)
      .get(uri"http://example.com/or-fail")
      .send(successBackend)
      .body

    assertEquals("ok-json", success)

    val errorBackend: SyncBackendStub = SyncBackendStub.whenAnyRequest.thenRespondAdjust(
      "bad-json",
      StatusCode.BadRequest
    )
    val thrown: SttpClientException.ResponseHandlingException[_] = assertThrows(
      classOf[SttpClientException.ResponseHandlingException[_]],
      () =>
        basicRequest
          .response(asString.orFail.showAsJsonOrFail)
          .get(uri"http://example.com/or-fail-error")
          .send(errorBackend)
    )
    val unexpected = assertInstanceOf(
      classOf[UnexpectedStatusCode[_]],
      thrown.responseException
    )

    assertEquals("bad-json", unexpected.body)
    assertEquals(StatusCode.BadRequest, unexpected.response.code)
  }

  @Test
  def showAsJsonEitherOrFailPreservesSuccessfulBodyAndFailsOnErrorStatus(): Unit = {
    val responseAs = asEither(
      asStringAlways.map(error => s"left:$error"),
      asStringAlways.map(success => s"right:$success")
    ).orFail.showAsJsonEitherOrFail

    val successBackend: SyncBackendStub = SyncBackendStub.whenAnyRequest.thenRespondAdjust(
      "created",
      StatusCode.Created
    )
    val success = basicRequest
      .response(responseAs)
      .post(uri"http://example.com/either-or-fail")
      .send(successBackend)
      .body

    assertEquals("right:created", success)

    val errorBackend: SyncBackendStub = SyncBackendStub.whenAnyRequest.thenRespondAdjust(
      "missing",
      StatusCode.NotFound
    )
    val thrown: SttpClientException.ResponseHandlingException[_] = assertThrows(
      classOf[SttpClientException.ResponseHandlingException[_]],
      () =>
        basicRequest
          .response(responseAs)
          .post(uri"http://example.com/either-or-fail-error")
          .send(errorBackend)
    )
    val unexpected = assertInstanceOf(
      classOf[UnexpectedStatusCode[_]],
      thrown.responseException
    )

    assertEquals("left:missing", unexpected.body)
    assertEquals(StatusCode.NotFound, unexpected.response.code)
  }
}
