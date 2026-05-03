/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sangria_graphql.sangria_streaming_api_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sangria.streaming.SubscriptionStream
import sangria.streaming.SubscriptionStreamLike
import sangria.streaming.ValidOutStreamType
import sangria.streaming.future

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration.*

class Sangria_streaming_api_3Test {
  import Sangria_streaming_api_3Test.*

  given ExecutionContext = ExecutionContext.global

  @Test
  def futureSubscriptionStreamCreatesSuccessfulFailedAndForwardedStreams(): Unit = {
    val stream: SubscriptionStream[Future] = future.futureSubscriptionStream
    val existingFuture: Future[String] = Future.successful("already-computed")

    val singleResult: Future[Int] = stream.single(42)
    val forwardedFuture: Future[String] = stream.singleFuture(existingFuture)
    val firstFuture: Future[String] = stream.first(forwardedFuture)

    assertEquals(42, await(singleResult))
    assertSame(existingFuture, forwardedFuture)
    assertSame(forwardedFuture, firstFuture)

    val failure: IllegalStateException = new IllegalStateException("boom")
    val failedResult: Future[Int] = stream.failed(failure)
    val thrown: IllegalStateException = assertThrows(classOf[IllegalStateException], () => await(failedResult))
    assertSame(failure, thrown)
  }

  @Test
  def mapsFlatMapsAndRecoversFutureBackedStreams(): Unit = {
    val stream: SubscriptionStream[Future] = future.futureSubscriptionStream

    val mapped: Future[String] = stream.map(stream.single(21))((value: Int) => s"value-${value * 2}")
    val flatMapped: Future[String] = stream.mapFuture(stream.single("native"))((value: String) =>
      Future.successful(value.reverse)
    )
    val flatMappedFromFuture: Future[String] = stream.flatMapFuture[Unit, String, Int](Future.successful(7))((value: Int) =>
      stream.single(s"subscription-${value + 1}")
    )
    val recovered: Future[Int] = stream.recover(stream.failed[Int](new RuntimeException("recoverable")))((error: Throwable) =>
      error.getMessage.length
    )

    assertEquals("value-42", await(mapped))
    assertEquals("evitan", await(flatMapped))
    assertEquals("subscription-8", await(flatMappedFromFuture))
    assertEquals("recoverable".length, await(recovered))
  }

  @Test
  def onCompleteRunsSideEffectForSuccessAndFailureWithoutChangingResult(): Unit = {
    val stream: SubscriptionStream[Future] = future.futureSubscriptionStream
    val completedCount: AtomicInteger = new AtomicInteger(0)
    val failedCount: AtomicInteger = new AtomicInteger(0)
    val failure: IllegalArgumentException = new IllegalArgumentException("invalid result")

    val successful: Future[String] = stream.onComplete[Unit, String](stream.single("payload")) {
      completedCount.incrementAndGet()
      ()
    }
    val failed: Future[Int] = stream.onComplete[Unit, Int](stream.failed[Int](failure)) {
      failedCount.incrementAndGet()
      ()
    }

    assertEquals("payload", await(successful))
    assertEquals(1, completedCount.get())

    val thrown: IllegalArgumentException = assertThrows(classOf[IllegalArgumentException], () => await(failed))
    assertSame(failure, thrown)
    assertEquals(1, failedCount.get())
  }

  @Test
  def mergeCompletesWithFirstCompletedStream(): Unit = {
    val stream: SubscriptionStream[Future] = future.futureSubscriptionStream
    val firstPromise: Promise[String] = Promise[String]()
    val secondPromise: Promise[String] = Promise[String]()
    val merged: Future[String] = stream.merge(Vector(firstPromise.future, secondPromise.future))

    assertFalse(merged.isCompleted)
    secondPromise.success("second-finished")

    assertEquals("second-finished", await(merged))
    firstPromise.success("first-finished")
  }

  @Test
  def futureSubscriptionStreamUsesProvidedExecutionContextForTransformations(): Unit = {
    val workerThreadName: String = "sangria-streaming-test-worker"
    val observedThreadName: AtomicReference[String] = new AtomicReference[String]()
    val executor: ExecutorService = Executors.newSingleThreadExecutor((r: Runnable) => {
      val thread: Thread = new Thread(r, workerThreadName)
      thread.setDaemon(true)
      thread
    })
    val executionContext: ExecutionContext = ExecutionContext.fromExecutorService(executor)

    try {
      val stream: SubscriptionStream[Future] = future.futureSubscriptionStream(using executionContext)
      val transformed: Future[String] = stream.map(stream.single("payload")) { value =>
        observedThreadName.set(Thread.currentThread().getName)
        value.toUpperCase
      }

      assertEquals("PAYLOAD", await(transformed))
      assertEquals(workerThreadName, observedThreadName.get())
    } finally {
      executor.shutdownNow()
      ()
    }
  }

