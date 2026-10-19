/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thesamet_scalapb.scalapb_runtime_2_13

import com.google.protobuf.ByteString
import com.google.protobuf.any.{Any => ProtoAny}
import com.google.protobuf.api.Method
import com.google.protobuf.api.Mixin
import com.google.protobuf.timestamp.Timestamp
import com.google.protobuf.`type`.OptionProto
import com.google.protobuf.`type`.Syntax
import com.google.protobuf.wrappers.Int32Value
import com.google.protobuf.wrappers.StringValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import scalapb.UnknownFieldSet

/**
 * Exercises unknown-field preservation through `scalapb.UnknownFieldSet`: parsing fields an older
 * schema does not know, carrying them through updates and re-serialization, merging them across
 * concatenated payloads, discarding them, and attaching hand-built unknown fields.
 *
 * An edge router was compiled against version 1 of a route schema with two string fields, `name`
 * (1) and `target` (2); the well-known `google.protobuf.Mixin` has exactly that shape. The control
 * plane already emits version 2, whose leading fields match and which adds streaming flags,
 * options and a syntax tag; `google.protobuf.Method` has that shape. During a rolling upgrade the
 * router must rename routes and forward them without losing the fields only version 2 knows.
 */
class RouteRelayUnknownFieldsTest {
  private val checkout: Method = Method(
    name = "Checkout",
    requestTypeUrl = "shop.internal:8443",
    requestStreaming = true,
    responseTypeUrl = "type.googleapis.com/shop.Receipt",
    options = Seq(
      OptionProto(name = "timeout_ms", value = Some(ProtoAny.pack(Int32Value(2500)))),
      OptionProto(name = "owner", value = Some(ProtoAny.pack(StringValue("payments-team"))))
    ),
    syntax = Syntax.SYNTAX_PROTO3
  )

  /** The version 1 router: it only understands `Mixin`, yet must forward version 2 bytes. */
  private object EdgeRouter {
    def decode(bytes: Array[Byte]): Mixin = Mixin.parseFrom(bytes)

    def rename(bytes: Array[Byte], prefix: String): Array[Byte] = {
      val route: Mixin = decode(bytes)
      route.withName(prefix + route.name).toByteArray
    }

    def strip(bytes: Array[Byte]): Array[Byte] = decode(bytes).discardUnknownFields.toByteArray
  }

  @Test
  def olderSchemaKeepsNewerFieldsAsUnknownFields(): Unit = {
    val route: Mixin = EdgeRouter.decode(checkout.toByteArray)

    assertEquals("Checkout", route.name)
    assertEquals("shop.internal:8443", route.root)

    val unknown: UnknownFieldSet = route.unknownFields
    assertEquals(Set(3, 4, 6, 7), unknown.asMap.keySet)
    assertEquals(Some(UnknownFieldSet.Field(varint = Seq(1L))), unknown.getField(3))
    assertEquals(
      Some(UnknownFieldSet.Field(
        lengthDelimited = Seq(ByteString.copyFromUtf8("type.googleapis.com/shop.Receipt"))
      )),
      unknown.getField(4)
    )
    assertEquals(
      Seq(checkout.options(0).toByteString, checkout.options(1).toByteString),
      unknown.getField(6).get.lengthDelimited
    )
    assertEquals(Seq(Syntax.SYNTAX_PROTO3.value.toLong), unknown.getField(7).get.varint)
    assertEquals(None, unknown.getField(5))
    assertEquals(checkout.serializedSize, route.serializedSize)
  }

  @Test
  def relayedRoutesReachNewerConsumersIntact(): Unit = {
    val forwarded: Array[Byte] = EdgeRouter.rename(checkout.toByteArray, "eu-west/")

    val received: Method = Method.parseFrom(forwarded)
    assertEquals(checkout.withName("eu-west/Checkout"), received)
    assertEquals(2500, received.options.head.value.get.unpack[Int32Value].value)
    assertEquals("payments-team", received.options(1).value.get.unpack[StringValue].value)
    assertTrue(received.unknownFields.asMap.isEmpty)
  }

  @Test
  def strippedRoutesLoseOnlyTheNewerFields(): Unit = {
    val stripped: Array[Byte] = EdgeRouter.strip(checkout.toByteArray)

    assertEquals(Method(name = "Checkout", requestTypeUrl = "shop.internal:8443"),
      Method.parseFrom(stripped))
    assertEquals(UnknownFieldSet.empty, EdgeRouter.decode(stripped).unknownFields)
    assertNotEquals(EdgeRouter.decode(checkout.toByteArray), EdgeRouter.decode(stripped))
  }

  @Test
  def concatenatedPayloadsMergeUnknownFields(): Unit = {
    val canaryUpdate: Method = Method(
      name = "CheckoutV2",
      responseTypeUrl = "type.googleapis.com/shop.ReceiptV2",
      options = Seq(OptionProto(name = "canary", value = Some(ProtoAny.pack(StringValue("5%")))))
    )

    val merged: Mixin = EdgeRouter.decode(checkout.toByteArray ++ canaryUpdate.toByteArray)
    assertEquals("CheckoutV2", merged.name)
    assertEquals("shop.internal:8443", merged.root)
    assertEquals(3, merged.unknownFields.getField(6).get.lengthDelimited.size)
    assertEquals(2, merged.unknownFields.getField(4).get.lengthDelimited.size)

    val received: Method = Method.parseFrom(merged.toByteArray)
    assertEquals(
      checkout
        .withName("CheckoutV2")
        .withResponseTypeUrl("type.googleapis.com/shop.ReceiptV2")
        .withOptions(checkout.options ++ canaryUpdate.options),
      received
    )
  }

  @Test
  def handBuiltUnknownFieldsTravelWithAMessage(): Unit = {
    val annotations: UnknownFieldSet = UnknownFieldSet.empty
      .withField(10, UnknownFieldSet.Field(varint = Seq(3L, 5L)))
      .withField(11, UnknownFieldSet.Field(fixed64 = Seq(0x1122334455667788L)))
      .withField(12, UnknownFieldSet.Field(fixed32 = Seq(-7)))
      .withField(13, UnknownFieldSet.Field(lengthDelimited = Seq(ByteString.copyFromUtf8("utc"))))

    val plain: Timestamp = Timestamp(seconds = 1760000000L, nanos = 125000000)
    val annotated: Timestamp = plain.withUnknownFields(annotations)

    assertEquals(plain.serializedSize + annotations.serializedSize, annotated.serializedSize)
    assertNotEquals(plain, annotated)

    val parsed: Timestamp = Timestamp.parseFrom(annotated.toByteArray)
    assertEquals(annotated, parsed)
    assertEquals(Seq(3L, 5L), parsed.unknownFields.getField(10).get.varint)
    assertEquals(Seq(0x1122334455667788L), parsed.unknownFields.getField(11).get.fixed64)
    assertEquals(Seq(-7), parsed.unknownFields.getField(12).get.fixed32)
    assertEquals("utc", parsed.unknownFields.getField(13).get.lengthDelimited.head.toStringUtf8)

    assertEquals(plain, parsed.discardUnknownFields)
    assertEquals(plain.toByteString, parsed.discardUnknownFields.toByteString)
  }
}
