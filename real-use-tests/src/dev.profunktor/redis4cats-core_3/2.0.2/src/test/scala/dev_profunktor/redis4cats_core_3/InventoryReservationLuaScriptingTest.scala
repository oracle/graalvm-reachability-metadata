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
import dev.profunktor.redis4cats.effects.ScriptOutputType
import dev.profunktor.redis4cats.extensions.luaScripting.LuaScript
import dev.profunktor.redis4cats.extensions.luaScripting.LuaScriptingExtensions
import dev.profunktor.redis4cats.log4cats.log4CatsInstance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

/**
 * Runs server-side Lua scripts through the `Scripting` commands and the `luaScripting` extension.
 *
 * A shop reserves stock atomically: a Lua script checks the remaining
 * quantity and decrements it in one step, so two orders can never both take
 * the last item. The script is loaded once and invoked by its SHA-1 digest;
 * smaller scripts restock, describe and relabel items with each
 * `ScriptOutputType`. After an operator flushes the script cache, a bare
 * EVALSHA fails with NOSCRIPT while `evalLua` transparently resends the
 * script source.
 */
class InventoryReservationLuaScriptingTest {

  /** What the shop observes while reserving and restocking items. */
  private final case class Observed(
      loadedSha: String,
      localDigest: String,
      cachedBeforeFlush: List[Boolean],
      firstReservation: Boolean,
      secondReservation: Boolean,
      remainingAfterReservations: Option[String],
      restocked: Long,
      description: List[String],
      readOnlyStock: String,
      relabelStatus: String,
      labelAfterRelabel: Option[String],
      cachedAfterFlush: List[Boolean],
      evalShaAfterFlush: Either[Throwable, Boolean],
      evalLuaAfterFlush: Boolean,
      remainingAfterEvalLua: Option[String]
  )

  /** Stores stock counts under `stock:<sku>` and labels under `label:<sku>`. */
  private final class Inventory(redis: RedisCommands[IO, String, String]) {

    val reserveSource: String =
      """local stock = tonumber(redis.call('GET', KEYS[1]) or '0')
        |local wanted = tonumber(ARGV[1])
        |if stock >= wanted then
        |  redis.call('DECRBY', KEYS[1], wanted)
        |  return 1
        |end
        |return 0""".stripMargin

    private val restockSource: String = "return redis.call('INCRBY', KEYS[1], ARGV[1])"

    private val describeSource: String = "return {KEYS[1], ARGV[1], redis.call('GET', KEYS[1])}"

    private val stockSource: String = "return redis.call('GET', KEYS[1])"

    private val relabelSource: String = "return redis.call('SET', KEYS[1], ARGV[1])"

    def stockKey(sku: String): String = s"stock:$sku"

    def reserve(sha: String, sku: String, quantity: Int): IO[Boolean] =
      redis.evalSha(sha, ScriptOutputType.Boolean, List(stockKey(sku)), List(quantity.toString))

    def restock(sku: String, quantity: Int): IO[Long] =
      redis.eval(restockSource, ScriptOutputType.Integer, List(stockKey(sku)), List(quantity.toString))

    def describe(sku: String, warehouse: String): IO[List[String]] =
      redis.eval(describeSource, ScriptOutputType.Multi, List(stockKey(sku)), List(warehouse))

    def stockReadOnly(sku: String): IO[String] =
      redis.evalReadOnly(stockSource, ScriptOutputType.Value, List(stockKey(sku)))

    def relabel(sku: String, label: String): IO[String] =
      redis.eval(relabelSource, ScriptOutputType.Status, List(s"label:$sku"), List(label))

    def reserveWithFallback(script: LuaScript, sku: String, quantity: Int): IO[Boolean] =
      redis.evalLua(script, ScriptOutputType.Boolean, List(stockKey(sku)), List(quantity.toString))
  }

  @Test
  def reservesStockAtomicallyWithLuaScripts(): Unit = {
    val server: RedisServer = RedisServer.newRedisServer().start()
    try {
      given Logger[IO] = Slf4jLogger.getLoggerFromName[IO]("inventory-reservation")
      given Log[IO] = log4CatsInstance[IO]

      val connection: Resource[IO, RedisCommands[IO, String, String]] =
        for {
          client <- RedisClient[IO].from(s"redis://${server.getHost}:${server.getBindPort}")
          redis <- Redis[IO].fromClient(client, RedisCodec.Utf8)
        } yield redis

      val unknownSha: String = "0" * 40

      val observed: Observed = connection
        .use { redis =>
          val inventory: Inventory = new Inventory(redis)
          for {
            _ <- redis.set(inventory.stockKey("lamp"), "5")
            sha <- redis.scriptLoad(inventory.reserveSource)
            digest <- redis.digest(inventory.reserveSource)
            cached <- redis.scriptExists(sha, unknownSha)
            first <- inventory.reserve(sha, "lamp", 3)
            second <- inventory.reserve(sha, "lamp", 3)
            remaining <- redis.get(inventory.stockKey("lamp"))
            restocked <- inventory.restock("lamp", 4)
            description <- inventory.describe("lamp", "north")
            readOnly <- inventory.stockReadOnly("lamp")
            relabelStatus <- inventory.relabel("lamp", "Desk lamp")
            label <- redis.get("label:lamp")
            _ <- redis.scriptFlush
            cachedAfterFlush <- redis.scriptExists(sha)
            evalShaAfterFlush <- inventory.reserve(sha, "lamp", 1).attempt
            script <- LuaScript.make[IO](redis)(inventory.reserveSource)
            fallback <- inventory.reserveWithFallback(script, "lamp", 2)
            remainingAfterEvalLua <- redis.get(inventory.stockKey("lamp"))
          } yield Observed(
            sha,
            digest,
            cached,
            first,
            second,
            remaining,
            restocked,
            description,
            readOnly,
            relabelStatus,
            label,
            cachedAfterFlush,
            evalShaAfterFlush,
            fallback,
            remainingAfterEvalLua
          )
        }
        .unsafeRunSync()

      assertEquals(observed.localDigest, observed.loadedSha)
      assertEquals(List(true, false), observed.cachedBeforeFlush)
      assertEquals(true, observed.firstReservation)
      assertEquals(false, observed.secondReservation)
      assertEquals(Some("2"), observed.remainingAfterReservations)
      assertEquals(6L, observed.restocked)
      assertEquals(List("stock:lamp", "north", "6"), observed.description)
      assertEquals("6", observed.readOnlyStock)
      assertEquals("OK", observed.relabelStatus)
      assertEquals(Some("Desk lamp"), observed.labelAfterRelabel)
      assertEquals(List(false), observed.cachedAfterFlush)
      assertTrue(
        observed.evalShaAfterFlush.left.exists(error => String.valueOf(error.getMessage).contains("NOSCRIPT")),
        observed.evalShaAfterFlush.toString
      )
      assertEquals(true, observed.evalLuaAfterFlush)
      assertEquals(Some("4"), observed.remainingAfterEvalLua)
    } finally {
      server.stop()
    }
  }
}
