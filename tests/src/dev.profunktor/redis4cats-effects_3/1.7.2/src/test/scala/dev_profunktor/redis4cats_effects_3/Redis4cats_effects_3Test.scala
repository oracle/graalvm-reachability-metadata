/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_profunktor.redis4cats_effects_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import dev.profunktor.redis4cats.{Redis, RedisCommands}
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.{KeyScanCursor, RedisCodec}
import dev.profunktor.redis4cats.effect.Log.NoOp._
import dev.profunktor.redis4cats.effects._
import io.lettuce.core.GeoArgs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

class Redis4cats_effects_3Test {
  @Test
  def utf8ResourceExercisesStringHashListAndSetCommands(): Unit = {
    Redis4cats_effects_3Test.ensureRedisServer()
    val prefix: String = Redis4cats_effects_3Test.nextPrefix("basic")

    runIO {
      Redis[IO].utf8(Redis4cats_effects_3Test.RedisUri).use { redis =>
        for {
          pong <- redis.ping
          _ <- IO(assertEquals("PONG", pong))
          _ <- redis.set(s"$prefix:string", "one")
          firstValue <- redis.get(s"$prefix:string")
          previousValue <- redis.getSet(s"$prefix:string", "two")
          currentValue <- redis.get(s"$prefix:string")
          stringLength <- redis.strLen(s"$prefix:string")
          _ <- redis.mSet(Map(s"$prefix:multi:one" -> "1", s"$prefix:multi:two" -> "2"))
          multiValues <- redis.mGet(Set(s"$prefix:multi:one", s"$prefix:multi:two", s"$prefix:multi:missing"))
          firstHashSet <- redis.hSet(s"$prefix:hash", "name", "redis4cats")
          duplicateHashSet <- redis.hSetNx(s"$prefix:hash", "name", "other")
          newHashFields <- redis.hSet(s"$prefix:hash", Map("language" -> "scala", "effect" -> "IO"))
          hashValues <- redis.hmGet(s"$prefix:hash", "name", "language", "effect")
          listLength <- redis.rPush(s"$prefix:list", "one", "two", "three")
          movedValue <- redis.rPopLPush(s"$prefix:list", s"$prefix:list:moved")
          remainingList <- redis.lRange(s"$prefix:list", 0, -1)
          movedList <- redis.lRange(s"$prefix:list:moved", 0, -1)
          setInsertions <- redis.sAdd(s"$prefix:set", "red", "green", "red")
          setMembers <- redis.sMembers(s"$prefix:set")
          greenMember <- redis.sIsMember(s"$prefix:set", "green")
          membership <- redis.sMisMember(s"$prefix:set", "red", "blue", "green")
        } yield {
          assertEquals(Some("one"), firstValue)
          assertEquals(Some("one"), previousValue)
          assertEquals(Some("two"), currentValue)
          assertEquals(3L, stringLength)
          assertEquals(Map(s"$prefix:multi:one" -> "1", s"$prefix:multi:two" -> "2"), multiValues)
          assertTrue(firstHashSet)
          assertFalse(duplicateHashSet)
          assertEquals(2L, newHashFields)
          assertEquals(Map("name" -> "redis4cats", "language" -> "scala", "effect" -> "IO"), hashValues)
          assertEquals(3L, listLength)
          assertEquals(Some("three"), movedValue)
          assertEquals(List("one", "two"), remainingList)
          assertEquals(List("three"), movedList)
          assertEquals(2L, setInsertions)
          assertEquals(Set("red", "green"), setMembers)
          assertTrue(greenMember)
          assertEquals(List(true, false, true), membership)
        }
      }
    }
  }

