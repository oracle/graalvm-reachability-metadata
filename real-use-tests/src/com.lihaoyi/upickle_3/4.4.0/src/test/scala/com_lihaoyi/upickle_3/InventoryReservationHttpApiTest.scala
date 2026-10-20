/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_lihaoyi.upickle_3

import java.net.InetSocketAddress

import scala.collection.mutable

import cask.endpoints.JsonData
import io.undertow.Undertow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import upickle.core.TraceVisitor.TraceException
import upickle.default.*
import upickle.implicits.key

/**
 * JSON request and response bodies of an HTTP REST service encoded and decoded by upickle: cask
 * postJson/getJson endpoints bind derived readers and render derived writers, and a requests
 * client writes request bodies and reads typed responses, as a warehouse exposes reservations.
 */
class InventoryReservationHttpApiTest {

  import InventoryReservationHttpApiTest.*

  @Test
  def reservationIsCreatedAndLookedUpThroughJsonEndpoints(): Unit = withService { baseUrl =>
    val request: ReserveRequest = ReserveRequest("SKU-1", 2, Customer("Ada Lovelace", Some("ada@example.org")))

    val created: requests.Response = postJson(s"$baseUrl/reservations", write(request))
    assertEquals(201, created.statusCode)
    assertTrue(created.text().contains("\"customer\":{\"name\":\"Ada Lovelace\""), created.text())
    val reservation: Reservation = read[Reservation](created.text())
    assertEquals(Reservation(1, "SKU-1", 2, Customer("Ada Lovelace", Some("ada@example.org")), Nil), reservation)

    val fetched: requests.Response = requests.get(s"$baseUrl/reservations/1", check = false)
    assertEquals(200, fetched.statusCode)
    assertEquals(reservation, read[Reservation](fetched.text()))

    val stock: Map[String, Int] = read[Map[String, Int]](requests.get(s"$baseUrl/stock").text())
    assertEquals(Map("SKU-1" -> 3, "SKU-2" -> 1), stock)
  }

  @Test
  def missingReservationIsReportedAsATypedJsonError(): Unit = withService { baseUrl =>
    val response: requests.Response = requests.get(s"$baseUrl/reservations/404", check = false)

    assertEquals(404, response.statusCode)
    assertEquals(ApiError("not_found", "no reservation 404"), read[ApiError](response.text()))
    assertEquals(ujson.Obj("error_code" -> "not_found", "message" -> "no reservation 404"), ujson.read(response.text()))
  }

  @Test
  def batchOrderYieldsATaggedOutcomePerLine(): Unit = withService { baseUrl =>
    val batch: Seq[ReserveRequest] = Seq(
      ReserveRequest("SKU-2", 1, Customer("Alan Turing", None), notes = Seq("gift wrap")),
      ReserveRequest("SKU-2", 1, Customer("Grace Hopper", None)),
      ReserveRequest("SKU-9", 1, Customer("Grace Hopper", None))
    )

    val response: requests.Response = postJson(s"$baseUrl/reservations/batch", write(batch, indent = 2))
    assertEquals(200, response.statusCode)
    assertEquals("application/json", response.headers("content-type").head)

    val outcomes: Seq[Outcome] = read[Seq[Outcome]](response.text())
    assertEquals(
      Seq(
        Outcome.Confirmed(Reservation(1, "SKU-2", 1, Customer("Alan Turing", None), Seq("gift wrap"))),
        Outcome.Rejected("SKU-2", "only 0 left"),
        Outcome.Rejected("SKU-9", "unknown sku")
      ),
      outcomes
    )
    val tags: Seq[String] = ujson.read(response.text()).arr.map(_("$type").str).toSeq
    assertEquals(Seq("confirmed", "rejected", "rejected"), tags)
  }

  @Test
  def malformedRequestBodiesAreRejectedBeforeTheHandlerRuns(): Unit = withService { baseUrl =>
    val wrongType: String = write(ujson.Obj("sku" -> "SKU-1", "quantity" -> "two", "customer" -> ujson.Obj("name" -> "Bob")))
    val wrongTypeResponse: requests.Response = postJson(s"$baseUrl/reservations", wrongType)
    assertEquals(400, wrongTypeResponse.statusCode)
    assertTrue(wrongTypeResponse.text().contains("quantity"), wrongTypeResponse.text())

    val badBatch: requests.Response = postJson(s"$baseUrl/reservations/batch", "[{\"sku\": \"SKU-1\"")
    assertEquals(400, badBatch.statusCode)
    val badBatchError: ApiError = read[ApiError](badBatch.text())
    assertEquals("invalid_body", badBatchError.code)
    assertTrue(badBatchError.message.endsWith(" at $[0]"), badBatchError.message)

    val stock: Map[String, Int] = read[Map[String, Int]](requests.get(s"$baseUrl/stock").text())
    assertEquals(Map("SKU-1" -> 5, "SKU-2" -> 1), stock)
  }
}

