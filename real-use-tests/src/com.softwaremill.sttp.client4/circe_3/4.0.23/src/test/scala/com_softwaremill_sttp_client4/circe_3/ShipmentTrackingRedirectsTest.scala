/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_client4.circe_3

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.circe.generic.auto.*
import io.circe.parser.decode
import io.circe.syntax.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import sttp.client4.*
import sttp.client4.circe.*
import sttp.client4.wrappers.ResolveRelativeUrisBackend
import sttp.model.Method
import sttp.model.StatusCode

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

/**
 * Exercises circe JSON exchanges across HTTP redirects: a shipment client resolves relative
 * request URIs against its API base, follows relative and absolute `Location` headers while
 * decoding the final JSON with `asJson`, keeps or drops `asJson` request bodies per redirect
 * status, decodes an unfollowed redirect with `asJsonAlways`, and stops at a redirect limit.
 */
class ShipmentTrackingRedirectsTest {
  private final case class Address(city: String, country: String)
  private final case class Shipment(id: Long, carrier: String, destination: Address, status: String)
  private final case class LabelRequest(shipmentId: Long, format: String, copies: Int)
  private final case class Label(shipmentId: Long, format: String, copies: Int, url: String)
  private final case class PickupRequest(shipmentId: Long, window: String)
  private final case class PickupStatus(scheduled: Int, lastWindow: String)
  private final case class MovedNotice(message: String, replacement: String)
  private final case class RecordedRequest(
      method: String,
      path: String,
      contentType: Option[String],
      authorization: Option[String],
      body: String
  )

  private var server: HttpServer = null
  private var backend: SyncBackend = null
  private var apiBackend: SyncBackend = null
  private val recorded: ConcurrentLinkedQueue[RecordedRequest] = new ConcurrentLinkedQueue[RecordedRequest]()
  private val pickups: ConcurrentLinkedQueue[PickupRequest] = new ConcurrentLinkedQueue[PickupRequest]()

  @BeforeEach
  def startServer(): Unit = {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext(
      "/",
      (exchange: HttpExchange) => {
        try {
          val path: String = exchange.getRequestURI.getPath
          val body: String = new String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
          recorded.add(
            RecordedRequest(
              exchange.getRequestMethod,
              path,
              Option(exchange.getRequestHeaders.getFirst("Content-Type")),
              Option(exchange.getRequestHeaders.getFirst("Authorization")),
              body
            )
          )
          route(exchange, exchange.getRequestMethod, path, body)
        } finally {
          exchange.close()
        }
      }
    )
    server.start()
    backend = DefaultSyncBackend(BackendOptions.connectionTimeout(10.seconds))
    apiBackend = ResolveRelativeUrisBackend(backend, uri"http://127.0.0.1:${server.getAddress.getPort}")
  }

  @AfterEach
  def stopServer(): Unit = {
    apiBackend.close()
    server.stop(0)
  }

  @Test
  def followsRelativeAndAbsoluteRedirectsToTypedShipments(): Unit = {
    val legacy: Response[Either[ResponseException[String], Shipment]] = basicRequest
      .get(uri"/v1/shipments/7")
      .auth
      .bearer("tracking-token")
      .response(asJson[Shipment])
      .readTimeout(10.seconds)
      .send(apiBackend)

    assertThat(legacy.code).isEqualTo(StatusCode.Ok)
    assertThat(legacy.body).isEqualTo(Right(Shipment(7L, "DHL", Address("Lyon", "FR"), "IN_TRANSIT")))
    assertThat(legacy.history.map(_.code)).isEqualTo(List(StatusCode.MovedPermanently, StatusCode.Found))
    assertThat(legacy.history.flatMap(_.header("Location")))
      .isEqualTo(List("/v2/shipments/7", s"http://127.0.0.1:${server.getAddress.getPort}/v3/shipments/7"))
    assertThat(legacy.request.uri.path).isEqualTo(List("v3", "shipments", "7"))

    val hops: List[RecordedRequest] = recorded.asScala.toList
    assertThat(hops.map(_.path)).isEqualTo(List("/v1/shipments/7", "/v2/shipments/7", "/v3/shipments/7"))
    assertThat(hops.map(_.method).distinct).isEqualTo(List("GET"))
    assertThat(hops.head.authorization).isEqualTo(Some("Bearer tracking-token"))
    assertThat(hops.tail.map(_.authorization)).isEqualTo(List(None, None))
  }

  @Test
  def keepsJsonBodyOnTemporaryRedirectAndDropsItOnSeeOther(): Unit = {
    val labelRequest: LabelRequest = LabelRequest(7L, "PDF", 2)
    val label: Label = basicRequest
      .post(uri"/v1/labels")
      .body(asJson(labelRequest))
      .response(asJsonOrFail[Label])
      .readTimeout(10.seconds)
      .send(apiBackend)
      .body

    assertThat(label).isEqualTo(Label(7L, "PDF", 2, "/labels/7.pdf"))
    val labelHops: List[RecordedRequest] = recorded.asScala.toList
    assertThat(labelHops.map(hop => (hop.method, hop.path)))
      .isEqualTo(List(("POST", "/v1/labels"), ("POST", "/v2/labels")))
    assertThat(labelHops.map(_.body).distinct).isEqualTo(List(labelRequest.asJson.noSpaces))
    assertThat(labelHops.last.contentType.getOrElse("")).startsWith("application/json")
    recorded.clear()

    val scheduled: Response[Either[ResponseException[String], PickupStatus]] = basicRequest
      .post(uri"/v2/pickups")
      .body(asJson(PickupRequest(7L, "09:00-12:00")))
      .response(asJson[PickupStatus])
      .readTimeout(10.seconds)
      .send(apiBackend)

    assertThat(scheduled.body).isEqualTo(Right(PickupStatus(1, "09:00-12:00")))
    assertThat(scheduled.history.map(_.code)).isEqualTo(List(StatusCode.SeeOther))
    assertThat(scheduled.request.method).isEqualTo(Method.GET)
    val pickupHops: List[RecordedRequest] = recorded.asScala.toList
    assertThat(pickupHops.map(hop => (hop.method, hop.path)))
      .isEqualTo(List(("POST", "/v2/pickups"), ("GET", "/v2/pickups/status")))
    assertThat(pickupHops.last.body).isEmpty()
    assertThat(pickupHops.last.contentType).isEqualTo(None)
  }