  @Test
  def reusableClientCoversKeysSortedSetsScriptsPipelinesAndTransactions(): Unit = {
    Redis4cats_effects_3Test.ensureRedisServer()
    val prefix: String = Redis4cats_effects_3Test.nextPrefix("advanced")

    runIO {
      RedisClient[IO].from(Redis4cats_effects_3Test.RedisUri).use { client =>
        Redis[IO].fromClient(client, RedisCodec.Utf8).use { redis =>
          for {
            _ <- redis.set(s"$prefix:key", "value")
            keyExists <- redis.exists(s"$prefix:key")
            keyType <- redis.typeOf(s"$prefix:key")
            ttlBeforeExpire <- redis.ttl(s"$prefix:key")
            expireSet <- redis.expire(s"$prefix:key", 30.seconds)
            ttlAfterExpire <- redis.ttl(s"$prefix:key")
            zAdded <- redis.zAdd(
              s"$prefix:zset",
              args = None,
              ScoreWithValue(Score(2.0), "two"),
              ScoreWithValue(Score(1.0), "one"),
              ScoreWithValue(Score(3.0), "three")
            )
            zRange <- redis.zRange(s"$prefix:zset", 0, -1)
            zScores <- redis.zRangeWithScores(s"$prefix:zset", 0, -1)
            integerScript <- redis.eval("return 42", ScriptOutputType.Integer)
            multiScript <- redis.eval("return {ARGV[1], ARGV[2]}", ScriptOutputType.Multi, Nil, List("hello", "redis"))
            sha <- redis.scriptLoad("return redis.call('get', KEYS[1])")
            shaValue <- redis.evalSha(sha, ScriptOutputType.Value, List(s"$prefix:key"))
            _ <- redis.set(s"$prefix:pipeline:source", "source-value")
            pipelineValues <- redis.pipeline { store =>
              List(
                redis.set(s"$prefix:pipeline:a", "A"),
                redis.get(s"$prefix:pipeline:source").flatMap(store.set("stored-source")),
                redis.set(s"$prefix:pipeline:b", "B")
              )
            }
            _ <- redis.set(s"$prefix:tx:source", "tx-value")
            transactionValues <- redis.transact { store =>
              List(
                redis.set(s"$prefix:tx:written", "written"),
                redis.get(s"$prefix:tx:source").flatMap(store.set("stored-tx-source"))
              )
            }
            pipelineA <- redis.get(s"$prefix:pipeline:a")
            pipelineB <- redis.get(s"$prefix:pipeline:b")
            transactionWritten <- redis.get(s"$prefix:tx:written")
          } yield {
            assertTrue(keyExists)
            assertEquals(Some(RedisType.String), keyType)
            assertTrue(ttlBeforeExpire.isEmpty)
            assertTrue(expireSet)
            assertTrue(ttlAfterExpire.exists(_ <= 30.seconds))
            assertEquals(3L, zAdded)
            assertEquals(List("one", "two", "three"), zRange)
            assertEquals(
              List(
                ScoreWithValue(Score(1.0), "one"),
                ScoreWithValue(Score(2.0), "two"),
                ScoreWithValue(Score(3.0), "three")
              ),
              zScores
            )
            assertEquals(42L, integerScript)
            assertEquals(List("hello", "redis"), multiScript)
            assertEquals("value", shaValue)
            assertEquals(Some(Some("source-value")), pipelineValues.get("stored-source"))
            assertEquals(Some("A"), pipelineA)
            assertEquals(Some("B"), pipelineB)
            assertEquals(Some(Some("tx-value")), transactionValues.get("stored-tx-source"))
            assertEquals(Some("written"), transactionWritten)
          }
        }
      }
    }
  }

