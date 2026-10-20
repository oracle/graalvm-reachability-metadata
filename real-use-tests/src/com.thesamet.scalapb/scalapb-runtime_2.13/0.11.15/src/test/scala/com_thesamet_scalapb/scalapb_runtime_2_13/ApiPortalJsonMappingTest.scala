/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thesamet_scalapb.scalapb_runtime_2_13

import com.google.protobuf.TextFormat.ParseException
import com.google.protobuf.any.{Any => ProtoAny}
import com.google.protobuf.api.Api
import com.google.protobuf.api.Method
import com.google.protobuf.api.Mixin
import com.google.protobuf.duration.Duration
import com.google.protobuf.field_mask.FieldMask
import com.google.protobuf.source_context.SourceContext
import com.google.protobuf.struct.ListValue
import com.google.protobuf.struct.Struct
import com.google.protobuf.struct.Value
import com.google.protobuf.timestamp.Timestamp
import com.google.protobuf.`type`.OptionProto
import com.google.protobuf.`type`.Syntax
import com.google.protobuf.wrappers.BoolValue
import com.google.protobuf.wrappers.Int64Value
import com.google.protobuf.wrappers.StringValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import scalapb.GeneratedMessage
import scalapb.GeneratedMessageCompanion
import scalapb.json4s.JsonFormat
import scalapb.json4s.JsonFormatException
import scalapb.json4s.Parser
import scalapb.json4s.Printer
import scalapb.json4s.TypeRegistry

import scala.collection.mutable

/**
 * Exercises the proto3 JSON mapping of generated messages through scalapb-json4s: `JsonFormat`,
 * `Printer` and `Parser` render and read lowerCamelCase field names, enum names, the canonical
 * JSON forms of well-known types, and `Any` payloads resolved through a `TypeRegistry`.
 *
 * An API portal stores service definitions as `google.protobuf.Api` messages. Its web console
 * talks JSON: the portal exports each definition as a JSON document, operators edit the document
 * in the browser, and the portal imports the edited JSON back into a message. Annotations on the
 * API travel as `Any`-packed options, and per-release metadata travels as well-known types.
 */
class ApiPortalJsonMappingTest {
  @Test
  def exportedDocumentUsesCamelCaseNamesEnumNamesAndOmitsDefaults(): Unit = {
    val portal: Portal = new Portal(JsonFormat.printer, JsonFormat.parser)
    val json: String = portal.exportDocument(ordersApi(Syntax.SYNTAX_PROTO3))

    assertEquals(
      "{\"name\":\"shop.orders.v1.Orders\",\"methods\":[" +
        "{\"name\":\"PlaceOrder\",\"requestTypeUrl\":\"type.googleapis.com/shop.PlaceOrderRequest\"," +
        "\"responseTypeUrl\":\"type.googleapis.com/shop.Order\",\"syntax\":\"SYNTAX_PROTO3\"}," +
        "{\"name\":\"WatchOrders\",\"requestTypeUrl\":\"type.googleapis.com/shop.WatchRequest\"," +
        "\"responseTypeUrl\":\"type.googleapis.com/shop.Order\",\"responseStreaming\":true," +
        "\"syntax\":\"SYNTAX_PROTO3\"}]," +
        "\"version\":\"1.4.0\",\"sourceContext\":{\"fileName\":\"shop/orders/v1/orders.proto\"}," +
        "\"mixins\":[{\"name\":\"google.longrunning.Operations\",\"root\":\"v1/operations\"}]," +
        "\"syntax\":\"SYNTAX_PROTO3\"}",
      json
    )
    assertEquals(ordersApi(Syntax.SYNTAX_PROTO3), portal.importDocument(json, Api))

    // A proto2 definition leaves syntax at its zero value, so the field disappears from JSON.
    val proto2Json: String = portal.exportDocument(ordersApi(Syntax.SYNTAX_PROTO2))
    assertTrue(!proto2Json.contains("syntax"), proto2Json)
    assertEquals(ordersApi(Syntax.SYNTAX_PROTO2), portal.importDocument(proto2Json, Api))
  }

