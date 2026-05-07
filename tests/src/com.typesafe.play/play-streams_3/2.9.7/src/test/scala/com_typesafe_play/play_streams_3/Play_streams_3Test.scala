/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_streams_3

import java.util.Arrays
import java.util.Locale
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.*

import akka.Done
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.Status
import akka.stream.Materializer
import akka.stream.OverflowStrategy
import akka.stream.SourceShape
import akka.stream.SystemMaterializer
import akka.stream.scaladsl.Compression
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.GraphDSL
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.reactivestreams.Processor
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import play.api.libs.streams.Accumulator
import play.api.libs.streams.ActorFlow
import play.api.libs.streams.AkkaStreams
import play.api.libs.streams.GzipFlow
import play.api.libs.streams.Probes

class Play_streams_3Test {
  import Play_streams_3Test.*

  @Test
  def scalaAccumulatorTransformsInputsAndResults(): Unit = {
    withActorSystem("accumulator-transforms") { (_, materializer) =>
      implicit val implicitMaterializer: Materializer = materializer
      implicit val executionContext: ExecutionContext = materializer.executionContext

      val accumulator: Accumulator[Int, Int] = Accumulator(Sink.fold[Int, Int](0)(_ + _))
      val parsed: Accumulator[String, Int] = accumulator
        .through(Flow[String].map(_.toInt))
        .map(_ * 2)
        .mapFuture(value => Future.successful(value + 1))

      assertThat(await(parsed.run(Source(List("1", "2", "3"))))).isEqualTo(13)
      assertThat(await(parsed.run("4"))).isEqualTo(9)
      assertThat(await(accumulator.run())).isEqualTo(0)
      assertThat(await(Source(List(5, 6)).runWith(accumulator.toSink))).isEqualTo(11)
    }
  }

  @Test
  def scalaAccumulatorRecoversStrictlyAndForStreamFallback(): Unit = {
    withActorSystem("accumulator-recovery") { (_, materializer) =>
      implicit val implicitMaterializer: Materializer = materializer
      implicit val executionContext: ExecutionContext = materializer.executionContext

      val failed: Accumulator[Any, Int] = Accumulator.done(Future.failed[Int](new IllegalStateException("boom")))
      assertThat(await(failed.recover { case _: IllegalStateException => 7 }.run())).isEqualTo(7)
      assertThat(await(failed.recoverWith { case _: IllegalStateException => Future.successful(9) }.run())).isEqualTo(9)

      val strict: Accumulator[String, String] = Accumulator.strict[String, String](
        {
          case Some(value) => Future.successful(s"single:$value")
          case None        => Future.successful("empty")
        },
        Sink.fold[String, String]("")(_ + _)
      )

      assertThat(await(strict.run())).isEqualTo("empty")
      assertThat(await(strict.run("alpha"))).isEqualTo("single:alpha")
      assertThat(await(strict.run(Source(List("a", "b", "c"))))).isEqualTo("abc")
    }
  }

  @Test
  def accumulatorCanExposeSourcesFlattenFuturesAndRoundTripToJavaApi(): Unit = {
    withActorSystem("accumulator-source") { (_, materializer) =>
      implicit val implicitMaterializer: Materializer = materializer
      implicit val executionContext: ExecutionContext = materializer.executionContext

      val sourceAccumulator: Accumulator[Int, Source[Int, ?]] = Accumulator.source[Int]
      val producedSource: Source[Int, ?] = await(sourceAccumulator.run(Source(List(1, 2, 3, 4))))
      assertThat(await(producedSource.runWith(Sink.seq[Int])).asJava).containsExactly(1, 2, 3, 4)

      val flattened: Accumulator[Int, Int] = Accumulator.flatten(
        Future.successful(Accumulator(Sink.fold[Int, Int](10)(_ + _)))
      )
      assertThat(await(flattened.run(Source(List(1, 2, 3))))).isEqualTo(16)

      val scalaAccumulator: Accumulator[String, String] = Accumulator(Sink.fold[String, String]("")(_ + _))
      val javaAccumulator: play.libs.streams.Accumulator[String, String] = scalaAccumulator.asJava
      val javaResult: CompletionStage[String] = javaAccumulator.run(
        akka.stream.javadsl.Source.from(Arrays.asList("j", "a", "v", "a")),
        materializer
      )
      assertThat(awaitStage(javaResult)).isEqualTo("java")

      val roundTripped: Accumulator[String, String] = javaAccumulator.asScala()
      assertThat(await(roundTripped.run(Source(List("s", "c", "a", "l", "a"))))).isEqualTo("scala")
    }
  }

