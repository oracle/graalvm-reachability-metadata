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
import java.util.concurrent.atomic.AtomicReference

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse}
import org.junit.jupiter.api.{Test, Timeout}
import sttp.model.StatusCode
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.reject.DefaultRejectHandler
import sttp.tapir.server.jdkhttp.{JdkHttpServer, JdkHttpServerOptions}
import sttp.tapir.server.model.ValuedEndpointOutput

/** Exercises the routing-rejection interceptor for a document draft API with multiple HTTP methods. */
class DocumentRoutingRejectionTest {
  @Test
  @Timeout(60)
  def distinguishesUnsupportedMethodsFromUnknownRoutes(): Unit = {
    val draft: AtomicReference[String] = new AtomicReference("Initial draft")
    val readDraft: ServerEndpoint[Any, Identity] = endpoint.get
      .in("documents" / "draft")
      .out(stringBody)
      .serverLogicSuccess[Identity](_ => draft.get())
    val saveDraft: ServerEndpoint[Any, Identity] = endpoint.put
      .in("documents" / "draft")
      .in(stringBody)
      .out(stringBody)
      .serverLogicSuccess[Identity] { content =>
        draft.set(content)
        "Draft saved"
      }
    val rejectionHandler: DefaultRejectHandler[Identity] = DefaultRejectHandler[Identity](
      response = (code, message) => ValuedEndpointOutput(
        statusCode.and(header[String]("X-Error-Code")).and(stringBody),
        (code, if (code == StatusCode.MethodNotAllowed) "unsupported_method" else "unknown_route", message)
      ),
      defaultStatusCodeAndBody = Some((StatusCode.NotFound, "Document route not found"))
    )
    val options: JdkHttpServerOptions = JdkHttpServerOptions.customiseInterceptors
      .rejectHandler(rejectionHandler).options
    val server = JdkHttpServer(options = options).host("127.0.0.1").port(0)
      .addEndpoint(readDraft).addEndpoint(saveDraft).start()
    try {
      val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
      try {
        val baseUri: String = s"http://127.0.0.1:${server.getAddress.getPort}"

        def request(method: String, path: String, body: Option[String] = None): HttpResponse[String] = {
          val publisher: HttpRequest.BodyPublisher = body match {
            case Some(content) => HttpRequest.BodyPublishers.ofString(content)
            case None => HttpRequest.BodyPublishers.noBody()
          }
          val message: HttpRequest = HttpRequest.newBuilder(URI.create(baseUri + path))
            .timeout(Duration.ofSeconds(10)).header("Content-Type", "text/plain")
            .method(method, publisher).build()
          client.send(message, HttpResponse.BodyHandlers.ofString())
        }

        val initial: HttpResponse[String] = request("GET", "/documents/draft")
        assertEquals(200, initial.statusCode())
        assertEquals("Initial draft", initial.body())

        // A method mismatch on the first endpoint must not prevent trying the next one.
        val saved: HttpResponse[String] = request("PUT", "/documents/draft", Some("Revised draft"))
        assertEquals(200, saved.statusCode())
        assertEquals("Draft saved", saved.body())
        assertFalse(saved.headers().firstValue("X-Error-Code").isPresent)

        val unsupported: HttpResponse[String] = request("DELETE", "/documents/draft")
        assertEquals(405, unsupported.statusCode())
        assertEquals("unsupported_method", unsupported.headers().firstValue("X-Error-Code").orElse(""))
        assertEquals("Method Not Allowed", unsupported.body())

        // A non-existent path is a missing route, even if its method is unsupported elsewhere.
        val missing: HttpResponse[String] = request("DELETE", "/documents/missing")
        assertEquals(404, missing.statusCode())
        assertEquals("unknown_route", missing.headers().firstValue("X-Error-Code").orElse(""))
        assertEquals("Document route not found", missing.body())

        val retained: HttpResponse[String] = request("GET", "/documents/draft")
        assertEquals(200, retained.statusCode())
        assertEquals("Revised draft", retained.body())
        assertFalse(retained.headers().firstValue("X-Error-Code").isPresent)
      } finally client.close()
    } finally server.stop(0)
  }
}
