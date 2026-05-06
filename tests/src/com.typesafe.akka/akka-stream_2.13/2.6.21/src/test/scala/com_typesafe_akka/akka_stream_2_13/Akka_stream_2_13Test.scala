/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_stream_2_13

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorAttributes
import akka.stream.ClosedShape
import akka.stream.KillSwitches
import akka.stream.Materializer
import akka.stream.OverflowStrategy
import akka.stream.QueueOfferResult
import akka.stream.Supervision
import akka.stream.scaladsl.Broadcast
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Framing
import akka.stream.scaladsl.GraphDSL
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.RunnableGraph
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.ZipWith
import akka.util.ByteString
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

class Akka_stream_2_13Test {
  private val testTimeout: FiniteDuration = 10.seconds
  private implicit var system: ActorSystem = _
  private implicit var materializer: Materializer = _

  @BeforeEach
  def startActorSystem(): Unit = {
    system = ActorSystem("akka-stream-reachability-test")
    materializer = Materializer(system)
  }

  @AfterEach
  def stopActorSystem(): Unit = {
    if (system != null) {
      Await.result(system.terminate(), testTimeout)
    }
  }

  @Test
  def transformsFiniteSourceWithFlowAndSink(): Unit = {
    val result: Future[Seq[Int]] = Source(1 to 8)
      .via(Flow[Int].filter(_ % 2 == 0).map(_ * 10))
      .runWith(Sink.seq)

    assertEquals(Seq(20, 40, 60, 80), awaitResult(result))
  }

  @Test
  def materializesRunnableGraphWithBroadcastAndZip(): Unit = {
    val graph: RunnableGraph[Future[Seq[Int]]] = RunnableGraph.fromGraph(
      GraphDSL.create(Sink.seq[Int]) { implicit builder => sink =>
        import GraphDSL.Implicits._

        val broadcast = builder.add(Broadcast[Int](2))
        val zip = builder.add(ZipWith[Int, Int, Int](_ + _))
        val multiplyByTwo: Flow[Int, Int, NotUsed] = Flow[Int].map(_ * 2)
        val multiplyByTen: Flow[Int, Int, NotUsed] = Flow[Int].map(_ * 10)

        Source(1 to 4) ~> broadcast.in
        broadcast.out(0) ~> multiplyByTwo ~> zip.in0
        broadcast.out(1) ~> multiplyByTen ~> zip.in1
        zip.out ~> sink

        ClosedShape
      }
    )

    assertEquals(Seq(12, 24, 36, 48), awaitResult(graph.run()))
  }

  @Test
  def sourceQueueOffersElementsAndCompletesDownstream(): Unit = {
    val (queue, result) = Source.queue[Int](bufferSize = 4, OverflowStrategy.backpressure)
      .map(_ * 3)
      .toMat(Sink.seq)(Keep.both)
      .run()

    assertEquals(QueueOfferResult.Enqueued, awaitResult(queue.offer(1)))
    assertEquals(QueueOfferResult.Enqueued, awaitResult(queue.offer(2)))
    assertEquals(QueueOfferResult.Enqueued, awaitResult(queue.offer(3)))
    queue.complete()

    assertEquals(Seq(3, 6, 9), awaitResult(result))
  }

  @Test
  def supervisionResumesFailedElementAndKeepsStreamRunning(): Unit = {
    val result: Future[Seq[Int]] = Source(List("1", "not-a-number", "3"))
      .map(_.toInt)
      .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
      .runWith(Sink.seq)

    assertEquals(Seq(1, 3), awaitResult(result))
  }

  @Test
  def delimiterFramingSplitsByteStringStreamIntoFrames(): Unit = {
    val result: Future[Seq[String]] = Source.single(ByteString("alpha\nbeta\ngamma\n"))
      .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 32, allowTruncation = false))
      .map(_.utf8String)
      .runWith(Sink.seq)

    assertEquals(Seq("alpha", "beta", "gamma"), awaitResult(result))
  }

  @Test
  def substreamsGroupElementsAndMergeFoldedResults(): Unit = {
    val words: Seq[String] = Seq("apple", "apricot", "banana", "blueberry", "avocado")
    val result: Future[Seq[String]] = Source(words)
      .groupBy(maxSubstreams = 2, _.head)
      .fold("")((letters, word) => letters + word.head)
      .mergeSubstreams
      .runWith(Sink.seq)

    assertEquals(Set("aaa", "bb"), awaitResult(result).toSet)
  }

  @Test
  def killSwitchCanStopATickingSourcePromptly(): Unit = {
    val (killSwitch, result) = Source.tick(0.seconds, 100.millis, "tick")
      .viaMat(KillSwitches.single)(Keep.right)
      .toMat(Sink.seq)(Keep.both)
      .run()

    killSwitch.shutdown()
    val ticks: Seq[String] = awaitResult(result)

    assertTrue(ticks.size <= 1, s"Expected at most one tick before shutdown, got $ticks")
  }

  private def awaitResult[T](future: Future[T]): T = Await.result(future, testTimeout)
}
