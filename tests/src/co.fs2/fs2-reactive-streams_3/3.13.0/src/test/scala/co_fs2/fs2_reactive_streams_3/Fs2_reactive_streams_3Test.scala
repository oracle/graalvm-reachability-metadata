/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package co_fs2.fs2_reactive_streams_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.Stream
import fs2.interop.reactivestreams.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription

import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

class Fs2_reactive_streams_3Test {
  @Test
  def convertsPublisherToStreamInBufferedBatches(): Unit = {
    val publisher = new SequencePublisher[Int](Vector(1, 2, 3, 4, 5, 6, 7))

    val values = runIO(fromPublisher[IO, Int](publisher, bufferSize = 3).compile.toVector)

    assertThat(values).isEqualTo(Vector(1, 2, 3, 4, 5, 6, 7))
    assertThat(publisher.subscribeCount.get()).isEqualTo(1)
    assertThat(publisher.requests.asScala.toVector.forall(_ == 3L)).isTrue()
  }

  @Test
  def publisherOpsStreamCancelsUpstreamWhenDownstreamTerminatesEarly(): Unit = {
    val publisher = new SequencePublisher[Int](Vector.range(1, 10))

    val values = runIO(publisher.toStreamBuffered[IO](bufferSize = 2).take(3).compile.toVector)

    assertThat(values).isEqualTo(Vector(1, 2, 3))
    assertThat(publisher.requests.asScala.toVector.head).isEqualTo(2L)
    assertThat(publisher.cancelled.get()).isTrue()
  }

  @Test
  def publisherErrorsArePropagatedThroughStream(): Unit = {
    val failure = new IllegalStateException("publisher failed")
    val publisher = new SequencePublisher[Int](Vector(1), Some(failure))

    assertThatThrownBy { () =>
      runIO(fromPublisher[IO, Int](publisher, bufferSize = 2).compile.toVector)
    }.isSameAs(failure)
  }

  @Test
  def directStreamSubscriberConsumesPublisherWithConfiguredBuffer(): Unit = {
    val publisher = new SequencePublisher[String](Vector("alpha", "beta", "gamma"))
    val program = for {
      subscriber <- StreamSubscriber[IO, String](bufferSize = 2)
      values <- subscriber.stream(IO.delay(publisher.subscribe(subscriber))).compile.toVector
    } yield values

    val values = runIO(program)

    assertThat(values).isEqualTo(Vector("alpha", "beta", "gamma"))
    assertThat(publisher.requests.asScala.toVector.contains(2L)).isTrue()
  }

  @Test
  def streamCanSubscribeReactiveSubscriberUsingExtensionMethod(): Unit = {
    val subscriber = new RecordingSubscriber[String](initialRequest = Long.MaxValue)

    runIO(Stream.emits(Vector("left", "right")).covary[IO].subscribe(subscriber))

    assertThat(subscriber.valuesVector).isEqualTo(Vector("left", "right"))
    assertThat(subscriber.completed.get()).isTrue()
    assertThat(subscriber.error.get()).isNull()
  }

  @Test
  def streamPublisherHonorsDemandAndSupportsUnboundedFollowUpRequest(): Unit = {
    val program = Stream.emits(Vector(1, 2, 3, 4, 5)).covary[IO].toUnicastPublisher.use { publisher =>
      IO.blocking {
        val subscriber = new RecordingSubscriber[Int](
          initialRequest = 2L,
          additionalRequests = Map(2 -> Long.MaxValue)
        )

        publisher.subscribe(subscriber)
        subscriber.awaitTerminal()

        assertThat(subscriber.valuesVector).isEqualTo(Vector(1, 2, 3, 4, 5))
        assertThat(subscriber.completed.get()).isTrue()
        assertThat(subscriber.error.get()).isNull()
      }
    }

    runIO(program)
  }

  @Test
  def streamPublisherCanServeIndependentSubscribers(): Unit = {
    val program = Stream.emits(Vector("a", "b", "c")).covary[IO].toUnicastPublisher.use { publisher =>
      IO.blocking {
        val first = new RecordingSubscriber[String](initialRequest = Long.MaxValue)
        val second = new RecordingSubscriber[String](initialRequest = Long.MaxValue)

        publisher.subscribe(first)
        publisher.subscribe(second)
        first.awaitTerminal()
        second.awaitTerminal()

        assertThat(first.valuesVector).isEqualTo(Vector("a", "b", "c"))
        assertThat(second.valuesVector).isEqualTo(Vector("a", "b", "c"))
        assertThat(first.completed.get()).isTrue()
        assertThat(second.completed.get()).isTrue()
      }
    }

    runIO(program)
  }

