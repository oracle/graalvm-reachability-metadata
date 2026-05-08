/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_playframework.play_streams_3

import org.apache.pekko.actor.{Actor, ActorRef, ActorSystem, Props, Status}
import org.apache.pekko.stream.{Materializer, OverflowStrategy, SourceShape, SystemMaterializer}
import org.apache.pekko.stream.scaladsl.{Flow, GraphDSL, Sink, Source}
import org.apache.pekko.util.ByteString
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.reactivestreams.{Processor, Subscription}
import play.api.libs.streams.{Accumulator, ActorFlow, GzipFlow, PekkoStreams, Probes}
import play.libs.streams.{Accumulator as JavaAccumulator}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.charset.StandardCharsets
import java.util.concurrent.{CompletionStage, Executor, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicLong}
import java.util.function.Function
import java.util.zip.GZIPInputStream
import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext, Future}

class Play_streams_3Test {
  private val timeout: FiniteDuration = 10.seconds
  private val actorSystemCounter: AtomicInteger = new AtomicInteger()
  private val directExecutor: Executor = (command: Runnable) => command.run()

  @Test
  def accumulatorRunsPekkoSinksAndTransformsResults(): Unit = withActorSystem { (system, materializer) =>
    given Materializer = materializer
    given ExecutionContext = system.dispatcher

    val accumulator: Accumulator[Int, Int] = Accumulator(Sink.fold[String, Int]("")(_ + _.toString))
      .map(_.toInt)
      .mapFuture(value => Future.successful(value + 1))

    assertEquals(124, await(accumulator.run(Source(List(1, 2, 3)))))
    assertEquals(10, await(accumulator.run(9)))

    val throughFlow: Flow[String, Int, ?] = Flow[String].map(_.length)
    val lengthAccumulator: Accumulator[String, Int] = accumulator.through(throughFlow)
    assertEquals(24, await(lengthAccumulator.run(Source(List("aa", "bbb")))))
  }

  @Test
  def strictAccumulatorUsesStrictInputsAndSinkFallback(): Unit = withActorSystem { (system, materializer) =>
    given Materializer = materializer
    given ExecutionContext = system.dispatcher

    val fallbackSink: Sink[Int, Future[String]] = Sink.fold[String, Int]("stream:") { (accumulator, element) =>
      if accumulator == "stream:" then s"$accumulator$element" else s"$accumulator,$element"
    }
    val strictAccumulator: Accumulator[Int, String] = Accumulator.strict[Int, String](
      maybeElement => Future.successful(s"strict:${maybeElement.fold("empty")(_.toString)}"),
      fallbackSink
    )

    assertEquals("strict:7", await(strictAccumulator.run(7)))
    assertEquals("strict:empty", await(strictAccumulator.run()))
    assertEquals("stream:1,2,3", await(strictAccumulator.run(Source(List(1, 2, 3)))))
  }

  @Test
  def accumulatorFlattensFutureAccumulatorsAndRecoversFailures(): Unit = withActorSystem { (system, materializer) =>
    given Materializer = materializer
    given ExecutionContext = system.dispatcher

    val failingAccumulator: Accumulator[Int, Int] = Accumulator(Sink.fold[Int, Int](0) { (_, element) =>
      if element == 2 then throw new IllegalArgumentException("boom") else element
    })

    val recovered: Accumulator[Int, Int] = failingAccumulator.recover { case _: IllegalArgumentException => -1 }
    val recoveredWith: Accumulator[Int, Int] = failingAccumulator.recoverWith {
      case _: IllegalArgumentException => Future.successful(-2)
    }

    assertEquals(-1, await(recovered.run(Source(List(1, 2)))))
    assertEquals(-2, await(recoveredWith.run(Source(List(1, 2)))))

    val summingAccumulator: Accumulator[Int, Int] = Accumulator(Sink.fold[Int, Int](0)(_ + _))
    val flattened: Accumulator[Int, Int] = Accumulator.flatten(Future.successful(summingAccumulator))
    assertEquals(6, await(flattened.run(Source(List(1, 2, 3)))))

  }

