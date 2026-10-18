/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_json_circe_3

import com.sun.net.httpserver.HttpServer
import io.circe.Codec as CirceCodec
import io.circe.Json
import io.circe.JsonObject
import io.circe.parser.parse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import sttp.client3.HttpClientSyncBackend
import sttp.client3.Identity
import sttp.client3.Response
import sttp.client3.SttpBackend
import sttp.client3.basicRequest
import sttp.model.MediaType
import sttp.model.StatusCode
import sttp.model.Uri
import sttp.model.Uri.UriContext
import sttp.tapir.*
import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.tapir.json.circe.*
import sttp.tapir.server.jdkhttp.JdkHttpServer

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Schemaless and raw-preserving JSON bodies: free-form circe `Json` and `JsonObject` payloads
 * described by the library's built-in schemas, and `jsonBodyWithRaw` handing the handler the exact
 * request text next to the decoded value.
 *
 * A webhook inbox verifies an HMAC signature over the raw body text before trusting the decoded
 * event, keeps each event's provider-specific payload as an opaque `JsonObject`, and serves it back
 * as a free-form `Json` document. A settings endpoint deep-merges an arbitrary JSON object patch
 * into the stored settings document.
 */
class WebhookInboxSchemalessJsonTest {
  import WebhookInboxSchemalessJsonTest.*

  private var server: HttpServer = null
  private var backend: SttpBackend[Identity, Any] = null
  private var baseUri: Uri = null
  private val inbox: Inbox = new Inbox

  @BeforeEach
  def startServer(): Unit = {
    server = JdkHttpServer()
      .host("localhost")
      .port(0)
      .addEndpoints(inbox.serverEndpoints)
      .start()
    backend = HttpClientSyncBackend()
    baseUri = uri"http://localhost:${server.getAddress.getPort}"
  }

  @AfterEach
  def stopServer(): Unit = {
    if (backend != null) backend.close()
    if (server != null) server.stop(0)
  }

  @Test
  def verifiesTheSignatureOverTheExactRawTextAndKeepsTheOpaquePayload(): Unit = {
    val body: String =
      """{ "kind" : "invoice.paid",
        |  "id": "evt-1001",
        |  "payload": {"invoice": {"number": "INV-7", "amountCents": 12900, "lines": [1, 2]}, "test": false} }""".stripMargin

    val raw: Response[Either[String, String]] = postWebhook("billing", body, sign(body))

    assertEquals(StatusCode.Accepted, raw.code)
    assertTrue(raw.header("Content-Type").exists(_.startsWith("application/json")))
    val receipt: Receipt = parse(raw.body.fold(identity, identity)).flatMap(_.as[Receipt]).fold(throw _, identity)
    assertEquals(Receipt("evt-1001", "billing", "invoice.paid", List("invoice", "test")), receipt)

    val payload: Json = SttpClientInterpreter()
      .toRequestThrowDecodeFailures(getPayload, Some(baseUri))
      .apply("evt-1001")
      .send(backend)
      .body
      .fold(_ => throw new AssertionError("payload not found"), identity)

    val expected: Json = parse("""{"invoice":{"number":"INV-7","amountCents":12900,"lines":[1,2]},"test":false}""")
      .fold(throw _, identity)
    assertEquals(expected, payload)
    assertEquals(Some(12900L), payload.hcursor.downField("invoice").get[Long]("amountCents").toOption)
  }

  @Test
  def rejectsAnEventReformattedAfterSigning(): Unit = {
    val signedText: String = """{"id":"evt-2002","kind":"invoice.voided","payload":{"invoice":"INV-8"}}"""
    val reformatted: String = parse(signedText).fold(throw _, identity).spaces2

    val raw: Response[Either[String, String]] = postWebhook("billing", reformatted, sign(signedText))

    assertEquals(StatusCode.Unauthorized, raw.code)
    val rejection: Rejection = parse(raw.body.fold(identity, identity)).flatMap(_.as[Rejection]).fold(throw _, identity)
    assertEquals(Rejection("bad-signature", "evt-2002"), rejection)
    assertEquals(0, inbox.eventCount)

    val missing: Response[Either[Unit, Json]] = SttpClientInterpreter()
      .toRequestThrowDecodeFailures(getPayload, Some(baseUri))
      .apply("evt-2002")
      .send(backend)
    assertEquals(StatusCode.NotFound, missing.code)
  }

