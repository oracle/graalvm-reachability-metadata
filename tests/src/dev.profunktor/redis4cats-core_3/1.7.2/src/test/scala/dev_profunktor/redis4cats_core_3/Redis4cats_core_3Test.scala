/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_profunktor.redis4cats_core_3

import dev.profunktor.redis4cats.codecs.Codecs
import dev.profunktor.redis4cats.codecs.splits.{SplitEpi, SplitMono}
import dev.profunktor.redis4cats.connection.{InvalidRedisURI, RedisURI}
import dev.profunktor.redis4cats.data.RedisCodec
import org.junit.jupiter.api.Assertions.{assertEquals, assertNotSame, assertTrue}
import org.junit.jupiter.api.Test

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class Redis4cats_core_3Test {
  @Test
  def test(): Unit = {
    println("This is just a placeholder, implement your test")
  }

  @Test
  def splitEpiAndSplitMonoComposeAndReversePureTransformations(): Unit = {
    val parseNumber: SplitEpi[String, Int] = SplitEpi[String, Int](_.toInt, _.toString)
    val numberToCounter: SplitEpi[Int, CounterValue] = SplitEpi[Int, CounterValue](value => CounterValue(value.toLong), _.value.toInt)
    val parseCounter: SplitEpi[String, CounterValue] = parseNumber.andThen(numberToCounter)
    val counterToString: SplitMono[CounterValue, String] = parseCounter.reverse

    assertEquals(CounterValue(7L), parseCounter("7"))
    assertEquals("7", parseCounter.reverseGet(CounterValue(7L)))
    assertEquals("7", counterToString(CounterValue(7L)))
    assertEquals(CounterValue(7L), counterToString.reverseGet("7"))
    assertNotSame(parseCounter, parseCounter.copy())
  }

  @Test
  def splitMonoCompositionPreservesBidirectionalConversions(): Unit = {
    val trimInput: SplitMono[String, String] = SplitMono[String, String](_.trim, identity)
    val lengthValue: SplitMono[String, StringLength] = SplitMono[String, StringLength](value => StringLength(value.length), length => "x" * length.value)
    val trimmedLength: SplitMono[String, StringLength] = trimInput.andThen(lengthValue)
    val lengthToString: SplitEpi[StringLength, String] = trimmedLength.reverse

    assertEquals(StringLength(5), trimmedLength("  redis  "))
    assertEquals("xxx", trimmedLength.reverseGet(StringLength(3)))
    assertEquals("xxxx", lengthToString(StringLength(4)))
    assertEquals(StringLength(4), lengthToString.reverseGet("data"))
    assertEquals("SplitMono", trimmedLength.productPrefix)
  }

  @Test
  def redisUriParsesValidUrisAndReportsInvalidOnes(): Unit = {
    val validUri: RedisURI = RedisURI.fromString("rediss://:secret@localhost:6380/2").toOption.get

    assertEquals("localhost", validUri.underlying.getHost)
    assertEquals(6380, validUri.underlying.getPort)
    assertEquals(2, validUri.underlying.getDatabase)
    assertEquals("secret", new String(validUri.underlying.getPassword))
    assertTrue(validUri.underlying.isSsl)

    val invalidUri: String = "ftp://localhost:6379"
    val invalidResult: Either[InvalidRedisURI, RedisURI] = RedisURI.fromString(invalidUri)
    assertTrue(invalidResult.isLeft)
    val error: InvalidRedisURI = invalidResult.swap.toOption.get
    assertEquals(invalidUri, error.uri)
    assertTrue(error.getMessage.nonEmpty)
  }

  @Test
  def derivedCodecTransformsValuesWhileKeepingBaseKeyEncoding(): Unit = {
    val counterSplit: SplitEpi[String, CounterValue] =
      SplitEpi[String, CounterValue](value => CounterValue(value.toLong), _.value.toString)
    val codec: RedisCodec[String, CounterValue] = Codecs.derive(RedisCodec.Utf8, counterSplit)

    assertEquals("counter:primary", utf8String(codec.underlying.encodeKey("counter:primary")))
    assertEquals("12", utf8String(codec.underlying.encodeValue(CounterValue(12L))))
    assertEquals("counter:secondary", codec.underlying.decodeKey(utf8Buffer("counter:secondary")))
    assertEquals(CounterValue(34L), codec.underlying.decodeValue(utf8Buffer("34")))
  }

  @Test
  def derivedCodecTransformsKeysAndValues(): Unit = {
    val cacheKeySplit: SplitEpi[String, CacheKey] =
      SplitEpi[String, CacheKey](CacheKey.parse, key => s"${key.namespace}:${key.id}")
    val usernameSplit: SplitEpi[String, Username] =
      SplitEpi[String, Username](Username.apply, _.value)
    val codec: RedisCodec[CacheKey, Username] =
      Codecs.derive(RedisCodec.Utf8, cacheKeySplit, usernameSplit)

    assertEquals("user:42", utf8String(codec.underlying.encodeKey(CacheKey("user", 42L))))
    assertEquals("alice", utf8String(codec.underlying.encodeValue(Username("alice"))))
    assertEquals(CacheKey("session", 7L), codec.underlying.decodeKey(utf8Buffer("session:7")))
    assertEquals(Username("bob"), codec.underlying.decodeValue(utf8Buffer("bob")))
  }

  private def utf8Buffer(value: String): ByteBuffer =
    ByteBuffer.wrap(value.getBytes(StandardCharsets.UTF_8))

  private def utf8String(buffer: ByteBuffer): String = {
    val readableBuffer: ByteBuffer = buffer.asReadOnlyBuffer()
    val bytes: Array[Byte] = new Array[Byte](readableBuffer.remaining())
    readableBuffer.get(bytes)
    new String(bytes, StandardCharsets.UTF_8)
  }
}

final case class CounterValue(value: Long)

final case class StringLength(value: Int)

final case class CacheKey(namespace: String, id: Long)

object CacheKey {
  def parse(value: String): CacheKey = {
    val parts: Array[String] = value.split(":", 2)
    CacheKey(parts(0), parts(1).toLong)
  }
}

final case class Username(value: String)