  @Test
  def schemaExportKeepsProtoNamesDefaultsAndEnumNumbersForTooling(): Unit = {
    val printer: Printer = new Printer()
      .preservingProtoFieldNames
      .includingDefaultValueFields
      .formattingEnumsAsNumber
    val portal: Portal = new Portal(printer, new Parser())
    val method: Method = Method(name = "CancelOrder", requestTypeUrl = "type.googleapis.com/shop.Cancel")

    val json: String = portal.exportDocument(method)
    assertEquals(
      "{\"name\":\"CancelOrder\",\"request_type_url\":\"type.googleapis.com/shop.Cancel\"," +
        "\"request_streaming\":false,\"response_type_url\":\"\",\"response_streaming\":false," +
        "\"options\":[],\"syntax\":0}",
      json
    )
    // The default parser accepts proto field names, enum numbers and explicit defaults alike.
    assertEquals(method, portal.importDocument(json, Method))
    assertEquals(
      Method(name = "CancelOrder", syntax = Syntax.SYNTAX_PROTO3),
      portal.importDocument("{\"name\":\"CancelOrder\",\"syntax\":1}", Method)
    )
  }

  @Test
  def releaseMetadataUsesTheCanonicalJsonFormsOfWellKnownTypes(): Unit = {
    val portal: Portal = new Portal(JsonFormat.printer, JsonFormat.parser)

    val publishedAt: Timestamp = Timestamp(seconds = 1760100000L, nanos = 250000000)
    assertEquals("\"2025-10-10T12:40:00.250Z\"", portal.exportDocument(publishedAt))
    assertEquals(publishedAt, portal.importDocument("\"2025-10-10T12:40:00.250Z\"", Timestamp))

    val deprecationWindow: Duration = Duration(seconds = 7776000L, nanos = 500000000)
    assertEquals("\"7776000.500s\"", portal.exportDocument(deprecationWindow))
    assertEquals(deprecationWindow, portal.importDocument("\"7776000.500s\"", Duration))

    val editable: FieldMask = FieldMask(paths = Seq("version", "source_context.file_name"))
    assertEquals("\"version,sourceContext.fileName\"", portal.exportDocument(editable))
    assertEquals(editable, portal.importDocument("\"version,sourceContext.fileName\"", FieldMask))

    val quota: Int64Value = Int64Value(9007199254740993L)
    assertEquals("\"9007199254740993\"", portal.exportDocument(quota))
    assertEquals(quota, portal.importDocument("9007199254740993", Int64Value))
    assertEquals(BoolValue(true), portal.importDocument("true", BoolValue))
    assertEquals("\"orders-team\"", portal.exportDocument(StringValue("orders-team")))

    val labels: Struct = Struct(fields = Map(
      "tier" -> Value(Value.Kind.StringValue("gold")),
      "public" -> Value(Value.Kind.BoolValue(true)),
      "regions" -> Value(Value.Kind.ListValue(ListValue(Seq(
        Value(Value.Kind.StringValue("eu-west")),
        Value(Value.Kind.NumberValue(2.0))
      ))))
    ))
    val labelsJson: String = portal.exportDocument(labels)
    assertEquals(labels, portal.importDocument(labelsJson, Struct))
    assertTrue(labelsJson.contains("\"regions\":[\"eu-west\",2.0]"), labelsJson)
  }

