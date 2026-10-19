/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_lihaoyi.upickle_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import upickle.default.*
import upickle.implicits.key

/**
 * Typed envelopes that embed schema-less payloads (ujson.Value, ujson.Obj and ujson.Arr fields
 * inside derived case classes), decoded lazily into a sealed hierarchy whose discriminator key is
 * renamed per trait and may arrive after the other fields, as a webhook inbox stores raw provider
 * payloads and dispatches only the event kinds it understands.
 */
class WebhookInboxEnvelopeTest {

  import WebhookInboxEnvelopeTest.*

  @Test
  def envelopeKeepsRawPayloadVerbatimAcrossStorage(): Unit = {
    val incoming: String =
      """{"id":"evt-1","source":"shipping","receivedAt":1700000000000,
        |"headers":{"x-signature":"abc","x-retry":2},
        |"payload":{"kind":"parcel-shipped","parcel":"P-7","carrier":"DHL","extra":{"legs":[1,2]}},
        |"trail":["edge","queue"]}""".stripMargin

    val envelope: Envelope = read[Envelope](incoming)
    val stored: String = write(envelope)
    val reloaded: Envelope = read[Envelope](stored)

    assertEquals(envelope, reloaded)
    assertEquals("abc", reloaded.headers("x-signature").str)
    assertEquals(2.0, reloaded.headers("x-retry").num)
    assertEquals(ujson.Arr(1, 2), reloaded.payload("extra")("legs"))
    assertEquals(Seq("edge", "queue"), reloaded.trail.arr.map(_.str).toSeq)
    assertEquals(ujson.read(incoming), ujson.read(stored))
  }

  @Test
  def payloadDecodesIntoEventsUsingTheRenamedDiscriminator(): Unit = {
    val envelope: Envelope = Envelope(
      id = "evt-2",
      source = "shipping",
      receivedAt = 1700000000500L,
      headers = ujson.Obj("x-signature" -> "def"),
      payload = writeJs[ShippingEvent](ShippingEvent.ParcelDelivered("P-7", signedBy = Some("Ana"))),
      trail = ujson.Arr()
    )

    val json: ujson.Value = ujson.read(write(envelope))

    assertEquals("parcel-delivered", json("payload")("kind").str)
    assertTrue(!json("payload").obj.contains("$type"), json.render())
    assertEquals(
      ShippingEvent.ParcelDelivered("P-7", Some("Ana")),
      read[ShippingEvent](json("payload"))
    )
  }

  @Test
  def discriminatorArrivingAfterOtherFieldsIsStillRecognized(): Unit = {
    val lateTag: String = """{"parcel":"P-9","reason":"address unknown","kind":"parcel-returned"}"""
    val middleTag: String = """{"parcel":"P-3","kind":"parcel-shipped","carrier":"UPS"}"""

    assertEquals(ShippingEvent.ParcelReturned("P-9", "address unknown"), read[ShippingEvent](lateTag))
    assertEquals(ShippingEvent.ParcelShipped("P-3", "UPS"), read[ShippingEvent](middleTag))
  }

  @Test
  def inboxDispatchesKnownKindsAndParksUnknownOnes(): Unit = {
    val batch: String =
      """[
        |{"id":"a","source":"shipping","receivedAt":1,"headers":{},"trail":[],
        | "payload":{"kind":"parcel-shipped","parcel":"P-1","carrier":"DHL"}},
        |{"id":"b","source":"shipping","receivedAt":2,"headers":{},"trail":[],
        | "payload":{"parcel":"P-1","kind":"parcel-delivered"}},
        |{"id":"c","source":"shipping","receivedAt":3,"headers":{},"trail":[],
        | "payload":{"kind":"parcel-lost","parcel":"P-2","insured":true}},
        |{"id":"d","source":"billing","receivedAt":4,"headers":{},"trail":[],
        | "payload":{"invoice":"INV-1"}}
        |]""".stripMargin

    val inbox: Inbox = Inbox()
    read[Seq[Envelope]](batch).foreach(inbox.accept)

    assertEquals(
      Seq(
        ShippingEvent.ParcelShipped("P-1", "DHL"),
        ShippingEvent.ParcelDelivered("P-1", None)
      ),
      inbox.dispatched.toSeq
    )
    assertEquals(Seq("c", "d"), inbox.parked.map(_.id).toSeq)

    val parkedReport: ujson.Value = writeJs(inbox.parked.toSeq)
    assertEquals(ujson.True, parkedReport(0)("payload")("insured"))
    assertEquals("INV-1", parkedReport(1)("payload")("invoice").str)
  }

  @Test
  def replayAnnotatesEnvelopesWithoutTouchingTheirPayloads(): Unit = {
    val original: Envelope = read[Envelope](
      """{"id":"evt-5","source":"shipping","receivedAt":10,"headers":{"x-retry":0},
        |"payload":{"kind":"parcel-shipped","parcel":"P-5","carrier":"GLS"},"trail":["edge"]}""".stripMargin
    )

    val replayed: Envelope = original.copy(
      headers = ujson.Obj.from(original.headers.value ++ Seq("x-retry" -> ujson.Num(1), "x-replay" -> ujson.True)),
      trail = ujson.Arr.from(original.trail.arr :+ ujson.Str("replay"))
    )
    val restored: Envelope = read[Envelope](write(replayed))

    assertEquals(original.payload, restored.payload)
    assertEquals(1.0, restored.headers("x-retry").num)
    assertEquals(ujson.True, restored.headers("x-replay"))
    assertEquals(Seq("edge", "replay"), restored.trail.arr.map(_.str).toSeq)
    assertEquals(0.0, original.headers("x-retry").num)
  }
}

object WebhookInboxEnvelopeTest {

  final case class Envelope(
      id: String,
      source: String,
      receivedAt: Long,
      headers: ujson.Obj,
      payload: ujson.Value,
      trail: ujson.Arr
  ) derives ReadWriter

  @key("kind")
  sealed trait ShippingEvent derives ReadWriter

  object ShippingEvent {
    @key("parcel-shipped")
    final case class ParcelShipped(parcel: String, carrier: String) extends ShippingEvent

    @key("parcel-delivered")
    final case class ParcelDelivered(parcel: String, signedBy: Option[String] = None) extends ShippingEvent

    @key("parcel-returned")
    final case class ParcelReturned(parcel: String, reason: String) extends ShippingEvent
  }

  /** Routes shipping payloads of known kinds to handlers and parks everything else for review. */
  final class Inbox {
    val dispatched: collection.mutable.ArrayBuffer[ShippingEvent] = collection.mutable.ArrayBuffer()
    val parked: collection.mutable.ArrayBuffer[Envelope] = collection.mutable.ArrayBuffer()

    def accept(envelope: Envelope): Unit = {
      val kind: Option[String] = envelope.payload.objOpt.flatMap(_.get("kind")).flatMap(_.strOpt)
      if (envelope.source == "shipping" && kind.exists(KnownKinds.contains)) {
        dispatched += read[ShippingEvent](envelope.payload)
      } else {
        parked += envelope
      }
    }
  }

  private val KnownKinds: Set[String] = Set("parcel-shipped", "parcel-delivered", "parcel-returned")
}
