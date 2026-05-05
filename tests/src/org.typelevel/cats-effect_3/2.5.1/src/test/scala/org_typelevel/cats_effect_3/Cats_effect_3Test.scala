/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_effect_3

import cats.effect.Blocker
import cats.effect.ConcurrentEffect
import cats.effect.ContextShift
import cats.effect.ExitCase
import cats.effect.IO
import cats.effect.Resource
import cats.effect.SyncIO
import cats.effect.Timer
import cats.effect.concurrent.Deferred
import cats.effect.concurrent.MVar
import cats.effect.concurrent.Ref
import cats.effect.concurrent.Semaphore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class Cats_effect_3Test {
  private implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  private implicit val effect: ConcurrentEffect[IO] = IO.ioConcurrentEffect(contextShift)
  private implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)

  private def await[A](io: IO[A]): A = {
    io.unsafeRunTimed(5.seconds).getOrElse(throw new AssertionError("IO did not complete within the expected timeout"))
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  def ioComposesLazySynchronousAndAsynchronousEffects(): Unit = {
    var evaluations: Int = 0
    val failure: IllegalStateException = new IllegalStateException("expected failure")

    val program: IO[(Int, String, Int)] = for {
      delayed <- IO {
        evaluations += 1
        40
      }.map(_ + 2)
      recovered <- IO.raiseError[Int](failure).attempt.map(_.fold(_.getMessage, _.toString))
      asynchronous <- IO.async[Int](callback => callback(Right(21))).map(_ * 2)
    } yield (delayed, recovered, asynchronous)

    val result: (Int, String, Int) = await(program)

    assertThat(evaluations).isEqualTo(1)
    assertThat(result).isEqualTo((42, "expected failure", 42))
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  def resourceRunsFinalizersForSuccessfulAndFailedUses(): Unit = {
    val events: ListBuffer[String] = ListBuffer.empty[String]
    val failure: RuntimeException = new RuntimeException("use failed")

    def managed(label: String): Resource[IO, String] = {
      Resource.make(IO {
        events += s"acquire-$label"
        label
      })(value => IO {
        events += s"release-$value"
      })
    }

    val program: IO[(Int, Either[Throwable, Int])] = for {
      success <- managed("success").use(value => IO {
        events += s"use-$value"
        value.length
      })
      failed <- managed("failure").use(_ => IO.raiseError[Int](failure)).attempt
    } yield (success, failed)

    val result: (Int, Either[Throwable, Int]) = await(program)

    assertThat(result._1).isEqualTo(7)
    assertThat(result._2.left.toOption.orNull).isSameAs(failure)
    assertThat(events.toList).isEqualTo(List(
      "acquire-success",
      "use-success",
      "release-success",
      "acquire-failure",
      "release-failure"
    ))
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  def refSupportsAtomicUpdatesModificationAndCompareAndSetAccess(): Unit = {
    val program: IO[(Int, Boolean, Map[String, Int])] = for {
      ref <- Ref.of[IO, Map[String, Int]](Map.empty[String, Int])
      _ <- ref.update(_.updated("alpha", 1))
      previous <- ref.modify { values =>
        val nextValue: Int = values("alpha") + 1
        (values.updated("alpha", nextValue), values("alpha"))
      }
      accessResult <- ref.access.flatMap { case (snapshot, setter) =>
        setter(snapshot.updated("beta", 3)).flatMap(success => ref.get.map(values => (success, values)))
      }
    } yield (previous, accessResult._1, accessResult._2)

    val result: (Int, Boolean, Map[String, Int]) = await(program)

    assertThat(result).isEqualTo((1, true, Map("alpha" -> 2, "beta" -> 3)))
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  def deferredCoordinatesFibersWithoutBlockingThreads(): Unit = {
    val program: IO[(String, Vector[String])] = for {
      gate <- Deferred[IO, String]
      observed <- Ref.of[IO, Vector[String]](Vector.empty[String])
      fiber <- gate.get.flatMap(value => observed.update(_ :+ value).map(_ => value.reverse)).start
      beforeComplete <- observed.get
      _ <- gate.complete("ready")
      joined <- fiber.join
      afterComplete <- observed.get
    } yield (s"$beforeComplete:$joined", afterComplete)

    val result: (String, Vector[String]) = await(program)

    assertThat(result).isEqualTo(("Vector():ydaer", Vector("ready")))
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  def semaphoreAndMVarCoordinatePermitProtectedExchange(): Unit = {
    val program: IO[(Boolean, Boolean, String, Long)] = for {
      semaphore <- Semaphore[IO](1L)
      mailbox <- MVar[IO].empty[String]
      _ <- semaphore.acquire
      acquireWhileHeld <- semaphore.tryAcquire
      _ <- semaphore.release
      acquireAfterRelease <- semaphore.tryAcquire
      _ <- if (acquireAfterRelease) semaphore.release else IO.unit
      producer <- semaphore.withPermit(mailbox.put("payload")).start
      received <- mailbox.take
      _ <- producer.join
      permits <- semaphore.available
    } yield (acquireWhileHeld, acquireAfterRelease, received, permits)

    val result: (Boolean, Boolean, String, Long) = await(program)

    assertThat(result).isEqualTo((false, true, "payload", 1L))
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  def cancelingFiberRunsBracketCaseFinalizerWithCanceledExitCase(): Unit = {
    val program: IO[ExitCase[Throwable]] = for {
      started <- Deferred[IO, Unit]
      canceled <- Deferred[IO, ExitCase[Throwable]]
      fiber <- started.complete(()).bracketCase(_ => IO.never) { (_, exitCase) =>
        canceled.complete(exitCase)
      }.start
      _ <- started.get
      _ <- fiber.cancel
      exitCase <- canceled.get.timeoutTo(1.second, IO.raiseError(new AssertionError("cancellation finalizer did not run")))
    } yield exitCase

    val result: ExitCase[Throwable] = await(program)

    assertThat(result).isEqualTo(ExitCase.Canceled)
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  def raceReturnsTheFirstCompletedEffect(): Unit = {
    val program: IO[Either[String, String]] = IO.race(
      timer.sleep(1.second).map(_ => "slow"),
      IO.pure("fast")
    )

    val result: Either[String, String] = await(program)

    assertThat(result).isEqualTo(Right("fast"))
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  def timerAndTimeoutToProduceBoundedFallbacks(): Unit = {
    val neverCompletes: IO[Int] = IO.never
    val program: IO[(Int, Boolean)] = for {
      before <- timer.clock.monotonic(TimeUnit.MILLISECONDS)
      value <- neverCompletes.timeoutTo(25.millis, IO.pure(99))
      after <- timer.clock.monotonic(TimeUnit.MILLISECONDS)
    } yield (value, after >= before)

    val result: (Int, Boolean) = await(program)

    assertThat(result).isEqualTo((99, true))
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  def contextShiftEvaluatesEffectsOnSpecifiedExecutionContext(): Unit = {
    val executor: ExecutorService = Executors.newSingleThreadExecutor(new ThreadFactory {
      override def newThread(runnable: Runnable): Thread = {
        val thread: Thread = new Thread(runnable)
        thread.setDaemon(true)
        thread.setName("cats-effect-eval-on-test")
        thread
      }
    })
    val dedicatedExecutionContext: ExecutionContext = ExecutionContext.fromExecutor(executor)

    try {
      val program: IO[(String, String)] = for {
        shiftedThreadName <- contextShift.evalOn(dedicatedExecutionContext)(IO(Thread.currentThread().getName))
        resumedThreadName <- IO(Thread.currentThread().getName)
      } yield (shiftedThreadName, resumedThreadName)

      val result: (String, String) = await(program)

      assertThat(result._1).startsWith("cats-effect-eval-on-test")
      assertThat(result._2).doesNotStartWith("cats-effect-eval-on-test")
    } finally {
      executor.shutdownNow()
      executor.awaitTermination(1, TimeUnit.SECONDS)
    }
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  def blockerEvaluatesBlockingWorkInsideManagedResource(): Unit = {
    val program: IO[(String, Int)] = Blocker[IO].use { blocker =>
      for {
        threadName <- blocker.blockOn(IO(Thread.currentThread().getName))
        computed <- blocker.delay(21 + 21)
      } yield (threadName, computed)
    }

    val result: (String, Int) = await(program)

    assertThat(result._1).isNotBlank
    assertThat(result._2).isEqualTo(42)
  }

  @Test
  @Timeout(value = 10, unit = TimeUnit.SECONDS)
  def syncIOEvaluatesPureSynchronousEffectsAndCapturesFailures(): Unit = {
    var counter: Int = 0

    val result: (Int, Either[Throwable, Int]) = SyncIO {
      counter += 1
      counter
    }.flatMap(value => SyncIO.pure(value + 1))
      .flatMap(value => SyncIO.raiseError[Int](new IllegalArgumentException(value.toString)).attempt.map(error => (value, error)))
      .unsafeRunSync()

    assertThat(counter).isEqualTo(1)
    assertThat(result._1).isEqualTo(2)
    assertThat(result._2.left.toOption.map(_.getMessage).orNull).isEqualTo("2")
  }
}
