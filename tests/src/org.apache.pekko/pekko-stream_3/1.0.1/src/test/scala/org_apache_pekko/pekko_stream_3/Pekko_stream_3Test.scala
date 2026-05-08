/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_stream_3

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Attributes
import org.apache.pekko.stream.FlowShape
import org.apache.pekko.stream.IOResult
import org.apache.pekko.stream.Inlet
import org.apache.pekko.stream.KillSwitches
import org.apache.pekko.stream.Outlet
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.QueueOfferResult
import org.apache.pekko.stream.SystemMaterializer
import org.apache.pekko.stream.UniqueKillSwitch
import org.apache.pekko.stream.scaladsl.Broadcast
import org.apache.pekko.stream.scaladsl.Compression
import org.apache.pekko.stream.scaladsl.FileIO
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.stream.scaladsl.Framing
import org.apache.pekko.stream.scaladsl.GraphDSL
import org.apache.pekko.stream.scaladsl.Keep
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.stream.scaladsl.ZipWith
import org.apache.pekko.stream.stage.GraphStage
import org.apache.pekko.stream.stage.GraphStageLogic
import org.apache.pekko.stream.stage.InHandler
import org.apache.pekko.stream.stage.OutHandler
import org.apache.pekko.util.ByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters.*

final class PrefixStage(prefix: String) extends GraphStage[FlowShape[Int, String]] {
  private val inlet: Inlet[Int] = Inlet[Int]("PrefixStage.in")
  private val outlet: Outlet[String] = Outlet[String]("PrefixStage.out")

  override val shape: FlowShape[Int, String] = FlowShape(inlet, outlet)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    setHandler(inlet, new InHandler {
      override def onPush(): Unit = push(outlet, s"$prefix-${grab(inlet)}")
    })

    setHandler(outlet, new OutHandler {
      override def onPull(): Unit = pull(inlet)
    })
  }
}

class Pekko_stream_3Test {
  private val timeout: FiniteDuration = 10.seconds

  @Test
  def runsFiniteSourceThroughFlowAndSink(): Unit = withActorSystem("finite-flow") { system =>
    val materializer = SystemMaterializer(system).materializer
    val streamResult: Future[Int] = Source(1 to 8)
      .via(Flow[Int].map(_ * 3).filter(_ % 2 == 0))
      .fold(0)(_ + _)
      .runWith(Sink.head)(materializer)

    assertThat(await(streamResult)).isEqualTo(60)
  }

  @Test
  def materializesQueueAndProcessesBackpressuredElements(): Unit = withActorSystem("queue-source") { system =>
    val materializer = SystemMaterializer(system).materializer
    val (queue, streamResult) = Source
      .queue[Int](4, OverflowStrategy.backpressure)
      .map(_ * 10)
      .toMat(Sink.seq)(Keep.both)
      .run()(materializer)

    assertThat(await(queue.offer(1))).isEqualTo(QueueOfferResult.Enqueued)
    assertThat(await(queue.offer(2))).isEqualTo(QueueOfferResult.Enqueued)
    assertThat(await(queue.offer(3))).isEqualTo(QueueOfferResult.Enqueued)
    queue.complete()

    assertThat(await(streamResult).toList.asJava).containsExactly(10, 20, 30)
  }

  @Test
  def composesFanOutGraphWithBroadcastAndZip(): Unit = withActorSystem("fan-out-graph") { system =>
    val materializer = SystemMaterializer(system).materializer
    val addOriginalToDouble: Flow[Int, Int, ?] = Flow.fromGraph(GraphDSL.create() { builder =>
      given GraphDSL.Builder[?] = builder
      import GraphDSL.Implicits.*

      val broadcast = builder.add(Broadcast[Int](2))
      val multiplyByTwo = builder.add(Flow[Int].map(_ * 2))
      val zip = builder.add(ZipWith[Int, Int, Int]((left: Int, right: Int) => left + right))

      broadcast.out(0) ~> multiplyByTwo ~> zip.in0
      broadcast.out(1) ~> zip.in1
      FlowShape(broadcast.in, zip.out)
    })

    val streamResult: Future[Seq[Int]] = Source(List(1, 2, 3))
      .via(addOriginalToDouble)
      .runWith(Sink.seq)(materializer)

    assertThat(await(streamResult).toList.asJava).containsExactly(3, 6, 9)
  }