  @Test
  def streamPublisherPropagatesStreamFailureToSubscriber(): Unit = {
    val failure = new IllegalStateException("stream failed")
    val failingStream: Stream[IO, String] =
      Stream.emit("before failure").covary[IO] ++ Stream.raiseError[IO](failure)
    val program = failingStream.toUnicastPublisher.use { publisher =>
      IO.blocking {
        val subscriber = new RecordingSubscriber[String](initialRequest = Long.MaxValue)

        publisher.subscribe(subscriber)
        subscriber.awaitTerminal()

        assertThat(subscriber.valuesVector).isEqualTo(Vector("before failure"))
        assertThat(subscriber.completed.get()).isFalse()
        assertThat(subscriber.error.get()).isSameAs(failure)
      }
    }

    runIO(program)
  }

  @Test
  def invalidRequestIsReportedToSubscriberAsError(): Unit = {
    val program = Stream.emits(Vector(1, 2, 3)).covary[IO].toUnicastPublisher.use { publisher =>
      IO.blocking {
        val subscriber = new RecordingSubscriber[Int](initialRequest = 0L)

        publisher.subscribe(subscriber)
        subscriber.awaitTerminal()

        assertThat(subscriber.valuesVector).isEqualTo(Vector.empty)
        assertThat(subscriber.completed.get()).isFalse()
        assertThat(subscriber.error.get()).isInstanceOf(classOf[IllegalArgumentException])
        assertThat(subscriber.error.get().getMessage).contains("Invalid number of elements [0]")
      }
    }

    runIO(program)
  }

  @Test
  def publisherRejectsSubscribersAfterResourceIsReleased(): Unit = {
    val program = Stream.emit(42).covary[IO].toUnicastPublisher.allocated.flatMap { case (publisher, release) =>
      release >> IO.blocking {
        val subscriber = new RecordingSubscriber[Int](initialRequest = 1L)

        publisher.subscribe(subscriber)
        subscriber.awaitTerminal()

        assertThat(subscriber.valuesVector).isEqualTo(Vector.empty)
        assertThat(subscriber.completed.get()).isFalse()
        assertThat(subscriber.error.get()).isInstanceOf(classOf[IllegalStateException])
      }
    }

    runIO(program)
  }

  private def runIO[A](io: IO[A]): A =
    io.timeout(10.seconds).unsafeRunSync()

  private final class SequencePublisher[A](
      elements: Vector[A],
      terminalError: Option[Throwable] = None
  ) extends Publisher[A] {
    val requests = new CopyOnWriteArrayList[Long]()
    val subscribeCount = new AtomicInteger(0)
    val cancelled = new AtomicBoolean(false)

    override def subscribe(subscriber: Subscriber[? >: A]): Unit = {
      subscribeCount.incrementAndGet()
      subscriber.onSubscribe(new Subscription {
        private var index = 0
        private var terminated = false

        override def request(n: Long): Unit = this.synchronized {
          if (!terminated) {
            if (n <= 0L) {
              terminated = true
              subscriber.onError(new IllegalArgumentException(s"non-positive request: $n"))
            } else {
              requests.add(n)
              var remaining = n
              while (remaining > 0L && index < elements.size && !terminated) {
                val next = elements(index)
                index += 1
                remaining -= 1L
                subscriber.onNext(next)
              }
              if (index == elements.size && !terminated) {
                terminated = true
                terminalError match {
                  case Some(error) => subscriber.onError(error)
                  case None        => subscriber.onComplete()
                }
              }
            }
          }
        }

        override def cancel(): Unit = this.synchronized {
          terminated = true
          cancelled.set(true)
        }
      })
    }
  }

  private final class RecordingSubscriber[A](
      initialRequest: Long,
      additionalRequests: Map[Int, Long] = Map.empty
  ) extends Subscriber[A] {
    private val subscription = new AtomicReference[Subscription]()
    private val terminal = new CountDownLatch(1)
    private val values = new CopyOnWriteArrayList[A]()

    val completed = new AtomicBoolean(false)
    val error = new AtomicReference[Throwable]()

    override def onSubscribe(s: Subscription): Unit = {
      if (subscription.compareAndSet(null, s)) {
        s.request(initialRequest)
      } else {
        s.cancel()
      }
    }

    override def onNext(value: A): Unit = {
      values.add(value)
      additionalRequests.get(values.size()).foreach { n =>
        subscription.get().request(n)
      }
    }

    override def onError(t: Throwable): Unit = {
      error.set(t)
      terminal.countDown()
    }

    override def onComplete(): Unit = {
      completed.set(true)
      terminal.countDown()
    }

    def valuesVector: Vector[A] =
      values.asScala.toVector

    def awaitTerminal(): Unit = {
      if (!terminal.await(10L, TimeUnit.SECONDS)) {
        throw new AssertionError("Timed out waiting for reactive-streams terminal signal")
      }
    }
  }
}
