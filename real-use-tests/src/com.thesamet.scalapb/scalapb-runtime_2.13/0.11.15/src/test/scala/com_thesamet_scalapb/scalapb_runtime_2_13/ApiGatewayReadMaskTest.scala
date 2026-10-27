/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thesamet_scalapb.scalapb_runtime_2_13

import com.google.protobuf.api.Api
import com.google.protobuf.api.Method
import com.google.protobuf.api.Mixin
import com.google.protobuf.field_mask.FieldMask
import com.google.protobuf.source_context.SourceContext
import com.google.protobuf.`type`.Syntax
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import scalapb.FieldMaskUtil

/**
 * Exercises field-mask handling through `FieldMaskUtil`: JSON read-mask parsing and printing,
 * mask validation, projection with `applyFieldMask`, field-number masks and mask union.
 *
 * A service directory answers `GetApi` requests the way Google-style APIs do: the caller sends a
 * `fields=` query parameter in the JSON form of a `google.protobuf.FieldMask`, the gateway
 * rejects masks that name unknown fields, and the response carries only the selected fields.
 * Dashboards that poll several views of one API have their masks merged into a single read.
 */
class ApiGatewayReadMaskTest {
  private val payments: Api = Api(
    name = "bank.PaymentService",
    methods = Seq(
      Method(name = "Authorize", requestTypeUrl = "type.googleapis.com/bank.AuthorizeRequest",
        responseTypeUrl = "type.googleapis.com/bank.Authorization"),
      Method(name = "Capture", requestTypeUrl = "type.googleapis.com/bank.CaptureRequest",
        responseTypeUrl = "type.googleapis.com/bank.Capture", responseStreaming = true)
    ),
    version = "v3",
    sourceContext = Some(SourceContext(fileName = "bank/payments.proto")),
    mixins = Seq(Mixin(name = "google.longrunning.Operations", root = "v3/operations")),
    syntax = Syntax.SYNTAX_PROTO3
  )

  /** The gateway's read path: parse the `fields=` parameter, validate it, project the answer. */
  private object Gateway {
    sealed trait Response
    final case class Found(api: Api) extends Response
    final case class BadRequest(fields: String) extends Response

    def getApi(api: Api, fieldsParam: String): Response = {
      val mask: FieldMask = FieldMaskUtil.fromJsonString(fieldsParam)
      if (FieldMaskUtil.isValid[Api](mask)) Found(FieldMaskUtil.applyFieldMask(api, mask))
      else BadRequest(fieldsParam)
    }
  }

  @Test
  def jsonReadMaskSelectsTopLevelAndNestedFields(): Unit = {
    val response: Gateway.Response = Gateway.getApi(payments, "name,version,sourceContext.fileName")

    val expected: Api = Api(
      name = "bank.PaymentService",
      version = "v3",
      sourceContext = Some(SourceContext(fileName = "bank/payments.proto"))
    )
    assertEquals(Gateway.Found(expected), response)

    val projected: Api = response.asInstanceOf[Gateway.Found].api
    assertTrue(projected.methods.isEmpty)
    assertTrue(projected.mixins.isEmpty)
    assertEquals(Syntax.SYNTAX_PROTO2, projected.syntax)
    assertTrue(projected.serializedSize < payments.serializedSize)
  }

  @Test
  def repeatedFieldsAreSelectedWhole(): Unit = {
    val response: Gateway.Response = Gateway.getApi(payments, "methods,syntax")

    val expected: Api = Api(methods = payments.methods, syntax = Syntax.SYNTAX_PROTO3)
    assertEquals(Gateway.Found(expected), response)
  }

  @Test
  def masksNamingUnknownOrScalarSubfieldsAreRejected(): Unit = {
    assertEquals(Gateway.BadRequest("name,owner"), Gateway.getApi(payments, "name,owner"))
    assertEquals(Gateway.BadRequest("version.major"), Gateway.getApi(payments, "version.major"))
    assertEquals(
      Gateway.BadRequest("sourceContext.lineNumber"),
      Gateway.getApi(payments, "sourceContext.lineNumber")
    )
  }

  @Test
  def readMasksRoundTripBetweenJsonAndProtoPaths(): Unit = {
    val mask: FieldMask = FieldMaskUtil.fromJsonString("name,sourceContext.fileName,mixins")
    assertEquals(Seq("name", "source_context.file_name", "mixins"), mask.paths)
    assertEquals("name,sourceContext.fileName,mixins", FieldMaskUtil.toJsonString(mask))

    assertEquals(FieldMask(), FieldMaskUtil.fromJsonString(""))
    assertEquals("", FieldMaskUtil.toJsonString(FieldMask()))
    assertEquals(
      Seq("request_type_url", "response_streaming"),
      FieldMaskUtil.fromJsonString("requestTypeUrl,responseStreaming").paths
    )
  }

  @Test
  def fieldNumberMasksDescribeStableSummaries(): Unit = {
    val summary: Option[FieldMask] =
      FieldMaskUtil.fromFieldNumbers[Api](Api.NAME_FIELD_NUMBER, Api.VERSION_FIELD_NUMBER)
    assertEquals(Some(FieldMask(paths = Seq("name", "version"))), summary)
    assertEquals(None, FieldMaskUtil.fromFieldNumbers[Api](Api.NAME_FIELD_NUMBER, 99))

    val withoutName: FieldMask =
      FieldMaskUtil.selectFieldNumbers[Method](_ != Method.NAME_FIELD_NUMBER)
    assertFalse(FieldMaskUtil.containsFieldNumber[Method](withoutName, Method.NAME_FIELD_NUMBER))
    assertTrue(FieldMaskUtil.containsFieldNumber[Method](
      withoutName, Method.RESPONSE_STREAMING_FIELD_NUMBER))

    val capture: Method = payments.methods(1)
    val anonymised: Method = FieldMaskUtil.applyFieldMask(capture, withoutName)
    assertEquals(capture.withName(""), anonymised)
  }

  @Test
  def dashboardMasksAreMergedIntoOneRead(): Unit = {
    val overview: FieldMask = FieldMask(paths = Seq("name", "source_context.file_name"))
    val wiring: FieldMask = FieldMask(paths = Seq("mixins", "source_context"))
    val release: FieldMask = FieldMask(paths = Seq("version", "name"))

    val merged: FieldMask = FieldMaskUtil.union(overview, wiring, release)
    assertEquals(Set("name", "source_context", "mixins", "version"), merged.paths.toSet)
    assertEquals(4, merged.paths.size)
    assertTrue(FieldMaskUtil.isValid[Api](merged))

    val combined: Api = FieldMaskUtil.applyFieldMask(payments, merged)
    assertEquals(payments.withMethods(Seq.empty).withSyntax(Syntax.SYNTAX_PROTO2), combined)
  }
}
