/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatra.scalatra_3

import java.util.concurrent.{ExecutorService, Executors, TimeUnit}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.{Duration, DurationInt}

import org.eclipse.jetty.http.HttpTester
import org.eclipse.jetty.server.{LocalConnector, Server}
import org.eclipse.jetty.servlet.{ServletContextHandler, ServletHolder}
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import org.scalatra.{AsyncResult, FutureSupport, ScalatraServlet}

/** Exercises asynchronous response rendering for delivery quotes computed on a worker executor. */
class AsyncDeliveryQuoteTest {
  @Test
  @Timeout(60)
  def returnsCompletedQuotesWithEachRequestsOwnParameters(): Unit = {
    val workers: ExecutorService = Executors.newFixedThreadPool(2)
    val server: Server = new Server()
    val connector: LocalConnector = new LocalConnector(server)
    connector.setIdleTimeout(10000)
    server.addConnector(connector)
    server.setStopTimeout(10000)

    val context: ServletContextHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS)
    context.setContextPath("/")
    val holder: ServletHolder = new ServletHolder(new QuoteServlet(ExecutionContext.fromExecutor(workers)))
    holder.setAsyncSupported(true)
    context.addServlet(holder, "/*")
    server.setHandler(context)

    try {
      server.start()

      assertQuote(connector, "/delivery/hammer?quantity=2&zone=local", "hammer:2:local:700")
      assertQuote(connector, "/delivery/spade?quantity=3&zone=remote", "spade:3:remote:1500")
      assertQuote(connector, "/delivery/hammer?quantity=1&zone=local", "hammer:1:local:600")
    } finally {
      try {
        try server.stop()
        finally server.destroy()
      } finally {
        workers.shutdownNow()
        assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS), "Quote workers must terminate")
      }
    }
  }

  private def assertQuote(connector: LocalConnector, path: String, expected: String): Unit = {
    val request: String = s"GET $path HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n"
    val response: HttpTester.Response =
      HttpTester.parseResponse(connector.getResponse(request, 10, TimeUnit.SECONDS))
    assertEquals(200, response.getStatus)
    assertEquals("text/plain", response.get("Content-Type").split(";")(0))
    assertEquals(expected, response.getContent)
  }

  private class QuoteServlet(quoteExecutor: ExecutionContext)
      extends ScalatraServlet with FutureSupport {
    override protected implicit val executor: ExecutionContext = quoteExecutor
    private val itemWeights: Map[String, Int] = Map("hammer" -> 2, "spade" -> 4)
    private val zoneCharges: Map[String, Int] = Map("local" -> 500, "remote" -> 900)

    get("/delivery/:sku") {
      contentType = "text/plain"
      val sku: String = params("sku")
      val quantity: Int = params.getOrElse("quantity", "1").toInt
      val zone: String = params.getOrElse("zone", "local")

      new AsyncResult {
        override val timeout: Duration = 15.seconds
        val is: Future[String] = Future {
          val chargeInCents: Int = zoneCharges(zone) + itemWeights(sku) * quantity * 50
          s"$sku:$quantity:$zone:$chargeInCents"
        }
      }
    }
  }
}