  @Test
  def deepMergesAFreeFormSettingsPatch(): Unit = {
    val patch: JsonObject = JsonObject(
      "theme" -> Json.obj("mode" -> Json.fromString("dark")),
      "beta" -> Json.arr(Json.fromString("search"))
    )

    val merged: Json = SttpClientInterpreter()
      .toRequestThrowDecodeFailures(patchSettings, Some(baseUri))
      .apply(patch)
      .send(backend)
      .body
      .fold(_ => throw new AssertionError("patch rejected"), identity)

    val expected: Json = parse(
      """{"theme":{"mode":"dark","accent":"blue"},"notifications":{"email":true},"beta":["search"]}"""
    ).fold(throw _, identity)
    assertEquals(expected, merged)
    assertEquals(expected, inbox.currentSettings)
  }

  @Test
  def refusesASettingsPatchThatIsNotAJsonObject(): Unit = {
    val raw: Response[Either[String, String]] = basicRequest
      .patch(uri"$baseUri/settings")
      .contentType(MediaType.ApplicationJson)
      .body("""["theme","dark"]""")
      .send(backend)

    assertEquals(StatusCode.BadRequest, raw.code)
    assertEquals(InitialSettings, inbox.currentSettings)
  }

  private def postWebhook(source: String, body: String, signature: String): Response[Either[String, String]] =
    basicRequest
      .post(uri"$baseUri/webhooks/$source")
      .header("X-Signature", signature)
      .contentType(MediaType.ApplicationJson)
      .body(body)
      .send(backend)
}

object WebhookInboxSchemalessJsonTest {
  val SigningSecret: String = "whsec-local-test"

  val InitialSettings: Json = Json.obj(
    "theme" -> Json.obj("mode" -> Json.fromString("light"), "accent" -> Json.fromString("blue")),
    "notifications" -> Json.obj("email" -> Json.True)
  )

  final case class WebhookEvent(id: String, kind: String, payload: JsonObject) derives CirceCodec.AsObject, Schema

  final case class Receipt(eventId: String, source: String, kind: String, payloadFields: List[String])
      derives CirceCodec.AsObject, Schema

  final case class Rejection(reason: String, eventId: String) derives CirceCodec.AsObject, Schema

  /** Hex-encoded HMAC-SHA256 of the text, as the webhook provider computes it. */
  def sign(text: String): String = {
    val mac: Mac = Mac.getInstance("HmacSHA256")
    mac.init(new SecretKeySpec(SigningSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
    HexFormat.of().formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)))
  }

  val receiveWebhook: PublicEndpoint[(String, String, String, WebhookEvent), Rejection, Receipt, Any] =
    endpoint.post
      .in("webhooks" / path[String]("source"))
      .in(header[String]("X-Signature"))
      .in(jsonBodyWithRaw[WebhookEvent])
      .out(statusCode(StatusCode.Accepted))
      .out(jsonBody[Receipt])
      .errorOut(statusCode(StatusCode.Unauthorized))
      .errorOut(jsonBody[Rejection])

  val getPayload: PublicEndpoint[String, Unit, Json, Any] = endpoint.get
    .in("webhooks" / "events" / path[String]("eventId") / "payload")
    .out(jsonBody[Json])
    .errorOut(statusCode(StatusCode.NotFound))

  val patchSettings: PublicEndpoint[JsonObject, Unit, Json, Any] = endpoint.patch
    .in("settings")
    .in(jsonBody[JsonObject])
    .out(jsonBody[Json])

  /** Stored events and settings behind the three endpoints. */
  final class Inbox {
    private val events: ConcurrentHashMap[String, WebhookEvent] = new ConcurrentHashMap[String, WebhookEvent]()
    private val settings: AtomicReference[Json] = new AtomicReference[Json](InitialSettings)

    def eventCount: Int = events.size()

    def currentSettings: Json = settings.get()

    val serverEndpoints = List(
      receiveWebhook.handle { (source, signature, rawText, event) =>
        if (!signatureMatches(sign(rawText), signature)) Left(Rejection("bad-signature", event.id))
        else {
          events.put(event.id, event)
          Right(Receipt(event.id, source, event.kind, event.payload.keys.toList))
        }
      },
      getPayload.handle { eventId =>
        Option(events.get(eventId)).map(event => Json.fromJsonObject(event.payload)).toRight(())
      },
      patchSettings.handle { patch =>
        Right(settings.updateAndGet(current => current.deepMerge(Json.fromJsonObject(patch))))
      }
    )

    /** Constant-time comparison, as a signature check should be. */
    private def signatureMatches(expected: String, actual: String): Boolean =
      MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.US_ASCII),
        actual.getBytes(StandardCharsets.US_ASCII)
      )
  }
}
