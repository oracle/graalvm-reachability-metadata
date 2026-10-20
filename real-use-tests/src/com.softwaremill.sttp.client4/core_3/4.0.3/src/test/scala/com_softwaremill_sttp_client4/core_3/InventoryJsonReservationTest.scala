/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_client4.core_3

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import io.circe.generic.semiauto.*
import io.circe.parser.parse
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.ConcurrentLinkedQueue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*
import sttp.client4.*
import sttp.client4.circe.*
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.model.StatusCode

/** Exercises typed JSON request bodies and status-dependent JSON response decoding for inventory reservations. */
class InventoryJsonReservationTest {
  private case class ReservationRequest(quantity: Int, deliveryNote: String)
  private case class ReservationReceipt(reservationId: String, item: String, quantity: Int)
  private case class ReservationProblem(code: String, available: Int)
  private case class SubmittedReservation(method: String, path: String, contentType: String, body: String)

  private given Encoder[ReservationRequest] = deriveEncoder[ReservationRequest]
  private given Decoder[ReservationReceipt] = deriveDecoder[ReservationReceipt]
  private given Decoder[ReservationProblem] = deriveDecoder[ReservationProblem]

  @Test
  @Timeout(60)
  def reservesStockAndDecodesAnInsufficientStockRejection(): Unit = {
    val submitted: ConcurrentLinkedQueue[SubmittedReservation] = new ConcurrentLinkedQueue[SubmittedReservation]()
    val server: HttpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    try {
      server.createContext("/inventory/tea/reservations", (exchange: HttpExchange) => {
        submitted.add(capture(exchange))
        respond(exchange, 201, """{"reservationId":"R17","item":"tea","quantity":2}""")
      })
      server.createContext("/inventory/coffee/reservations", (exchange: HttpExchange) => {
        submitted.add(capture(exchange))
        respond(exchange, 409, """{"code":"INSUFFICIENT_STOCK","available":1}""")
      })
      server.start()

      val backend: WebSocketSyncBackend = HttpClientSyncBackend(BackendOptions.connectionTimeout(10.seconds))
      try {
        val port: Int = server.getAddress.getPort
        val note: String = "Leave at the caf\u00e9 reception"
        val reserved: Response[Either[ReservationProblem, ReservationReceipt]] = basicRequest
          .body(asJson(ReservationRequest(2, note)))
          .post(uri"http://127.0.0.1:$port/inventory/tea/reservations")
          .readTimeout(20.seconds)
          .response(asJsonEitherOrFail[ReservationProblem, ReservationReceipt])
          .send(backend)

        assertEquals(StatusCode.Created, reserved.code)
        assertEquals(Right(ReservationReceipt("R17", "tea", 2)), reserved.body)

        val rejected: Response[Either[ReservationProblem, ReservationReceipt]] = basicRequest
          .body(asJson(ReservationRequest(5, "Collect in store")))
          .post(uri"http://127.0.0.1:$port/inventory/coffee/reservations")
          .readTimeout(20.seconds)
          .response(asJsonEitherOrFail[ReservationProblem, ReservationReceipt])
          .send(backend)

        assertEquals(StatusCode.Conflict, rejected.code)
        assertEquals(Left(ReservationProblem("INSUFFICIENT_STOCK", 1)), rejected.body)

        val requests: List[SubmittedReservation] = submitted.iterator().asScala.toList
        assertEquals(2, requests.size)
        assertEquals(List("POST", "POST"), requests.map(_.method))
        assertEquals(
          List("/inventory/tea/reservations", "/inventory/coffee/reservations"),
          requests.map(_.path)
        )
        assertEquals(List("application/json", "application/json"), requests.map(_.contentType.takeWhile(_ != ';')))
        assertEquals(
          List(
            Right(Json.obj("quantity" -> Json.fromInt(2), "deliveryNote" -> Json.fromString(note))),
            Right(Json.obj("quantity" -> Json.fromInt(5), "deliveryNote" -> Json.fromString("Collect in store")))
          ),
          requests.map(request => parse(request.body))
        )
      } finally backend.close()
    } finally server.stop(0)
  }

  private def capture(exchange: HttpExchange): SubmittedReservation = {
    SubmittedReservation(
      exchange.getRequestMethod,
      exchange.getRequestURI.getPath,
      exchange.getRequestHeaders.getFirst("Content-Type"),
      new String(exchange.getRequestBody.readAllBytes(), UTF_8)
    )
  }

  private def respond(exchange: HttpExchange, status: Int, json: String): Unit = {
    try {
      val bytes: Array[Byte] = json.getBytes(UTF_8)
      exchange.getResponseHeaders.set("Content-Type", "application/json; charset=UTF-8")
      exchange.sendResponseHeaders(status, bytes.length.toLong)
      exchange.getResponseBody.write(bytes)
    } finally exchange.close()
  }
}
