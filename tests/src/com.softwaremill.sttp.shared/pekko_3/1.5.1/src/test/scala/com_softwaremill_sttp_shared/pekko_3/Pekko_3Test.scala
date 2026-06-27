/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_shared.pekko_3

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.stream.scaladsl.Keep
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sttp.capabilities.StreamMaxLengthExceededException
import sttp.capabilities.Streams
import sttp.capabilities.pekko.PekkoStreams

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.*
import scala.util.control.NonFatal

class Pekko_3Test {
  @Test
  def pekkoStreamsExposeTypedBinaryStreamAndFlowCapabilities(): Unit = withActorSystem {
    val streamsCapability: Streams[PekkoStreams] = PekkoStreams
    val binary: PekkoStreams.BinaryStream = Source.single(ByteString("pekko"))
    val toUpperCase: PekkoStreams.Pipe[ByteString, String] = Flow[ByteString].map(_.utf8String.toUpperCase)

    val collected: Either[Throwable, Seq[String]] = collect(binary.via(toUpperCase))

    assertNotNull(streamsCapability)
    assertEquals(Right(Seq("PEKKO")), collected)
  }

  @Test
  def limitBytesAllowsEmptyStreamsAndStreamsUnderTheLimit(): Unit = withActorSystem {
    val empty: Source[ByteString, Any] = Source.empty[ByteString]
    val underLimit: Source[ByteString, Any] = Source(List(ByteString("ab"), ByteString("c")))

    assertEquals(Right(Seq.empty[ByteString]), collect(PekkoStreams.limitBytes(empty, 0L)))
    assertEquals(Right(Seq(ByteString("ab"), ByteString("c"))), collect(PekkoStreams.limitBytes(underLimit, 4L)))
  }

  @Test
  def limitBytesAllowsExactlyTheMaximumNumberOfBytes(): Unit = withActorSystem {
    val exactLimit: Source[ByteString, Any] = Source(List(ByteString("ab"), ByteString("cd"), ByteString("e")))

    val collected: Either[Throwable, Seq[ByteString]] = collect(PekkoStreams.limitBytes(exactLimit, 5L))

    assertEquals(Right(Seq(ByteString("ab"), ByteString("cd"), ByteString("e"))), collected)
  }

  @Test
  def limitBytesPreservesOriginalByteStringChunksAndOrdering(): Unit = withActorSystem {
    val chunked: Source[ByteString, Any] = Source(List(ByteString("one"), ByteString.empty, ByteString("two")))

    val collected: Either[Throwable, Seq[ByteString]] = collect(PekkoStreams.limitBytes(chunked, 6L))

    assertEquals(Right(Seq(ByteString("one"), ByteString.empty, ByteString("two"))), collected)
  }

  @Test
  def limitBytesCanBeUsedWithOrdinaryPekkoFlowTransformations(): Unit = withActorSystem {
    val transformed: Source[ByteString, Any] = Source(List("a", "bb", "ccc", "dddd"))
      .filter(_.length <= 3)
      .map(value => ByteString(value))
    val limited: Source[ByteString, Any] = PekkoStreams.limitBytes(transformed, 6L)

    val collected: Either[Throwable, Seq[String]] = collect(limited.map(_.utf8String))

    assertEquals(Right(Seq("a", "bb", "ccc")), collected)
  }

  @Test
  def limitBytesPreservesTheSourceMaterializedValue(): Unit = withActorSystem {
    val expectedMaterializedValue: String = "source-materialized-value"
    val source: Source[ByteString, String] = Source
      .single(ByteString("ok"))
      .mapMaterializedValue(_ => expectedMaterializedValue)
    val limited: Source[ByteString, Any] = PekkoStreams.limitBytes(source, 2L)

    val (materializedValue: Any, collectedFuture: Future[Seq[ByteString]]) = limited
      .toMat(Sink.seq[ByteString])(Keep.both)
      .run()

    assertEquals(expectedMaterializedValue, materializedValue)
    assertEquals(Seq(ByteString("ok")), Await.result(collectedFuture, 10.seconds))
  }

