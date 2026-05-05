/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_fs2.fs2_core_3

import cats.effect.ContextShift
import cats.effect.IO
import cats.effect.Timer
import cats.effect.concurrent.Deferred
import fs2.Chunk
import fs2.Pull
import fs2.Stream
import fs2.compression
import fs2.concurrent.Queue
import fs2.concurrent.Signal
import fs2.concurrent.Topic
import fs2.hash
import fs2.text
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.*

class Fs2_core_3Test {
  private implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  private implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  def streamTransformationsCompileToExpectedCollections(): Unit = {
    val transformed: Vector[String] = Stream
      .range(0, 20)
      .filter(_ % 3 != 0)
      .map(_ + 1)
      .drop(2)
      .take(5)
      .zipWithIndex
      .map { case (value, index) => s"$index:$value" }
      .intersperse("|")
      .covary[IO]
      .compile
      .toVector
      .unsafeRunSync()

    val sum: Int = Stream
      .emits(List(1, 2, 3, 4, 5))
      .scan(0)(_ + _)
      .covary[IO]
      .compile
      .lastOrError
      .unsafeRunSync()

    val chunked: Vector[List[Int]] = Stream
      .emits(List(1, 2, 3, 4, 5))
      .chunkN(2)
      .map(_.toList)
      .covary[IO]
      .compile
      .toVector
      .unsafeRunSync()