  @Test
  def annotationsPackedInAnyRoundTripThroughTheTypeRegistry(): Unit = {
    val registry: TypeRegistry = TypeRegistry.empty
      .addMessage[StringValue]
      .addMessage[Timestamp]
      .addMessage[Struct]
    val portal: Portal = new Portal(
      new Printer().withTypeRegistry(registry),
      new Parser().withTypeRegistry(registry)
    )
    val annotated: Api = Api(
      name = "shop.billing.v2.Billing",
      options = Seq(
        OptionProto("owner", Some(ProtoAny.pack(StringValue("billing-team")))),
        OptionProto("sunset", Some(ProtoAny.pack(Timestamp(seconds = 1767225600L)))),
        OptionProto("sla", Some(ProtoAny.pack(Struct(fields = Map(
          "p99_ms" -> Value(Value.Kind.NumberValue(120.0))
        )))))
      )
    )

    val json: String = portal.exportDocument(annotated)
    assertEquals(
      "{\"name\":\"shop.billing.v2.Billing\",\"options\":[" +
        "{\"name\":\"owner\",\"value\":{\"@type\":\"type.googleapis.com/google.protobuf.StringValue\"," +
        "\"value\":\"billing-team\"}}," +
        "{\"name\":\"sunset\",\"value\":{\"@type\":\"type.googleapis.com/google.protobuf.Timestamp\"," +
        "\"value\":\"2026-01-01T00:00:00Z\"}}," +
        "{\"name\":\"sla\",\"value\":{\"@type\":\"type.googleapis.com/google.protobuf.Struct\"," +
        "\"value\":{\"p99_ms\":120.0}}}]}",
      json
    )
    val imported: Api = portal.importDocument(json, Api)
    assertEquals(annotated, imported)
    assertEquals(
      StringValue("billing-team"),
      imported.options.find(_.name == "owner").get.getValue.unpack[StringValue]
    )

    // An annotation whose payload type no registry knows cannot be rendered as JSON.
    val vendorAnnotated: Api = annotated.addOptions(OptionProto(
      "vendor",
      Some(ProtoAny(typeUrl = "type.googleapis.com/acme.Contract", value = StringValue("x").toByteString))
    ))
    assertThrows(classOf[IllegalStateException], () => portal.exportDocument(vendorAnnotated))
  }

  @Test
  def importRejectsMalformedEditsUnlessTheConsoleIsLenient(): Unit = {
    val strict: Portal = new Portal(JsonFormat.printer, JsonFormat.parser)
    val edited: String =
      "{\"name\":\"shop.orders.v1.Orders\",\"version\":\"1.5.0\",\"lastEditor\":\"ops@shop.example\"}"

    val unknown: JsonFormatException =
      assertThrows(classOf[JsonFormatException], () => strict.importDocument(edited, Api))
    assertTrue(unknown.getMessage.contains("lastEditor"), unknown.getMessage)

    val lenient: Portal = new Portal(JsonFormat.printer, new Parser().ignoringUnknownFields)
    assertEquals(Api(name = "shop.orders.v1.Orders", version = "1.5.0"), lenient.importDocument(edited, Api))

    assertThrows(
      classOf[JsonFormatException],
      () => strict.importDocument("{\"name\":\"Ping\",\"syntax\":\"SYNTAX_PROTO9\"}", Method)
    )
    assertThrows(
      classOf[ParseException],
      () => strict.importDocument("\"next tuesday\"", Timestamp)
    )
    assertEquals(3, strict.failedImports.size)
  }

  private def ordersApi(syntax: Syntax): Api = Api(
    name = "shop.orders.v1.Orders",
    methods = Seq(
      Method(
        name = "PlaceOrder",
        requestTypeUrl = "type.googleapis.com/shop.PlaceOrderRequest",
        responseTypeUrl = "type.googleapis.com/shop.Order",
        syntax = syntax
      ),
      Method(
        name = "WatchOrders",
        requestTypeUrl = "type.googleapis.com/shop.WatchRequest",
        responseTypeUrl = "type.googleapis.com/shop.Order",
        responseStreaming = true,
        syntax = syntax
      )
    ),
    version = "1.4.0",
    sourceContext = Some(SourceContext(fileName = "shop/orders/v1/orders.proto")),
    mixins = Seq(Mixin(name = "google.longrunning.Operations", root = "v1/operations")),
    syntax = syntax
  )

  /** Portal backend that converts definitions to and from the console's JSON documents. */
  private final class Portal(printer: Printer, parser: Parser) {
    val failedImports: mutable.ListBuffer[String] = mutable.ListBuffer.empty

    def exportDocument[A <: GeneratedMessage](message: A): String = printer.print(message)

    def importDocument[A <: GeneratedMessage](json: String, companion: GeneratedMessageCompanion[A]): A =
      try parser.fromJsonString[A](json)(companion)
      catch {
        case e @ (_: JsonFormatException | _: ParseException) =>
          failedImports += json
          throw e
      }
  }
}