  @Test
  def decodesUnfollowedRedirectAndStopsAtRedirectLimit(): Unit = {
    val unfollowed: Response[Either[ResponseException.DeserializationException, MovedNotice]] = basicRequest
      .get(uri"/v1/shipments/7")
      .followRedirects(false)
      .response(asJsonAlways[MovedNotice])
      .readTimeout(10.seconds)
      .send(apiBackend)

    assertThat(unfollowed.code).isEqualTo(StatusCode.MovedPermanently)
    assertThat(unfollowed.isRedirect).isTrue()
    assertThat(unfollowed.header("Location")).isEqualTo(Some("/v2/shipments/7"))
    assertThat(unfollowed.history.isEmpty).isTrue()
    assertThat(unfollowed.body).isEqualTo(Right(MovedNotice("Shipment API v1 is retired", "/v2/shipments/7")))
    assertThat(recorded.asScala.toList.map(_.path)).isEqualTo(List("/v1/shipments/7"))
    recorded.clear()

    val looping: Request[Either[ResponseException[String], Shipment]] = basicRequest
      .get(uri"/loop/0")
      .maxRedirects(3)
      .response(asJson[Shipment])
      .readTimeout(10.seconds)
    assertThatThrownBy(() => looping.send(apiBackend))
      .isInstanceOf(classOf[SttpClientException.TooManyRedirectsException])
      .satisfies(error => assertThat(error.asInstanceOf[SttpClientException.TooManyRedirectsException].redirects).isEqualTo(3))
    assertThat(recorded.asScala.toList.map(_.path)).isEqualTo(List("/loop/0", "/loop/1", "/loop/2", "/loop/3"))
  }

  private def route(exchange: HttpExchange, method: String, path: String, body: String): Unit = {
    val port: Int = server.getAddress.getPort
    (method, path) match {
      case ("GET", "/v1/shipments/7") =>
        redirect(exchange, 301, "/v2/shipments/7", MovedNotice("Shipment API v1 is retired", "/v2/shipments/7"))
      case ("GET", "/v2/shipments/7") =>
        redirect(exchange, 302, s"http://127.0.0.1:$port/v3/shipments/7", MovedNotice("Moved to v3", "/v3/shipments/7"))
      case ("GET", "/v3/shipments/7") =>
        respond(exchange, 200, Shipment(7L, "DHL", Address("Lyon", "FR"), "IN_TRANSIT").asJson.noSpaces)
      case ("POST", "/v1/labels") =>
        redirect(exchange, 307, "/v2/labels", MovedNotice("Labels moved", "/v2/labels"))
      case ("POST", "/v2/labels") =>
        decode[LabelRequest](body) match {
          case Right(request) =>
            val label: Label = Label(request.shipmentId, request.format, request.copies, s"/labels/${request.shipmentId}.pdf")
            respond(exchange, 201, label.asJson.noSpaces)
          case Left(error) =>
            respond(exchange, 400, error.getMessage.asJson.noSpaces)
        }
      case ("POST", "/v2/pickups") =>
        decode[PickupRequest](body).foreach(request => pickups.add(request))
        redirect(exchange, 303, "/v2/pickups/status", MovedNotice("Pickup accepted", "/v2/pickups/status"))
      case ("GET", "/v2/pickups/status") =>
        val all: List[PickupRequest] = pickups.asScala.toList
        respond(exchange, 200, PickupStatus(all.size, all.lastOption.map(_.window).getOrElse("")).asJson.noSpaces)
      case ("GET", loop) if loop.startsWith("/loop/") =>
        val next: Int = loop.stripPrefix("/loop/").toInt + 1
        redirect(exchange, 302, s"/loop/$next", MovedNotice("Still moving", s"/loop/$next"))
      case _ =>
        respond(exchange, 404, MovedNotice("Unknown route", path).asJson.noSpaces)
    }
  }

  private def redirect(exchange: HttpExchange, status: Int, location: String, notice: MovedNotice): Unit = {
    exchange.getResponseHeaders.set("Location", location)
    respond(exchange, status, notice.asJson.noSpaces)
  }

  private def respond(exchange: HttpExchange, status: Int, body: String): Unit = {
    val bytes: Array[Byte] = body.getBytes(StandardCharsets.UTF_8)
    exchange.getResponseHeaders.set("Content-Type", "application/json")
    exchange.sendResponseHeaders(status, if (bytes.isEmpty) -1 else bytes.length.toLong)
    if (bytes.nonEmpty) {
      exchange.getResponseBody.write(bytes)
    }
  }
}