  @Test
  def expirationArgumentsAndTypedScanCommands(): Unit = {
    Redis4cats_effects_3Test.ensureRedisServer()
    val prefix: String = Redis4cats_effects_3Test.nextPrefix("arguments")
    val expiringKey: String = s"$prefix:expiring"
    val scanStringOne: String = s"$prefix:scan:string:one"
    val scanStringTwo: String = s"$prefix:scan:string:two"
    val scanHash: String = s"$prefix:scan:hash"

    runIO {
      Redis[IO].utf8(Redis4cats_effects_3Test.RedisUri).use { redis =>
        for {
          setWithNx <- redis.set(
            expiringKey,
            "first",
            SetArgs(SetArg.Existence.Nx, SetArg.Ttl.Ex(20.seconds))
          )
          duplicateNx <- redis.set(expiringKey, "second", SetArgs(SetArg.Existence.Nx))
          valueAfterDuplicate <- redis.get(expiringKey)
          ttlAfterInitialSet <- redis.pttl(expiringKey)
          persistedValue <- redis.getEx(expiringKey, GetExArg.Persist)
          ttlAfterPersist <- redis.pttl(expiringKey)
          expireNxWithoutTtl <- redis.expire(expiringKey, 20.seconds, ExpireExistenceArg.Nx)
          expireNxWithTtl <- redis.expire(expiringKey, 30.seconds, ExpireExistenceArg.Nx)
          expireXxWithTtl <- redis.expire(expiringKey, 40.seconds, ExpireExistenceArg.Xx)
          updatedExisting <- redis.set(
            expiringKey,
            "updated",
            SetArgs(SetArg.Existence.Xx, SetArg.Ttl.Ex(20.seconds))
          )
          valueWithRefreshedTtl <- redis.getEx(expiringKey, GetExArg.Ex(25.seconds))
          ttlAfterGetEx <- redis.ttl(expiringKey)
          _ <- redis.set(scanStringOne, "one")
          _ <- redis.set(scanStringTwo, "two")
          _ <- redis.hSet(scanHash, "field", "value")
          scannedStringKeys <- scanAll(
            redis,
            KeyScanArgs(RedisType.String, s"$prefix:scan:*", 100L)
          )
          scannedHashKeys <- scanAll(
            redis,
            KeyScanArgs(RedisType.Hash, s"$prefix:scan:*", 100L)
          )
          unlinked <- redis.unlink(expiringKey, scanStringOne, scanStringTwo, scanHash)
        } yield {
          assertTrue(setWithNx)
          assertFalse(duplicateNx)
          assertEquals(Some("first"), valueAfterDuplicate)
          assertTrue(ttlAfterInitialSet.exists(_ <= 20.seconds))
          assertEquals(Some("first"), persistedValue)
          assertTrue(ttlAfterPersist.isEmpty)
          assertTrue(expireNxWithoutTtl)
          assertFalse(expireNxWithTtl)
          assertTrue(expireXxWithTtl)
          assertTrue(updatedExisting)
          assertEquals(Some("updated"), valueWithRefreshedTtl)
          assertTrue(ttlAfterGetEx.exists(_ <= 25.seconds))
          assertEquals(Set(scanStringOne, scanStringTwo), scannedStringKeys.toSet)
          assertEquals(Set(scanHash), scannedHashKeys.toSet)
          assertEquals(4L, unlinked)
        }
      }
    }
  }

  @Test
  def simpleResourceCoversGeoHyperLogLogAndBitmapCommands(): Unit = {
    Redis4cats_effects_3Test.ensureRedisServer()
    val prefix: String = Redis4cats_effects_3Test.nextPrefix("specialized")

    val buenosAires: GeoLocation[String] = GeoLocation(Longitude(-58.3816), Latitude(-34.6037), "Buenos Aires")
    val montevideo: GeoLocation[String] = GeoLocation(Longitude(-56.164532), Latitude(-34.901112), "Montevideo")
    val tokyo: GeoLocation[String] = GeoLocation(Longitude(139.6917), Latitude(35.6895), "Tokyo")

    runIO {
      Redis[IO].simple(Redis4cats_effects_3Test.RedisUri, RedisCodec.Utf8).use { redis =>
        for {
          _ <- redis.geoAdd(s"$prefix:geo", buenosAires, montevideo, tokyo)
          distance <- redis.geoDist(s"$prefix:geo", "Buenos Aires", "Tokyo", GeoArgs.Unit.km)
          nearby <- redis.geoRadius(s"$prefix:geo", GeoRadius(Longitude(-56.0), Latitude(-35.0), Distance(300.0)), GeoArgs.Unit.km)
          hashes <- redis.geoHash(s"$prefix:geo", "Tokyo", "Montevideo")
          positions <- redis.geoPos(s"$prefix:geo", "Montevideo")
          firstPfAdd <- redis.pfAdd(s"$prefix:hll:one", "alice", "bob", "alice")
          secondPfAdd <- redis.pfAdd(s"$prefix:hll:two", "carol")
          firstEstimate <- redis.pfCount(s"$prefix:hll:one")
          _ <- redis.pfMerge(s"$prefix:hll:merged", s"$prefix:hll:one", s"$prefix:hll:two")
          mergedEstimate <- redis.pfCount(s"$prefix:hll:merged")
          previousBit <- redis.setBit(s"$prefix:bits", 3, 1)
          currentBit <- redis.getBit(s"$prefix:bits", 3)
          bitCount <- redis.bitCount(s"$prefix:bits")
          firstSetBit <- redis.bitPos(s"$prefix:bits", state = true)
        } yield {
          assertTrue(distance > 18000.0)
          assertTrue(nearby.contains("Buenos Aires"))
          assertTrue(nearby.contains("Montevideo"))
          assertEquals(2, hashes.size)
          assertTrue(hashes.forall(_.nonEmpty))
          assertEquals(1, positions.size)
          assertTrue(Math.abs(positions.head.x - montevideo.lon.value) < 0.001)
          assertTrue(Math.abs(positions.head.y - montevideo.lat.value) < 0.001)
          assertEquals(1L, firstPfAdd)
          assertEquals(1L, secondPfAdd)
          assertTrue(firstEstimate >= 2L)
          assertTrue(mergedEstimate >= 3L)
          assertEquals(0L, previousBit)
          assertEquals(Some(1L), currentBit)
          assertEquals(1L, bitCount)
          assertEquals(3L, firstSetBit)
        }
      }
    }
  }

