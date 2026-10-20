/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thesamet_scalapb.scalapb_runtime_2_13

import com.google.protobuf.any.{Any => ProtoAny}
import com.google.protobuf.duration.Duration
import com.google.protobuf.empty.Empty
import com.google.protobuf.field_mask.FieldMask
import com.google.protobuf.struct.Struct
import com.google.protobuf.struct.Value
import com.google.protobuf.timestamp.Timestamp
import com.google.protobuf.wrappers.Int32Value
import com.google.protobuf.wrappers.StringValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import scalapb.GeneratedMessage
import scalapb.GeneratedMessageCompanion

import scala.collection.mutable.ListBuffer

/**
 * Exercises `google.protobuf.Any` packing and unpacking: `Any.pack`, `is` and `unpack` wrap
 * well-known-type payloads in type-URL envelopes and recover them by type.
 *
 * An order service writes domain events to a transactional outbox. Each outbox row holds the
 * serialized bytes of an `Any` envelope, so heterogeneous payloads share one column. A relay
 * reads the rows back, dispatches each envelope to the handler registered for its type, and
 * parks envelopes no handler understands instead of losing them.
 */
class EventOutboxAnyEnvelopeTest {
  @Test
  def relayDispatchesEveryEnvelopeToTheHandlerForItsPayloadType(): Unit = {
    val outbox: Outbox = new Outbox
    outbox.publish("order-17", Struct(fields = Map(
      "sku" -> Value(Value.Kind.StringValue("KB-104")),
      "quantity" -> Value(Value.Kind.NumberValue(3))
    )))
    outbox.publish("order-17", Timestamp(seconds = 1760100000L))
    outbox.publish("order-17", Duration(seconds = 86400L, nanos = 500000000))
    outbox.publish("order-17", StringValue("gift wrap requested"))
    outbox.publish("order-17", Int32Value(2))
    outbox.publish("customer-5", FieldMask(paths = Seq("email", "shipping_address.city")))
    outbox.publish("relay", Empty())

    val relay: Relay = new Relay
    relay.drain(outbox)

    assertEquals(
      List(
        "order-17 placed KB-104 x3",
        "order-17 payment due at 1760100000",
        "order-17 sla extended by 86400500 ms",
        "order-17 note: gift wrap requested",
        "order-17 retry #2",
        "customer-5 updated email,shipping_address.city",
        "relay heartbeat"
      ),
      relay.handled.toList
    )
    assertTrue(relay.parked.isEmpty)
  }

  @Test
  def envelopesCarryTypeUrlsAndSurviveTheOutboxColumnByteForByte(): Unit = {
    val due: Timestamp = Timestamp(seconds = 1760200000L, nanos = 42)
    val envelope: ProtoAny = ProtoAny.pack(due)
    assertEquals("type.googleapis.com/google.protobuf.Timestamp", envelope.typeUrl)
    assertEquals(due.toByteString, envelope.value)

    val tenantEnvelope: ProtoAny = ProtoAny.pack(due, "type.shop.example")
    assertEquals("type.shop.example/google.protobuf.Timestamp", tenantEnvelope.typeUrl)
    assertTrue(tenantEnvelope.is[Timestamp])
    assertEquals(due, tenantEnvelope.unpack[Timestamp])

    val column: Array[Byte] = envelope.toByteArray
    val restored: ProtoAny = ProtoAny.parseFrom(column)
    assertEquals(envelope, restored)
    assertTrue(restored.is[Timestamp])
    assertFalse(restored.is[Duration])
    assertFalse(restored.is[Struct])
    assertEquals(due, restored.unpack[Timestamp])
  }

  @Test
  def envelopesFromNewerProducersAreParkedAndWrongUnpacksAreRejected(): Unit = {
    val outbox: Outbox = new Outbox
    outbox.publish("order-18", StringValue("leave at door"))
    outbox.append(new OutboxRow(
      "order-18",
      ProtoAny(typeUrl = "type.shop.example/shop.v2.LoyaltyPointsAwarded", value =
        Int32Value(150).toByteString).toByteArray
    ))
    outbox.publish("order-18", Int32Value(1))

    val relay: Relay = new Relay
    relay.drain(outbox)

    assertEquals(List("order-18 note: leave at door", "order-18 retry #1"), relay.handled.toList)
    assertEquals(List("type.shop.example/shop.v2.LoyaltyPointsAwarded"), relay.parked.map(_.typeUrl).toList)

    val note: ProtoAny = ProtoAny.pack(StringValue("fragile"))
    val rejected: IllegalArgumentException =
      assertThrows(classOf[IllegalArgumentException], () => note.unpack[Int32Value])
    assertTrue(rejected.getMessage.contains("does not match"))
  }

  /** One outbox row: the aggregate the event belongs to and the serialized `Any` envelope. */
  final class OutboxRow(val aggregateId: String, val payload: Array[Byte])

  /** The outbox table, kept in insertion order the way a database sequence would order it. */
  final class Outbox {
    private val rows: ListBuffer[OutboxRow] = ListBuffer.empty

    def publish[A <: GeneratedMessage](aggregateId: String, event: A): Unit =
      append(new OutboxRow(aggregateId, ProtoAny.pack(event, "type.googleapis.com").toByteArray))

    def append(row: OutboxRow): Unit = rows += row

    def pending: List[OutboxRow] = rows.toList
  }

  /** A handler bound to one payload type; it only sees envelopes that hold that type. */
  final class Handler[A <: GeneratedMessage](
      val companion: GeneratedMessageCompanion[A],
      val describe: (String, A) => String
  ) {
    def accepts(envelope: ProtoAny): Boolean = envelope.is(companion)

    def handle(aggregateId: String, envelope: ProtoAny): String =
      describe(aggregateId, envelope.unpack(companion))
  }

  /** Reads the outbox, unwraps each envelope and dispatches it by payload type. */
  final class Relay {
    val handled: ListBuffer[String] = ListBuffer.empty
    val parked: ListBuffer[ProtoAny] = ListBuffer.empty

    private val handlers: List[Handler[_ <: GeneratedMessage]] = List(
      new Handler[Struct](Struct, (id, order) =>
        s"$id placed ${order.fields("sku").getStringValue} " +
          s"x${order.fields("quantity").getNumberValue.toInt}"),
      new Handler[Timestamp](Timestamp, (id, due) => s"$id payment due at ${due.seconds}"),
      new Handler[Duration](Duration, (id, sla) =>
        s"$id sla extended by ${sla.seconds * 1000 + sla.nanos / 1000000} ms"),
      new Handler[StringValue](StringValue, (id, note) => s"$id note: ${note.value}"),
      new Handler[Int32Value](Int32Value, (id, attempt) => s"$id retry #${attempt.value}"),
      new Handler[FieldMask](FieldMask, (id, mask) => s"$id updated ${mask.paths.mkString(",")}"),
      new Handler[Empty](Empty, (id, _) => s"$id heartbeat")
    )

    def drain(outbox: Outbox): Unit =
      outbox.pending.foreach { row =>
        val envelope: ProtoAny = ProtoAny.parseFrom(row.payload)
        handlers.find(_.accepts(envelope)) match {
          case Some(handler) => handled += handler.handle(row.aggregateId, envelope)
          case None => parked += envelope
        }
      }
  }
}
