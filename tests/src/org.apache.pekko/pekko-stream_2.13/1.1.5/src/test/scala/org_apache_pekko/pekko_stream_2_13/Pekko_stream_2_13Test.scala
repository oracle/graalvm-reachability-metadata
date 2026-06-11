/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pekko.pekko_stream_2_13

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.{ClosedShape, Materializer, OverflowStrategy, QueueOfferResult}
import org.apache.pekko.stream.scaladsl.{Broadcast, Flow, Framing, GraphDSL, Keep, RunnableGraph, Sink, Source, StreamConverters}
import org.apache.pekko.util.ByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import java.io.{BufferedReader, ByteArrayInputStream, StringReader}
import java.nio.charset.StandardCharsets
import java.util.Locale
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.jdk.CollectionConverters._

class Pekko_stream_2_13Test {
  private val timeout: FiniteDuration = 10.seconds

  @Test
  def sourceFlowAndSinkMaterializeFiniteCollections(): Unit = {
    withSystem("source-flow-sink") { (_, materializer) =>
      implicit val mat: Materializer = materializer

      val result: Seq[String] = Await.result(
        Source(1 to 8)
          .filter(_ % 2 == 0)
          .map(number => s"item-${number * number}")
          .grouped(2)
          .map(_.mkString("[", ",", "]"))
          .runWith(Sink.seq),
        timeout)

      assertThat(result.asJava).containsExactly("[item-4,item-16]", "[item-36,item-64]")
    }
  }

  @Test
  def graphDslBroadcastsOneSourceIntoIndependentMaterializedSinks(): Unit = {
    withSystem("graph-dsl") { (_, materializer) =>
      implicit val mat: Materializer = materializer

      val graph: RunnableGraph[(Future[Seq[Int]], Future[Seq[String]])] = RunnableGraph.fromGraph(
        GraphDSL.createGraph(Sink.seq[Int], Sink.seq[String])((lengths, upperCase) => (lengths, upperCase)) {
          implicit builder => (lengthSink, upperCaseSink) =>
            import GraphDSL.Implicits._

            val words = builder.add(Source(List("alpha", "bb", "ccc")))
            val broadcast = builder.add(Broadcast[String](2))
            val lengths = builder.add(Flow[String].map(_.length))
            val upperCase = builder.add(Flow[String].map(_.toUpperCase(Locale.ROOT)))

            words ~> broadcast.in
            broadcast.out(0) ~> lengths ~> lengthSink
            broadcast.out(1) ~> upperCase ~> upperCaseSink
            ClosedShape
        })

      val (lengthsFuture, upperCaseFuture) = graph.run()

      assertThat(Await.result(lengthsFuture, timeout).asJava).containsExactly(5, 2, 3)
      assertThat(Await.result(upperCaseFuture, timeout).asJava).containsExactly("ALPHA", "BB", "CCC")
    }
  }

  @Test
  def framingDelimiterReassemblesChunkedByteStringsIntoLines(): Unit = {
    withSystem("framing") { (_, materializer) =>
      implicit val mat: Materializer = materializer

      val chunks: List[ByteString] = List(
        ByteString("alpha\nbe"),
        ByteString("ta\ngamma"))
      val lines: Seq[String] = Await.result(
        Source(chunks)
          .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 32, allowTruncation = true))
          .map(_.utf8String)
          .runWith(Sink.seq),
        timeout)