  @Test
  def supportedIdentifiesFutureSubscriptionStreamsOnly(): Unit = {
    val stream: SubscriptionStream[Future] = future.futureSubscriptionStream
    val anotherFutureStream: SubscriptionStream[Future] = future.futureSubscriptionStream
    val listStream: SubscriptionStream[List] = new ListSubscriptionStream

    assertTrue(stream.supported(anotherFutureStream))
    assertFalse(stream.supported(listStream))
  }

  @Test
  def subscriptionStreamLikeDefaultPreservesResolvedSubscriptionStream(): Unit = {
    val stream: SubscriptionStream[Future] = future.futureSubscriptionStream
    given SubscriptionStream[Future] = stream

    val like: SubscriptionStreamLike[Future[GraphAction[Unit, String]], GraphAction, Unit, String, CharSequence] =
      summon[SubscriptionStreamLike[Future[GraphAction[Unit, String]], GraphAction, Unit, String, CharSequence]]

    assertSame(stream, like.subscriptionStream)
    assertTrue(like.subscriptionStream.supported(stream))
  }

  @Test
  def futureSubscriptionStreamGivenSupportsAutomaticTypeClassResolution(): Unit = {
    import sangria.streaming.future.given

    val stream: SubscriptionStream[Future] = summon[SubscriptionStream[Future]]
    val like: SubscriptionStreamLike[Future[GraphAction[String, Int]], GraphAction, String, Int, AnyVal] =
      summon[SubscriptionStreamLike[Future[GraphAction[String, Int]], GraphAction, String, Int, AnyVal]]
    val actionStream: like.StreamSource[GraphAction[String, Int]] =
      like.subscriptionStream.single((context: String) => context.length)

    assertTrue(stream.supported(like.subscriptionStream))
    assertEquals(6, await(like.subscriptionStream.first(actionStream))("native"))
  }

  @Test
  def validOutStreamTypeInstancesCoverSubclassesOptionsSequencesAndNothing(): Unit = {
    val subclass: ValidOutStreamType[String, CharSequence] = summon[ValidOutStreamType[String, CharSequence]]
    val exact: ValidOutStreamType[String, String] = summon[ValidOutStreamType[String, String]]
    val option: ValidOutStreamType[String, Option[CharSequence]] = summon[ValidOutStreamType[String, Option[CharSequence]]]
    val sequence: ValidOutStreamType[String, Seq[CharSequence]] = summon[ValidOutStreamType[String, Seq[CharSequence]]]
    val nothing: ValidOutStreamType[Nothing, CharSequence] = summon[ValidOutStreamType[Nothing, CharSequence]]

    assertNotNull(subclass)
    assertSame(subclass.asInstanceOf[AnyRef], exact.asInstanceOf[AnyRef])
    assertSame(subclass.asInstanceOf[AnyRef], option.asInstanceOf[AnyRef])
    assertSame(subclass.asInstanceOf[AnyRef], sequence.asInstanceOf[AnyRef])
    assertSame(subclass.asInstanceOf[AnyRef], nothing.asInstanceOf[AnyRef])
  }

  private def await[A](future: Future[A]): A = Await.result(future, 5.seconds)
}

object Sangria_streaming_api_3Test {
  type GraphAction[Ctx, Res] = Ctx => Res

  private final class ListSubscriptionStream extends SubscriptionStream[List] {
    override def supported[T[X]](other: SubscriptionStream[T]): Boolean = other.isInstanceOf[ListSubscriptionStream]

    override def single[T](value: T): List[T] = List(value)

    override def singleFuture[T](value: Future[T]): List[T] = List(Await.result(value, 5.seconds))

    override def first[T](s: List[T]): Future[T] = Future.successful(s.head)

    override def failed[T](e: Throwable): List[T] = throw e

    override def onComplete[Ctx, Res](result: List[Res])(op: => Unit): List[Res] = {
      op
      result
    }

    override def flatMapFuture[Ctx, Res, T](future: Future[T])(resultFn: T => List[Res]): List[Res] =
      resultFn(Await.result(future, 5.seconds))

    override def mapFuture[A, B](source: List[A])(fn: A => Future[B]): List[B] =
      source.map(value => Await.result(fn(value), 5.seconds))

    override def map[A, B](source: List[A])(fn: A => B): List[B] = source.map(fn)

    override def merge[T](streams: Vector[List[T]]): List[T] = streams.flatten.toList

    override def recover[T](stream: List[T])(fn: Throwable => T): List[T] = stream
  }
}
