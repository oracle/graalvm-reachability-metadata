/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_client4.circe_3

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.circe.generic.auto.*
import io.circe.parser.decode
import io.circe.syntax.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import sttp.client4.*
import sttp.client4.circe.*
import sttp.client4.logging.LogConfig
import sttp.client4.logging.LogLevel
import sttp.client4.logging.slf4j.Slf4jLoggingBackend
import sttp.model.StatusCode

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import scala.concurrent.duration.*

/**
 * Exercises circe JSON exchanges through the SLF4J logging backend: a payments client wraps its
 * synchronous backend in `Slf4jLoggingBackend`, sends `asJson` bodies and decodes replies, and
 * relies on the audit log to show the JSON payloads while redacting credentials and secrets.
 */
class AuditedPaymentsLoggingBackendTest {
  private final case class Payment(account: String, amountCents: Long, currency: String)
  private final case class Confirmation(paymentId: String, status: String)
  private final case class Rejection(code: String, detail: String)

  private val auditConfig: LogConfig = LogConfig(
    logRequestBody = true,
    logResponseBody = true,
    sensitiveQueryParams = Set("signature"),
    beforeRequestSendLogLevel = LogLevel.Info,
    responseLogLevel = (code: StatusCode) => if (code.isSuccess) LogLevel.Info else LogLevel.Warn,
    responseExceptionLogLevel = LogLevel.Error
  )

  private var serverExecutor: ExecutorService = null
  private var server: HttpServer = null
  private var backend: SyncBackend = null

  @BeforeEach
  def startServer(): Unit = {
    serverExecutor = Executors.newFixedThreadPool(2)
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.setExecutor(serverExecutor)
    server.createContext(
      "/payments",
      (exchange: HttpExchange) => {
        try {
          val body: String = new String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
          val payment: Payment = decode[Payment](body).fold(error => throw error, identity)
          if (payment.amountCents <= 0L) {
            respond(exchange, 422, Rejection("INVALID_AMOUNT", s"${payment.amountCents} is not payable").asJson.noSpaces)
          } else if (payment.currency == "XXX") {
            respond(exchange, 200, """{"paymentId":"p-broken"}""")
          } else {
            respond(exchange, 201, Confirmation(s"p-${payment.account}", "ACCEPTED").asJson.noSpaces)
          }
        } finally {
          exchange.close()
        }
      }
    )
    server.start()
    backend = Slf4jLoggingBackend(DefaultSyncBackend(BackendOptions.connectionTimeout(10.seconds)), auditConfig)
  }

  @AfterEach
  def stopServer(): Unit = {
    backend.close()
    server.stop(0)
    serverExecutor.shutdownNow()
  }

  @Test
  def auditsJsonPayloadsAndRedactsSecrets(): Unit = {
    val payment: Payment = Payment("acc-7", 1999L, "EUR")
    val (confirmation, audit): (Either[ResponseException[Rejection], Confirmation], String) = captureAudit {
      paymentRequest(payment).response(asJsonEither[Rejection, Confirmation]).send(backend).body
    }

    assertThat(confirmation).isEqualTo(Right(Confirmation("p-acc-7", "ACCEPTED")))
    assertThat(audit).contains(payment.asJson.noSpaces)
    assertThat(audit).contains(Confirmation("p-acc-7", "ACCEPTED").asJson.noSpaces)
    assertThat(audit).contains("INFO sttp.client4.logging.slf4j.Slf4jLoggingBackend - Sending request: POST")
    assertThat(audit).contains("response as: either(as json, as json)")
    assertThat(audit).contains("response: 201")
    assertThat(audit).contains("Authorization: ***")
    assertThat(audit).contains("merchant=shop-1&signature=***")
    assertThat(audit).doesNotContain("secret-token-123")
    assertThat(audit).doesNotContain("sig-abc")
  }

  @Test
  def auditsTypedRejectionsAndDecodingFailures(): Unit = {
    val (rejection, rejectionAudit): (Either[ResponseException[Rejection], Confirmation], String) = captureAudit {
      paymentRequest(Payment("acc-8", 0L, "EUR")).response(asJsonEither[Rejection, Confirmation]).send(backend).body
    }

    rejection match {
      case Left(ResponseException.UnexpectedStatusCode(error, metadata)) =>
        assertThat(metadata.code).isEqualTo(StatusCode.UnprocessableEntity)
        assertThat(error).isEqualTo(Rejection("INVALID_AMOUNT", "0 is not payable"))
      case other => throw new AssertionError(s"Expected a typed rejection, got $other")
    }
    assertThat(rejectionAudit).contains("WARN sttp.client4.logging.slf4j.Slf4jLoggingBackend - Request: POST")
    assertThat(rejectionAudit).contains("response: 422")
    assertThat(rejectionAudit).contains(Rejection("INVALID_AMOUNT", "0 is not payable").asJson.noSpaces)

    val (failure, failureAudit): (Throwable, String) = captureAudit {
      try {
        paymentRequest(Payment("acc-9", 500L, "XXX")).response(asJsonOrFail[Confirmation]).send(backend)
        throw new AssertionError("Expected the confirmation to fail decoding")
      } catch {
        case failure: SttpClientException.ResponseHandlingException[?] => failure.getCause
      }
    }

    failure match {
      case ResponseException.DeserializationException(body, _, metadata) =>
        assertThat(metadata.code).isEqualTo(StatusCode.Ok)
        assertThat(body).isEqualTo("""{"paymentId":"p-broken"}""")
      case other => throw new AssertionError(s"Expected a deserialization failure, got $other")
    }
    assertThat(failureAudit).contains("response as: as json or fail")
    assertThat(failureAudit).contains(Payment("acc-9", 500L, "XXX").asJson.noSpaces)
    assertThat(failureAudit).contains("response: 200")
    assertThat(failureAudit).doesNotContain("WARN")
    assertThat(failureAudit).doesNotContain("secret-token-123")
  }

  private def paymentRequest(payment: Payment): Request[Either[String, String]] =
    basicRequest
      .post(uri"http://127.0.0.1:${port}/payments?merchant=shop-1&signature=sig-abc")
      .auth
      .bearer("secret-token-123")
      .body(asJson(payment))
      .readTimeout(10.seconds)

  /** Runs `action` while SLF4J Simple's standard-error target is redirected into a buffer. */
  private def captureAudit[T](action: => T): (T, String) = {
    val buffer: ByteArrayOutputStream = new ByteArrayOutputStream()
    val original: PrintStream = System.err
    System.setErr(new PrintStream(buffer, true, StandardCharsets.UTF_8))
    val result: T =
      try action
      finally System.setErr(original)
    (result, buffer.toString(StandardCharsets.UTF_8))
  }

  private def port: Int = server.getAddress.getPort

  private def respond(exchange: HttpExchange, status: Int, body: String): Unit = {
    val bytes: Array[Byte] = body.getBytes(StandardCharsets.UTF_8)
    exchange.getResponseHeaders.set("Content-Type", "application/json")
    exchange.sendResponseHeaders(status, bytes.length.toLong)
    exchange.getResponseBody.write(bytes)
  }
}
