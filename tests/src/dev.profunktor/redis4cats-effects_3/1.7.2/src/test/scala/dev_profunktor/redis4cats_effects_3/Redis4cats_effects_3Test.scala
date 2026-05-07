/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_profunktor.redis4cats_effects_3

import dev.profunktor.redis4cats.algebra.BitCommandOperation
import dev.profunktor.redis4cats.codecs.Codecs
import dev.profunktor.redis4cats.codecs.splits.{SplitEpi, SplitMono, stringIntEpi}
import dev.profunktor.redis4cats.config._
import dev.profunktor.redis4cats.connection.RedisURI
import dev.profunktor.redis4cats.data.{NodeId, RedisChannel, RedisCodec, RedisPattern, RedisPatternEvent, ReadFrom}
import dev.profunktor.redis4cats.effects._
import io.lettuce.core.protocol.CommandArgs
import io.lettuce.core.{BitFieldArgs, CompositeArgument, GeoArgs}
import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.Test

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.time.Instant
import scala.concurrent.duration._

class Redis4cats_effects_3Test {

  @Test
  def redisEffectModelsPreserveValuesAndBuilderOverloads(): Unit = {
    val location: GeoLocation[String] = GeoLocation(Longitude(13.4050), Latitude(52.5200), "berlin")
    val radius: GeoRadius = GeoRadius(Longitude(13.0), Latitude(52.0), Distance(10.5))
    val result: GeoRadiusResult[String] =
      GeoRadiusResult("berlin", Distance(1.2), GeoHash(123456789L), GeoCoordinate(13.405, 52.52))

    assertEquals(Longitude(13.4050), location.lon)
    assertEquals(Latitude(52.5200), location.lat)
    assertEquals("berlin", location.value)
    assertEquals(Distance(10.5), radius.dist)
    assertEquals(GeoCoordinate(13.405, 52.52), result.coordinate)
    assertEquals(GeoRadiusKeyStorage("nearby", 3L, GeoArgs.Sort.asc), GeoRadiusKeyStorage("nearby", 3L, GeoArgs.Sort.asc))
    assertEquals(GeoRadiusDistStorage("distances", 4L, GeoArgs.Sort.desc), GeoRadiusDistStorage("distances", 4L, GeoArgs.Sort.desc))

    assertEquals(CopyArgs(Some(2L), None), CopyArgs(2L))
    assertEquals(CopyArgs(None, Some(true)), CopyArgs(replace = true))
    assertEquals(CopyArgs(Some(3L), Some(false)), CopyArgs(3L, replace = false))

    val restoreArgs: RestoreArgs = RestoreArgs().ttl(1000L).replace(true).absttl(false).idleTime(25L)
    assertEquals(RestoreArgs(Some(1000L), Some(true), Some(false), Some(25L)), restoreArgs)

    val ttl: SetArg.Ttl = SetArg.Ttl.Px(250.millis)
    assertEquals(SetArgs(Some(SetArg.Existence.Nx), None), SetArgs(SetArg.Existence.Nx))
    assertEquals(SetArgs(None, Some(ttl)), SetArgs(ttl))
    assertEquals(SetArgs(Some(SetArg.Existence.Xx), Some(ttl)), SetArgs(SetArg.Existence.Xx, ttl))

    assertEquals(GetExArg.Ex(1.second), GetExArg.Ex(1.second))
    assertEquals(GetExArg.ExAt(Instant.parse("2024-01-01T00:00:00Z")), GetExArg.ExAt(Instant.parse("2024-01-01T00:00:00Z")))
    assertEquals(GetExArg.Persist, GetExArg.Persist)
  }

