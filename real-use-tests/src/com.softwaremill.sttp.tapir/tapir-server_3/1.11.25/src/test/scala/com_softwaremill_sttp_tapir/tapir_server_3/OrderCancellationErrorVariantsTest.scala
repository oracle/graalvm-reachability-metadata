/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_server_3

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration

import io.circe.generic.auto.*
import io.circe.parser.parse
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import sttp.model.StatusCode
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.jdkhttp.JdkHttpServer

/** Exercises polymorphic error-output encoding for an order cancellation API. */
class OrderCancellationErrorVariantsTest {
  private sealed trait CancellationError
  private case class UnknownOrder(orderId: String) extends CancellationError
  private case class AlreadyDispatched(orderId: String, trackingNumber: String) extends CancellationError
  private case class AlreadyCancelled(orderId: String, refundReference: String) extends CancellationError
  private case class CancellationReceipt(orderId: String, refundReference: String)

  private class Orders {
    private var cancelled: Map[String, String] = Map.empty

    def cancel(id: String): Either[CancellationError, CancellationReceipt] = synchronized {
      if (id == "order-shipped") Left(AlreadyDispatched(id, "TRACK-42"))
      else if (id != "order-pending") Left(UnknownOrder(id))
      else cancelled.get(id) match {
        case Some(reference) => Left(AlreadyCancelled(id, reference))
        case None =>
          val reference: String = "REFUND-100"
          cancelled = cancelled.updated(id, reference)
          Right(CancellationReceipt(id, reference))
      }
    }
  }

  @Test
  @Timeout(60)
  def returnsTheStatusAndPayloadOfEachBusinessErrorVariant(): Unit = {
    val orders: Orders = new Orders()
    val cancel: ServerEndpoint[Any, Identity] = endpoint.post
      .in("orders" / path[String]("orderId") / "cancellation")
      .errorOut(oneOf[CancellationError](
        oneOfVariant(StatusCode.NotFound, jsonBody[UnknownOrder]),
        oneOfVariant(StatusCode.Conflict, jsonBody[AlreadyDispatched]),
        oneOfVariant(StatusCode.Conflict, jsonBody[AlreadyCancelled])
      ))
      .out(statusCode(StatusCode.Created).and(jsonBody[CancellationReceipt]))
      .serverLogic[Identity](orders.cancel)

    val server = JdkHttpServer().host("127.0.0.1").port(0).addEndpoint(cancel).start()
    try {
      val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
      try {
        val baseUri: String = s"http://127.0.0.1:${server.getAddress.getPort}/orders/"

        def cancelOrder(id: String): HttpResponse[String] = {
          val request: HttpRequest = HttpRequest.newBuilder(URI.create(baseUri + id + "/cancellation"))
            .timeout(Duration.ofSeconds(10)).POST(HttpRequest.BodyPublishers.noBody()).build()
          client.send(request, HttpResponse.BodyHandlers.ofString())
        }

        def assertJson(response: HttpResponse[String], status: Int, expected: String): Unit = {
          assertEquals(status, response.statusCode())
          assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"))
          // Compare the wire document, not field order or the server's case-class representation.
          assertEquals(parse(expected).toOption.get, parse(response.body()).toOption.get)
        }

        assertJson(cancelOrder("order-missing"), 404,
          """{"orderId":"order-missing"}""")
        assertJson(cancelOrder("order-shipped"), 409,
          """{"orderId":"order-shipped","trackingNumber":"TRACK-42"}""")

        // Domain failures leave the pending order available for an ordinary cancellation.
        assertJson(cancelOrder("order-pending"), 201,
          """{"orderId":"order-pending","refundReference":"REFUND-100"}""")

        // Both errors use 409: the runtime error type, not the status, selects the JSON codec.
        assertJson(cancelOrder("order-pending"), 409,
          """{"orderId":"order-pending","refundReference":"REFUND-100"}""")
        assertJson(cancelOrder("order-shipped"), 409,
          """{"orderId":"order-shipped","trackingNumber":"TRACK-42"}""")
        assertJson(cancelOrder("order-pending"), 409,
          """{"orderId":"order-pending","refundReference":"REFUND-100"}""")
      } finally client.close()
    } finally server.stop(0)
  }
}
