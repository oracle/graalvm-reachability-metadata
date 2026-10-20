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
import java.util.concurrent.atomic.AtomicLong

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import sttp.model.{Header, StatusCode}
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.{RequestInterceptor, RequestResult}
import sttp.tapir.server.jdkhttp.{JdkHttpServer, JdkHttpServerOptions}
import sttp.tapir.server.model.ServerResponse

/** Exercises request-result transformation for correlation headers on a support ticket API. */
class TicketCorrelationHeadersTest {
  private class CorrelationHeaders extends RequestInterceptor.RequestResultTransform[Identity] {
    private val sequence: AtomicLong = new AtomicLong()

    override def apply[B](request: ServerRequest, result: RequestResult[B]): RequestResult[B] = {
      val correlationId: String = request.header("X-Correlation-Id")
        .getOrElse(s"support-${sequence.incrementAndGet()}")
      val headers: List[Header] = List(Header("X-Correlation-Id", correlationId))
      result match {
        case RequestResult.Response(response) =>
          RequestResult.Response(response.addHeaders(headers))
        // Turn an unhandled route into an application response rather than the backend's bare 404.
        case _: RequestResult.Failure =>
          RequestResult.Response(ServerResponse.notFound[B].addHeaders(headers))
      }
    }
  }

  @Test
  @Timeout(60)
  def returnsCorrelationIdsEvenWhenRequestsFailBeforeReachingTicketLogic(): Unit = {
    val tickets: Map[Int, String] = Map(42 -> "Replacement shipped")
    val ticket: ServerEndpoint[Any, Identity] = endpoint.get
      .in("support" / "tickets" / path[Int]("ticketId"))
      .errorOut(statusCode(StatusCode.NotFound).and(stringBody))
      .out(stringBody)
      .serverLogic[Identity](id => tickets.get(id).toRight("Ticket not found"))
    val options: JdkHttpServerOptions = JdkHttpServerOptions.customiseInterceptors
      // Run outside the built-in handlers so decoding and routing errors also carry the header.
      .prependInterceptor(RequestInterceptor.transformResult[Identity](new CorrelationHeaders))
      .options
    val server = JdkHttpServer(options = options).host("127.0.0.1").port(0).addEndpoint(ticket).start()
    try {
      val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
      try {
        val baseUri: String = s"http://127.0.0.1:${server.getAddress.getPort}"

        def get(path: String, correlationId: Option[String]): HttpResponse[String] = {
          val builder: HttpRequest.Builder = HttpRequest.newBuilder(URI.create(baseUri + path))
            .timeout(Duration.ofSeconds(10)).GET()
          correlationId.foreach(value => builder.header("X-Correlation-Id", value))
          client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        }

        def assertCorrelation(response: HttpResponse[String], expected: String): Unit = {
          assertEquals(1, response.headers().allValues("X-Correlation-Id").size())
          assertEquals(expected, response.headers().firstValue("X-Correlation-Id").orElse(""))
        }

        val found: HttpResponse[String] = get("/support/tickets/42", Some("customer-request-101"))
        assertEquals(200, found.statusCode())
        assertEquals("Replacement shipped", found.body())
        assertCorrelation(found, "customer-request-101")

        val missing: HttpResponse[String] = get("/support/tickets/99", Some("customer-request-102"))
        assertEquals(404, missing.statusCode())
        assertEquals("Ticket not found", missing.body())
        assertCorrelation(missing, "customer-request-102")

        val malformed: HttpResponse[String] = get("/support/tickets/not-a-number", Some("customer-request-103"))
        assertEquals(400, malformed.statusCode())
        assertTrue(malformed.body().contains("ticketId"), malformed.body())
        assertCorrelation(malformed, "customer-request-103")

        val unknownRoute: HttpResponse[String] = get("/support/unknown", Some("customer-request-104"))
        assertEquals(404, unknownRoute.statusCode())
        assertCorrelation(unknownRoute, "customer-request-104")

        // Callers without an ID receive a fresh one, without inheriting the previous request's value.
        val generated: HttpResponse[String] = get("/support/tickets/42", None)
        assertEquals(200, generated.statusCode())
        assertEquals("Replacement shipped", generated.body())
        assertCorrelation(generated, "support-1")
        val nextGenerated: HttpResponse[String] = get("/support/tickets/99", None)
        assertEquals(404, nextGenerated.statusCode())
        assertEquals("Ticket not found", nextGenerated.body())
        assertCorrelation(nextGenerated, "support-2")
      } finally client.close()
    } finally server.stop(0)
  }
}