      assertThat(lines.asJava).containsExactly("alpha", "beta", "gamma")
    }
  }

  @Test
  def queueSourceAcceptsOfferedElementsAndCompletesDownstream(): Unit = {
    withSystem("queue-source") { (_, materializer) =>
      implicit val mat: Materializer = materializer

      val (queue, valuesFuture) = Source
        .queue[Int](4, OverflowStrategy.backpressure)
        .map(_ * 10)
        .toMat(Sink.seq[Int])(Keep.both)
        .run()

      val offerResults: List[QueueOfferResult] = List(1, 2, 3).map { value =>
        Await.result(queue.offer(value), timeout)
      }
      queue.complete()

      assertThat(offerResults.asJava).containsOnly(QueueOfferResult.Enqueued)
      assertThat(Await.result(valuesFuture, timeout).asJava).containsExactly(10, 20, 30)
    }
  }

  @Test
  def unfoldResourceReadsAndClosesFiniteResources(): Unit = {
    withSystem("unfold-resource") { (_, materializer) =>
      implicit val mat: Materializer = materializer

      val closeTracker: CloseTracker = new CloseTracker
      val result: Seq[String] = Await.result(
        Source
          .unfoldResource[String, BufferedReader](
            () => new BufferedReader(new StringReader("red\ngreen\nblue")),
            reader => Option(reader.readLine()),
            reader => {
              closeTracker.markClosed()
              reader.close()
            })
          .runWith(Sink.seq),
        timeout)

      assertThat(result.asJava).containsExactly("red", "green", "blue")
      assertThat(closeTracker.closed).isTrue
    }
  }

  @Test
  def streamConvertersReadInputStreamsAsByteStringSources(): Unit = {
    withSystem("stream-converters") { (_, materializer) =>
      implicit val mat: Materializer = materializer

      val input: Array[Byte] = "pekko-stream-input".getBytes(StandardCharsets.UTF_8)
      val result: ByteString = Await.result(
        StreamConverters
          .fromInputStream(() => new ByteArrayInputStream(input), chunkSize = 5)
          .runFold(ByteString.empty)((accumulator, bytes) => accumulator ++ bytes),
        timeout)

      assertThat(result.utf8String).isEqualTo("pekko-stream-input")
    }
  }

  @Test
  def mapAsyncAndFoldComposeAsynchronousStagesDeterministically(): Unit = {
    withSystem("map-async-fold") { (system, materializer) =>
      implicit val mat: Materializer = materializer
      implicit val executionContext = system.dispatcher

      val sumOfSquares: Int = Await.result(
        Source(1 to 5)
          .mapAsync(parallelism = 2)(number => Future(number * number))
          .fold(0)(_ + _)
          .runWith(Sink.head),
        timeout)

      assertThat(sumOfSquares).isEqualTo(55)
    }
  }

  @Test
  def recoverTransformsSupportedStageFailuresIntoFinalElements(): Unit = {
    withSystem("recover") { (_, materializer) =>
      implicit val mat: Materializer = materializer

      val result: Seq[Int] = Await.result(
        Source(List("1", "2", "not-a-number", "4"))
          .map(_.toInt)
          .recover { case _: NumberFormatException => -1 }
          .runWith(Sink.seq),
        timeout)

      assertThat(result.asJava).containsExactly(1, 2, -1)
    }
  }

  @Test
  def prefixAndTailMaterializesPrefixBeforeStreamingRemainder(): Unit = {
    withSystem("prefix-and-tail") { (system, materializer) =>
      implicit val mat: Materializer = materializer
      implicit val executionContext = system.dispatcher

      val result: (Seq[Int], Seq[Int]) = Await.result(
        Source(1 to 5)
          .prefixAndTail(2)
          .runWith(Sink.head)
          .flatMap { case (prefix, tail) =>
            tail.runWith(Sink.seq).map(tailValues => (prefix, tailValues))
          },
        timeout)

      assertThat(result._1.asJava).containsExactly(1, 2)
      assertThat(result._2.asJava).containsExactly(3, 4, 5)
    }
  }

  private def withSystem[A](name: String)(testBody: (ActorSystem, Materializer) => A): A = {
    val system: ActorSystem = ActorSystem(name)
    val materializer: Materializer = Materializer(system)
    try {
      testBody(system, materializer)
    } finally {
      Await.result(system.terminate(), timeout)
    }
  }

  private final class CloseTracker {
    @volatile var closed: Boolean = false

    def markClosed(): Unit = {
      closed = true
    }
  }
}