  @Test
  def akkaStreamsBypassRoutesSelectedElementsAroundInnerFlow(): Unit = {
    withActorSystem("akka-streams-bypass") { (_, materializer) =>
      implicit val implicitMaterializer: Materializer = materializer
      val route: Int => Either[Int, String] = value =>
        if (value % 2 == 0) Left(value) else Right(s"bypassed:$value")
      val flow: Flow[Int, String, ?] = AkkaStreams.bypassWith(route) {
        Flow[Int].map(value => s"flowed:${value * 10}")
      }

      val result: Seq[String] = await(Source(List(1, 2, 3, 4)).via(flow).runWith(Sink.seq[String]))
      assertThat(result.asJava).containsExactlyInAnyOrder("bypassed:1", "flowed:20", "bypassed:3", "flowed:40")
    }
  }

  @Test
  def onlyFirstCanFinishMergeIgnoresSecondaryInputCompletion(): Unit = {
    withActorSystem("only-first-can-finish-merge") { (_, materializer) =>
      implicit val implicitMaterializer: Materializer = materializer
      val merged: Source[String, ?] = Source.fromGraph(GraphDSL.create() { implicit builder =>
        import GraphDSL.Implicits.*

        val merge = builder.add(AkkaStreams.onlyFirstCanFinishMerge[String](2))
        Source.single("from-primary") ~> merge.in(0)
        Source.empty[String] ~> merge.in(1)
        SourceShape(merge.out)
      })

      val result: Seq[String] = await(merged.runWith(Sink.seq[String]))
      assertThat(result.asJava).containsExactly("from-primary")
    }
  }

  @Test
  def gzipFlowProducesDataThatAkkaCompressionCanInflate(): Unit = {
    withActorSystem("gzip-flow") { (_, materializer) =>
      implicit val implicitMaterializer: Materializer = materializer
      val text: String = "play-streams gzip integration ".repeat(200)
      val compressed: ByteString = await(
        Source
          .single(ByteString(text))
          .via(GzipFlow.gzip(128))
          .runFold(ByteString.empty)(_ ++ _)
      )
      val decompressed: ByteString = await(
        Source.single(compressed).via(Compression.gunzip()).runFold(ByteString.empty)(_ ++ _)
      )

      assertThat(compressed.length).isLessThan(ByteString(text).length)
      assertThat(decompressed.utf8String).isEqualTo(text)
    }
  }

  @Test
  def actorFlowDelegatesStreamElementsToActor(): Unit = {
    withActorSystem("actor-flow") { (actorSystem, materializer) =>
      implicit val implicitMaterializer: Materializer = materializer
      implicit val implicitActorSystem: ActorSystem = actorSystem
      val flow: Flow[String, String, ?] = ActorFlow.actorRef[String, String](
        out => Props(new ReversingActor(out)),
        8,
        OverflowStrategy.fail
      )

      val result: Seq[String] = await(Source(List("abc", "stream")).via(flow).runWith(Sink.seq[String]))
      assertThat(result.asJava).containsExactly("cba", "maerts")
    }
  }

  @Test
  def probesDelegateReactiveStreamsSignalsAndFlowElements(): Unit = {
    withActorSystem("probes") { (_, materializer) =>
      implicit val implicitMaterializer: Materializer = materializer
      val flowResult: Seq[Int] = await(
        Source(List(1, 2, 3)).via(Probes.flowProbe[Int]("flow", _.toString)).runWith(Sink.seq[Int])
      )
      assertThat(flowResult.asJava).containsExactly(1, 2, 3)

      val publisher = Source(List("a", "b")).runWith(Sink.asPublisher[String](false))
      val publisherResult: Seq[String] = await(
        Source.fromPublisher(Probes.publisherProbe("publisher", publisher)).runWith(Sink.seq[String])
      )
      assertThat(publisherResult.asJava).containsExactly("a", "b")

      val requested = new AtomicLong(0L)
      val cancelled = new AtomicBoolean(false)
      val subscription: Subscription = Probes.subscriptionProbe(
        "subscription",
        new Subscription {
          override def request(n: Long): Unit = requested.addAndGet(n)
          override def cancel(): Unit = cancelled.set(true)
        }
      )
      subscription.request(5)
      subscription.cancel()
      assertThat(requested.get()).isEqualTo(5L)
      assertThat(cancelled.get()).isTrue()

      val events = new ConcurrentLinkedQueue[String]()
      val subscriber: Subscriber[String] = Probes.subscriberProbe(
        "subscriber",
        new Subscriber[String] {
          override def onSubscribe(s: Subscription): Unit = events.add("subscribe")
          override def onNext(t: String): Unit = events.add(s"next:$t")
          override def onError(t: Throwable): Unit = events.add(s"error:${t.getClass.getSimpleName}")
          override def onComplete(): Unit = events.add("complete")
        }
      )
      subscriber.onSubscribe(new Subscription {
        override def request(n: Long): Unit = ()
        override def cancel(): Unit = ()
      })
      subscriber.onNext("value")
      subscriber.onComplete()

      assertThat(events.asScala.toSeq.asJava).containsExactly("subscribe", "next:value", "complete")
    }
  }

