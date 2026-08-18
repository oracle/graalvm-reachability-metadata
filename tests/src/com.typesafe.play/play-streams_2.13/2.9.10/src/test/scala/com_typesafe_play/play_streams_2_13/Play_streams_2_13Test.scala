/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_streams_2_13

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.Arrays
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.{List => JList}
import java.util.function.{Function => JFunction}
import java.util.zip.GZIPInputStream

import akka.actor.ActorSystem
import akka.japi.function.{Function => AkkaFunction}
import akka.japi.function.{Function2 => AkkaFunction2}
import akka.stream.Materializer
import akka.stream.javadsl.Flow
import akka.stream.javadsl.Sink
import akka.stream.javadsl.Source
import akka.stream.scaladsl.{Flow => ScalaFlow, Sink => ScalaSink, Source => ScalaSource}
import akka.util.ByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import play.api.libs.streams.AkkaStreams
import play.api.libs.streams.GzipFlow
import play.libs.streams.Accumulator

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters._

class Play_streams_2_13Test {
  import Play_streams_2_13Test._

  private var system: ActorSystem = _
  private var materializer: Materializer = _

  @BeforeEach
  def createActorSystem(): Unit = {
    system = ActorSystem.create("play-streams-accumulator-test")
    materializer = Materializer.matFromSystem(system)
  }

  @AfterEach
  def terminateActorSystem(): Unit = {
    if (system != null) {
      Await.result(system.terminate(), 10.seconds)
      system = null
      materializer = null
    }
  }

  @Test
  def composesSinkAccumulatorWithFlowAndResultMappings(): Unit = {
    val sumAccumulator: Accumulator[Integer, Integer] = Accumulator.fromSink(integerSumSink)
    val parseNumbers: Flow[String, Integer, _] = Flow.of(classOf[String]).map(
      new AkkaFunction[String, Integer] {
        override def apply(value: String): Integer = Integer.valueOf(value)
      }
    )
    val parsedAccumulator: Accumulator[String, Integer] = sumAccumulator.through(parseNumbers)
    val labelledAccumulator: Accumulator[String, String] = parsedAccumulator.map(
      new JFunction[Integer, String] {
        override def apply(sum: Integer): String = s"sum=${sum.intValue() * 2}"
      },
      DirectExecutor
    )
    val asynchronousAccumulator: Accumulator[String, String] = labelledAccumulator.mapFuture(
      new JFunction[String, CompletionStage[String]] {
        override def apply(label: String): CompletionStage[String] = completed(label.toUpperCase)
      },
      DirectExecutor
    )

    val result: String = awaitStage(
      asynchronousAccumulator.run(Source.from(Arrays.asList("1", "2", "3")), materializer)
    )

    assertThat(result).isEqualTo("SUM=12")
  }

  @Test
  def recoversFailedStreamsWithImmediateAndAsynchronousFallbacks(): Unit = {
    val sumAccumulator: Accumulator[Integer, Integer] = Accumulator.fromSink(integerSumSink)
    val recoveredAccumulator: Accumulator[Integer, Integer] = sumAccumulator.recover(
      new JFunction[Throwable, Integer] {
        override def apply(error: Throwable): Integer = Integer.valueOf(17)
      },
      DirectExecutor
    )
    val recoveredWithAccumulator: Accumulator[Integer, Integer] = sumAccumulator.recoverWith(
      new JFunction[Throwable, CompletionStage[Integer]] {
        override def apply(error: Throwable): CompletionStage[Integer] = completed(Integer.valueOf(23))
      },
      DirectExecutor
    )

    val recoveredResult: Integer = awaitStage(
      recoveredAccumulator.run(Source.failed[Integer](new IllegalStateException("failed-source")), materializer)
    )
    val recoveredWithResult: Integer = awaitStage(
      recoveredWithAccumulator.run(Source.failed[Integer](new IllegalArgumentException("failed-source")), materializer)
    )

    assertThat(recoveredResult).isEqualTo(Integer.valueOf(17))
    assertThat(recoveredWithResult).isEqualTo(Integer.valueOf(23))
  }

  @Test
  def exposesAccumulatedElementsAsSourceAndSinkRepresentations(): Unit = {
    val sourceAccumulator: Accumulator[String, Source[String, _]] = Accumulator.source[String]()
    val capturedSource: Source[String, _] = awaitStage(
      sourceAccumulator.run(Source.from(Arrays.asList("alpha", "beta", "gamma")), materializer)
    )
    val capturedElements: JList[String] = awaitStage(capturedSource.runWith(Sink.seq[String], materializer))

    val sinkAccumulator: Accumulator[String, JList[String]] = Accumulator.fromSink(Sink.seq[String])
    val sinkResult: JList[String] = awaitStage(
      Source.from(Arrays.asList("left", "right")).runWith(sinkAccumulator.toSink(), materializer)
    )

    assertThat(capturedElements).containsExactly("alpha", "beta", "gamma")
    assertThat(sinkResult).containsExactly("left", "right")
  }

