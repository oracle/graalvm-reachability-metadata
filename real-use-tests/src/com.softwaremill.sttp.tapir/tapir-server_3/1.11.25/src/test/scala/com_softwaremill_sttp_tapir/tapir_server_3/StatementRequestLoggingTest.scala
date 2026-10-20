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
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}

import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertNotNull, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import sttp.model.StatusCode
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.log.DefaultServerLog
import sttp.tapir.server.jdkhttp.{JdkHttpServer, JdkHttpServerOptions}

/** Exercises the server logging interceptor for privacy-conscious statement request logs. */
class StatementRequestLoggingTest {
  @Test
  @Timeout(60)
  def logsRequestOutcomesWithoutCustomerDetailsOrHealthProbeNoise(): Unit = {
    val logs: LinkedBlockingQueue[String] = new LinkedBlockingQueue[String]()
    val statements: Map[String, String] = Map("alice@example.com" -> "Private balance: USD 125.00")
    val statement: ServerEndpoint[Any, Identity] = endpoint.get
      .name("customer-statement")
      .in("statements")
      .in(query[String]("email"))
      .errorOut(statusCode(StatusCode.NotFound).and(stringBody))
      .out(stringBody)
      .serverLogic[Identity](email => statements.get(email).toRight("Statement not found"))
    val health: ServerEndpoint[Any, Identity] = endpoint.get
      .name("health-probe")
      .in("health")
      .out(stringBody)
      .serverLogicSuccess[Identity](_ => "ready")

    def record(level: String, message: String): Unit = {
      logs.add(s"$level $message")
      ()
    }

    val requestLog: DefaultServerLog[Identity] = DefaultServerLog[Identity](
      doLogWhenReceived = message => record("received", message),
      doLogWhenHandled = (message, _) => record("handled", message),
      doLogAllDecodeFailures = (message, _) => record("decode", message),
      doLogExceptions = (message, _) => record("error", message),
      noLog = (),
      includeTiming = false,
      // Query values and response bodies contain customer data and must not enter the log sink.
      showRequest = request => s"${request.method.method} ${request.uri.path.mkString("/", "/", "")}",
      showEndpoint = endpoint => endpoint.info.name.getOrElse("unnamed"),
      showResponse = response => response.code.code.toString,
      ignoreEndpoints = Set(health.endpoint)
    )
    val options: JdkHttpServerOptions = JdkHttpServerOptions.customiseInterceptors
      .serverLog(requestLog).options
    val server = JdkHttpServer(options = options).host("127.0.0.1").port(0)
      .addEndpoint(statement).addEndpoint(health).start()
    try {
      val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
      try {
        val baseUri: String = s"http://127.0.0.1:${server.getAddress.getPort}"

        def get(path: String): HttpResponse[String] = {
          val request: HttpRequest = HttpRequest.newBuilder(URI.create(baseUri + path))
            .timeout(Duration.ofSeconds(10)).GET().build()
          client.send(request, HttpResponse.BodyHandlers.ofString())
        }

        val found: HttpResponse[String] = get("/statements?email=alice%40example.com")
        assertEquals(200, found.statusCode())
        assertEquals("Private balance: USD 125.00", found.body())
        val missing: HttpResponse[String] = get("/statements?email=bob%40example.com")
        assertEquals(404, missing.statusCode())
        assertEquals("Statement not found", missing.body())
        val probe: HttpResponse[String] = get("/health")
        assertEquals(200, probe.statusCode())
        assertEquals("ready", probe.body())

        val successLog: String = logs.poll(10, TimeUnit.SECONDS)
        val missingLog: String = logs.poll(10, TimeUnit.SECONDS)
        assertNotNull(successLog)
        assertNotNull(missingLog)
        assertTrue(successLog.startsWith("handled "), successLog)
        assertTrue(successLog.contains("GET /statements"), successLog)
        assertTrue(successLog.contains("customer-statement"), successLog)
        assertTrue(successLog.endsWith("response: 200"), successLog)
        assertTrue(missingLog.contains("customer-statement"), missingLog)
        assertTrue(missingLog.endsWith("response: 404"), missingLog)
        val exported: String = successLog + missingLog
        List("alice", "bob", "example.com", "email=", "Private balance", "125.00", "health-probe")
          .foreach(sensitive => assertFalse(exported.contains(sensitive), exported))
        // Handled-request logging completes before the response is sent, including for the probe.
        assertTrue(logs.isEmpty, logs.toString)
      } finally client.close()
    } finally server.stop(0)
  }
}