  @Test
  def processorProbeDelegatesBothSubscriberAndPublisherSides(): Unit = {
    val events = new ConcurrentLinkedQueue[String]()
    val requested = new AtomicLong(0L)
    val processor: Processor[String, String] = new UppercaseProcessor
    val probed: Processor[String, String] = Probes.processorProbe[String, String]("processor", processor, identity, identity)

    probed.subscribe(new Subscriber[String] {
      override def onSubscribe(s: Subscription): Unit = {
        events.add("subscribe")
        s.request(2)
      }

      override def onNext(t: String): Unit = events.add(s"next:$t")
      override def onError(t: Throwable): Unit = events.add(s"error:${t.getClass.getSimpleName}")
      override def onComplete(): Unit = events.add("complete")
    })
    probed.onSubscribe(new Subscription {
      override def request(n: Long): Unit = requested.addAndGet(n)
      override def cancel(): Unit = events.add("upstream-cancel")
    })
    probed.onNext("alpha")
    probed.onNext("beta")
    probed.onComplete()

    assertThat(events.asScala.toSeq.asJava).containsExactly("subscribe", "next:ALPHA", "next:BETA", "complete")
    assertThat(requested.get()).isEqualTo(Long.MaxValue)
  }

  @Test
  def ignoreAfterCancellationKeepsUpstreamDrainedForRemainingConsumers(): Unit = {
    withActorSystem("ignore-after-cancel") { (_, materializer) =>
      implicit val implicitMaterializer: Materializer = materializer
      val drained: Done = await(
        Source(1 to 5)
          .viaMat(AkkaStreams.ignoreAfterCancellation[Int])(Keep.right)
          .toMat(Sink.cancelled)(Keep.left)
          .run()
      )
      assertThat(drained).isEqualTo(Done)
    }
  }
}

object Play_streams_3Test {
  private val Timeout = 10.seconds

  private def withActorSystem(testName: String)(test: (ActorSystem, Materializer) => Unit): Unit = {
    val system: ActorSystem = ActorSystem(testName)
    val materializer: Materializer = SystemMaterializer(system).materializer
    try test(system, materializer)
    finally Await.result(system.terminate(), Timeout)
  }

  private def await[A](future: Future[A]): A = Await.result(future, Timeout)

  private def awaitStage[A](stage: CompletionStage[A]): A = {
    stage.toCompletableFuture.get(Timeout.toSeconds, TimeUnit.SECONDS)
  }

  private final class ReversingActor(out: ActorRef) extends Actor {
    override def receive: Receive = { case text: String => out ! text.reverse }

    override def postStop(): Unit = out ! Status.Success(())
  }

  private final class UppercaseProcessor extends Processor[String, String] {
    private var downstream: Subscriber[_ >: String] = _

    override def subscribe(s: Subscriber[_ >: String]): Unit = {
      downstream = s
      s.onSubscribe(new Subscription {
        override def request(n: Long): Unit = ()
        override def cancel(): Unit = ()
      })
    }

    override def onSubscribe(s: Subscription): Unit = s.request(Long.MaxValue)

    override def onNext(t: String): Unit = downstream.onNext(t.toUpperCase(Locale.ROOT))

    override def onError(t: Throwable): Unit = downstream.onError(t)

    override def onComplete(): Unit = downstream.onComplete()
  }
}
