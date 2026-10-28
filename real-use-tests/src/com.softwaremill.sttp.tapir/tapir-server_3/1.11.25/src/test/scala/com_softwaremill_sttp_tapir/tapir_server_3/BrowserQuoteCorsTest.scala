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

import scala.concurrent.duration.*

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import sttp.model.{Method, StatusCode}
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.cors.{CORSConfig, CORSInterceptor}
import sttp.tapir.server.jdkhttp.{JdkHttpServer, JdkHttpServerOptions}

/** Exercises the CORS interceptor for a browser-facing shipping quote API. */
class BrowserQuoteCorsTest {
  @Test
  @Timeout(60)
  def grantsCrossOriginAccessOnlyToTheStorefront(): Unit = {
    val storefront: String = "https://shop.example"
    val corsConfig: CORSConfig = CORSConfig.default
      .allowMatchingOrigins(_ == storefront)
      .allowCredentials
      .allowMethods(Method.POST)
      .allowHeaders("Content-Type", "X-Customer")
      .exposeHeaders("X-Quote-Id")
      .maxAge(10.minutes)
      .preflightResponseStatusCode(StatusCode.NoContent)
    val options: JdkHttpServerOptions = JdkHttpServerOptions.customiseInterceptors
      .corsInterceptor(CORSInterceptor.customOrThrow[Identity](corsConfig)).options
    val quote: ServerEndpoint[Any, Identity] = endpoint.post
      .in("shipping" / "quotes")
      .in(header[String]("X-Customer"))
      .in(stringBody)
      .out(stringBody.and(header[String]("X-Quote-Id")))
      .serverLogicSuccess[Identity] { case (customer, destination) =>
        (s"Shipping to $destination for $customer: USD 7.50", "quote-42")
      }

    val server = JdkHttpServer(options = options).host("127.0.0.1").port(0).addEndpoint(quote).start()
    try {
      val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
      try {
        val uri: URI = URI.create(s"http://127.0.0.1:${server.getAddress.getPort}/shipping/quotes")

        def preflight(origin: String, method: String): HttpResponse[String] = {
          val request: HttpRequest = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
            .header("Origin", origin)
            .header("Access-Control-Request-Method", method)
            .header("Access-Control-Request-Headers", "Content-Type, X-Customer")
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build()
          client.send(request, HttpResponse.BodyHandlers.ofString())
        }

        val permission: HttpResponse[String] = preflight(storefront, "POST")
        assertEquals(204, permission.statusCode())
        assertEquals("", permission.body())
        assertEquals(storefront, permission.headers().firstValue("Access-Control-Allow-Origin").orElse(""))
        assertEquals("true", permission.headers().firstValue("Access-Control-Allow-Credentials").orElse(""))
        assertEquals("POST", permission.headers().firstValue("Access-Control-Allow-Methods").orElse(""))
        val allowedHeaders: Set[String] = permission.headers().firstValue("Access-Control-Allow-Headers")
          .orElse("").split(",").map(_.trim.toLowerCase(java.util.Locale.ROOT)).toSet
        assertTrue(allowedHeaders.contains("content-type"))
        assertTrue(allowedHeaders.contains("x-customer"))
        assertEquals("600", permission.headers().firstValue("Access-Control-Max-Age").orElse(""))

        val request: HttpRequest = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
          .header("Origin", storefront)
          .header("Content-Type", "text/plain")
          .header("X-Customer", "alice")
          .POST(HttpRequest.BodyPublishers.ofString("Boston")).build()
        val response: HttpResponse[String] = client.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, response.statusCode())
        assertEquals("Shipping to Boston for alice: USD 7.50", response.body())
        assertEquals("quote-42", response.headers().firstValue("X-Quote-Id").orElse(""))
        assertEquals(storefront, response.headers().firstValue("Access-Control-Allow-Origin").orElse(""))
        assertEquals("true", response.headers().firstValue("Access-Control-Allow-Credentials").orElse(""))
        assertEquals("X-Quote-Id", response.headers().firstValue("Access-Control-Expose-Headers").orElse(""))

        // Browsers must not receive permission for another origin or an unsupported method.
        val untrusted: HttpResponse[String] = preflight("https://untrusted.example", "POST")
        assertEquals(204, untrusted.statusCode())
        assertFalse(untrusted.headers().firstValue("Access-Control-Allow-Origin").isPresent)
        val unsupported: HttpResponse[String] = preflight(storefront, "DELETE")
        assertEquals(204, unsupported.statusCode())
        assertFalse(unsupported.headers().firstValue("Access-Control-Allow-Methods").isPresent)
      } finally client.close()
    } finally server.stop(0)
  }
}
