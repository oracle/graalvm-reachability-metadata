/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_akka.akka_stream_3

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorAttributes
import akka.stream.FlowShape
import akka.stream.IOResult
import akka.stream.KillSwitches
import akka.stream.Materializer
import akka.stream.OverflowStrategy
import akka.stream.QueueOfferResult
import akka.stream.SharedKillSwitch
import akka.stream.Supervision
import akka.stream.SystemMaterializer
import akka.stream.scaladsl.Broadcast
import akka.stream.scaladsl.FileIO
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Framing
import akka.stream.scaladsl.GraphDSL
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Zip
import akka.util.ByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Await
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

class Akka_stream_3Test {
  private val timeout: FiniteDuration = 10.seconds

  @Test
  def transformsAndReducesFiniteSource(): Unit = {
    withStreamSystem { (system: ActorSystem, materializer: Materializer) =>
      implicit val mat: Materializer = materializer
      implicit val ec: ExecutionContextExecutor = system.dispatcher

      val result: Future[Seq[Int]] = Source(1 to 6)
        .via(Flow[Int].map(_ * 2).filter(_ % 3 != 0))
        .mapAsync(2)(number => Future.successful(number + 1))
        .runWith(Sink.seq)

      assertThat(await(result).asJava).containsExactly(3, 5, 9, 11)
    }
  }

  @Test
  def combinesBranchesWithGraphDsl(): Unit = {
    withStreamSystem { (_: ActorSystem, materializer: Materializer) =>
      implicit val mat: Materializer = materializer

      val squareLabels: Flow[Int, (Int, String), NotUsed] = Flow.fromGraph(GraphDSL.create() {
        implicit builder: GraphDSL.Builder[NotUsed] =>
          import GraphDSL.Implicits._

          val broadcast = builder.add(Broadcast[Int](2))
          val zip = builder.add(Zip[Int, String]())
          val toSquareLabel: Flow[Int, String, NotUsed] = Flow[Int]
            .map(number => s"square=${number * number}")

          broadcast.out(0) ~> zip.in0
          broadcast.out(1) ~> toSquareLabel ~> zip.in1

          FlowShape(broadcast.in, zip.out)
      })

      val result: Future[Seq[String]] = Source(List(2, 3, 4))
        .via(squareLabels)
        .map { case (number: Int, label: String) => s"$number:$label" }
        .runWith(Sink.seq)

      assertThat(await(result).asJava).containsExactly("2:square=4", "3:square=9", "4:square=16")
    }
  }

  @Test
  def framesDelimitedByteStrings(): Unit = {
    withStreamSystem { (_: ActorSystem, materializer: Materializer) =>
      implicit val mat: Materializer = materializer

      val result: Future[Seq[String]] = Source(List(ByteString("alpha\nbe"), ByteString("ta\ngamma\n")))
        .via(Framing.delimiter(ByteString("\n"), 32, false))
        .map(_.utf8String)
        .runWith(Sink.seq)

      assertThat(await(result).asJava).containsExactly("alpha", "beta", "gamma")
    }
  }

  @Test
  def writesAndReadsByteStringsWithFileIO(): Unit = {
    withStreamSystem { (_: ActorSystem, materializer: Materializer) =>
      implicit val mat: Materializer = materializer
      val path: Path = Files.createTempFile("akka-stream-file-io-", ".txt")

      try {
        val payload: List[ByteString] = List(ByteString("alpha\n"), ByteString("beta\n"))
        val writeResult: IOResult = await(Source(payload).runWith(FileIO.toPath(path)))
        assertThat(writeResult.status.isSuccess).isTrue()
        assertThat(writeResult.count).isEqualTo(11L)

        val readResult: Future[ByteString] = FileIO.fromPath(path)
          .runWith(Sink.fold(ByteString.empty)((acc: ByteString, next: ByteString) => acc ++ next))
        assertThat(await(readResult).utf8String).isEqualTo("alpha\nbeta\n")
      } finally {
        Files.deleteIfExists(path)
      }
    }
  }

