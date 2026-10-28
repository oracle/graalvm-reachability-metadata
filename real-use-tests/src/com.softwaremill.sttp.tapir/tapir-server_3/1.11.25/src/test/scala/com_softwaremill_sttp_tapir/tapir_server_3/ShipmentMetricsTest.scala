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

import io.prometheus.metrics.model.registry.PrometheusRegistry
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import sttp.model.StatusCode
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.jdkhttp.{JdkHttpServer, JdkHttpServerOptions}
import sttp.tapir.server.metrics.prometheus.PrometheusMetrics

/** Exercises the server metrics interceptor for a monitored shipment tracking API. */
class ShipmentMetricsTest {
  @Test
  @Timeout(60)
  def exportsRequestCountsWithoutHighCardinalityShipmentIds(): Unit = {
    val metrics: PrometheusMetrics[Identity] = PrometheusMetrics[Identity](
      namespace = "shipping", registry = new PrometheusRegistry()
    ).addRequestsActive().addRequestsTotal()
    val shipments: Map[String, String] = Map("s-100" -> "Dispatched", "s-200" -> "Delivered")
    val tracking: ServerEndpoint[Any, Identity] = endpoint.get
      .in("shipments" / path[String]("shipmentId"))
      .errorOut(statusCode(StatusCode.NotFound).and(stringBody))
      .out(stringBody)
      .serverLogic[Identity] { id => shipments.get(id).toRight("Shipment not found") }
    val options: JdkHttpServerOptions = JdkHttpServerOptions.customiseInterceptors
      .metricsInterceptor(metrics.metricsInterceptor()).options
    val server = JdkHttpServer(options = options).host("127.0.0.1").port(0)
      .addEndpoint(tracking).addEndpoint(metrics.metricsEndpoint).start()
    try {
      val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
      try {
        val baseUri: String = s"http://127.0.0.1:${server.getAddress.getPort}"

        def get(path: String): HttpResponse[String] = {
          val request: HttpRequest = HttpRequest.newBuilder(URI.create(baseUri + path))
            .timeout(Duration.ofSeconds(10)).GET().build()
          client.send(request, HttpResponse.BodyHandlers.ofString())
        }

        def scrape(): String = {
          val response: HttpResponse[String] = get("/metrics")
          assertEquals(200, response.statusCode())
          assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/plain"))
          response.body()
        }

        // Compare labels as a set: exposition label ordering is not part of the API.
        def sample(body: String, name: String, labels: Set[String]): Option[Double] = {
          body.linesIterator.find { line =>
            line.startsWith(name + "{") &&
              line.substring(line.indexOf('{') + 1, line.lastIndexOf('}')).split(",").toSet == labels
          }.map(line => line.substring(line.lastIndexOf(' ') + 1).toDouble)
        }

        val dispatched: HttpResponse[String] = get("/shipments/s-100")
        assertEquals(200, dispatched.statusCode())
        assertEquals("Dispatched", dispatched.body())
        val delivered: HttpResponse[String] = get("/shipments/s-200")
        assertEquals(200, delivered.statusCode())
        assertEquals("Delivered", delivered.body())
        val missing: HttpResponse[String] = get("/shipments/unknown")
        assertEquals(404, missing.statusCode())
        assertEquals("Shipment not found", missing.body())

        val requestLabels: Set[String] = Set("path=\"/shipments/{shipmentId}\"", "method=\"GET\"")
        val successLabels: Set[String] = requestLabels + "status=\"2xx\""
        val failureLabels: Set[String] = requestLabels + "status=\"4xx\""

        def completed(body: String): Boolean = {
          sample(body, "shipping_request_total", successLabels).contains(2.0) &&
            sample(body, "shipping_request_total", failureLabels).contains(1.0) &&
            sample(body, "shipping_request_active", requestLabels).contains(0.0)
        }

        // A client can receive a body before the server's completion callback records it.
        val deadline: Long = System.nanoTime() + Duration.ofSeconds(10).toNanos
        var exported: String = scrape()
        while (!completed(exported) && System.nanoTime() < deadline) exported = scrape()
        assertEquals(Some(2.0), sample(exported, "shipping_request_total", successLabels), exported)
        assertEquals(Some(1.0), sample(exported, "shipping_request_total", failureLabels), exported)
        assertEquals(Some(0.0), sample(exported, "shipping_request_active", requestLabels), exported)
        assertFalse(exported.contains("s-100"))
        assertFalse(exported.contains("s-200"))
        assertFalse(exported.contains("unknown"))

        // Monitoring must not count its own scrapes as application traffic.
        val afterScrape: String = scrape()
        assertTrue(completed(afterScrape), afterScrape)
        assertFalse(afterScrape.contains("path=\"/metrics\""))
        assertEquals(2, afterScrape.linesIterator.count(_.startsWith("shipping_request_total{")))
      } finally client.close()
    } finally server.stop(0)
  }
}