  @Test
  def limitBytesRaisesStreamMaxLengthExceededWhenAccumulatedChunksExceedTheLimit(): Unit = withActorSystem {
    val source: Source[ByteString, Any] = Source(List(ByteString("ab"), ByteString("cd")))

    val result: Either[Throwable, Seq[ByteString]] = collect(PekkoStreams.limitBytes(source, 3L))

    assertLimitExceeded(result, expectedMaxBytes = 3L)
  }

  @Test
  def limitBytesRaisesStreamMaxLengthExceededWhenASingleChunkIsTooLarge(): Unit = withActorSystem {
    val source: Source[ByteString, Any] = Source.single(ByteString("abcd"))

    val result: Either[Throwable, Seq[ByteString]] = collect(PekkoStreams.limitBytes(source, 2L))

    assertLimitExceeded(result, expectedMaxBytes = 2L)
  }

  @Test
  def limitBytesTreatsZeroAsOnlyAllowingEmptyStreams(): Unit = withActorSystem {
    val nonEmpty: Source[ByteString, Any] = Source.single(ByteString("x"))

    val result: Either[Throwable, Seq[ByteString]] = collect(PekkoStreams.limitBytes(nonEmpty, 0L))

    assertLimitExceeded(result, expectedMaxBytes = 0L)
  }

  @Test
  def limitBytesPropagatesUpstreamFailuresWithoutWrappingThem(): Unit = withActorSystem {
    val upstreamFailure: IllegalStateException = new IllegalStateException("upstream failed")
    val source: Source[ByteString, Any] = Source
      .single(ByteString("ok"))
      .concat(Source.failed[ByteString](upstreamFailure))

    val result: Either[Throwable, Seq[ByteString]] = collect(PekkoStreams.limitBytes(source, 10L))

    assertEquals(Left(upstreamFailure), result)
    assertSame(upstreamFailure, failure(result))
  }

  @Test
  def limitedStreamsCanBeMaterializedMoreThanOnceWithIndependentByteCounts(): Unit = withActorSystem {
    val source: Source[ByteString, Any] = Source(List(ByteString("a"), ByteString("bc")))
    val limited: Source[ByteString, Any] = PekkoStreams.limitBytes(source, 3L)

    val firstRun: Either[Throwable, Seq[ByteString]] = collect(limited)
    val secondRun: Either[Throwable, Seq[ByteString]] = collect(limited)

    assertEquals(Right(Seq(ByteString("a"), ByteString("bc"))), firstRun)
    assertEquals(Right(Seq(ByteString("a"), ByteString("bc"))), secondRun)
  }

  private def withActorSystem[A](body: Materializer ?=> A): A = {
    val system: ActorSystem = ActorSystem("Pekko_3Test")
    val materializer: Materializer = Materializer(system)
    try {
      given Materializer = materializer
      body
    } finally {
      Await.result(system.terminate(), 10.seconds)
    }
  }

  private def collect[A](source: Source[A, Any])(using materializer: Materializer): Either[Throwable, Seq[A]] = {
    val collected: Future[Seq[A]] = source.runWith(Sink.seq[A])
    try {
      Right(Await.result(collected, 10.seconds))
    } catch {
      case NonFatal(error) => Left(error)
    }
  }

  private def assertLimitExceeded[A](result: Either[Throwable, Seq[A]], expectedMaxBytes: Long): Unit = {
    assertTrue(result.isLeft)
    val exception: StreamMaxLengthExceededException = assertInstanceOf(
      classOf[StreamMaxLengthExceededException],
      failure(result)
    )
    assertEquals(expectedMaxBytes, exception.maxBytes)
    assertEquals(s"Stream length limit of $expectedMaxBytes bytes exceeded", exception.getMessage)
    assertFalse(result.isRight)
  }

  private def failure[A](result: Either[Throwable, A]): Throwable = result match {
    case Left(error) => error
    case Right(_)    => throw new AssertionError("Expected stream to fail")
  }
}
