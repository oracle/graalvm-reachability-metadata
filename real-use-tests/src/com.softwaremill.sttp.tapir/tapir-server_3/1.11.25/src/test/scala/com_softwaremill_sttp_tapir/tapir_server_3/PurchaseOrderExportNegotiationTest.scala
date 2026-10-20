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
import java.util.concurrent.atomic.AtomicInteger

import io.circe.parser.decode
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.content.NotAcceptableInterceptor
import sttp.tapir.server.jdkhttp.{JdkHttpServer, JdkHttpServerOptions}

/** Exercises response content negotiation and the not-acceptable interceptor for purchase order exports. */
class PurchaseOrderExportNegotiationTest {
  @Test
  @Timeout(60)
  def exportsTheRequestedRepresentationWithoutGeneratingUnsupportedFormats(): Unit = {
    val orders: List[String] = List("PO-100", "PO-200")
    val exports: AtomicInteger = new AtomicInteger()
    val exportOrders: ServerEndpoint[Any, Identity] = endpoint.get
      .in("purchasing" / "orders" / "export")
      .out(oneOfBody(
        jsonBody[List[String]],
        stringBody.map[List[String]](_.linesIterator.toList)(_.mkString("\n"))
      ))
      .serverLogicSuccess[Identity] { _ =>
        exports.incrementAndGet()
        orders
      }
    val options: JdkHttpServerOptions = JdkHttpServerOptions.customiseInterceptors
      .notAcceptableInterceptor(new NotAcceptableInterceptor[Identity]()).options
    val server = JdkHttpServer(options = options).host("127.0.0.1").port(0)
      .addEndpoint(exportOrders).start()
    try {
      val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
      try {
        val uri: URI = URI.create(s"http://127.0.0.1:${server.getAddress.getPort}/purchasing/orders/export")

        def download(accept: Option[String]): HttpResponse[String] = {
          val builder: HttpRequest.Builder = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(10)).GET()
          accept.foreach(value => builder.header("Accept", value))
          client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        }

        val unsupported: HttpResponse[String] = download(Some("application/xml"))
        assertEquals(406, unsupported.statusCode())
        assertEquals("", unsupported.body())
        // Reject before the application performs the potentially expensive export query.
        assertEquals(0, exports.get())

        val json: HttpResponse[String] = download(Some("application/json"))
        assertEquals(200, json.statusCode())
        assertTrue(json.headers().firstValue("Content-Type").orElse("").startsWith("application/json"))
        assertEquals(Right(orders), decode[List[String]](json.body()))

        val plain: HttpResponse[String] = download(Some("text/plain"))
        assertEquals(200, plain.statusCode())
        assertTrue(plain.headers().firstValue("Content-Type").orElse("").startsWith("text/plain"))
        assertEquals("PO-100\nPO-200", plain.body())

        val preferred: HttpResponse[String] = download(Some("application/json;q=0.3, text/plain;q=0.9"))
        assertEquals(200, preferred.statusCode())
        assertTrue(preferred.headers().firstValue("Content-Type").orElse("").startsWith("text/plain"))
        assertEquals(plain.body(), preferred.body())

        // Consumers without a format preference receive the first declared representation.
        val defaultFormat: HttpResponse[String] = download(None)
        assertEquals(200, defaultFormat.statusCode())
        assertTrue(defaultFormat.headers().firstValue("Content-Type").orElse("").startsWith("application/json"))
        assertEquals(Right(orders), decode[List[String]](defaultFormat.body()))
        assertEquals(4, exports.get())
      } finally client.close()
    } finally server.stop(0)
  }
}