  @Test
  def completesStrictDoneAndFlattenedAccumulators(): Unit = {
    val strictAccumulator: Accumulator[String, String] = Accumulator.strict(
      new JFunction[Optional[String], CompletionStage[String]] {
        override def apply(element: Optional[String]): CompletionStage[String] = {
          completed(element.orElse("empty").toUpperCase)
        }
      },
      Sink.fold[String, String]("", new AkkaFunction2[String, String, String] {
        override def apply(total: String, element: String): String = total + element
      })
    )
    val doneAccumulator: Accumulator[String, String] = Accumulator.done[String, String]("precomputed")
    val futureDoneAccumulator: Accumulator[String, String] = Accumulator.done[String, String](completed("future-value"))
    val flattenedAccumulator: Accumulator[Integer, Integer] = Accumulator.flatten(
      completed(Accumulator.fromSink[Integer, Integer](integerSumSink)),
      materializer
    )

    val strictResult: String = awaitStage(strictAccumulator.run("play", materializer))
    val doneResult: String = awaitStage(
      doneAccumulator.run(Source.from(Arrays.asList("ignored", "elements")), materializer)
    )
    val futureDoneResult: String = awaitStage(futureDoneAccumulator.run(materializer))
    val flattenedResult: Integer = awaitStage(
      flattenedAccumulator.run(Source.from(Arrays.asList(Integer.valueOf(4), Integer.valueOf(5))), materializer)
    )

    assertThat(strictResult).isEqualTo("PLAY")
    assertThat(doneResult).isEqualTo("precomputed")
    assertThat(futureDoneResult).isEqualTo("future-value")
    assertThat(flattenedResult).isEqualTo(Integer.valueOf(9))
  }

  @Test
  def routesSelectedElementsAroundScalaStreamProcessing(): Unit = {
    val processingFlow: ScalaFlow[String, String, _] = ScalaFlow[String].map(value => s"processed:$value")
    val bypassingFlow: ScalaFlow[String, String, _] = AkkaStreams
      .bypassWith[String, String, String] { value: String =>
        if (value.startsWith("cached:")) Right(s"bypassed:${value.stripPrefix("cached:")}")
        else Left(value)
      }
      .apply(processingFlow)

    val results: Seq[String] = Await.result(
      ScalaSource(List("cached:alpha", "beta", "cached:gamma", "delta"))
        .via(bypassingFlow)
        .runWith(ScalaSink.seq[String])(materializer),
      10.seconds
    )

    assertThat(results.asJava).containsExactlyInAnyOrder(
      "bypassed:alpha",
      "processed:beta",
      "bypassed:gamma",
      "processed:delta"
    )
  }

  @Test
  def compressesChunkedByteStringsWithScalaGzipFlow(): Unit = {
    val plainText: String = "Play streams compresses chunked data"
    val inputChunks: List[ByteString] = List(
      ByteString("Play streams ", StandardCharsets.UTF_8.name()),
      ByteString("compresses ", StandardCharsets.UTF_8.name()),
      ByteString("chunked data", StandardCharsets.UTF_8.name())
    )

    val compressedBytes: ByteString = Await.result(
      ScalaSource(inputChunks).via(GzipFlow.gzip()).runFold(ByteString.empty)(_ ++ _)(materializer),
      10.seconds
    )
    val decompressedText: String = gunzip(compressedBytes.toArray)

    assertThat(compressedBytes.length).isGreaterThan(0)
    assertThat(decompressedText).isEqualTo(plainText)
  }

  @Test
  def convertsBetweenJavaAndScalaAccumulatorApis(): Unit = {
    val javaAccumulator: Accumulator[Integer, Integer] = Accumulator.fromSink(integerSumSink)
    val scalaAccumulator: play.api.libs.streams.Accumulator[Integer, Integer] = javaAccumulator.asScala()
    val roundTrippedAccumulator: Accumulator[Integer, Integer] = scalaAccumulator.asJava

    val result: Integer = awaitStage(
      roundTrippedAccumulator.run(Source.from(Arrays.asList(Integer.valueOf(7), Integer.valueOf(8))), materializer)
    )

    assertThat(result).isEqualTo(Integer.valueOf(15))
  }
}

object Play_streams_2_13Test {
  private object DirectExecutor extends Executor {
    override def execute(command: Runnable): Unit = command.run()
  }

  private def integerSumSink: Sink[Integer, CompletionStage[Integer]] = {
    Sink.fold[Integer, Integer](Integer.valueOf(0), new AkkaFunction2[Integer, Integer, Integer] {
      override def apply(total: Integer, element: Integer): Integer = {
        Integer.valueOf(total.intValue() + element.intValue())
      }
    })
  }

  private def completed[A](value: A): CompletionStage[A] = CompletableFuture.completedFuture(value)

  private def gunzip(bytes: Array[Byte]): String = {
    val gzipInputStream: GZIPInputStream = new GZIPInputStream(new ByteArrayInputStream(bytes))
    try {
      new String(gzipInputStream.readAllBytes(), StandardCharsets.UTF_8)
    } finally {
      gzipInputStream.close()
    }
  }

  private def awaitStage[A](stage: CompletionStage[A]): A = {
    stage.toCompletableFuture.get(10, TimeUnit.SECONDS)
  }
}
