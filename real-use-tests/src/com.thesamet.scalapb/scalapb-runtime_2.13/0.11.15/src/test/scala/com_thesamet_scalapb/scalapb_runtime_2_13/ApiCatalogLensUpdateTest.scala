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
import com.google.protobuf.source_context.SourceContext
import com.google.protobuf.struct.ListValue
import com.google.protobuf.struct.Struct
import com.google.protobuf.struct.Value
import com.google.protobuf.`type`.Syntax
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import scalapb.lenses.Lens

/**
 * Exercises the lens-based immutable update API of generated messages: `update` with `:=`,
 * `modify`, `setIfDefined`, sequence and map lenses, optional-field lenses and composed lenses.
 *
 * An API catalog keeps every published service as a `google.protobuf.Api` and its rollout
 * settings as a `google.protobuf.Struct`. Release tooling never rebuilds these messages by hand:
 * it applies small, named edits (bump a version, add a method, rename a method, attach a mixin,
 * tune a nested setting) and the catalog checks that each edit leaves the original untouched.
 */
class ApiCatalogLensUpdateTest {
  private val orders: Api = Api(
    name = "shop.OrderService",
    version = "v1",
    methods = Seq(
      Method(name = "GetOrder", requestTypeUrl = "type.googleapis.com/shop.GetOrderRequest",
        responseTypeUrl = "type.googleapis.com/shop.Order"),
      Method(name = "ListOrders", requestTypeUrl = "type.googleapis.com/shop.ListOrdersRequest",
        responseTypeUrl = "type.googleapis.com/shop.Order")
    ),
    syntax = Syntax.SYNTAX_PROTO3
  )

  private val rollout: Struct = Struct(Map(
    "enabled" -> Value(Value.Kind.BoolValue(false)),
    "timeoutSeconds" -> Value(Value.Kind.NumberValue(5)),
    "regions" -> Value(Value.Kind.ListValue(ListValue(Seq(Value(Value.Kind.StringValue("eu")))))),
    "limits" -> Value(Value.Kind.StructValue(Struct(Map(
      "rps" -> Value(Value.Kind.NumberValue(100))
    ))))
  ))

  /** The catalog's named release edits, each a function from one revision to the next. */
  private object ReleaseEdits {
    val versionLens: Lens[Api, String] = Lens.unit[Api].version
    val firstMethodName: Lens[Api, String] = Lens.unit[Api].methods(0).name

    def bumpVersion(api: Api): Api = api.update(_.version.modify(v => "v" + (v.drop(1).toInt + 1)))

    def addMethod(api: Api, method: Method): Api = api.update(_.methods :+= method)

    def enableServerStreaming(api: Api, methodName: String): Api = api.update(
      _.methods.modify(_.map(m =>
        if (m.name == methodName) m.update(_.responseStreaming := true) else m))
    )

    def renameFirstMethod(api: Api, name: String): Api = firstMethodName.set(name)(api)

    def recordSource(api: Api, fileName: Option[String]): Api =
      api.update(_.sourceContext.fileName.setIfDefined(fileName))
  }

  @Test
  def releaseEditsProduceNewRevisionsWithoutTouchingTheOriginal(): Unit = {
    val withSearch: Api = ReleaseEdits.addMethod(
      orders,
      Method(name = "SearchOrders", requestTypeUrl = "type.googleapis.com/shop.SearchRequest",
        responseTypeUrl = "type.googleapis.com/shop.Order")
    )
    val v2: Api = ReleaseEdits.enableServerStreaming(ReleaseEdits.bumpVersion(withSearch), "ListOrders")

    assertEquals("v2", v2.version)
    assertEquals(Seq("GetOrder", "ListOrders", "SearchOrders"), v2.methods.map(_.name))
    assertEquals(Seq(false, true, false), v2.methods.map(_.responseStreaming))
    assertEquals("v2", ReleaseEdits.versionLens.get(v2))

    assertEquals("v1", orders.version)
    assertEquals(2, orders.methods.size)
    assertFalse(orders.methods.exists(_.responseStreaming))

    val renamed: Api = ReleaseEdits.renameFirstMethod(v2, "FetchOrder")
    assertEquals("FetchOrder", ReleaseEdits.firstMethodName.get(renamed))
    assertEquals("GetOrder", ReleaseEdits.firstMethodName.get(v2))
  }

