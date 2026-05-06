/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_stream_2_13

import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorAttributes
import akka.stream.ClosedShape
import akka.stream.KillSwitches
import akka.stream.Materializer
import akka.stream.OverflowStrategy
import akka.stream.QueueOfferResult
import akka.stream.Supervision
import akka.stream.SystemMaterializer
import akka.stream.scaladsl.Broadcast
import akka.stream.scaladsl.Compression
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Framing
import akka.stream.scaladsl.GraphDSL
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.RunnableGraph
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.StreamConverters
import akka.stream.scaladsl.Zip
import akka.util.ByteString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

class Akka_stream_2_13Test {
  private val awaitTimeout: FiniteDuration = 10.seconds

  @Test
  def transformsAndCollectsBoundedStreams(): Unit = withStreamSystem("AkkaStreamTransforms") {
    (_: ActorSystem, materializer: Materializer) =>
      implicit val implicitMaterializer: Materializer = materializer

      val result: Seq[Int] = await(
        Source(1 to 8)
          .via(Flow[Int].map(_ * 3))
          .filter(_ % 2 == 0)
          .grouped(2)
          .map(_.sum)
          .runWith(Sink.seq)
      )

      assertEquals(Seq(18, 42), result)
  }

  @Test
  def graphDslBroadcastsAndZipsElements(): Unit = withStreamSystem("AkkaStreamGraphDsl") {
    (_: ActorSystem, materializer: Materializer) =>
      implicit val implicitMaterializer: Materializer = materializer

      val sink: Sink[(Int, String), Future[Seq[(Int, String)]]] = Sink.seq[(Int, String)]
      val graph: RunnableGraph[Future[Seq[(Int, String)]]] = RunnableGraph.fromGraph(
        GraphDSL.create(sink) { implicit builder => collected =>
          import GraphDSL.Implicits._

          val broadcast = builder.add(Broadcast[Int](2))
          val zip = builder.add(Zip[Int, String]())

          Source(1 to 4) ~> broadcast.in
          broadcast.out(0) ~> Flow[Int].map(_ * 10) ~> zip.in0
          broadcast.out(1) ~> Flow[Int].map(number => if (number % 2 == 0) "even" else "odd") ~> zip.in1
          zip.out ~> collected.in

          ClosedShape
        }
      )

      val result: Seq[(Int, String)] = await(graph.run())

      assertEquals(Seq((10, "odd"), (20, "even"), (30, "odd"), (40, "even")), result)
  }

  @Test
  def supervisionStrategyResumesAfterElementFailure(): Unit = withStreamSystem("AkkaStreamSupervision") {
    (_: ActorSystem, materializer: Materializer) =>
      implicit val implicitMaterializer: Materializer = materializer
      val decider: Supervision.Decider = {
        case _: ArithmeticException => Supervision.Resume
        case _ => Supervision.Stop
      }

      val result: Seq[Int] = await(
        Source(List(4, 2, 0, 1))
          .map(8 / _)
          .withAttributes(ActorAttributes.supervisionStrategy(decider))
          .runWith(Sink.seq)
      )

      assertEquals(Seq(2, 4, 8), result)
  }

