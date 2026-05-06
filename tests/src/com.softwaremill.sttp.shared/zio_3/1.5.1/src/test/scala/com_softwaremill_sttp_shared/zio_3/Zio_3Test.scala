/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_shared.zio_3

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sttp.capabilities.StreamMaxLengthExceededException
import sttp.capabilities.Streams
import sttp.capabilities.zio.ZioStreams
import zio.Chunk
import zio.Runtime
import zio.Unsafe
import zio.ZIO
import zio.stream.Stream
import zio.stream.ZStream

class Zio_3Test {
  @Test
  def zioStreamsExposeTypedBinaryStreamAndPipeCapabilities(): Unit = {
    val streamsCapability: Streams[ZioStreams] = ZioStreams
    val binaryStream: ZioStreams.BinaryStream = ZStream.fromIterable(List[Byte](1, 2, 3))
    val limitToThreeBytes: ZioStreams.Pipe[Byte, Byte] = stream => ZioStreams.limitBytes(stream, 3L)

    val collected: Chunk[Byte] = unsafeRun(limitToThreeBytes(binaryStream).runCollect)

    assertNotNull(streamsCapability)
    assertArrayEquals(Array[Byte](1, 2, 3), collected.toArray)
  }

  @Test
  def limitBytesPreservesEmptyStreamsAndStreamsUnderTheLimit(): Unit = {
    val empty: Stream[Throwable, Byte] = ZStream.empty
    val chunked: Stream[Throwable, Byte] = ZStream.fromChunks(
      Chunk[Byte](1, 2),
      Chunk[Byte](3, 4),
      Chunk[Byte](5)
    )

    assertTrue(unsafeRun(ZioStreams.limitBytes(empty, 0L).runCollect).isEmpty)
    assertArrayEquals(
      Array[Byte](1, 2, 3, 4, 5),
      unsafeRun(ZioStreams.limitBytes(chunked, 5L).runCollect).toArray
    )
  }

  @Test
  def limitBytesAllowsExactlyTheMaximumNumberOfBytes(): Unit = {
    val exactLimit: Stream[Throwable, Byte] = ZStream.fromChunks(
      Chunk[Byte](10, 11),
      Chunk[Byte](12)
    )

    val collected: Chunk[Byte] = unsafeRun(ZioStreams.limitBytes(exactLimit, 3L).runCollect)

    assertArrayEquals(Array[Byte](10, 11, 12), collected.toArray)
  }

  @Test
  def limitBytesFailsWhenAccumulatedChunksExceedTheMaximum(): Unit = {
    val stream: Stream[Throwable, Byte] = ZStream.fromChunks(
      Chunk[Byte](1, 2),
      Chunk[Byte](3, 4)
    )

    val result: Either[Throwable, Chunk[Byte]] = unsafeRun(ZioStreams.limitBytes(stream, 3L).runCollect.either)

    assertTrue(result.isLeft)
    val exceeded: StreamMaxLengthExceededException = assertInstanceOf(
      classOf[StreamMaxLengthExceededException],
      failure(result)
    )
    assertEquals(3L, exceeded.maxBytes)
    assertEquals("Stream length limit of 3 bytes exceeded", exceeded.getMessage)
  }

  @Test
  def limitBytesFailsImmediatelyWhenASingleChunkIsTooLarge(): Unit = {
    val stream: Stream[Throwable, Byte] = ZStream.fromChunk(Chunk[Byte](1, 2, 3))

    val result: Either[Throwable, Chunk[Byte]] = unsafeRun(ZioStreams.limitBytes(stream, 2L).runCollect.either)

    assertTrue(result.isLeft)
    val exceeded: StreamMaxLengthExceededException = assertInstanceOf(
      classOf[StreamMaxLengthExceededException],
      failure(result)
    )
    assertEquals(2L, exceeded.maxBytes)
  }

  @Test
  def limitBytesPropagatesUpstreamFailuresWithoutWrappingThem(): Unit = {
    val upstreamFailure: IllegalStateException = new IllegalStateException("upstream failed")
    val stream: Stream[Throwable, Byte] = ZStream.fromChunk(Chunk[Byte](1)) ++ ZStream.fail(upstreamFailure)

    val result: Either[Throwable, Chunk[Byte]] = unsafeRun(ZioStreams.limitBytes(stream, 10L).runCollect.either)

    assertTrue(result.isLeft)
    assertSame(upstreamFailure, failure(result))
  }

  @Test
  def limitBytesTreatsNegativeLimitsAsExceededForNonEmptyStreams(): Unit = {
    val nonEmpty: Stream[Throwable, Byte] = ZStream.fromIterable(List[Byte](42))

    val result: Either[Throwable, Chunk[Byte]] = unsafeRun(ZioStreams.limitBytes(nonEmpty, -1L).runCollect.either)

    assertTrue(result.isLeft)
    val exceeded: StreamMaxLengthExceededException = assertInstanceOf(
      classOf[StreamMaxLengthExceededException],
      failure(result)
    )
    assertEquals(-1L, exceeded.maxBytes)
  }

  @Test
  def limitBytesWorksAfterOrdinaryZioStreamTransformations(): Unit = {
    val transformed: Stream[Throwable, Byte] = ZStream
      .fromIterable(1 to 6)
      .map(value => value.toByte)
      .filter(byte => byte % 2 == 0)

    val collected: Chunk[Byte] = unsafeRun(ZioStreams.limitBytes(transformed, 3L).runCollect)

    assertArrayEquals(Array[Byte](2, 4, 6), collected.toArray)
  }

  private def failure[A](result: Either[Throwable, A]): Throwable = result match {
    case Left(error) => error
    case Right(_)    => throw new AssertionError("Expected stream to fail")
  }

  private def unsafeRun[A](effect: ZIO[Any, Throwable, A]): A = {
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
  }
}