  private def scanAll(
      redis: RedisCommands[IO, String, String],
      keyScanArgs: KeyScanArgs
  ): IO[List[String]] = {
    def loop(cursor: Option[KeyScanCursor[String]], accumulated: List[String]): IO[List[String]] = {
      val nextCursor: IO[KeyScanCursor[String]] = cursor match {
        case Some(previous) => redis.scan(previous, keyScanArgs)
        case None           => redis.scan(keyScanArgs)
      }

      nextCursor.flatMap { next =>
        val keys: List[String] = accumulated ++ next.keys
        if (next.isFinished) {
          IO.pure(keys)
        } else {
          loop(Some(next), keys)
        }
      }
    }

    loop(None, List.empty)
  }

  private def runIO[A](ioa: IO[A]): A =
    ioa.timeout(35.seconds).unsafeRunSync()
}

object Redis4cats_effects_3Test {
  val RedisUri: String = "redis://localhost:6379"
  private val DockerImage: String = "valkey/valkey:7.2.6"
  private val Host: String = "127.0.0.1"
  private val Port: Int = 6379
  @volatile private var dockerProcess: Option[Process] = None
  private lazy val server: Unit = startServerIfNecessary()

  def ensureRedisServer(): Unit = server

  def nextPrefix(label: String): String =
    s"redis4cats:$label:${System.nanoTime()}"

  private def startServerIfNecessary(): Unit = synchronized {
    if (canConnect()) {
      return
    }

    val processBuilder: ProcessBuilder = new ProcessBuilder(
      "docker",
      "run",
      "--rm",
      "-p",
      s"$Port:6379",
      DockerImage,
      "--notify-keyspace-events",
      "KEA"
    )
    processBuilder.redirectErrorStream(true)
    processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT)
    val process: Process = processBuilder.start()
    dockerProcess = Some(process)
    Runtime.getRuntime.addShutdownHook(new Thread(() => stopDockerProcess()))

    val deadline: Long = System.nanoTime() + 10.seconds.toNanos
    var ready: Boolean = false
    while (!ready && System.nanoTime() < deadline) {
      if (!process.isAlive) {
        throw new IllegalStateException(s"Docker process for $DockerImage exited before accepting Redis connections")
      }
      ready = canConnect()
      if (!ready) {
        Thread.sleep(500L)
      }
    }
    if (!ready) {
      stopDockerProcess()
      throw new IllegalStateException(s"Timed out waiting for $DockerImage to accept Redis connections")
    }
  }

  private def canConnect(): Boolean = {
    val socket: Socket = new Socket()
    try {
      socket.connect(new InetSocketAddress(Host, Port), 10000)
      true
    } catch {
      case _: IOException => false
    } finally {
      socket.close()
    }
  }

  private def stopDockerProcess(): Unit = synchronized {
    dockerProcess.foreach { process =>
      process.destroy()
      if (!process.waitFor(10L, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        process.waitFor(10L, TimeUnit.SECONDS)
      }
    }
    dockerProcess = None
  }
}
