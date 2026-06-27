/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_stream_2_13

import akka.Done
import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorAttributes
import akka.stream.ClosedShape
import akka.stream.IOResult
import akka.stream.KillSwitches
import akka.stream.Materializer
import akka.stream.OverflowStrategy
import akka.stream.QueueOfferResult
import akka.stream.Supervision
import akka.stream.SystemMaterializer
import akka.stream.UniqueKillSwitch
import akka.stream.scaladsl.Broadcast
import akka.stream.scaladsl.BroadcastHub
import akka.stream.scaladsl.Compression
import akka.stream.scaladsl.FileIO
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Framing
import akka.stream.scaladsl.GraphDSL
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.MergeHub
import akka.stream.scaladsl.RunnableGraph
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.SourceQueueWithComplete
import akka.stream.scaladsl.StreamConverters
import akka.stream.scaladsl.ZipWith
import akka.util.ByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

class Akka_stream_2_13Test {
  private val Timeout: FiniteDuration = 10.seconds

  @Test
  def runTypedSourceFlowAndSinkPipeline(): Unit = withStream("source-flow-sink") {
    (_: ActorSystem, materializer: Materializer) =>
      implicit val mat: Materializer = materializer

      val result: Seq[String] = awaitResult(
        Source(1 to 8)
          .filter(_ % 2 == 0)
          .via(Flow[Int].map(_ * 3))
          .grouped(2)
          .map(numbers => numbers.mkString("[", ",", "]"))
          .runWith(Sink.seq))

      assertThat(result.asJava).containsExactly("[6,12]", "[18,24]")
  }

  @Test
  def materializeRunnableGraphBuiltWithGraphDsl(): Unit = withStream("graph-dsl") {
    (_: ActorSystem, materializer: Materializer) =>
      implicit val mat: Materializer = materializer

      val graph: RunnableGraph[Future[Int]] = RunnableGraph.fromGraph(
        GraphDSL.create(Sink.head[Int]) { implicit builder => sink =>
          import GraphDSL.Implicits._

          val broadcast = builder.add(Broadcast[Int](2))
          val zip = builder.add(ZipWith[Int, Int, Int]((left, right) => left + right))

          Source.single(5) ~> broadcast.in
          broadcast.out(0) ~> Flow[Int].map(_ + 1) ~> zip.in0
          broadcast.out(1) ~> Flow[Int].map(_ * 4) ~> zip.in1
          zip.out ~> sink.in

          ClosedShape
        })

      assertThat(awaitResult(graph.run())).isEqualTo(26)
  }

