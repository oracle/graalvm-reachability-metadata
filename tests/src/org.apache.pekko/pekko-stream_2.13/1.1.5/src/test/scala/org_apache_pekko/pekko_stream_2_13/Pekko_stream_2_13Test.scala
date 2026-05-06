/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_stream_2_13

import org.apache.pekko.Done
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.ClosedShape
import org.apache.pekko.stream.IOResult
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.QueueOfferResult
import org.apache.pekko.stream.SystemMaterializer
import org.apache.pekko.stream.scaladsl.Broadcast
import org.apache.pekko.stream.scaladsl.Compression
import org.apache.pekko.stream.scaladsl.FileIO
import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.stream.scaladsl.Framing
import org.apache.pekko.stream.scaladsl.GraphDSL
import org.apache.pekko.stream.scaladsl.Keep
import org.apache.pekko.stream.scaladsl.RunnableGraph
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.stream.scaladsl.ZipWith
import org.apache.pekko.util.ByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import java.nio.file.Files
import java.nio.file.Path
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters._

class Pekko_stream_2_13Test {
  private val Timeout = 10.seconds

  @Test
  def runsSourceFlowAndSinkPipelines(): Unit = {
    withStreamSystem("source-flow-sink") { (_, materializer, _) =>
      implicit val implicitMaterializer: Materializer = materializer

      val result: Seq[Int] = Await.result(
        Source(1 to 10)
          .via(Flow[Int].map(_ * 2).filter(_ % 4 == 0))
          .grouped(3)
          .map(_.sum)
          .runWith(Sink.seq[Int]),
        Timeout
      )

      assertThat(result.toList.asJava).containsExactly(24, 36)
    }
  }

  @Test
  def materializesAndCompletesBackpressuredQueues(): Unit = {
    withStreamSystem("source-queue") { (_, materializer, _) =>
      implicit val implicitMaterializer: Materializer = materializer

      val (queue, resultFuture) = Source
        .queue[Int](bufferSize = 4, OverflowStrategy.backpressure)
        .map(value => value * value)
        .toMat(Sink.seq[Int])(Keep.both)
        .run()

      assertThat(Await.result(queue.offer(2), Timeout)).isEqualTo(QueueOfferResult.Enqueued)
      assertThat(Await.result(queue.offer(3), Timeout)).isEqualTo(QueueOfferResult.Enqueued)
      assertThat(Await.result(queue.offer(4), Timeout)).isEqualTo(QueueOfferResult.Enqueued)

      queue.complete()

      val result: Seq[Int] = Await.result(resultFuture, Timeout)
      val completion: Done = Await.result(queue.watchCompletion(), Timeout)
      assertThat(completion).isEqualTo(Done)
      assertThat(result.toList.asJava).containsExactly(4, 9, 16)
    }
  }

  @Test
  def buildsRunnableGraphsWithFanOutAndFanInStages(): Unit = {
    withStreamSystem("graph-dsl") { (_, materializer, _) =>
      implicit val implicitMaterializer: Materializer = materializer

      val graph: RunnableGraph[Future[Seq[Int]]] = RunnableGraph.fromGraph(
        GraphDSL.createGraph(Sink.seq[Int]) { implicit builder: GraphDSL.Builder[Future[Seq[Int]]] => sink =>
          import GraphDSL.Implicits._

          val broadcast = builder.add(Broadcast[Int](2))
          val zipWith = builder.add(ZipWith[Int, Int, Int]((left, right) => left + right))

          Source(1 to 4) ~> broadcast.in
          broadcast.out(0) ~> Flow[Int].map(_ * 10) ~> zipWith.in0
          broadcast.out(1) ~> Flow[Int].map(_ + 1) ~> zipWith.in1
          zipWith.out ~> sink

          ClosedShape
        }
      )

      val result: Seq[Int] = Await.result(graph.run(), Timeout)
      assertThat(result.toList.asJava).containsExactly(12, 23, 34, 45)
    }
  }

  @Test
  def framesDelimitedByteStringsAcrossChunkBoundaries(): Unit = {
    withStreamSystem("framing") { (_, materializer, _) =>
      implicit val implicitMaterializer: Materializer = materializer

      val result: Seq[String] = Await.result(
        Source(List(ByteString("alpha\nbe"), ByteString("ta\ngamma\n")))
          .via(Framing.delimiter(ByteString("\n"), 64, allowTruncation = false))
          .map(_.utf8String)
          .runWith(Sink.seq[String]),
        Timeout
      )

      assertThat(result.toList.asJava).containsExactly("alpha", "beta", "gamma")
    }
  }

