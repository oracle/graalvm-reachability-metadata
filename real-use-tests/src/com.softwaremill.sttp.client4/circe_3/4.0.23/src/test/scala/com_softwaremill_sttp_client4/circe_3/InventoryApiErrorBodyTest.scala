/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_client4.circe_3

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.circe.ParsingFailure
import io.circe.DecodingFailure
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import sttp.client4.*
import sttp.client4.circe.*
import sttp.model.StatusCode

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import scala.concurrent.duration.*

/**
 * Exercises typed error-body decoding with `asJsonEither` and `asJsonEitherOrFail`: an inventory
 * client reads successful lookups as items, non-2xx responses as a structured API error, and
 * reports malformed or mistyped success payloads as deserialization failures.
 */
class InventoryApiErrorBodyTest {
  private final case class Item(sku: String, name: String, stock: Int, tags: List[String])
  private final case class ApiError(code: String, message: String, details: Map[String, String])

  private var server: HttpServer = null
  private var backend: SyncBackend = null

  @BeforeEach
  def startServer(): Unit = {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext(
      "/items/",
      (exchange: HttpExchange) => {
        try {
          exchange.getRequestURI.getPath.stripPrefix("/items/") match {
            case "KB-01" =>
              respond(exchange, 200, Item("KB-01", "Mechanical keyboard", 12, List("input", "usb")).asJson.noSpaces)
            case "RETIRED-9" =>
              val gone: ApiError = ApiError("ITEM_RETIRED", "Item was retired", Map("replacement" -> "KB-01"))
              respond(exchange, 410, gone.asJson.noSpaces)
            case "TRUNCATED" =>
              respond(exchange, 200, """{"sku":"TRUNCATED","name":""")
            case "MISTYPED" =>
              respond(exchange, 200, """{"sku":"MISTYPED","name":"Cable","stock":"many","tags":[]}""")
            case sku =>
              val missing: ApiError = ApiError("ITEM_NOT_FOUND", s"No item $sku", Map("sku" -> sku))
              respond(exchange, 404, missing.asJson.noSpaces)
          }
        } finally {
          exchange.close()
        }
      }
    )
    server.start()
    backend = DefaultSyncBackend(BackendOptions.connectionTimeout(10.seconds))
  }

  @AfterEach
  def stopServer(): Unit = {
    backend.close()
    server.stop(0)
  }

  @Test
  def decodesSuccessAndErrorBodiesIntoSeparateTypes(): Unit = {
    val found: Response[Either[ResponseException[ApiError], Item]] = lookup("KB-01")
    assertThat(found.code).isEqualTo(StatusCode.Ok)
    assertThat(found.body).isEqualTo(Right(Item("KB-01", "Mechanical keyboard", 12, List("input", "usb"))))

    val missing: Response[Either[ResponseException[ApiError], Item]] = lookup("NOPE-3")
    assertThat(missing.code).isEqualTo(StatusCode.NotFound)
    missing.body match {
      case Left(ResponseException.UnexpectedStatusCode(error, metadata)) =>
        assertThat(error).isEqualTo(ApiError("ITEM_NOT_FOUND", "No item NOPE-3", Map("sku" -> "NOPE-3")))
        assertThat(metadata.code).isEqualTo(StatusCode.NotFound)
        assertThat(metadata.contentType).isEqualTo(Some("application/json"))
      case other =>
        throw new AssertionError(s"Expected a typed 404 error body, got $other")
    }

    val retired: Response[Either[ResponseException[ApiError], Item]] = lookup("RETIRED-9")
    retired.body match {
      case Left(ResponseException.UnexpectedStatusCode(error, metadata)) =>
        assertThat(metadata.code).isEqualTo(StatusCode.Gone)
        assertThat(error.code).isEqualTo("ITEM_RETIRED")
        assertThat(error.details).isEqualTo(Map("replacement" -> "KB-01"))
      case other =>
        throw new AssertionError(s"Expected a typed 410 error body, got $other")
    }
  }

  @Test
  def reportsMalformedAndMistypedSuccessPayloadsAsDeserializationFailures(): Unit = {
    lookup("TRUNCATED").body match {
      case Left(ResponseException.DeserializationException(body, cause, metadata)) =>
        assertThat(body).isEqualTo("""{"sku":"TRUNCATED","name":""")
        assertThat(cause).isInstanceOf(classOf[ParsingFailure])
        assertThat(metadata.code).isEqualTo(StatusCode.Ok)
      case other =>
        throw new AssertionError(s"Expected a parsing failure, got $other")
    }

    lookup("MISTYPED").body match {
      case Left(ResponseException.DeserializationException(_, cause, _)) =>
        assertThat(cause).isInstanceOf(classOf[DecodingFailure])
        val failure: DecodingFailure = cause.asInstanceOf[DecodingFailure]
        assertThat(failure.pathToRootString).isEqualTo(Some(".stock"))
      case other =>
        throw new AssertionError(s"Expected a decoding failure, got $other")
    }
  }

  @Test
  def orFailVariantReturnsPlainErrorsAndThrowsOnBrokenPayloads(): Unit = {
    val found: Either[ApiError, Item] = lookupOrFail("KB-01").body
    assertThat(found.map(_.stock)).isEqualTo(Right(12))

    val missing: Either[ApiError, Item] = lookupOrFail("NOPE-4").body
    assertThat(missing).isEqualTo(Left(ApiError("ITEM_NOT_FOUND", "No item NOPE-4", Map("sku" -> "NOPE-4"))))

    val failure: SttpClientException =
      try {
        lookupOrFail("TRUNCATED")
        throw new AssertionError("Expected the broken payload to fail the request")
      } catch {
        case e: SttpClientException.ResponseHandlingException[?] => e
      }
    failure.getCause match {
      case ResponseException.DeserializationException(body, cause, metadata) =>
        assertThat(body).isEqualTo("""{"sku":"TRUNCATED","name":""")
        assertThat(cause).isInstanceOf(classOf[ParsingFailure])
        assertThat(metadata.code).isEqualTo(StatusCode.Ok)
      case other =>
        throw new AssertionError(s"Expected a deserialization failure as the cause, got $other")
    }
  }

  private def itemUri(sku: String) = uri"http://127.0.0.1:${server.getAddress.getPort}/items/$sku"

  private def lookup(sku: String): Response[Either[ResponseException[ApiError], Item]] =
    basicRequest
      .get(itemUri(sku))
      .response(asJsonEither[ApiError, Item])
      .readTimeout(10.seconds)
      .send(backend)

  private def lookupOrFail(sku: String): Response[Either[ApiError, Item]] =
    basicRequest
      .get(itemUri(sku))
      .response(asJsonEitherOrFail[ApiError, Item])
      .readTimeout(10.seconds)
      .send(backend)

  private def respond(exchange: HttpExchange, status: Int, body: String): Unit = {
    val bytes: Array[Byte] = body.getBytes(StandardCharsets.UTF_8)
    exchange.getResponseHeaders.set("Content-Type", "application/json")
    exchange.sendResponseHeaders(status, bytes.length.toLong)
    exchange.getResponseBody.write(bytes)
  }
}
