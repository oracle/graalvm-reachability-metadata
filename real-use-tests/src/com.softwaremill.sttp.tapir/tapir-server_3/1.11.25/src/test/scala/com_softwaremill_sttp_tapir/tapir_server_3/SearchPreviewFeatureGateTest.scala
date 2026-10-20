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
import java.util.concurrent.atomic.AtomicBoolean

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse}
import org.junit.jupiter.api.{Test, Timeout}
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.RequestInterceptor
import sttp.tapir.server.jdkhttp.{JdkHttpServer, JdkHttpServerOptions}

/** Exercises endpoint-filtering request interceptors for a dynamically enabled preview search API. */
class SearchPreviewFeatureGateTest {
  private class PreviewGate extends RequestInterceptor.ServerEndpointFilter[Identity] {
    private val enabled: AtomicBoolean = new AtomicBoolean(false)

    def configure(value: Boolean): String = {
      enabled.set(value)
      if (value) "Preview search enabled" else "Preview search disabled"
    }

    override def apply[R](endpoints: List[ServerEndpoint[R, Identity]]): List[ServerEndpoint[R, Identity]] = {
      val allowPreview: Boolean = enabled.get()
      endpoints.filter(endpoint => allowPreview || !endpoint.endpoint.info.tags.contains("preview"))
    }
  }

  @Test
  @Timeout(60)
  def changesPreviewAvailabilityWithoutRestartingOrDisablingOrdinarySearch(): Unit = {
    val gate: PreviewGate = new PreviewGate()
    val catalog: List[String] = List("coffee", "coffee beans", "tea")
    val search: ServerEndpoint[Any, Identity] = endpoint.get
      .in("search")
      .in(query[String]("q"))
      .out(stringBody)
      .serverLogicSuccess[Identity](q => catalog.filter(_.contains(q)).mkString(","))
    val preview: ServerEndpoint[Any, Identity] = endpoint.get
      .tag("preview")
      .in("search" / "preview")
      .in(query[String]("q"))
      .in(query[Int]("limit").validate(Validator.min(1)))
      .out(stringBody)
      .serverLogicSuccess[Identity] { case (q, limit) =>
        catalog.filter(_.contains(q)).sortBy(_.length).take(limit).mkString(",")
      }
    val configure: ServerEndpoint[Any, Identity] = endpoint.put
      .in("operations" / "preview-search")
      .in(query[Boolean]("enabled"))
      .out(stringBody)
      .serverLogicSuccess[Identity](gate.configure)
    val options: JdkHttpServerOptions = JdkHttpServerOptions.customiseInterceptors
      .prependInterceptor(RequestInterceptor.filterServerEndpoints[Identity](gate)).options
    val server = JdkHttpServer(options = options).host("127.0.0.1").port(0)
      .addEndpoint(search).addEndpoint(preview).addEndpoint(configure).start()
    try {
      val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
      try {
        val baseUri: String = s"http://127.0.0.1:${server.getAddress.getPort}"

        def request(path: String, method: String = "GET"): HttpResponse[String] = {
          val message: HttpRequest = HttpRequest.newBuilder(URI.create(baseUri + path))
            .timeout(Duration.ofSeconds(10)).method(method, HttpRequest.BodyPublishers.noBody()).build()
          client.send(message, HttpResponse.BodyHandlers.ofString())
        }

        def assertOrdinarySearch(): Unit = {
          val response: HttpResponse[String] = request("/search?q=coffee")
          assertEquals(200, response.statusCode())
          assertEquals("coffee,coffee beans", response.body())
        }

        assertOrdinarySearch()
        val hidden: HttpResponse[String] = request("/search/preview?q=coffee&limit=1")
        assertEquals(404, hidden.statusCode())
        // An unpublished route must not reveal its input contract through decoding errors.
        val hiddenMalformed: HttpResponse[String] = request("/search/preview?q=coffee&limit=invalid")
        assertEquals(404, hiddenMalformed.statusCode())
        assertFalse(hiddenMalformed.body().contains("limit"))

        val enabled: HttpResponse[String] = request("/operations/preview-search?enabled=true", "PUT")
        assertEquals(200, enabled.statusCode())
        assertEquals("Preview search enabled", enabled.body())
        val available: HttpResponse[String] = request("/search/preview?q=coffee&limit=1")
        assertEquals(200, available.statusCode())
        assertEquals("coffee", available.body())
        val malformed: HttpResponse[String] = request("/search/preview?q=coffee&limit=invalid")
        assertEquals(400, malformed.statusCode())
        assertOrdinarySearch()

        val disabled: HttpResponse[String] = request("/operations/preview-search?enabled=false", "PUT")
        assertEquals(200, disabled.statusCode())
        assertEquals("Preview search disabled", disabled.body())
        assertEquals(404, request("/search/preview?q=coffee&limit=1").statusCode())
        assertOrdinarySearch()

        // The filter must not permanently remove endpoints when the feature is switched off.
        assertEquals(200, request("/operations/preview-search?enabled=true", "PUT").statusCode())
        val restored: HttpResponse[String] = request("/search/preview?q=coffee&limit=2")
        assertEquals(200, restored.statusCode())
        assertEquals("coffee,coffee beans", restored.body())
      } finally client.close()
    } finally server.stop(0)
  }
}
