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
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log
import dev.profunktor.redis4cats.log4cats.log4CatsInstance
import dev.profunktor.redis4cats.tx.TxStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.duration.DurationInt

/**
 * Batches commands through pipelining, run by the core `TxRunner` with auto-flush disabled and results kept in a `TxStore`.
 *
 * A pricing service warms its Redis cache at startup: it writes every product
 * price into a hash, bumps a per-category product counter and tags each
 * product, all in one pipelined batch so the writes travel in a single flush
 * instead of one round trip each. The replies of the batch are collected in
 * the pipeline's store. When a product is discontinued the service evicts its
 * cache entries in a second pipeline whose replies it does not need.
 */
class PriceCacheWarmupPipelineTest {

  /** A product the pricing service caches. */
  private final case class Product(sku: String, category: String, cents: Long, tags: List[String])

  /** What the application reads back after warming and evicting. */
  private final case class Observed(
      warmup: Map[String, String],
      prices: Map[String, String],
      categoryCounts: Map[String, Option[String]],
      gadgetTags: Set[String],
      discontinuedPrice: Option[String],
      discontinuedTags: Set[String],
      remainingPrices: Map[String, String]
  )

  /** Keeps prices in the `prices` hash, counts in `category:<name>` and tags in `tags:<sku>`. */
  private final class PriceCache(redis: RedisCommands[IO, String, String]) {

    /** Writes every product in one pipeline and returns each reply keyed by what it describes. */
    def warm(products: List[Product]): IO[Map[String, String]] =
      redis.pipeline { (store: TxStore[IO, String, String]) =>
        products.flatMap { product =>
          List(
            redis.hSet("prices", product.sku, product.cents.toString).flatMap(added => store.set(s"price:${product.sku}")(added.toString)),
            redis.incr(s"category:${product.category}").flatMap(count => store.set(s"count:${product.sku}")(count.toString)),
            redis.sAdd(s"tags:${product.sku}", product.tags*).flatMap(added => store.set(s"tags:${product.sku}")(added.toString))
          )
        }
      }

    /** Removes a product's cache entries in a pipeline whose replies are discarded. */
    def evict(product: Product): IO[Unit] =
      redis.pipeline_(
        List(
          redis.hDel("prices", product.sku).void,
          redis.decr(s"category:${product.category}").void,
          redis.del(s"tags:${product.sku}").void
        )
      )

    def prices: IO[Map[String, String]] = redis.hGetAll("prices")

    def price(sku: String): IO[Option[String]] = redis.hGet("prices", sku)

    def count(category: String): IO[Option[String]] = redis.get(s"category:$category")

    def tags(sku: String): IO[Set[String]] = redis.sMembers(s"tags:$sku")
  }

  @Test
  def warmsAndEvictsCacheInPipelinedBatches(): Unit = {
    val server: RedisServer = RedisServer.newRedisServer().start()
    try {
      given Logger[IO] = Slf4jLogger.getLoggerFromName[IO]("price-cache")
      given Log[IO] = log4CatsInstance[IO]

      val products: List[Product] = List(
        Product("kb-1", "peripherals", 4999L, List("keyboard", "wireless")),
        Product("ms-2", "peripherals", 1999L, List("mouse")),
        Product("gx-9", "gadgets", 12900L, List("drone", "camera", "outdoor"))
      )

      val connection: Resource[IO, RedisCommands[IO, String, String]] =
        for {
          client <- RedisClient[IO].from(s"redis://${server.getHost}:${server.getBindPort}")
          redis <- Redis[IO].fromClient(client, RedisCodec.Utf8)
        } yield redis

      val observed: Observed = connection
        .use { redis =>
          val cache: PriceCache = new PriceCache(redis)
          for {
            warmup <- cache.warm(products).timeout(30.seconds)
            prices <- cache.prices
            counts <- cache.count("peripherals").product(cache.count("gadgets"))
            gadgetTags <- cache.tags("gx-9")
            _ <- cache.evict(products(1)).timeout(30.seconds)
            discontinuedPrice <- cache.price("ms-2")
            discontinuedTags <- cache.tags("ms-2")
            peripheralsAfter <- cache.count("peripherals")
            remainingPrices <- cache.prices
          } yield Observed(
            warmup,
            prices,
            Map("peripherals" -> counts._1, "gadgets" -> counts._2, "peripheralsAfterEviction" -> peripheralsAfter),
            gadgetTags,
            discontinuedPrice,
            discontinuedTags,
            remainingPrices
          )
        }
        .unsafeRunSync()

      assertEquals(
        Map(
          "price:kb-1" -> "true",
          "price:ms-2" -> "true",
          "price:gx-9" -> "true",
          "tags:kb-1" -> "2",
          "tags:ms-2" -> "1",
          "tags:gx-9" -> "3"
        ),
        observed.warmup.filter((key, _) => !key.startsWith("count:"))
      )
      assertEquals(Set("1", "2"), Set(observed.warmup("count:kb-1"), observed.warmup("count:ms-2")))
      assertEquals("1", observed.warmup("count:gx-9"))
      assertEquals(Map("kb-1" -> "4999", "ms-2" -> "1999", "gx-9" -> "12900"), observed.prices)
      assertEquals(
        Map("peripherals" -> Some("2"), "gadgets" -> Some("1"), "peripheralsAfterEviction" -> Some("1")),
        observed.categoryCounts
      )
      assertEquals(Set("drone", "camera", "outdoor"), observed.gadgetTags)
      assertEquals(None, observed.discontinuedPrice)
      assertEquals(Set.empty[String], observed.discontinuedTags)
      assertEquals(Map("kb-1" -> "4999", "gx-9" -> "12900"), observed.remainingPrices)
    } finally {
      server.stop()
    }
  }
}