  @Test
  def accumulatorSourcesDoneValuesAndJavaAdaptersRoundTrip(): Unit = withActorSystem { (system, materializer) =>
    given Materializer = materializer
    given ExecutionContext = system.dispatcher

    val sourceAccumulator: Accumulator[String, Source[String, ?]] = Accumulator.source
    val capturedSource: Source[String, ?] = await(sourceAccumulator.run(Source(List("red", "blue"))))
    assertEquals(List("red", "blue"), await(capturedSource.runWith(Sink.seq)).toList)

    assertEquals("ready", await(Accumulator.done("ready").run()))

    val scalaAccumulator: Accumulator[Int, Int] = Accumulator(Sink.fold[Int, Int](0)(_ + _))
    val javaAccumulator: JavaAccumulator[Int, Int] = scalaAccumulator.asJava
    val mappedJavaAccumulator: JavaAccumulator[Int, Int] = javaAccumulator.map(
      new Function[Int, Int] {
        override def apply(value: Int): Int = value * 2
      },
      directExecutor
    )

    assertEquals(8, awaitStage(mappedJavaAccumulator.run(4, materializer)))
    assertEquals(10, await(mappedJavaAccumulator.asScala().run(Source(List(2, 3)))))

    val javaDone: JavaAccumulator[String, String] = JavaAccumulator.done("java-ready")
    assertEquals("java-ready", awaitStage(javaDone.run(materializer)))
  }

  @Test
  def gzipFlowEmitsValidGzipPayload(): Unit = withActorSystem { (_, materializer) =>
    given Materializer = materializer

    val plainText: String = "Play streams can compress several ByteString chunks."
    val chunks: List[ByteString] = List(
      ByteString.fromString("Play streams can ", StandardCharsets.UTF_8),
      ByteString.fromString("compress several ByteString chunks.", StandardCharsets.UTF_8)
    )

    val compressedChunks: Seq[ByteString] = await(Source(chunks).via(GzipFlow.gzip()).runWith(Sink.seq))
    val compressed: ByteString = compressedChunks.foldLeft(ByteString.emptyByteString)(_ ++ _)

    assertEquals(plainText, gunzip(compressed.toArray))
  }

  @Test
  def pekkoStreamsBypassesSelectedElementsAndPassesThroughIgnoreHelpers(): Unit = withActorSystem { (_, materializer) =>
    given Materializer = materializer

    val split: Int => Either[Int, String] = value =>
      if value % 2 == 0 then Right(s"bypass:$value") else Left(value)
    val bypassingFlow: Flow[Int, String, ?] = PekkoStreams.bypassWith[Int, Int, String](split) {
      Flow[Int].map(value => s"flow:${value * 2}")
    }

    assertEquals(
      List("flow:2", "bypass:2", "flow:6"),
      await(Source(List(1, 2, 3)).via(bypassingFlow).runWith(Sink.seq)).toList
    )

    assertEquals(
      List("first", "second"),
      await(Source(List("first", "second")).via(PekkoStreams.ignoreAfterFinish[String]).take(2).runWith(Sink.seq)).toList
    )
    assertEquals(
      List(1, 2, 3),
      await(Source(List(1, 2, 3)).via(PekkoStreams.ignoreAfterCancellation[Int]).runWith(Sink.seq)).toList
    )
  }