  @Test
  def bulkEditsApplySeveralLensesInOneUpdate(): Unit = {
    val published: Api = orders.update(
      _.name := "shop.v2.OrderService",
      _.methods.foreach(_.requestStreaming := true),
      _.methods(1).responseTypeUrl := "type.googleapis.com/shop.OrderPage",
      _.mixins :++= Seq(Mixin(name = "google.iam.v1.IAMPolicy"), Mixin(name = "shop.Health", root = "health")),
      _.sourceContext.fileName := "shop/v2/orders.proto"
    )

    assertEquals("shop.v2.OrderService", published.name)
    assertTrue(published.methods.forall(_.requestStreaming))
    assertEquals("type.googleapis.com/shop.OrderPage", published.methods(1).responseTypeUrl)
    assertEquals("type.googleapis.com/shop.Order", published.methods(0).responseTypeUrl)
    assertEquals(Seq("google.iam.v1.IAMPolicy", "shop.Health"), published.mixins.map(_.name))
    assertEquals(Some(SourceContext(fileName = "shop/v2/orders.proto")), published.sourceContext)
    assertEquals(Syntax.SYNTAX_PROTO3, published.syntax)

    val unchanged: Api = ReleaseEdits.recordSource(published, None)
    assertEquals(published, unchanged)
    val moved: Api = ReleaseEdits.recordSource(published, Some("shop/orders.proto"))
    assertEquals("shop/orders.proto", moved.getSourceContext.fileName)

    val detached: Api = moved.update(_.optionalSourceContext := None)
    assertEquals(None, detached.sourceContext)
    assertEquals(Api.parseFrom(detached.toByteArray), detached)
  }

  @Test
  def rolloutSettingsAreTunedThroughMapAndOneofLenses(): Unit = {
    val launched: Struct = rollout.update(
      _.fields("enabled").boolValue := true,
      _.fields("timeoutSeconds").numberValue.modify(_ * 3),
      _.fields("regions").listValue.values :+= Value(Value.Kind.StringValue("us")),
      _.fields("limits").structValue.fields("rps").numberValue := 250,
      _.fields("limits").structValue.fields :+= ("burst" -> Value(Value.Kind.NumberValue(500))),
      _.fields :+= ("owner" -> Value(Value.Kind.StringValue("checkout-team")))
    )

    assertEquals(Value.Kind.BoolValue(true), launched.fields("enabled").kind)
    assertEquals(15.0, launched.fields("timeoutSeconds").getNumberValue)
    assertEquals(Seq("eu", "us"), launched.fields("regions").getListValue.values.map(_.getStringValue))
    val limits: Struct = launched.fields("limits").getStructValue
    assertEquals(250.0, limits.fields("rps").getNumberValue)
    assertEquals(500.0, limits.fields("burst").getNumberValue)
    assertEquals("checkout-team", launched.fields("owner").getStringValue)

    val enabledAndTimeout: Lens[Struct, (Value, Value)] =
      Lens.unit[Struct].fields("enabled").zip(Lens.unit[Struct].fields("timeoutSeconds"))
    val (enabled, timeout): (Value, Value) = enabledAndTimeout.get(launched)
    assertTrue(enabled.getBoolValue)
    assertEquals(15.0, timeout.getNumberValue)

    val frozen: Struct = launched.update(
      _.fields.foreachValue(_.modify(v => if (v.kind.isNumberValue) v.update(_.numberValue := 0) else v))
    )
    assertEquals(0.0, frozen.fields("timeoutSeconds").getNumberValue)
    assertEquals(250.0, frozen.fields("limits").getStructValue.fields("rps").getNumberValue)
    assertTrue(frozen.fields("enabled").getBoolValue)

    assertEquals(Value.Kind.BoolValue(false), rollout.fields("enabled").kind)
    assertEquals(Struct.parseFrom(launched.toByteArray), launched)
  }
}
