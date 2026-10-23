/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package realuse.tapircirce

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.comcast.ip4s.*
import io.circe.Codec
import org.http4s.HttpApp
import org.http4s.ember.server.EmberServerBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import sttp.client3.HttpURLConnectionBackend
import sttp.client3.SttpBackendOptions
import sttp.model.StatusCode
import sttp.model.Uri
import sttp.tapir.*
import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.tapir.json.circe.TapirJsonCirce
import sttp.tapir.server.http4s.Http4sServerInterpreter

import scala.concurrent.duration.*

/** Exercises Circe JSON client response decoding for inventory lookups and structured API errors. */
class InventoryJsonClientTest extends TapirJsonCirce {
  case class Stock(sku: String, available: Int, warehouses: Map[String, Int])
      derives Codec.AsObject, Schema
  case class InventoryError(code: String, message: String, retryable: Boolean)
      derives Codec.AsObject, Schema

  @Test
  @Timeout(60)
  def readsTypedStockAndHandlesMissingSkuOverHttp(): Unit = {
    val stock: Stock = Stock("tea-17", 12, Map("north" -> 7, "south" -> 5))
    val missing: InventoryError = InventoryError("unknown_sku", "No stock record for missing-99", false)
    val lookup: PublicEndpoint[String, InventoryError, Stock, Any] = endpoint.get
      .in("inventory" / path[String]("sku"))
      .errorOut(statusCode(StatusCode.NotFound))
      .errorOut(jsonBody[InventoryError])
      .out(jsonBody[Stock])
    val service = lookup.serverLogic[IO] { sku =>
      IO.pure(if (sku == stock.sku) Right(stock) else Left(missing))
    }
    val app: HttpApp[IO] = Http4sServerInterpreter[IO]().toRoutes(service).orNotFound

    EmberServerBuilder.default[IO]
      .withHost(ipv4"127.0.0.1")
      .withPort(port"0")
      .withHttpApp(app)
      .build
      .use { server =>
        IO.blocking {
          val backend = HttpURLConnectionBackend(
            options = SttpBackendOptions.connectionTimeout(10.seconds)
          )
          try {
            val baseUri: Uri = Uri.unsafeParse(s"http://127.0.0.1:${server.address.getPort}")
            val request = SttpClientInterpreter().toRequest(lookup, Some(baseUri))
            val found = request("tea-17").readTimeout(15.seconds).send(backend)
            assertEquals(StatusCode.Ok, found.code)
            assertEquals(DecodeResult.Value(Right(stock)), found.body)

            val notFound = request("missing-99").readTimeout(15.seconds).send(backend)
            assertEquals(StatusCode.NotFound, notFound.code)
            assertEquals(DecodeResult.Value(Left(missing)), notFound.body)
          } finally {
            backend.close()
          }
        }
      }
      .timeout(45.seconds)
      .unsafeRunSync()
  }
}