  @Test
  def pekkoStreamsOnlyFirstCanFinishMergeWaitsForPrimaryInput(): Unit = withActorSystem { (system, materializer) =>
    given Materializer = materializer
    given ExecutionContext = system.dispatcher

    val primaryElement = scala.concurrent.Promise[String]()
    val secondaryCompleted = scala.concurrent.Promise[Unit]()
    val mergedSource: Source[String, ?] = Source.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits.*

      val merge = builder.add(PekkoStreams.onlyFirstCanFinishMerge[String](2))
      Source.future(primaryElement.future) ~> merge.in(0)
      Source
        .single("secondary")
        .watchTermination() { (_, completion) =>
          completion.foreach(_ => secondaryCompleted.success(()))
        } ~> merge.in(1)
      SourceShape(merge.out)
    })

    val result: Future[Seq[String]] = mergedSource.runWith(Sink.seq)
    await(secondaryCompleted.future)
    assertFalse(result.isCompleted)

    primaryElement.success("primary")
    val resultValues: Seq[String] = await(result)
    assertEquals(2, resultValues.size)
    assertEquals(Set("primary", "secondary"), resultValues.toSet)
  }

  @Test
  def actorFlowPublishesRepliesFromBackingActor(): Unit = withActorSystem { (system, materializer) =>
    given Materializer = materializer

    val flow: Flow[Int, String, ?] = ActorFlow.actorRef[Int, String](
      output => Props(new ReplyActor(output)),
      8,
      OverflowStrategy.fail
    )(system, materializer)

    assertEquals(
      List("reply:2", "reply:4", "reply:6"),
      await(Source(List(1, 2, 3)).via(flow).take(3).runWith(Sink.seq)).toList
    )
  }

  @Test
  def probesWrapFlowsAndPublishersWithoutChangingElements(): Unit = withActorSystem { (_, materializer) =>
    given Materializer = materializer

    val flowResult: Seq[String] = await(
      Source(List("alpha", "beta"))
        .via(Probes.flowProbe[String]("flow-probe", value => s"flow=$value"))
        .runWith(Sink.seq)
    )
    assertEquals(List("alpha", "beta"), flowResult.toList)

    val publisher = Source(List("left", "right")).runWith(Sink.asPublisher[String](fanout = false))
    val probedPublisher = Probes.publisherProbe[String]("publisher-probe", publisher, value => s"publisher=$value")
    assertEquals(List("left", "right"), await(Source.fromPublisher(probedPublisher).runWith(Sink.seq)).toList)
  }

  @Test
  def probesWrapProcessorsAndSubscriptionsWithoutChangingSignals(): Unit = withActorSystem { (_, materializer) =>
    given Materializer = materializer

    val processor: Processor[String, String] = Flow[String].map(_.reverse).toProcessor.run()
    val probedProcessor: Processor[String, String] = Probes.processorProbe[String, String](
      "processor-probe",
      processor,
      value => s"input=$value",
      value => s"output=$value"
    )

    val processorResult: Seq[String] = await(
      Source(List("abc", "def"))
        .via(Flow.fromProcessor(() => probedProcessor))
        .runWith(Sink.seq)
    )
    assertEquals(List("cba", "fed"), processorResult.toList)

    val requested: AtomicLong = new AtomicLong()
    val cancelled: AtomicBoolean = new AtomicBoolean(false)
    val subscription: Subscription = new Subscription {
      override def request(elements: Long): Unit = requested.addAndGet(elements)

      override def cancel(): Unit = cancelled.set(true)
    }

    val probedSubscription: Subscription = Probes.subscriptionProbe(
      "subscription-probe",
      subscription,
      System.nanoTime()
    )
    probedSubscription.request(3L)
    probedSubscription.cancel()

    assertEquals(3L, requested.get())
    assertTrue(cancelled.get())
  }

  private def withActorSystem(testCode: (ActorSystem, Materializer) => Unit): Unit = {
    val systemName: String = s"playStreamsTest${actorSystemCounter.incrementAndGet()}"
    val system: ActorSystem = ActorSystem(systemName)
    val materializer: Materializer = SystemMaterializer(system).materializer
    try {
      testCode(system, materializer)
    } finally {
      await(system.terminate())
    }
  }

  private def await[T](future: Future[T]): T = Await.result(future, timeout)

  private def awaitStage[T](stage: CompletionStage[T]): T = stage.toCompletableFuture.get(timeout.toSeconds, TimeUnit.SECONDS)

  private def gunzip(bytes: Array[Byte]): String = {
    val buffer: Array[Byte] = new Array[Byte](128)
    val output: ByteArrayOutputStream = new ByteArrayOutputStream()
    val input: GZIPInputStream = new GZIPInputStream(new ByteArrayInputStream(bytes))
    try {
      var read: Int = input.read(buffer)
      while read != -1 do {
        output.write(buffer, 0, read)
        read = input.read(buffer)
      }
    } finally {
      input.close()
      output.close()
    }
    output.toString(StandardCharsets.UTF_8)
  }

  private final class ReplyActor(output: ActorRef) extends Actor {
    override def receive: PartialFunction[Any, Unit] = {
      case value: Int => output ! s"reply:${value * 2}"
      case _: Status.Success => context.stop(self)
      case Status.Failure(_) => context.stop(self)
    }
  }
}