    assertEquals(Vector("0:5", "|", "1:6", "|", "2:8", "|", "3:9", "|", "4:11"), transformed)
    assertEquals(15, sum)
    assertEquals(Vector(List(1, 2), List(3, 4), List(5)), chunked)
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  def pullUnconsumesAndReemitsBoundedChunks(): Unit = {
    def grouped(source: Stream[IO, Int]): Pull[IO, String, Unit] =
      source.pull.unconsLimit(3).flatMap {
        case Some((chunk, tail)) =>
          Pull.output1(chunk.toList.mkString("[", ",", "]")) >> grouped(tail)
        case None =>
          Pull.done
      }

    val groups: Vector[String] = grouped(Stream.chunk(Chunk.array(Array(1, 2, 3, 4, 5, 6, 7))).covary[IO])
      .stream
      .compile
      .toVector
      .unsafeRunSync()

    assertEquals(Vector("[1,2,3]", "[4,5,6]", "[7]"), groups)
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  def bracketFinalizerRunsWhenStreamFails(): Unit = {
    val events = new CopyOnWriteArrayList[String]()
    val boom = new IllegalStateException("boom")

    val result: Either[Throwable, Vector[Int]] = Stream
      .bracket(IO {
        events.add("acquire")
        "token"
      })(resource => IO {
        events.add(s"release:$resource")
        ()
      })
      .flatMap(resource => Stream.emit(resource.length) ++ Stream.raiseError[IO](boom))
      .compile
      .toVector
      .attempt
      .unsafeRunSync()

    result match {
      case Left(error) => assertSame(boom, error)
      case Right(value) => fail(s"Expected stream failure, got $value")
    }
    assertEquals(List("acquire", "release:token"), events.asScala.toList)
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  def chunksSupportPrimitiveStorageSlicingAndStatefulMapping(): Unit = {
    val chunk: Chunk[Int] = Chunk.array(Array(1, 2, 3, 4, 5), 1, 3)
    val positiveTens: List[Int] = chunk
      .flatMap(value => Chunk(value, -value))
      .filter(_ > 0)
      .map(_ * 10)
      .toList
    val (finalState, accumulated): (Int, Chunk[String]) = chunk.mapAccumulate(0) { (sum, value) =>
      val nextSum = sum + value
      (nextSum, s"$value->$nextSum")
    }
    val copied = Array.fill(5)(0)

    chunk.copyToArray(copied, 1)
    val bytes: Array[Byte] = Chunk
      .byteBuffer(ByteBuffer.wrap(Array[Byte](10, 20, 30, 40)))
      .drop(1)
      .take(2)
      .toBytes
      .toArray

    assertEquals(List(20, 30, 40), positiveTens)
    assertEquals(9, finalState)
    assertEquals(List("2->2", "3->5", "4->9"), accumulated.toList)
    assertArrayEquals(Array(0, 2, 3, 4, 0), copied)
    assertArrayEquals(Array[Byte](20, 30), bytes)
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  def textPipesHandleUtf8LineSplittingAndBase64(): Unit = {
    val textBytes: Array[Byte] = Array[Byte](0xef.toByte, 0xbb.toByte, 0xbf.toByte) ++
      "hé\nworld\r\nlast".getBytes(StandardCharsets.UTF_8)

    val decodedLines: Vector[String] = byteStream(textBytes, 2)
      .through(text.utf8Decode)
      .through(text.lines)
      .compile
      .toVector
      .unsafeRunSync()

    val plainText: String = "native-image friendly fs2"
    val plainBytes: Array[Byte] = plainText.getBytes(StandardCharsets.UTF_8)
    val encoded: String = byteStream(plainBytes, 5)
      .through(text.base64.encode)
      .compile
      .string
      .unsafeRunSync()
    val decodedBytes: Vector[Byte] = Stream
      .emits(Vector(encoded.take(4), " \n", encoded.drop(4)))
      .covary[IO]
      .through(text.base64.decode)
      .compile
      .toVector
      .unsafeRunSync()

    assertEquals(Vector("hé", "world", "last"), decodedLines)
    assertEquals(Base64.getEncoder.encodeToString(plainBytes), encoded)
    assertArrayEquals(plainBytes, decodedBytes.toArray)
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  def hashAndCompressionPipesRoundTripChunkedBytes(): Unit = {
    val payload: Array[Byte] = ("FS2 compression and hashing " * 16).getBytes(StandardCharsets.UTF_8)

    val sha256: Vector[Byte] = byteStream(payload, 11)
      .through(hash.sha256)
      .compile
      .toVector
      .unsafeRunSync()
    val expectedDigest: Array[Byte] = MessageDigest.getInstance("SHA-256").digest(payload)

    val deflatedRoundTrip: Vector[Byte] = byteStream(payload, 7)
      .through(compression.deflate[IO](compression.DeflateParams.BEST_SPEED))
      .through(compression.inflate[IO]())
      .compile
      .toVector
      .unsafeRunSync()

    val modificationTime: Instant = Instant.parse("2020-01-02T03:04:05Z")
    val gzipRoundTrip: Vector[Byte] = byteStream(payload, 9)
      .through(
        compression.gzip[IO](
          fileName = Some("payload.txt"),
          modificationTime = Some(modificationTime),
          comment = Some("metadata")
        )
      )
      .through(compression.gunzip[IO]())
      .flatMap { result =>
        Stream.eval(IO {
          assertEquals(Some("payload.txt"), result.fileName)
          assertEquals(Some(modificationTime), result.modificationTime)
          assertEquals(Some("metadata"), result.comment)
        }).drain ++ result.content
      }
      .compile
      .toVector
      .unsafeRunSync()

    assertArrayEquals(expectedDigest, sha256.toArray)
    assertArrayEquals(payload, deflatedRoundTrip.toArray)
    assertArrayEquals(payload, gzipRoundTrip.toArray)
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  def mapAsyncRunsEffectsConcurrentlyWhilePreservingInputOrder(): Unit = {
    val ordered: Vector[Int] = (for {
      firstReady <- Deferred[IO, Unit]
      secondReady <- Deferred[IO, Unit]
      thirdReady <- Deferred[IO, Unit]
      firstGate <- Deferred[IO, Unit]
      secondGate <- Deferred[IO, Unit]
      thirdGate <- Deferred[IO, Unit]
      readyByValue: Map[Int, Deferred[IO, Unit]] = Map(1 -> firstReady, 2 -> secondReady, 3 -> thirdReady)
      gateByValue: Map[Int, Deferred[IO, Unit]] = Map(1 -> firstGate, 2 -> secondGate, 3 -> thirdGate)
      fiber <- Stream
        .emits(List(1, 2, 3))
        .covary[IO]
        .mapAsync(3) { value =>
          for {
            _ <- readyByValue(value).complete(())
            _ <- gateByValue(value).get
          } yield value
        }
        .compile
        .toVector
        .start
      _ <- firstReady.get
      _ <- secondReady.get
      _ <- thirdReady.get
      _ <- thirdGate.complete(())
      _ <- secondGate.complete(())
      _ <- firstGate.complete(())
      values <- fiber.join
    } yield values).unsafeRunSync()

    assertEquals(Vector(1, 2, 3), ordered)
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  def queuesExposeBoundedOffersChunksAndTermination(): Unit = {
    val queueResult: (Boolean, Boolean, Boolean, List[Int], Option[Int], Int, Int) = (for {
      queue <- Queue.bounded[IO, Int](2)
      firstOffer <- queue.offer1(1)
      secondOffer <- queue.offer1(2)
      thirdOffer <- queue.offer1(3)
      chunk <- queue.dequeueChunk1(5)
      empty <- queue.tryDequeue1
      _ <- queue.enqueue1(4)
      dequeued <- queue.dequeue1
      mapped = queue.imap[String](_.toString)(_.toInt)
      _ <- mapped.enqueue1("5")
      mappedDequeued <- queue.dequeue1
    } yield (
      firstOffer,
      secondOffer,
      thirdOffer,
      chunk.toList,
      empty,
      dequeued,
      mappedDequeued
    )).unsafeRunSync()

    val terminated: Vector[String] = (for {
      queue <- Queue.noneTerminated[IO, String]
      _ <- queue.enqueue1(Some("alpha"))
      _ <- queue.enqueue1(Some("beta"))
      _ <- queue.enqueue1(None)
      values <- queue.dequeue.compile.toVector
    } yield values).unsafeRunSync()

    assertTrue(queueResult._1)
    assertTrue(queueResult._2)
    assertFalse(queueResult._3)
    assertEquals(List(1, 2), queueResult._4)
    assertEquals(None, queueResult._5)
    assertEquals(4, queueResult._6)
    assertEquals(5, queueResult._7)
    assertEquals(Vector("alpha", "beta"), terminated)
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  def topicPublishesUpdatesToMultipleSubscribers(): Unit = {
    val observed: (Vector[String], Vector[String]) = (for {
      topic <- Topic[IO, String]("initial")
      firstSubscriber <- topic.subscribe(8).take(3).compile.toVector.start
      secondSubscriber <- topic.subscribe(8).take(3).compile.toVector.start
      _ <- topic.subscribers.dropWhile(_ < 2).take(1).compile.drain
      _ <- topic.publish1("alpha")
      _ <- topic.publish1("beta")
      firstValues <- firstSubscriber.join
      secondValues <- secondSubscriber.join
    } yield (firstValues, secondValues)).unsafeRunSync()

    assertEquals(Vector("initial", "alpha", "beta"), observed._1)
    assertEquals(Vector("initial", "alpha", "beta"), observed._2)
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  def signalConstantCanBeMappedAndSampledContinuously(): Unit = {
    val signal: Signal[IO, Int] = Signal.constant[IO, Int](21).map(_ * 2)
    val current: Int = signal.get.unsafeRunSync()
    val samples: Vector[Int] = signal.continuous.take(3).compile.toVector.unsafeRunSync()

    assertEquals(42, current)
    assertEquals(Vector(42, 42, 42), samples)
  }

  private def byteStream(bytes: Array[Byte], chunkSize: Int): Stream[IO, Byte] = {
    val chunks: Vector[Chunk[Byte]] = bytes
      .grouped(chunkSize)
      .map(chunk => Chunk.bytes(chunk))
      .toVector
    Stream.emits(chunks).covary[IO].flatMap(chunk => Stream.chunk(chunk).covary[IO])
  }
}