object InventoryReservationHttpApiTest {

  case class Customer(name: String, email: Option[String] = None) derives ReadWriter

  case class ReserveRequest(sku: String, quantity: Int, customer: Customer, notes: Seq[String] = Nil)
      derives ReadWriter

  case class Reservation(id: Int, sku: String, quantity: Int, customer: Customer, notes: Seq[String])
      derives ReadWriter

  case class ApiError(@key("error_code") code: String, message: String) derives ReadWriter

  sealed trait Outcome derives ReadWriter

  object Outcome {
    @key("confirmed")
    case class Confirmed(reservation: Reservation) extends Outcome

    @key("rejected")
    case class Rejected(sku: String, reason: String) extends Outcome
  }

  /** The warehouse API: an in-memory stock ledger behind cask JSON endpoints. */
  class ReservationService extends cask.MainRoutes {

    private val stock: mutable.LinkedHashMap[String, Int] = mutable.LinkedHashMap("SKU-1" -> 5, "SKU-2" -> 1)
    private val reservations: mutable.LinkedHashMap[Int, Reservation] = mutable.LinkedHashMap.empty

    @cask.postJson("/reservations")
    def reserve(sku: String, quantity: Int, customer: Customer, notes: Seq[String] = Nil): cask.Response[JsonData] =
      place(ReserveRequest(sku, quantity, customer, notes)) match {
        case Outcome.Confirmed(reservation) => cask.Response[JsonData](reservation, statusCode = 201)
        case Outcome.Rejected(_, reason) => cask.Response[JsonData](ApiError("rejected", reason), statusCode = 409)
      }

    @cask.getJson("/reservations/:id")
    def lookup(id: Int): cask.Response[JsonData] = synchronized {
      reservations.get(id) match {
        case Some(reservation) => cask.Response[JsonData](reservation)
        case None => cask.Response[JsonData](ApiError("not_found", s"no reservation $id"), statusCode = 404)
      }
    }

    @cask.getJson("/stock")
    def currentStock(): cask.Response[JsonData] = synchronized {
      cask.Response[JsonData](stock.toMap)
    }

    @cask.post("/reservations/batch")
    def reserveBatch(request: cask.Request): cask.Response[String] = {
      val jsonHeaders: Seq[(String, String)] = Seq("Content-Type" -> "application/json")
      try {
        val batch: Seq[ReserveRequest] = read[Seq[ReserveRequest]](request.text())
        cask.Response(write(batch.map(place)), headers = jsonHeaders)
      } catch {
        case e: TraceException =>
          val error: ApiError = ApiError("invalid_body", s"${e.getCause.getMessage} at ${e.jsonPath}")
          cask.Response(write(error), statusCode = 400, headers = jsonHeaders)
      }
    }

    private def place(request: ReserveRequest): Outcome = synchronized {
      stock.get(request.sku) match {
        case None => Outcome.Rejected(request.sku, "unknown sku")
        case Some(available) if available < request.quantity => Outcome.Rejected(request.sku, s"only $available left")
        case Some(available) =>
          stock(request.sku) = available - request.quantity
          val reservation: Reservation =
            Reservation(reservations.size + 1, request.sku, request.quantity, request.customer, request.notes)
          reservations(reservation.id) = reservation
          Outcome.Confirmed(reservation)
      }
    }

    initialize()
  }

  /** Runs `body` against a fresh service listening on an ephemeral local port. */
  def withService(body: String => Unit): Unit = {
    val service: ReservationService = new ReservationService
    val server: Undertow = Undertow.builder.addHttpListener(0, "localhost").setHandler(service.defaultHandler).build
    server.start()
    try {
      val port: Int = server.getListenerInfo.get(0).getAddress.asInstanceOf[InetSocketAddress].getPort
      body(s"http://localhost:$port")
    } finally {
      server.stop()
    }
  }

  def postJson(url: String, body: String): requests.Response =
    requests.post(url, data = body, headers = Map("Content-Type" -> "application/json"), check = false)
}
