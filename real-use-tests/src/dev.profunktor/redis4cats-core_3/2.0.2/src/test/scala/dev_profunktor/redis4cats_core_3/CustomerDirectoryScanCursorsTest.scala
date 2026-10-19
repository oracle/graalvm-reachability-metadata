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
import cats.syntax.all.*
import com.github.fppt.jedismock.RedisServer
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.RedisCommands
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.KeyScanCursor
import dev.profunktor.redis4cats.data.MapScanCursor
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.data.ValueScanCursor
import dev.profunktor.redis4cats.effect.Log
import dev.profunktor.redis4cats.effects.KeyScanArgs
import dev.profunktor.redis4cats.effects.ScanArgs
import dev.profunktor.redis4cats.log4cats.log4CatsInstance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

/**
 * Pages through keys, hash fields and set members with core's `KeyScanCursor`, `MapScanCursor` and `ValueScanCursor`.
 *
 * A back-office tool exports a customer directory without blocking Redis:
 * it walks every `customer:*` key with SCAN in small batches, reads each
 * customer's profile hash with HSCAN and their tag set with SSCAN, and keeps
 * following the cursor each reply returns until the cursor reports that the
 * iteration is finished. Unrelated keys sharing the keyspace never reach the
 * export.
 */
class CustomerDirectoryScanCursorsTest {

  /** One exported row: the customer ID, their profile fields and their tags. */
  private final case class Customer(id: String, profile: Map[String, String], tags: Set[String])

  /** What the export produced, plus how the cursors behaved while producing it. */
  private final case class Export(
      customers: List[Customer],
      keyPages: Int,
      firstCursorFinished: Boolean,
      lastCursor: String,
      premiumTags: Set[String],
      unknownKeys: List[String]
  )

  /** Walks the directory with cursors, one bounded batch per round trip. */
  private final class DirectoryExporter(redis: RedisCommands[IO, String, String]) {

    private val batch: Long = 2L

    /** Follows SCAN cursors to the end, returning every key seen and the cursors visited. */
    def customerKeys: IO[(List[String], List[KeyScanCursor[String]])] = {
      val args: ScanArgs = ScanArgs("customer:*", batch)
      def loop(cursor: KeyScanCursor[String], keys: List[String], pages: List[KeyScanCursor[String]])
          : IO[(List[String], List[KeyScanCursor[String]])] =
        if (cursor.isFinished) IO.pure((keys, pages.reverse))
        else redis.scan(cursor, args).flatMap(next => loop(next, keys ++ next.keys, next :: pages))
      redis.scan(args).flatMap(first => loop(first, first.keys, List(first)))
    }

    /** Follows SCAN cursors filtered by `KeyScanArgs` to the end, returning every matching key. */
    def keysMatching(args: KeyScanArgs): IO[List[String]] = {
      def loop(cursor: KeyScanCursor[String], keys: List[String]): IO[List[String]] =
        if (cursor.isFinished) IO.pure(keys)
        else redis.scan(cursor, args).flatMap(next => loop(next, keys ++ next.keys))
      redis.scan(args).flatMap(first => loop(first, first.keys))
    }

    /** Follows HSCAN cursors over one profile hash and merges every page. */
    def profile(key: String): IO[Map[String, String]] = {
      val args: ScanArgs = ScanArgs(batch)
      def loop(cursor: MapScanCursor[String, String], fields: Map[String, String]): IO[Map[String, String]] =
        if (cursor.isFinished) IO.pure(fields)
        else redis.hScan(key, cursor, args).flatMap(next => loop(next, fields ++ next.map))
      redis.hScan(key, args).flatMap(first => loop(first, first.map))
    }

    /** Follows SSCAN cursors over one tag set, keeping only tags matching `pattern`. */
    def tags(key: String, pattern: String): IO[Set[String]] = {
      val args: ScanArgs = ScanArgs(pattern, batch)
      def loop(cursor: ValueScanCursor[String], values: Set[String]): IO[Set[String]] =
        if (cursor.isFinished) IO.pure(values)
        else redis.sScan(key, cursor, args).flatMap(next => loop(next, values ++ next.values))
      redis.sScan(key, args).flatMap(first => loop(first, first.values.toSet))
    }

    def run: IO[Export] =
      for {
        (keys, pages) <- customerKeys
        profileKeys = keys.filter(_.endsWith(":profile")).distinct.sorted
        customers <- profileKeys.traverse { key =>
          val id: String = key.stripPrefix("customer:").stripSuffix(":profile")
          (profile(key), tags(s"customer:$id:tags", "*")).mapN((p, t) => Customer(id, p, t))
        }
        premium <- tags("customer:c1:tags", "premium-*")
        unknown <- keysMatching(KeyScanArgs("nobody:*", batch))
      } yield Export(
        customers,
        pages.size,
        pages.head.isFinished,
        pages.last.cursor,
        premium,
        unknown
      )
  }

  @Test
  def exportsDirectoryByFollowingScanCursors(): Unit = {
    val server: RedisServer = RedisServer.newRedisServer().start()
    try {
      given Logger[IO] = Slf4jLogger.getLoggerFromName[IO]("customer-directory")
      given Log[IO] = log4CatsInstance[IO]

      val connection: Resource[IO, RedisCommands[IO, String, String]] =
        for {
          client <- RedisClient[IO].from(s"redis://${server.getHost}:${server.getBindPort}")
          redis <- Redis[IO].fromClient(client, RedisCodec.Utf8)
        } yield redis

      val profiles: Map[String, Map[String, String]] = Map(
        "c1" -> Map("name" -> "Ada", "city" -> "London", "plan" -> "pro", "since" -> "2019", "lang" -> "en"),
        "c2" -> Map("name" -> "Linus", "city" -> "Helsinki", "plan" -> "free"),
        "c3" -> Map("name" -> "Grace", "city" -> "Arlington", "plan" -> "pro", "since" -> "2021")
      )
      val tags: Map[String, Set[String]] = Map(
        "c1" -> Set("premium-gold", "premium-early", "newsletter", "beta"),
        "c2" -> Set("newsletter"),
        "c3" -> Set("premium-silver", "beta", "events")
      )

      val exported: Export = connection
        .use { redis =>
          val seed: IO[Unit] =
            profiles.toList.traverse_((id, fields) => redis.hSet(s"customer:$id:profile", fields)) *>
              tags.toList.traverse_((id, values) => redis.sAdd(s"customer:$id:tags", values.toSeq*)) *>
              redis.set("orders:pending", "17") *>
              redis.set("config:currency", "EUR")
          seed *> new DirectoryExporter(redis).run
        }
        .unsafeRunSync()

      val expected: List[Customer] = List("c1", "c2", "c3").map(id => Customer(id, profiles(id), tags(id)))
      assertEquals(expected, exported.customers)
      assertTrue(exported.keyPages > 1, s"expected several SCAN pages, got ${exported.keyPages}")
      assertEquals(false, exported.firstCursorFinished)
      assertEquals("0", exported.lastCursor)
      assertEquals(Set("premium-gold", "premium-early"), exported.premiumTags)
      assertEquals(Nil, exported.unknownKeys)
    } finally {
      server.stop()
    }
  }
}