  @Test
  def aggregatesSubstreamsAndMergesThem(): Unit = withActorSystem("substreams") { system =>
    val materializer = SystemMaterializer(system).materializer
    val streamResult: Future[Seq[Int]] = Source(1 to 6)
      .groupBy(2, (element: Int) => element % 2)
      .fold(0)(_ + _)
      .mergeSubstreams
      .runWith(Sink.seq)(materializer)

    assertThat(await(streamResult).toList.sorted.asJava).containsExactly(9, 12)
  }

  @Test
  def framesByteStringDataFromFileIO(): Unit = withActorSystem("file-io") { system =>
    val materializer = SystemMaterializer(system).materializer
    val file: Path = Files.createTempFile("pekko-stream-", ".txt")

    try {
      val lines = List("alpha\n", "beta\n", "gamma\n")
      val expectedBytes = lines.map(_.getBytes(StandardCharsets.UTF_8).length.toLong).sum
      val writeResult: IOResult = await(
        Source(lines)
          .map((line: String) => ByteString(line))
          .runWith(FileIO.toPath(file))(materializer)
      )

      assertThat(writeResult.count).isEqualTo(expectedBytes)
      assertThat(writeResult.status.isSuccess).isTrue()

      val framedLines: Future[Seq[String]] = FileIO.fromPath(file)
        .via(Framing.delimiter(ByteString("\n"), 64, allowTruncation = true))
        .map(_.utf8String)
        .runWith(Sink.seq)(materializer)

      assertThat(await(framedLines).toList.asJava).containsExactly("alpha", "beta", "gamma")
    } finally {
      Files.deleteIfExists(file)
    }
  }

  @Test
  def compressesAndDecompressesByteStringStream(): Unit = withActorSystem("compression") { system =>
    val materializer = SystemMaterializer(system).materializer
    val payload: ByteString = ByteString("alpha\nbeta\ngamma\n")
    val streamResult: Future[ByteString] = Source(List(payload))
      .via(Compression.gzip)
      .via(Compression.gunzip())
      .runFold(ByteString.empty)(_ ++ _)(materializer)

    assertThat(await(streamResult)).isEqualTo(payload)
  }

  @Test
  def recoversFromStreamFailure(): Unit = withActorSystem("recover") { system =>
    val materializer = SystemMaterializer(system).materializer
    val streamResult: Future[Seq[Int]] = Source(List(1, 0, 2))
      .map(10 / _)
      .recover { case _: ArithmeticException => -1 }
      .runWith(Sink.seq)(materializer)

    assertThat(await(streamResult).toList.asJava).containsExactly(10, -1)
  }

  @Test
  def runsCustomGraphStage(): Unit = withActorSystem("custom-stage") { system =>
    val materializer = SystemMaterializer(system).materializer
    val streamResult: Future[Seq[String]] = Source(List(1, 2, 3))
      .via(Flow.fromGraph(new PrefixStage("element")))
      .runWith(Sink.seq)(materializer)

    assertThat(await(streamResult).toList.asJava)
      .containsExactly("element-1", "element-2", "element-3")
  }

  @Test
  def shutsDownStreamWithKillSwitch(): Unit = withActorSystem("kill-switch") { system =>
    val materializer = SystemMaterializer(system).materializer
    val (killSwitch: UniqueKillSwitch, streamResult: Future[Seq[Int]]) = Source
      .maybe[Int]
      .viaMat(KillSwitches.single)(Keep.right)
      .toMat(Sink.seq)(Keep.both)
      .run()(materializer)

    killSwitch.shutdown()

    assertThat(await(streamResult).toList.asJava).isEmpty()
  }

  private def withActorSystem(testName: String)(testBody: ActorSystem => Unit): Unit = {
    val system: ActorSystem = ActorSystem(s"pekko-stream-$testName")
    try {
      testBody(system)
    } finally {
      await(system.terminate())
    }
  }

  private def await[T](future: Future[T]): T = Await.result(future, timeout)
}
