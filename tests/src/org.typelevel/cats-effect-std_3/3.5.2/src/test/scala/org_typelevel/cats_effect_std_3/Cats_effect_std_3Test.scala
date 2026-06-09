/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_effect_std_3

import scala.concurrent.duration.*

import cats.effect.Deferred
import cats.effect.IO
import cats.effect.std.AtomicCell
import cats.effect.std.Dispatcher
import cats.effect.std.Queue
import cats.effect.std.Random
import cats.effect.std.Semaphore
import cats.effect.std.Supervisor
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Cats_effect_std_3Test:
  private val TestTimeout: FiniteDuration = 5.seconds

  @Test
  def queueCoordinatesBoundedOffersAndWaitingConsumers(): Unit =
    val result: QueueResult = run {
      for
        queue <- Queue.bounded[IO, String](2)
        _ <- queue.offer("first")
        _ <- queue.offer("second")
        fullOffer <- queue.tryOffer("third")
        first <- queue.take
        _ <- queue.offer("third")
        second <- queue.take
        third <- queue.take
        emptyBeforeConsumer <- queue.tryTake
        waitingConsumer <- queue.take.start
        _ <- IO.cede
        _ <- queue.offer("fourth")
        fourth <- waitingConsumer.joinWithNever
      yield QueueResult(fullOffer, first, second, third, emptyBeforeConsumer.isEmpty, fourth)
    }

    assertFalse(result.fullOffer)
    assertEquals("first", result.first)
    assertEquals("second", result.second)
    assertEquals("third", result.third)
    assertTrue(result.queueWasEmptyBeforeConsumer)
    assertEquals("fourth", result.fourth)

  @Test
  def semaphoreProtectsExclusiveSectionsAndRestoresPermits(): Unit =
    val result: SemaphoreResult = run {
      for
        semaphore <- Semaphore[IO](1)
        firstAcquire <- semaphore.tryAcquire
        deniedWhileHeld <- semaphore.tryAcquire
        _ <- semaphore.release
        deniedInsidePermit <- semaphore.permit.use(_ => semaphore.tryAcquire)
        acquiredAfterPermit <- semaphore.tryAcquire
        _ <- semaphore.release
      yield SemaphoreResult(firstAcquire, deniedWhileHeld, deniedInsidePermit, acquiredAfterPermit)
    }

    assertTrue(result.firstAcquire)
    assertFalse(result.deniedWhileHeld)
    assertFalse(result.deniedInsidePermit)
    assertTrue(result.acquiredAfterPermit)

  @Test
  def atomicCellSerializesConcurrentEffectfulUpdates(): Unit =
    val result: AtomicCellResult = run {
      for
        cell <- AtomicCell[IO].of(0)
        observedUpdates <- (1 to 20).toList.parTraverse { _ =>
          cell.evalModify { current =>
            val next: Int = current + 1
            IO.cede.as((next, next))
          }
        }
        finalValue <- cell.get
      yield AtomicCellResult(observedUpdates, finalValue)
    }

    assertEquals((1 to 20).toList, result.observedUpdates.sorted)
    assertEquals(20, result.finalValue)

  @Test
  def dispatcherRunsEffectsFromUnsafeCallbacksAndFutures(): Unit =
    val result: DispatcherResult = run {
      Dispatcher.parallel[IO].use { dispatcher =>
        for
          signal <- Deferred[IO, Int]
          _ <- IO.delay(dispatcher.unsafeRunAndForget(signal.complete(42).map(_ => ())))
          signaled <- signal.get
          futureValue <- IO.fromFuture(IO.delay(dispatcher.unsafeToFuture(IO.pure("completed"))))
        yield DispatcherResult(futureValue, signaled)
      }
    }

    assertEquals("completed", result.futureValue)
    assertEquals(42, result.signaled)

  @Test
  def randomProducesBoundedValuesAndShufflesCollections(): Unit =
    val result: RandomResult = run {
      for
        random <- Random.scalaUtilRandomSeedLong[IO](8675309L)
        boundedValues <- List.fill(12)(random.nextIntBounded(10)).sequence
        shuffled <- random.shuffleList(List("alpha", "beta", "gamma", "delta"))
      yield RandomResult(boundedValues, shuffled)
    }

    assertTrue(result.boundedValues.forall(value => value >= 0 && value < 10))
    assertEquals(List("alpha", "beta", "delta", "gamma"), result.shuffled.sorted)

  @Test
  def supervisorStartsAndCancelsBackgroundFibersInResourceScope(): Unit =
    val value: String = run {
      Supervisor[IO].use { supervisor =>
        for
          started <- Deferred[IO, String]
          fiber <- supervisor.supervise(signalThenWait(started))
          value <- started.get
          _ <- fiber.cancel
        yield value
      }
    }

    assertEquals("running", value)

  private def run[A](io: IO[A]): A =
    io.timeout(TestTimeout).unsafeRunSync()

  private def signalThenWait(started: Deferred[IO, String]): IO[Unit] =
    started.complete("running").flatMap(_ => IO.never[Unit])

  private final case class QueueResult(
      fullOffer: Boolean,
      first: String,
      second: String,
      third: String,
      queueWasEmptyBeforeConsumer: Boolean,
      fourth: String)

  private final case class SemaphoreResult(
      firstAcquire: Boolean,
      deniedWhileHeld: Boolean,
      deniedInsidePermit: Boolean,
      acquiredAfterPermit: Boolean)

  private final case class AtomicCellResult(observedUpdates: List[Int], finalValue: Int)

  private final case class DispatcherResult(futureValue: String, signaled: Int)

  private final case class RandomResult(boundedValues: List[Int], shuffled: List[String])
