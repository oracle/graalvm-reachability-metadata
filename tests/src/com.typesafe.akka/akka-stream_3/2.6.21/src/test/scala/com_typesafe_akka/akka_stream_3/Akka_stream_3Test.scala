/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_stream_3

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ActorAttributes, FlowShape, Materializer, OverflowStrategy, QueueOfferResult, RestartSettings, Supervision}
import akka.stream.scaladsl.{Broadcast, Flow, Framing, GraphDSL, Keep, RestartSource, Sink, Source, Zip}
import akka.util.ByteString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Await
import scala.concurrent.duration.*

class Akka_stream_3Test {
  private val Timeout: FiniteDuration = 10.seconds

  @Test
  def sourceFlowAndSinkTransformFiniteElements(): Unit = {
    withMaterializer("finite-elements") { materializer =>
      val result: Seq[Seq[Int]] = Await.result(
        Source(1 to 8)
          .via(Flow[Int].map(_ * 2).filter(_ % 3 != 0))
          .grouped(3)
          .runWith(Sink.seq)(materializer),
        Timeout
      )

      assertEquals(List(List(2, 4, 8), List(10, 14, 16)), result.map(_.toList).toList)
    }
  }

  @Test
  def graphDslCombinesParallelBranchesIntoOneFlow(): Unit = {
    withMaterializer("graph-dsl") { materializer =>
      val summarize: Flow[String, (Int, String), NotUsed] = Flow.fromGraph(
        GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
          import GraphDSL.Implicits.*

          val broadcast = builder.add(Broadcast[String](2))
          val zip = builder.add(Zip[Int, String]())
          val lengths: Flow[String, Int, NotUsed] = Flow[String].map(_.length)
          val uppercase: Flow[String, String, NotUsed] = Flow[String].map(_.toUpperCase)

          broadcast.out(0) ~> lengths ~> zip.in0
          broadcast.out(1) ~> uppercase ~> zip.in1

          FlowShape(broadcast.in, zip.out)
        }
      )

      val result: Seq[(Int, String)] = Await.result(
        Source(List("akka", "stream"))
          .via(summarize)
          .runWith(Sink.seq)(materializer),
        Timeout
      )

      assertEquals(List((4, "AKKA"), (6, "STREAM")), result.toList)
    }
  }

  @Test
  def sourceQueueBackpressuresAndMaterializesCompletionFuture(): Unit = {
    withMaterializer("source-queue") { materializer =>
      val (queue, completion) = Source
        .queue[Int](bufferSize = 4, OverflowStrategy.backpressure)
        .map(value => value * value)
        .toMat(Sink.seq)(Keep.both)
        .run()(materializer)

      try {
        assertEquals(QueueOfferResult.Enqueued, Await.result(queue.offer(2), Timeout))
        assertEquals(QueueOfferResult.Enqueued, Await.result(queue.offer(3), Timeout))
        assertEquals(QueueOfferResult.Enqueued, Await.result(queue.offer(5), Timeout))
        queue.complete()

        val result: Seq[Int] = Await.result(completion, Timeout)
        assertEquals(List(4, 9, 25), result.toList)
      } finally {
        queue.complete()
        Await.ready(completion, Timeout)
      }
    }
  }

  @Test
  def supervisionStrategyResumesStreamAfterExpectedElementFailure(): Unit = {
    withMaterializer("supervision") { materializer =>
      val supervisedDivision: Flow[Int, Int, NotUsed] = Flow[Int]
        .map { value =>
          if value == 0 then throw new ArithmeticException("zero is not accepted")
          12 / value
        }
        .withAttributes(ActorAttributes.supervisionStrategy {
          case _: ArithmeticException => Supervision.Resume
          case _ => Supervision.Stop
        })

      val result: Seq[Int] = Await.result(
        Source(List(3, 0, 2))
          .via(supervisedDivision)
          .runWith(Sink.seq)(materializer),
        Timeout
      )

      assertEquals(List(4, 6), result.toList)
    }
  }

  @Test
  def delimiterFramingEmitsCompleteAndTruncatedByteStringFrames(): Unit = {
    withMaterializer("framing") { materializer =>
      val result: Seq[String] = Await.result(
        Source(List(ByteString("alpha\nbr"), ByteString("avo\ncharlie")))
          .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 32, allowTruncation = true))
          .map(_.utf8String)
          .runWith(Sink.seq)(materializer),
        Timeout
      )

      assertEquals(List("alpha", "bravo", "charlie"), result.toList)
    }
  }

  @Test
  def substreamsCanBeSplitFoldedAndMerged(): Unit = {
    withMaterializer("substreams") { materializer =>
      val result: Seq[Seq[Int]] = Await.result(
        Source(1 to 7)
          .splitWhen(value => value > 1 && value % 3 == 1)
          .fold(Vector.empty[Int])(_ :+ _)
          .mergeSubstreams
          .runWith(Sink.seq)(materializer),
        Timeout
      )

      assertEquals(Set(List(1, 2, 3), List(4, 5, 6), List(7)), result.map(_.toList).toSet)
    }
  }

  @Test
  def recoverConvertsStreamFailureIntoFallbackElement(): Unit = {
    withMaterializer("recover") { materializer =>
      val result: Seq[String] = Await.result(
        Source(List("first", "boom", "unreached"))
          .map { value =>
            if value == "boom" then throw new IllegalStateException("expected failure")
            value.toUpperCase
          }
          .recover { case _: IllegalStateException => "RECOVERED" }
          .runWith(Sink.seq)(materializer),
        Timeout
      )

      assertEquals(List("FIRST", "RECOVERED"), result.toList)
    }
  }

  @Test
  def foldSinkAggregatesElementsWithoutLeakingActorSystem(): Unit = {
    withMaterializer("fold-sink") { materializer =>
      val result: Int = Await.result(
        Source(List(1, 2, 3, 4))
          .runWith(Sink.fold(0)(_ + _))(materializer),
        Timeout
      )

      assertEquals(10, result)
    }
  }

  @Test
  def restartSourceRetriesFailedSourceFactoryAndEmitsRecoveredElements(): Unit = {
    withMaterializer("restart-source") { materializer =>
      val attempts: AtomicInteger = AtomicInteger(0)
      val restartSettings: RestartSettings = RestartSettings(
        minBackoff = 10.millis,
        maxBackoff = 100.millis,
        randomFactor = 0.0
      ).withMaxRestarts(1, 1.second)

      val result: Seq[Int] = Await.result(
        RestartSource
          .onFailuresWithBackoff(restartSettings) { () =>
            if attempts.incrementAndGet() == 1 then
              Source.failed[Int](IllegalStateException("transient source failure"))
            else Source.single(42)
          }
          .runWith(Sink.seq)(materializer),
        Timeout
      )

      assertEquals(List(42), result.toList)
      assertEquals(2, attempts.get())
    }
  }

  private def withMaterializer(testName: String)(body: Materializer => Unit): Unit = {
    val system: ActorSystem = ActorSystem(s"akka-stream-$testName-test")
    try {
      val materializer: Materializer = Materializer(system)
      body(materializer)
    } finally {
      Await.result(system.terminate(), Timeout)
    }
  }
}
