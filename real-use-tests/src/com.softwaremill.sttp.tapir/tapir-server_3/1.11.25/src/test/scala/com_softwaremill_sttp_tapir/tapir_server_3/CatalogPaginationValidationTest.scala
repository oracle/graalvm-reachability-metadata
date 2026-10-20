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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.{Test, Timeout}
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.decodefailure.DefaultDecodeFailureHandler
import sttp.tapir.server.jdkhttp.{JdkHttpServer, JdkHttpServerOptions}
import sttp.tapir.server.model.ValuedEndpointOutput

/** Exercises the decode-failure interceptor for validated catalog pagination requests. */
class CatalogPaginationValidationTest {
  @Test
  @Timeout(60)
  def rejectsInvalidPaginationWithApplicationErrors(): Unit = {
    val products: Vector[String] = Vector("coffee", "tea", "cocoa", "juice")
    val catalog: ServerEndpoint[Any, Identity] = endpoint.get
      .in("catalog")
      .in(query[Int]("limit").validate(Validator.min(1)).validate(Validator.max(3)))
      .in(query[Int]("offset").default(0).validate(Validator.min(0)))
      .out(stringBody)
      .serverLogicSuccess[Identity] { case (limit, offset) =>
        products.slice(offset, offset + limit).mkString("\n")
      }

    val failureHandler: DefaultDecodeFailureHandler[Identity] = DefaultDecodeFailureHandler[Identity]
      .copy(failureMessage = context =>
        DefaultDecodeFailureHandler.FailureMessages.failureSourceMessage(context.failingInput))
      .response(message => ValuedEndpointOutput(
        header[String]("X-Error-Code").and(stringBody),
        ("invalid_pagination", message)
      ))
    val options: JdkHttpServerOptions = JdkHttpServerOptions.customiseInterceptors
      .decodeFailureHandler(failureHandler).options
    val server = JdkHttpServer(options = options).host("127.0.0.1").port(0).addEndpoint(catalog).start()
    try {
      val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
      try {
        val baseUri: String = s"http://127.0.0.1:${server.getAddress.getPort}"

        def get(path: String): HttpResponse[String] = {
          val request: HttpRequest = HttpRequest.newBuilder(URI.create(baseUri + path))
            .timeout(Duration.ofSeconds(10)).GET().build()
          client.send(request, HttpResponse.BodyHandlers.ofString())
        }

        val firstPage: HttpResponse[String] = get("/catalog?limit=2")
        assertEquals(200, firstPage.statusCode())
        assertEquals("coffee\ntea", firstPage.body())
        assertEquals(false, firstPage.headers().firstValue("X-Error-Code").isPresent)

        val nextPage: HttpResponse[String] = get("/catalog?limit=2&offset=2")
        assertEquals(200, nextPage.statusCode())
        assertEquals("cocoa\njuice", nextPage.body())

        val invalidQueries: List[(String, String)] = List(
          "" -> "limit",
          "?limit=many" -> "limit",
          "?limit=0" -> "limit",
          "?limit=4" -> "limit",
          "?limit=2&offset=-1" -> "offset"
        )
        invalidQueries.foreach { case (queryString, parameter) =>
          val response: HttpResponse[String] = get("/catalog" + queryString)
          assertEquals(400, response.statusCode(), queryString)
          assertEquals("invalid_pagination", response.headers().firstValue("X-Error-Code").orElse(""))
          assertEquals(s"Invalid value for: query parameter $parameter", response.body())
        }

        val unrelated: HttpResponse[String] = get("/not-a-catalog?limit=many")
        assertEquals(404, unrelated.statusCode())
        assertEquals(false, unrelated.headers().firstValue("X-Error-Code").isPresent)
      } finally client.close()
    } finally server.stop(0)
  }
}