  @Test
  def frameByteStringsDelimitedAcrossChunks(): Unit = withStream("framing") {
    (_: ActorSystem, materializer: Materializer) =>
      implicit val mat: Materializer = materializer

      val framed: Seq[String] = awaitResult(
        Source(List(ByteString("alpha\nbr"), ByteString("avo\ncharlie\n")))
          .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 64, allowTruncation = false))
          .map(_.utf8String)
          .runWith(Sink.seq))

      assertThat(framed.asJava).containsExactly("alpha", "bravo", "charlie")
  }

  @Test
  def roundTripGzipCompressedByteStrings(): Unit = withStream("compression") {
    (_: ActorSystem, materializer: Materializer) =>
      implicit val mat: Materializer = materializer

      val original = "Akka Streams can transform bounded byte streams in-process."
      val compressed: ByteString = awaitResult(
        Source.single(ByteString(original))
          .via(Compression.gzip)
          .runFold(ByteString.empty)(_ ++ _))
      val inflated: ByteString = awaitResult(
        Source.single(compressed)
          .via(Compression.gunzip())
          .runFold(ByteString.empty)(_ ++ _))

      assertThat(compressed.length).isGreaterThan(0)
      assertThat(inflated.utf8String).isEqualTo(original)
  }

  @Test
  def readInputStreamThroughStreamConverters(): Unit = withStream("stream-converters") {
    (_: ActorSystem, materializer: Materializer) =>
      implicit val mat: Materializer = materializer

      val bytes: Array[Byte] = "chunked-input-stream".getBytes("UTF-8")
      val collected: ByteString = awaitResult(
        StreamConverters
          .fromInputStream(() => new ByteArrayInputStream(bytes), chunkSize = 3)
          .runFold(ByteString.empty)(_ ++ _))

      assertThat(collected.utf8String).isEqualTo("chunked-input-stream")
  }

  @Test
  def writeAndReadFilesWithFileIO(): Unit = withStream("file-io") {
    (_: ActorSystem, materializer: Materializer) =>
      implicit val mat: Materializer = materializer

      val path: Path = Files.createTempFile("akka-stream-file-io", ".txt")
      try {
        val lines: List[String] = List("alpha\n", "beta\n", "gamma\n")
        val expectedText: String = lines.mkString
        val expectedByteCount: Long = expectedText.getBytes(StandardCharsets.UTF_8).length.toLong

        val writeResult: IOResult = awaitResult(
          Source(lines)
            .map(line => ByteString(line, StandardCharsets.UTF_8.name()))
            .runWith(FileIO.toPath(path)))
        val readText: String = awaitResult(
          FileIO
            .fromPath(path, chunkSize = 4)
            .runFold(ByteString.empty)(_ ++ _))
          .utf8String

        assertThat(writeResult.wasSuccessful).isTrue()
        assertThat(writeResult.count).isEqualTo(expectedByteCount)
        assertThat(readText).isEqualTo(expectedText)
      } finally {
        Files.deleteIfExists(path)
      }
  }

  @Test
  def offerElementsToBackpressuredSourceQueue(): Unit = withStream("source-queue") {
    (_: ActorSystem, materializer: Materializer) =>
      implicit val mat: Materializer = materializer

      val (queue, resultFuture): (SourceQueueWithComplete[Int], Future[Seq[Int]]) =
        Source
          .queue[Int](4, OverflowStrategy.backpressure)
          .map(_ * 10)
          .toMat(Sink.seq)(Keep.both)
          .run()

      assertThat(awaitResult(queue.offer(1))).isEqualTo(QueueOfferResult.Enqueued)
      assertThat(awaitResult(queue.offer(2))).isEqualTo(QueueOfferResult.Enqueued)
      assertThat(awaitResult(queue.offer(3))).isEqualTo(QueueOfferResult.Enqueued)
      queue.complete()

      assertThat(awaitResult(resultFuture).asJava).containsExactly(10, 20, 30)
  }

  @Test
  def publishAndConsumeThroughMergeHubAndBroadcastHub(): Unit = withStream("hubs") {
    (_: ActorSystem, materializer: Materializer) =>
      implicit val mat: Materializer = materializer

      val (publish, subscribe): (Sink[String, NotUsed], Source[String, NotUsed]) =
        MergeHub
          .source[String](perProducerBufferSize = 16)
          .toMat(BroadcastHub.sink[String](bufferSize = 16))(Keep.both)
          .run()
      val received: Future[Seq[String]] = subscribe.take(3).runWith(Sink.seq)

      Source(List("red", "green", "blue")).runWith(publish)

      assertThat(awaitResult(received).asJava).containsExactly("red", "green", "blue")
  }

  @Test
  def resumeAfterElementFailureWithSupervisionAttribute(): Unit = withStream("supervision") {
    (_: ActorSystem, materializer: Materializer) =>
      implicit val mat: Materializer = materializer

      val guardedDivision: Flow[Int, Int, NotUsed] = Flow[Int]
        .map(100 / _)
        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
      val result: Seq[Int] = awaitResult(
        Source(List(10, 0, 5))
          .via(guardedDivision)
          .runWith(Sink.seq))

      assertThat(result.asJava).containsExactly(10, 20)
  }

  @Test
  def stopOpenStreamWithUniqueKillSwitch(): Unit = withStream("kill-switch") {
    (_: ActorSystem, materializer: Materializer) =>
      implicit val mat: Materializer = materializer

      val (killSwitch, completion): (UniqueKillSwitch, Future[Done]) = Source
        .maybe[Int]
        .viaMat(KillSwitches.single)(Keep.right)
        .toMat(Sink.ignore)(Keep.both)
        .run()

      killSwitch.shutdown()

      assertThat(awaitResult(completion)).isEqualTo(Done)
  }

  @Test
  def mergeFoldedSubstreamsByKey(): Unit = withStream("substreams") {
    (_: ActorSystem, materializer: Materializer) =>
      implicit val mat: Materializer = materializer

      val result: Seq[String] = awaitResult(
        Source(List("a", "bb", "c", "dd"))
          .groupBy(2, _.length)
          .fold("")(_ + _)
          .mergeSubstreams
          .runWith(Sink.seq))

      assertThat(result.asJava).containsExactlyInAnyOrder("ac", "bbdd")
  }

  @Test
  def combineMaterializedValuesWithKeepBoth(): Unit = withStream("materialized-values") {
    (_: ActorSystem, materializer: Materializer) =>
      implicit val mat: Materializer = materializer

      val (completion, sum): (Future[Done], Future[Int]) = Source(1 to 5)
        .watchTermination()(Keep.right)
        .toMat(Sink.fold[Int, Int](0)(_ + _))(Keep.both)
        .run()

      assertThat(awaitResult(sum)).isEqualTo(15)
      assertThat(awaitResult(completion)).isEqualTo(Done)
  }

  private def withStream(testName: String)(testBody: (ActorSystem, Materializer) => Unit): Unit = {
    val system: ActorSystem = ActorSystem(testName)
    try {
      testBody(system, SystemMaterializer(system).materializer)
    } finally {
      Await.result(system.terminate(), Timeout)
    }
  }

  private def awaitResult[T](future: Future[T]): T = Await.result(future, Timeout)
}
