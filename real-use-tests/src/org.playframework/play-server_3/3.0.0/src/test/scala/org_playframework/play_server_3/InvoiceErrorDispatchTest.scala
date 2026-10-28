/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_server_3

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import play.api.ApplicationLoader.Context
import play.api.BuiltInComponentsFromContext
import play.api.Mode
import play.api.http.HttpErrorHandler
import play.api.libs.json.Json
import play.api.mvc.EssentialFilter
import play.api.mvc.RequestHeader
import play.api.mvc.Result
import play.api.mvc.Results.Ok
import play.api.mvc.Results.Status
import play.api.routing.Router
import play.core.server.Server
import play.core.server.ServerConfig
import scala.concurrent.Future

/** Exercises server-to-application HTTP error dispatch for safe, correlated invoice API responses. */
class InvoiceErrorDispatchTest {
  @Test
  @Timeout(60)
  def translatesFailedActionsAndMissingRoutesWithoutLeakingLedgerDetails(): Unit = {
    val config: ServerConfig = ServerConfig(
      port = Some(0),
      address = "127.0.0.1",
      mode = Mode.Test
    )

    Server.withApplicationFromContext(config) { context =>
      new InvoiceComponents(context).application
    } { port =>
      val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()
      try {
        val base: URI = URI.create(s"http://127.0.0.1:${port.value}")
        def fetch(path: String, requestId: String): HttpResponse[String] = {
          val request: HttpRequest = HttpRequest.newBuilder(base.resolve(path))
            .timeout(Duration.ofSeconds(20))
            .header("X-Request-ID", requestId)
            .header("Accept", "application/json")
            .GET()
            .build()
          client.send(request, HttpResponse.BodyHandlers.ofString())
        }

        val failed: HttpResponse[String] = fetch("/invoices/INV-42", "invoice-request-42")
        assertProblem(failed, 500, "Invoice service unavailable", "invoice-request-42")
        assertFalse(failed.body().contains("ledger-primary.internal"))
        assertFalse(failed.body().contains("IllegalStateException"))

        val missing: HttpResponse[String] = fetch("/obsolete-invoice-export", "invoice-request-43")
        assertProblem(missing, 404, "Invoice endpoint not found", "invoice-request-43")

        // A failed action must not prevent the same server from handling the next valid request.
        val healthy: HttpResponse[String] = fetch("/invoices/status", "invoice-request-44")
        assertEquals(200, healthy.statusCode())
        assertEquals("Invoice API ready", healthy.body())
      } finally {
        client.close()
      }
    }
  }

  private def assertProblem(
      response: HttpResponse[String],
      status: Int,
      title: String,
      requestId: String
  ): Unit = {
    assertEquals(status, response.statusCode())
    assertEquals("application/problem+json", response.headers().firstValue("Content-Type").orElseThrow())
    assertEquals(requestId, response.headers().firstValue("X-Request-ID").orElseThrow())
    assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow())
    assertEquals(
      Json.obj("status" -> status, "title" -> title, "requestId" -> requestId),
      Json.parse(response.body())
    )
  }

  private class InvoiceComponents(context: Context) extends BuiltInComponentsFromContext(context) {
    override def httpFilters: Seq[EssentialFilter] = Seq.empty

    override lazy val httpErrorHandler: HttpErrorHandler = new InvoiceErrorHandler()

    override lazy val router: Router = Router.from {
      case request if request.method == "GET" && request.path == "/invoices/INV-42" =>
        defaultActionBuilder.async { _ =>
          // Model a failed ledger operation at the asynchronous application/server boundary.
          Future.failed[Result](new IllegalStateException("Cannot read ledger-primary.internal"))
        }
      case request if request.method == "GET" && request.path == "/invoices/status" =>
        defaultActionBuilder { _ => Ok("Invoice API ready") }
    }
  }

  private class InvoiceErrorHandler extends HttpErrorHandler {
    override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
      problem(request, statusCode, "Invoice endpoint not found")
    }

    override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
      problem(request, 500, "Invoice service unavailable")
    }

    private def problem(request: RequestHeader, status: Int, title: String): Future[Result] = {
      val requestId: String = request.headers.get("X-Request-ID").getOrElse("unassigned")
      Future.successful(
        Status(status)(Json.obj("status" -> status, "title" -> title, "requestId" -> requestId))
          .as("application/problem+json")
          .withHeaders("X-Request-ID" -> requestId, "Cache-Control" -> "no-store")
      )
    }
  }
}
