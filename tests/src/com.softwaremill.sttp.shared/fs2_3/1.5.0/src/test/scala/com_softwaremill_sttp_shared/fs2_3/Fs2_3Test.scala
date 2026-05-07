/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_shared.fs2_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.Chunk
import fs2.Stream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sttp.capabilities.StreamMaxLengthExceededException
import sttp.capabilities.fs2.Fs2Streams


class Fs2_3Test {
  @Test
  def fs2StreamsExposeTypedBinaryStreamAndPipeAliases(): Unit = {
    val streams: Fs2Streams[IO] = Fs2Streams[IO]
    val binary: streams.BinaryStream = Stream.emits(Seq[Byte](1, 2, 3)).covary[IO]
    val pipe: streams.Pipe[Byte, String] = _.map(byte => s"byte-$byte")

    assertEquals(Right(List[Byte](1, 2, 3)), collect(binary))
    assertEquals(Right(List("byte-1", "byte-2", "byte-3")), collect(pipe(binary)))
  }

  @Test
  def limitBytesAllowsEmptyStreamsExactSizeAndSmallerStreams(): Unit = {
    val empty: Stream[IO, Byte] = Stream.empty.covary[IO]
    val exact: Stream[IO, Byte] = chunk(1, 2) ++ chunk(3, 4)
    val smaller: Stream[IO, Byte] = chunk(9, 10)

    assertEquals(Right(List.empty[Byte]), collect(Fs2Streams.limitBytes(empty, maxBytes = 0L)))
    assertEquals(Right(List[Byte](1, 2, 3, 4)), collect(Fs2Streams.limitBytes(exact, maxBytes = 4L)))
    assertEquals(Right(List[Byte](9, 10)), collect(Fs2Streams.limitBytes(smaller, maxBytes = 5L)))
  }

  @Test
  def limitBytesPreservesChunkOrderingAndCanBeUsedAsAnFs2Pipe(): Unit = {
    val input: Stream[IO, Byte] = chunk(1, 2) ++ chunk(3) ++ chunk(4, 5)
    val limitingPipe: fs2.Pipe[IO, Byte, Byte] = stream => Fs2Streams.limitBytes(stream, maxBytes = 5L)
    val decoded: Either[Throwable, List[String]] = input
      .through(limitingPipe)
      .map(byte => s"item-${byte.toInt}")
      .compile
      .toList
      .attempt
      .unsafeRunSync()

    assertEquals(Right(List("item-1", "item-2", "item-3", "item-4", "item-5")), decoded)
  }

  @Test
  def limitBytesRaisesStreamMaxLengthExceededWhenASingleChunkIsTooLarge(): Unit = {
    val result: Either[Throwable, List[Byte]] = collect(Fs2Streams.limitBytes(chunk(1, 2, 3), maxBytes = 2L))

    assertLimitExceeded(result, expectedMaxBytes = 2L)
  }

  @Test
  def limitBytesRaisesStreamMaxLengthExceededWhenAccumulatedChunksExceedTheLimit(): Unit = {
    val input: Stream[IO, Byte] = chunk(1, 2) ++ chunk(3, 4)
    val result: Either[Throwable, List[Byte]] = collect(Fs2Streams.limitBytes(input, maxBytes = 3L))

    assertLimitExceeded(result, expectedMaxBytes = 3L)
  }

  @Test
  def limitBytesTreatsZeroAsOnlyAllowingEmptyStreams(): Unit = {
    val result: Either[Throwable, List[Byte]] = collect(Fs2Streams.limitBytes(chunk(1), maxBytes = 0L))

    assertLimitExceeded(result, expectedMaxBytes = 0L)
  }

  @Test
  def limitBytesWithNegativeMaximumFailsBeforePullingTheStream(): Unit = {
    var streamPulled: Boolean = false
    val input: Stream[IO, Byte] = Stream.eval(IO {
      streamPulled = true
      1.toByte
    })

    val result: Either[Throwable, List[Byte]] = collect(Fs2Streams.limitBytes(input, maxBytes = -1L))

    assertLimitExceeded(result, expectedMaxBytes = -1L)
    assertFalse(streamPulled)
  }

  @Test
  def limitBytesPropagatesUpstreamFailuresWhenTheLimitHasNotBeenExceeded(): Unit = {
    val boom: IllegalStateException = new IllegalStateException("upstream")
    val failing: Stream[IO, Byte] = chunk(1, 2) ++ Stream.raiseError[IO](boom)
    val result: Either[Throwable, List[Byte]] = collect(Fs2Streams.limitBytes(failing, maxBytes = 10L))

    assertEquals(Left(boom), result)
    assertSame(boom, result.left.toOption.get)
  }

  private def chunk(bytes: Byte*): Stream[IO, Byte] = Stream.chunk(Chunk.array(bytes.toArray)).covary[IO]

  private def collect[A](stream: Stream[IO, A]): Either[Throwable, List[A]] = stream.compile.toList.attempt.unsafeRunSync()

  private def assertLimitExceeded(result: Either[Throwable, List[Byte]], expectedMaxBytes: Long): Unit = {
    assertTrue(result.isLeft)
    val exception: StreamMaxLengthExceededException = assertInstanceOf(
      classOf[StreamMaxLengthExceededException],
      result.left.toOption.get
    )
    assertEquals(expectedMaxBytes, exception.maxBytes)
    assertEquals(s"Stream length limit of $expectedMaxBytes bytes exceeded", exception.getMessage)
    assertFalse(result.isRight)
  }
}
