/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_profunktor.redis4cats_core_3

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.unsafe.implicits.global
import com.github.fppt.jedismock.RedisServer
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.codecs.Codecs
import dev.profunktor.redis4cats.codecs.splits.SplitEpi
import dev.profunktor.redis4cats.codecs.splits.stringLongEpi
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log
import dev.profunktor.redis4cats.log4cats.log4CatsInstance
import io.circe.Codec
import io.circe.Json
import io.circe.parser.decode
import io.circe.parser.parse
import io.circe.syntax.EncoderOps
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

/**
 * Derives typed Redis codecs from the UTF-8 codec with `Codecs.derive` and `SplitEpi`.
 *
 * A shop keeps its product catalog in Redis: products are stored as JSON
 * under typed SKU keys through a codec derived from circe, and stock levels
 * are stored as numbers through a codec derived from `stringLongEpi`. Both
 * typed connections share one client, and a plain UTF-8 connection shows
 * what the derived codecs actually wrote to the server.
 */
class ProductCatalogDerivedCodecsTest {

  /** A stock-keeping unit, stored under `product:<code>` and `stock:<code>`. */
  private final case class Sku(code: String)

  /** A catalog entry, stored as a JSON document. */
  private final case class Product(name: String, priceCents: Long, tags: List[String]) derives Codec.AsObject

  /** What the application reads back from the catalog. */
  private final case class Observed(
      keyboard: Option[Product],
      catalog: Map[Sku, Product],
      rawKeyboard: Option[String],
      stockAfterSales: Option[Long],
      remainingAfterSale: Long,
      rawStock: Option[String]
  )

  private val productSku: SplitEpi[String, Sku] =
    SplitEpi[String, Sku](key => Sku(key.stripPrefix("product:")), sku => s"product:${sku.code}")

  private val stockSku: SplitEpi[String, Sku] =
    SplitEpi[String, Sku](key => Sku(key.stripPrefix("stock:")), sku => s"stock:${sku.code}")

  private val productJson: SplitEpi[String, Product] =
    SplitEpi[String, Product](
      json => decode[Product](json).fold(error => throw new IllegalStateException(s"Corrupt product: $json", error), identity),
      product => product.asJson.noSpaces
    )

  @Test
  def storesProductsAsJsonAndStockAsNumbers(): Unit = {
    val server: RedisServer = RedisServer.newRedisServer().start()
    try {
      given Logger[IO] = Slf4jLogger.getLoggerFromName[IO]("product-catalog")
      given Log[IO] = log4CatsInstance[IO]

      val products: RedisCodec[Sku, Product] = Codecs.derive(RedisCodec.Utf8, productSku, productJson)
      val stock: RedisCodec[Sku, Long] = Codecs.derive(RedisCodec.Utf8, stockSku, stringLongEpi)

      val connections: Resource[
        IO,
        (RedisCommands[IO, Sku, Product], RedisCommands[IO, Sku, Long], RedisCommands[IO, String, String])
      ] =
        for {
          client <- RedisClient[IO].from(s"redis://${server.getHost}:${server.getBindPort}")
          catalog <- Redis[IO].fromClient(client, products)
          inventory <- Redis[IO].fromClient(client, stock)
          raw <- Redis[IO].fromClient(client, RedisCodec.Utf8)
        } yield (catalog, inventory, raw)

      val keyboard: Product = Product("Mechanical keyboard", 8999L, List("peripherals", "keyboards"))
      val mouse: Product = Product("Wireless mouse", 2999L, List("peripherals"))

      val observed: Observed = connections
        .use { (catalog, inventory, raw) =>
          for {
            _ <- catalog.set(Sku("kb-1"), keyboard)
            _ <- catalog.mSet(Map(Sku("ms-1") -> mouse))
            storedKeyboard <- catalog.get(Sku("kb-1"))
            stored <- catalog.mGet(Set(Sku("kb-1"), Sku("ms-1"), Sku("missing")))
            rawKeyboard <- raw.get("product:kb-1")
            _ <- inventory.set(Sku("kb-1"), 40L)
            _ <- inventory.decrBy(Sku("kb-1"), 3L)
            stockAfterSales <- inventory.get(Sku("kb-1"))
            remaining <- inventory.decr(Sku("kb-1"))
            rawStock <- raw.get("stock:kb-1")
          } yield Observed(storedKeyboard, stored, rawKeyboard, stockAfterSales, remaining, rawStock)
        }
        .unsafeRunSync()

      assertEquals(Some(keyboard), observed.keyboard)
      assertEquals(Map(Sku("kb-1") -> keyboard, Sku("ms-1") -> mouse), observed.catalog)
      assertEquals(
        Some(
          Json.obj(
            "name" -> Json.fromString("Mechanical keyboard"),
            "priceCents" -> Json.fromLong(8999L),
            "tags" -> Json.arr(Json.fromString("peripherals"), Json.fromString("keyboards"))
          )
        ),
        observed.rawKeyboard.map(json => parse(json).fold(error => throw error, identity))
      )
      assertEquals(Some(37L), observed.stockAfterSales)
      assertEquals(36L, observed.remainingAfterSale)
      assertEquals(Some("36"), observed.rawStock)
    } finally {
      server.stop()
    }
  }
}