  @Test
  def sortedSetCommandModelsPreserveScoresRangesAndLimits(): Unit = {
    val first: ScoreWithValue[String] = ScoreWithValue(Score(10.25), "alpha")
    val second: ScoreWithValue[String] = first.copy(score = Score(11.5), value = "beta")

    assertEquals(Score(10.25), first.score)
    assertEquals("alpha", first.value)
    assertEquals(ScoreWithValue(Score(11.5), "beta"), second)

    val scoreRange: ZRange[Double] = ZRange(1.5, 10.0)
    val lexicographicRange: ZRange[String] = ZRange("a", "z")
    val limit: RangeLimit = RangeLimit(2L, 5L)

    assertEquals(1.5d, scoreRange.start, 0.0d)
    assertEquals(10.0d, scoreRange.end, 0.0d)
    assertEquals("a", lexicographicRange.start)
    assertEquals("z", lexicographicRange.end)
    assertEquals(2L, limit.offset)
    assertEquals(5L, limit.count)
    assertEquals(RangeLimit(0L, 10L), limit.copy(offset = 0L, count = 10L))
  }

  @Test
  def redisEnumsAndAlgebraicArgumentsExposeExpectedPublicValues(): Unit = {
    assertEquals(io.lettuce.core.FlushMode.SYNC, FlushMode.Sync.asJava)
    assertEquals(io.lettuce.core.FlushMode.ASYNC, FlushMode.Async.asJava)

    assertEquals(Some(RedisType.String), RedisType.fromString("string"))
    assertEquals(Some(RedisType.List), RedisType.fromString("list"))
    assertEquals(Some(RedisType.Set), RedisType.fromString("set"))
    assertEquals(Some(RedisType.SortedSet), RedisType.fromString("zset"))
    assertEquals(Some(RedisType.Hash), RedisType.fromString("hash"))
    assertEquals(Some(RedisType.Stream), RedisType.fromString("stream"))
    assertEquals(None, RedisType.fromString("unknown"))
    assertEquals(List(RedisType.String, RedisType.List, RedisType.Set, RedisType.SortedSet, RedisType.Hash, RedisType.Stream), RedisType.all)

    assertEquals(FunctionRestoreMode.Append, FunctionRestoreMode.Append)
    assertEquals(FunctionRestoreMode.Flush, FunctionRestoreMode.Flush)
    assertEquals(FunctionRestoreMode.Replace, FunctionRestoreMode.Replace)
    assertEquals(ExpireExistenceArg.Nx, ExpireExistenceArg.Nx)
    assertEquals(ExpireExistenceArg.Xx, ExpireExistenceArg.Xx)
    assertEquals(ExpireExistenceArg.Gt, ExpireExistenceArg.Gt)
    assertEquals(ExpireExistenceArg.Lt, ExpireExistenceArg.Lt)

    val operations: List[BitCommandOperation] = List(
      BitCommandOperation.Get(BitFieldArgs.unsigned(8), 0),
      BitCommandOperation.SetSigned(1, 2L),
      BitCommandOperation.SetUnsigned(2, 3L, bits = 4),
      BitCommandOperation.IncrSignedBy(3, 4L, bits = 5),
      BitCommandOperation.IncrUnsignedBy(4, 5L, bits = 6),
      BitCommandOperation.Overflow(BitCommandOperation.Overflows.WRAP),
      BitCommandOperation.Overflow(BitCommandOperation.Overflows.SAT),
      BitCommandOperation.Overflow(BitCommandOperation.Overflows.FAIL)
    )

    assertEquals(8, operations.size)
    assertEquals(Set("WRAP", "SAT", "FAIL"), BitCommandOperation.Overflows.values.map(_.toString).toSet)
  }

  @Test
  def scanArgumentsRenderToLettuceCommandArguments(): Unit = {
    val scanCommand: String = render(ScanArgs("user:*", 42L).underlying)
    assertTrue(scanCommand.contains("MATCH"), scanCommand)
    assertTrue(scanCommand.contains("COUNT 42"), scanCommand)

    val countOnly: String = render(ScanArgs(5L).underlying)
    assertFalse(countOnly.contains("MATCH"), countOnly)
    assertTrue(countOnly.contains("COUNT 5"), countOnly)

    val keyScanCommand: String = render(KeyScanArgs(RedisType.Hash, "hash:*", 10L).underlying)
    assertTrue(keyScanCommand.contains("MATCH"), keyScanCommand)
    assertTrue(keyScanCommand.contains("COUNT 10"), keyScanCommand)
    assertTrue(keyScanCommand.contains("TYPE hash"), keyScanCommand)

    val typedOnly: String = render(KeyScanArgs(RedisType.SortedSet).underlying)
    assertEquals("TYPE zset", typedOnly)
  }