  @Test
  def groupsAndMergesSubstreamsByKey(): Unit = {
    withStreamSystem("substreams") { (_, materializer, _) =>
      implicit val implicitMaterializer: Materializer = materializer
      val words: List[String] = List("ant", "ape", "bear", "bee", "cat", "bat")

      val groupedCounts: Seq[(String, Int)] = Await.result(
        Source(words)
          .groupBy(maxSubstreams = 4, word => word.charAt(0))
          .fold("" -> 0) { case ((_, count), word) =>
            word.substring(0, 1) -> (count + 1)
          }
          .mergeSubstreams
          .runWith(Sink.seq[(String, Int)]),
        Timeout
      )

      val formatted: List[String] = groupedCounts.toList.sortBy(_._1).map { case (key, count) => s"$key:$count" }
      assertThat(formatted.asJava).containsExactly("a:2", "b:3", "c:1")
    }
  }

  @Test
  def recoversFailedStreamsWithFallbackElements(): Unit = {
    withStreamSystem("recover") { (_, materializer, _) =>
      implicit val implicitMaterializer: Materializer = materializer

      val result: Seq[Int] = Await.result(
        Source(List("1", "bad", "2"))
          .map(_.toInt)
          .recover { case _: NumberFormatException => -1 }
          .runWith(Sink.seq[Int]),
        Timeout
      )

      assertThat(result.toList.asJava).containsExactly(1, -1)
    }
  }

  @Test
  def writesAndReadsFilesAsByteStringStreams(): Unit = {
    val path: Path = Files.createTempFile("pekko-stream-", ".txt")
    try {
      withStreamSystem("file-io") { (_, materializer, _) =>
        implicit val implicitMaterializer: Materializer = materializer
        val chunks: List[ByteString] = List(ByteString("first\n"), ByteString("second\n"))

        val writeResult: IOResult = Await.result(Source(chunks).runWith(FileIO.toPath(path)), Timeout)
        assertThat(writeResult.wasSuccessful).isTrue
        assertThat(writeResult.count).isEqualTo(13L)

        val readBytes: ByteString = Await.result(
          FileIO.fromPath(path, chunkSize = 4).runFold(ByteString.empty)(_ ++ _),
          Timeout
        )
        assertThat(readBytes.utf8String).isEqualTo("first\nsecond\n")
      }
    } finally {
      Files.deleteIfExists(path)
    }
  }

  @Test
  def compressesAndDecompressesByteStringStreams(): Unit = {
    withStreamSystem("compression") { (_, materializer, _) =>
      implicit val implicitMaterializer: Materializer = materializer
      val text: String = "Apache Pekko streams keep data flowing.\n" * 8

      val compressed: ByteString = Await.result(
        Source.single(ByteString(text)).via(Compression.gzip).runFold(ByteString.empty)(_ ++ _),
        Timeout
      )
      assertThat(compressed.length).isGreaterThan(0)
      assertThat(compressed).isNotEqualTo(ByteString(text))

      val decompressed: ByteString = Await.result(
        Source.single(compressed).via(Compression.gunzip()).runFold(ByteString.empty)(_ ++ _),
        Timeout
      )
      assertThat(decompressed.utf8String).isEqualTo(text)
    }
  }

  @Test
  def preservesElementOrderAcrossAsynchronousStages(): Unit = {
    withStreamSystem("map-async") { (_, materializer, executionContext) =>
      implicit val implicitMaterializer: Materializer = materializer
      implicit val implicitExecutionContext: ExecutionContext = executionContext
      val first: Promise[Int] = Promise[Int]()
      val second: Promise[Int] = Promise[Int]()
      val third: Promise[Int] = Promise[Int]()
      val completions: Map[Int, Promise[Int]] = Map(1 -> first, 2 -> second, 3 -> third)

      val resultFuture: Future[Seq[Int]] = Source(List(1, 2, 3))
        .mapAsync(parallelism = 3) { value =>
          completions(value).future.map(_ * 10)
        }
        .runWith(Sink.seq[Int])

      third.success(3)
      second.success(2)
      first.success(1)

      val result: Seq[Int] = Await.result(resultFuture, Timeout)
      assertThat(result.toList.asJava).containsExactly(10, 20, 30)
    }
  }

  @Test
  def combinesFuturesScanningAndTerminalSinks(): Unit = {
    withStreamSystem("futures-and-sinks") { (_, materializer, executionContext) =>
      implicit val implicitMaterializer: Materializer = materializer
      implicit val implicitExecutionContext: ExecutionContext = executionContext

      val result: Int = Await.result(
        Source
          .future(Future.successful(5))
          .concat(Source.single(7))
          .via(Flow[Int].scan(0)(_ + _).drop(1))
          .runWith(Sink.last[Int]),
        Timeout
      )

      assertThat(result).isEqualTo(12)
    }
  }

  private def withStreamSystem[A](name: String)(block: (ActorSystem, Materializer, ExecutionContext) => A): A = {
    val system: ActorSystem = ActorSystem(s"PekkoStreamTest-$name")
    try {
      val materializer: Materializer = SystemMaterializer(system).materializer
      block(system, materializer, system.dispatcher)
    } finally {
      Await.result(system.terminate(), Timeout)
    }
  }
}