  @Test
  def byteStringFramingEmitsDelimitedLinesAcrossChunks(): Unit = withStreamSystem("AkkaStreamFraming") {
    (_: ActorSystem, materializer: Materializer) =>
      implicit val implicitMaterializer: Materializer = materializer

      val result: Seq[String] = await(
        Source(List(ByteString("alpha\nbe"), ByteString("ta\ngamma\n")))
          .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 32, allowTruncation = false))
          .map(_.utf8String)
          .runWith(Sink.seq)
      )

      assertEquals(Seq("alpha", "beta", "gamma"), result)
  }

  @Test
  def compressionRoundTripPreservesByteStringPayload(): Unit = withStreamSystem("AkkaStreamCompression") {
    (_: ActorSystem, materializer: Materializer) =>
      implicit val implicitMaterializer: Materializer = materializer
      val payload: ByteString = ByteString("Akka Streams compression round trip " * 20)

      val result: ByteString = await(
        Source
          .single(payload)
          .via(Compression.gzip)
          .via(Compression.gunzip())
          .runFold(ByteString.empty)(_ ++ _)
      )

      assertEquals(payload, result)
  }

  @Test
  def sourceQueueMaterializedValueAcceptsAndCompletesElements(): Unit = withStreamSystem("AkkaStreamQueue") {
    (_: ActorSystem, materializer: Materializer) =>
      implicit val implicitMaterializer: Materializer = materializer

      val (queue, resultFuture) = Source
        .queue[Int](bufferSize = 4, overflowStrategy = OverflowStrategy.backpressure)
        .toMat(Sink.seq)(Keep.both)
        .run()

      assertEquals(QueueOfferResult.Enqueued, await(queue.offer(1)))
      assertEquals(QueueOfferResult.Enqueued, await(queue.offer(2)))
      assertEquals(QueueOfferResult.Enqueued, await(queue.offer(3)))
      queue.complete()

      assertEquals(Seq(1, 2, 3), await(resultFuture))
      assertEquals(Done, await(queue.watchCompletion()))
  }

  @Test
  def killSwitchStopsAnInfiniteSourceWithoutLeakingTheStream(): Unit = withStreamSystem("AkkaStreamKillSwitch") {
    (_: ActorSystem, materializer: Materializer) =>
      implicit val implicitMaterializer: Materializer = materializer

      val (killSwitch, completion) = Source
        .maybe[String]
        .viaMat(KillSwitches.single)(Keep.right)
        .toMat(Sink.ignore)(Keep.both)
        .run()

      killSwitch.shutdown()

      assertEquals(Done, await(completion))
  }

  @Test
  def substreamsAggregateIndependentlyBeforeMerging(): Unit = withStreamSystem("AkkaStreamSubstreams") {
    (_: ActorSystem, materializer: Materializer) =>
      implicit val implicitMaterializer: Materializer = materializer
      val words: List[String] = List("apple", "apricot", "banana", "blueberry", "avocado")

      val result: Seq[(String, Int)] = await(
        Source(words)
          .groupBy(maxSubstreams = 4, _.head)
          .fold(("", 0)) { case ((_, totalLength), word) =>
            (word.head.toString, totalLength + word.length)
          }
          .mergeSubstreams
          .runWith(Sink.seq)
      )

      assertEquals(Map("a" -> 19, "b" -> 15), result.toMap)
  }

  @Test
  def mapAsyncCanRecoverFromFailedFuture(): Unit = withStreamSystem("AkkaStreamMapAsyncRecovery") {
    (_: ActorSystem, materializer: Materializer) =>
      implicit val implicitMaterializer: Materializer = materializer

      val result: Seq[Int] = await(
        Source(1 to 5)
          .mapAsync(parallelism = 1) { number =>
            if (number == 3) Future.failed[Int](new IllegalStateException("boom"))
            else Future.successful(number * number)
          }
          .recover { case _: IllegalStateException => -1 }
          .runWith(Sink.seq)
      )

      assertEquals(Seq(1, 4, -1), result)
  }

  @Test
  def streamConvertersReadInputStreamAsByteStrings(): Unit = withStreamSystem("AkkaStreamConverters") {
    (_: ActorSystem, materializer: Materializer) =>
      implicit val implicitMaterializer: Materializer = materializer
      val bytes: Array[Byte] = "stream-converter-input".getBytes(StandardCharsets.UTF_8)

      val result: ByteString = await(
        StreamConverters
          .fromInputStream(() => new ByteArrayInputStream(bytes), chunkSize = 5)
          .runFold(ByteString.empty)(_ ++ _)
      )

      assertEquals(ByteString(bytes), result)
  }

  private def withStreamSystem(name: String)(testBody: (ActorSystem, Materializer) => Unit): Unit = {
    val system: ActorSystem = ActorSystem(name)
    val materializer: Materializer = SystemMaterializer(system).materializer
    try testBody(system, materializer)
    finally Await.result(system.terminate(), awaitTimeout)
  }

  private def await[T](future: Future[T]): T = Await.result(future, awaitTimeout)
}