  @Test
  def redisCodecsEncodeDecodeAndDeriveTypedCodecs(): Unit = {
    val utf8: RedisCodec[String, String] = RedisCodec.Utf8
    assertEquals("café", decodeUtf8(utf8.underlying.encodeValue("café")))
    assertEquals("value", utf8.underlying.decodeValue(ByteBuffer.wrap("value".getBytes(StandardCharsets.UTF_8))))

    val bytes: Array[Byte] = Array[Byte](1, 2, 3, 4)
    val decodedBytes: Array[Byte] = RedisCodec.Bytes.underlying.decodeValue(readable(RedisCodec.Bytes.underlying.encodeValue(bytes)))
    assertArrayEquals(bytes, decodedBytes)

    val gzip: RedisCodec[String, String] = RedisCodec.gzip(RedisCodec.Utf8)
    val deflate: RedisCodec[String, String] = RedisCodec.deflate(RedisCodec.Utf8)
    assertEquals("compressed value", gzip.underlying.decodeValue(readable(gzip.underlying.encodeValue("compressed value"))))
    assertEquals("deflated value", deflate.underlying.decodeValue(readable(deflate.underlying.encodeValue("deflated value"))))
    assertEquals("plain-key", decodeUtf8(gzip.underlying.encodeKey("plain-key")))

    val intValueCodec: RedisCodec[String, Int] = Codecs.derive(RedisCodec.Utf8, stringIntEpi)
    assertEquals("42", decodeUtf8(intValueCodec.underlying.encodeValue(42)))
    assertEquals(123, intValueCodec.underlying.decodeValue(ByteBuffer.wrap("123".getBytes(StandardCharsets.UTF_8))))
    assertEquals(0, intValueCodec.underlying.decodeValue(ByteBuffer.wrap("not-an-int".getBytes(StandardCharsets.UTF_8))))

    val keyEpi: SplitEpi[String, Long] = SplitEpi[String, Long](_.toLong, _.toString)
    val valueEpi: SplitEpi[String, Boolean] = SplitEpi[String, Boolean](_ == "yes", (value: Boolean) => if (value) "yes" else "no")
    val fullyDerived: RedisCodec[Long, Boolean] = Codecs.derive(RedisCodec.Utf8, keyEpi, valueEpi)
    assertEquals("7", decodeUtf8(fullyDerived.underlying.encodeKey(7L)))
    assertEquals(9L, fullyDerived.underlying.decodeKey(ByteBuffer.wrap("9".getBytes(StandardCharsets.UTF_8))))
    assertEquals("yes", decodeUtf8(fullyDerived.underlying.encodeValue(true)))
    assertFalse(fullyDerived.underlying.decodeValue(ByteBuffer.wrap("no".getBytes(StandardCharsets.UTF_8))))
  }

  @Test
  def splitEpiAndSplitMonoComposeAndReverseMappings(): Unit = {
    val parseInt: SplitEpi[String, Int] = SplitEpi[String, Int](_.toInt, _.toString)
    val intToEven: SplitEpi[Int, Boolean] = SplitEpi[Int, Boolean](_ % 2 == 0, (value: Boolean) => if (value) 2 else 1)
    val parseEven: SplitEpi[String, Boolean] = parseInt.andThen(intToEven)

    assertTrue(parseEven("4"))
    assertFalse(parseEven("5"))
    assertEquals("2", parseEven.reverseGet(true))
    assertEquals("10", parseInt.reverse(10))

    val intToLong: SplitMono[Int, Long] = SplitMono[Int, Long](_.toLong, _.toInt)
    val longToString: SplitMono[Long, String] = SplitMono[Long, String](long => s"n-$long", _.stripPrefix("n-").toLong)
    val intToString: SplitMono[Int, String] = intToLong.andThen(longToString)

    assertEquals("n-8", intToString(8))
    assertEquals(8, intToString.reverseGet("n-8"))
    assertEquals("11", parseInt.reverse(11))
  }

