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

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse}
import org.junit.jupiter.api.{Test, Timeout}
import sttp.model.StatusCode
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.exception.ExceptionHandler
import sttp.tapir.server.jdkhttp.{JdkHttpServer, JdkHttpServerOptions}
import sttp.tapir.server.model.ValuedEndpointOutput

/** Exercises the exception interceptor for checkout inventory reservations. */
class CheckoutExceptionHandlingTest {
  private class InventoryExhausted(product: String)
      extends RuntimeException(s"Internal warehouse stock exhausted for SKU $product")

  private class Inventory {
    private var stock: Map[String, Int] = Map("coffee" -> 1, "tea" -> 2)

    def reserve(product: String): String = synchronized {
      val remaining: Int = stock.getOrElse(product, 0)
      if (remaining == 0) throw new InventoryExhausted(product)
      stock = stock.updated(product, remaining - 1)
      s"Reserved one $product"
    }
  }

  @Test
  @Timeout(60)
  def translatesInventoryExceptionsWithoutLeakingDiagnostics(): Unit = {
    val inventory: Inventory = new Inventory
    val reservation: ServerEndpoint[Any, Identity] = endpoint.post
      .in("checkout" / "reservations" / path[String]("product"))
      .out(statusCode(StatusCode.Created).and(stringBody))
      .serverLogicSuccess[Identity](product => inventory.reserve(product))

    val handler: ExceptionHandler[Identity] = ExceptionHandler.pure[Identity] { context =>
      context.e match {
        case _: InventoryExhausted =>
          val requestId: String = context.request.header("X-Request-Id").getOrElse("unknown")
          Some(ValuedEndpointOutput(
            statusCode(StatusCode.Conflict).and(header[String]("X-Request-Id")).and(stringBody),
            (requestId, "This product is no longer available")
          ))
        case _ => None
      }
    }
    val options: JdkHttpServerOptions = JdkHttpServerOptions.customiseInterceptors
      .exceptionHandler(handler).options
    val server = JdkHttpServer(options = options).host("127.0.0.1").port(0).addEndpoint(reservation).start()
    try {
      val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
      try {
        val baseUri: String = s"http://127.0.0.1:${server.getAddress.getPort}/checkout/reservations/"

        def reserve(product: String, requestId: String): HttpResponse[String] = {
          val request: HttpRequest = HttpRequest.newBuilder(URI.create(baseUri + product))
            .timeout(Duration.ofSeconds(10)).header("X-Request-Id", requestId)
            .POST(HttpRequest.BodyPublishers.noBody()).build()
          client.send(request, HttpResponse.BodyHandlers.ofString())
        }

        val first: HttpResponse[String] = reserve("coffee", "checkout-1")
        assertEquals(201, first.statusCode())
        assertEquals("Reserved one coffee", first.body())

        val soldOut: HttpResponse[String] = reserve("coffee", "checkout-2")
        assertEquals(409, soldOut.statusCode())
        assertEquals("This product is no longer available", soldOut.body())
        assertEquals("checkout-2", soldOut.headers().firstValue("X-Request-Id").orElse(""))
        assertFalse(soldOut.body().contains("warehouse"))
        assertFalse(soldOut.body().contains("InventoryExhausted"))

        // A handled exception must not prevent later checkouts from succeeding.
        val next: HttpResponse[String] = reserve("tea", "checkout-3")
        assertEquals(201, next.statusCode())
        assertEquals("Reserved one tea", next.body())
      } finally client.close()
    } finally server.stop(0)
  }
}