  @Test
  def foldsGroupedSubstreamsIndependently(): Unit = {
    withStreamSystem { (_: ActorSystem, materializer: Materializer) =>
      implicit val mat: Materializer = materializer

      val elements: List[(String, Int)] = List(
        "blue" -> 1,
        "red" -> 2,
        "blue" -> 3,
        "red" -> 4,
        "green" -> 5
      )
      val result: Future[Seq[String]] = Source(elements)
        .groupBy(3, element => element._1)
        .fold(("", 0)) { case ((currentColor: String, total: Int), (nextColor: String, value: Int)) =>
          val color: String = if (currentColor.isEmpty) nextColor else currentColor
          (color, total + value)
        }
        .mergeSubstreams
        .map { case (color: String, total: Int) => s"$color=$total" }
        .runWith(Sink.seq)

      assertThat(await(result).asJava).containsExactlyInAnyOrder("blue=4", "red=6", "green=5")
    }
  }

  @Test
  def materializesSourceQueueWithBackpressure(): Unit = {
    withStreamSystem { (_: ActorSystem, materializer: Materializer) =>
      implicit val mat: Materializer = materializer

      val (queue, result) = Source.queue[Int](4, OverflowStrategy.backpressure, 4)
        .map(_ * 10)
        .toMat(Sink.seq)(Keep.both)
        .run()

      assertThat(await(queue.offer(1))).isEqualTo(QueueOfferResult.Enqueued)
      assertThat(await(queue.offer(2))).isEqualTo(QueueOfferResult.Enqueued)
      assertThat(await(queue.offer(3))).isEqualTo(QueueOfferResult.Enqueued)
      queue.complete()

      assertThat(await(result).asJava).containsExactly(10, 20, 30)
    }
  }

  @Test
  def resumesStreamAfterSupervisedElementFailure(): Unit = {
    withStreamSystem { (_: ActorSystem, materializer: Materializer) =>
      implicit val mat: Materializer = materializer

      val decider: Supervision.Decider = {
        case _: ArithmeticException => Supervision.Resume
        case _ => Supervision.Stop
      }
      val result: Future[Seq[Int]] = Source(List(2, 0, 5))
        .map(number => 10 / number)
        .withAttributes(ActorAttributes.supervisionStrategy(decider))
        .runWith(Sink.seq)

      assertThat(await(result).asJava).containsExactly(5, 2)
    }
  }

  @Test
  def completesStreamsThroughSharedKillSwitchAfterShutdown(): Unit = {
    withStreamSystem { (_: ActorSystem, materializer: Materializer) =>
      implicit val mat: Materializer = materializer

      val killSwitch: SharedKillSwitch = KillSwitches.shared("shared-kill-switch")
      val beforeShutdown: Future[Seq[Int]] = Source(1 to 3)
        .via(killSwitch.flow[Int])
        .runWith(Sink.seq)

      assertThat(await(beforeShutdown).asJava).containsExactly(1, 2, 3)

      killSwitch.shutdown()

      val afterShutdown: Future[Seq[Int]] = Source(4 to 6)
        .via(killSwitch.flow[Int])
        .runWith(Sink.seq)

      assertThat(await(afterShutdown).asJava).isEmpty()
    }
  }

  private def withStreamSystem(testBody: (ActorSystem, Materializer) => Unit): Unit = {
    val systemId: Int = Akka_stream_3Test.systemIds.incrementAndGet()
    val system: ActorSystem = ActorSystem(s"akka-stream-$systemId")
    val materializer: Materializer = SystemMaterializer(system).materializer
    try {
      testBody(system, materializer)
    } finally {
      Await.result(system.terminate(), timeout)
    }
  }

  private def await[T](future: Future[T]): T = Await.result(future, timeout)
}

object Akka_stream_3Test {
  private val systemIds: AtomicInteger = new AtomicInteger(0)
}