  @Test
  def coreDataTypesConfigurationAndUrisExposeStablePublicState(): Unit = {
    assertEquals("updates", RedisChannel("updates").underlying)
    assertEquals("orders:*", RedisPattern("orders:*").underlying)
    assertEquals(RedisPatternEvent("orders:*", "orders:created", "payload"), RedisPatternEvent("orders:*", "orders:created", "payload"))
    assertEquals("node-a", NodeId("node-a").value)

    assertEquals(io.lettuce.core.ReadFrom.UPSTREAM, ReadFrom.Upstream)
    assertEquals(io.lettuce.core.ReadFrom.UPSTREAM_PREFERRED, ReadFrom.UpstreamPreferred)
    assertEquals(io.lettuce.core.ReadFrom.LOWEST_LATENCY, ReadFrom.LowestLatency)
    assertEquals(io.lettuce.core.ReadFrom.REPLICA, ReadFrom.Replica)
    assertEquals(io.lettuce.core.ReadFrom.REPLICA_PREFERRED, ReadFrom.ReplicaPreferred)

    val defaultShutdown: ShutdownConfig = ShutdownConfig()
    val customShutdown: ShutdownConfig = ShutdownConfig(1.second, 3.seconds)
    val customStrategy: TopologyViewRefreshStrategy = Both(Periodic(2.seconds), Adaptive(4.seconds))

    assertEquals(ShutdownConfig(0.seconds, 2.seconds), defaultShutdown)
    assertEquals(ShutdownConfig(1.second, 3.seconds), customShutdown)
    assertEquals(Both(Periodic(2.seconds), Adaptive(4.seconds)), customStrategy)
    assertEquals(NoRefresh, NoRefresh)

    val uri: RedisURI = RedisURI.unsafeFromString("redis://localhost:6379/2")
    assertEquals("localhost", uri.underlying.getHost)
    assertEquals(6379, uri.underlying.getPort)
    assertEquals(2, uri.underlying.getDatabase)
    assertEquals(uri.underlying, RedisURI.fromUnderlying(uri.underlying).underlying)
    assertTrue(RedisURI.fromString("redis://localhost:6379").isRight)
    assertTrue(RedisURI.fromString("\u0000").isLeft)
  }

  @Test
  def scriptOutputFactoriesProduceTypedOutputDescriptors(): Unit = {
    val booleanOutput: ScriptOutputType.Aux[String, Boolean] = ScriptOutputType.Boolean[String]
    val integerOutput: ScriptOutputType.Aux[String, Long] = ScriptOutputType.Integer[String]
    val valueOutput: ScriptOutputType.Aux[String, String] = ScriptOutputType.Value[String]
    val multiOutput: ScriptOutputType.Aux[String, List[String]] = ScriptOutputType.Multi[String]
    val statusOutput: ScriptOutputType.Aux[String, String] = ScriptOutputType.Status[String]

    assertNotNull(booleanOutput)
    assertNotNull(integerOutput)
    assertNotNull(valueOutput)
    assertNotNull(multiOutput)
    assertNotNull(statusOutput)
  }

  private def render(argument: CompositeArgument): String = {
    val commandArgs: CommandArgs[String, String] = new CommandArgs[String, String](RedisCodec.Utf8.underlying)
    argument.build(commandArgs)
    commandArgs.toCommandString
  }

  private def readable(buffer: ByteBuffer): ByteBuffer = {
    val duplicate: ByteBuffer = buffer.duplicate()
    duplicate.rewind()
    duplicate
  }

  private def decodeUtf8(buffer: ByteBuffer): String =
    StandardCharsets.UTF_8.decode(readable(buffer)).toString
}
