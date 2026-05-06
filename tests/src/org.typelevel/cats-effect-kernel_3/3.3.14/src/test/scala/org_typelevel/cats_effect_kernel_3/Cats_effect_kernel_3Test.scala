/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.cats_effect_kernel_3

import cats.arrow.FunctionK
import cats.effect.kernel.CancelScope
import cats.effect.kernel.Clock
import cats.effect.kernel.MonadCancel
import cats.effect.kernel.Outcome
import cats.effect.kernel.Poll
import cats.effect.kernel.Ref
import cats.effect.kernel.Resource
import cats.effect.kernel.Sync
import cats.effect.kernel.Unique
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.NANOSECONDS
import scala.util.control.NonFatal

class Cats_effect_kernel_3Test {
  import Cats_effect_kernel_3Test.*
  import Cats_effect_kernel_3Test.given

  @Test
  def resourceUseRunsAcquireUseAndReleaseInOrder(): Unit = {
    val events: ListBuffer[String] = ListBuffer.empty

    val resource: Resource[TestEffect, String] = Resource
      .makeCase(TestEffect.delay {
        events += "acquire"
        "connection"
      }) { (handle: String, exitCase: Resource.ExitCase) =>
        TestEffect.delay {
          events += s"release:$handle:${exitCaseName(exitCase)}"
          ()
        }
      }
      .evalTap { (handle: String) =>
        TestEffect.delay(events += s"tap:$handle")
      }
      .map(_.toUpperCase)

    val result: Int = resource.use { (handle: String) =>
      TestEffect.delay {
        events += s"use:$handle"
        handle.length
      }
    }.unsafeRun()

    assertEquals(10, result)
    assertEquals(
      List("acquire", "tap:connection", "use:CONNECTION", "release:connection:succeeded"),
      events.toList
    )
  }

  @Test
  def resourceFinalizerReceivesErroredExitCase(): Unit = {
    val failure: IllegalStateException = IllegalStateException("boom")
    val events: ListBuffer[String] = ListBuffer.empty

    val resource: Resource[TestEffect, String] = Resource.makeCase(TestEffect.pure("socket")) {
      (handle: String, exitCase: Resource.ExitCase) =>
        TestEffect.delay {
          events += s"release:$handle:${exitCaseName(exitCase)}"
          ()
        }
    }

    val thrown: IllegalStateException = assertThrows(
      classOf[IllegalStateException],
      () => resource.use(_ => TestEffect.raiseError[Int](failure)).unsafeRun()
    )

    assertSame(failure, thrown)
    assertEquals(List("release:socket:errored:boom"), events.toList)
  }

  @Test
  def allocatedResourceReleasesOnlyWhenReturnedFinalizerIsRun(): Unit = {
    val events: ListBuffer[String] = ListBuffer.empty
    val resource: Resource[TestEffect, String] = Resource
      .make(TestEffect.delay {
        events += "acquire"
        "file"
      }) { (handle: String) =>
        TestEffect.delay {
          events += s"close:$handle"
          ()
        }
      }
      .onFinalize(TestEffect.delay {
        events += "after-close"
        ()
      })

    val allocated: (String, TestEffect[Unit]) = resource.allocated.unsafeRun()

    assertEquals("file", allocated._1)
    assertEquals(List("acquire"), events.toList)

    allocated._2.unsafeRun()

    assertEquals("acquire", events.head)
    assertEquals(Set("acquire", "close:file", "after-close"), events.toList.toSet)
  }

  @Test
  def resourceCombinatorsComposePureEvalErrorsAndFinalizers(): Unit = {
    val events: ListBuffer[String] = ListBuffer.empty

    val first: Resource[TestEffect, Int] = Resource.eval(TestEffect.delay {
      events += "eval-first"
      21
    })
    val second: Resource[TestEffect, Int] = Resource.pure[TestEffect, Int](2)
    val combined: Resource[TestEffect, String] = first
      .flatMap { (value: Int) => second.map(multiplier => value * multiplier) }
      .map(total => s"total=$total")
      .onFinalizeCase { (exitCase: Resource.ExitCase) =>
        TestEffect.delay {
          events += s"final:${exitCaseName(exitCase)}"
          ()
        }
      }

    val successful: String = combined.use(TestEffect.pure).unsafeRun()
    val failed: Either[Throwable, String] = combined
      .forceR(Resource.raiseError[TestEffect, String, Throwable](IllegalArgumentException("replacement failed")))
      .use(TestEffect.pure)
      .unsafeEither

    assertEquals("total=42", successful)
    assertTrue(failed match {
      case Left(throwable) => throwable.getMessage == "replacement failed"
      case Right(_) => false
    })
    assertEquals(
      List("eval-first", "final:succeeded", "eval-first", "final:succeeded"),
      events.toList
    )
  }

  @Test
  def outcomeValuesFoldEmbedAndMapAcrossEffectTypes(): Unit = {
    val success: Outcome[TestEffect, Throwable, Int] = Outcome.succeeded(TestEffect.pure(42))
    val error: IllegalArgumentException = IllegalArgumentException("bad")
    val errored: Outcome[TestEffect, Throwable, Int] = Outcome.errored(error)
    val canceled: Outcome[TestEffect, Throwable, Int] = Outcome.canceled

    assertTrue(success.isSuccess)
    assertFalse(success.isError)
    assertFalse(success.isCanceled)
    assertEquals("success:42", success.fold("canceled", _.getMessage, _.mapValue(value => s"success:$value").unsafeRun()))
    assertEquals(42, success.embed(TestEffect.pure(-1)).unsafeRun())
    assertEquals(-1, canceled.embed(TestEffect.pure(-1)).unsafeRun())

    val embeddedFailure: Either[Throwable, Int] = errored.embed(TestEffect.pure(-1)).unsafeEither
    assertTrue(embeddedFailure match {
      case Left(throwable) => throwable eq error
      case Right(_) => false
    })

    val fromRight: Outcome[TestEffect, Throwable, String] = Outcome.fromEither(Right("ok"))
    val fromLeft: Outcome[TestEffect, Throwable, String] = Outcome.fromEither(Left(error))
    assertEquals("ok", fromRight.embed(TestEffect.pure("fallback")).unsafeRun())
    assertTrue(fromLeft.embed(TestEffect.pure("fallback")).unsafeEither match {
      case Left(throwable) => throwable eq error
      case Right(_) => false
    })

    val transformed: Outcome[LoggedEffect, Throwable, Int] = success.mapK(new FunctionK[TestEffect, LoggedEffect] {
      override def apply[A](fa: TestEffect[A]): LoggedEffect[A] = LoggedEffect("mapped", fa.unsafeEither)
    })
    val logged: LoggedEffect[Int] = transformed.fold(
      LoggedEffect("canceled", Right(-1)),
      throwable => LoggedEffect("error", Left(throwable)),
      identity
    )

    assertEquals("mapped", logged.label)
    assertEquals(Right(42), logged.value)
  }

  @Test
  def clockAndUniqueProvideEffectfulValuesAndResourceConstructors(): Unit = {
    val timed: (FiniteDuration, String) = Clock[TestEffect].timed(TestEffect.pure("payload")).unsafeRun()
    val realTime: FiniteDuration = Resource.realTime[TestEffect].use(TestEffect.pure).unsafeRun()
    val monotonic: FiniteDuration = Resource.monotonic[TestEffect].use(TestEffect.pure).unsafeRun()
    val firstToken: Unique.Token = Unique[TestEffect].unique.unsafeRun()
    val secondToken: Unique.Token = Resource.unique[TestEffect].use(TestEffect.pure).unsafeRun()

    assertEquals("payload", timed._2)
    assertTrue(timed._1 >= FiniteDuration(0, NANOSECONDS))
    assertTrue(realTime >= FiniteDuration(0, MILLISECONDS))
    assertTrue(monotonic >= FiniteDuration(0, NANOSECONDS))
    assertNotEquals(firstToken, secondToken)
  }

  @Test
  def refSupportsAtomicUpdatesAndVersionedAccess(): Unit = {
    given Sync[TestEffect] = Cats_effect_kernel_3Test.testEffectSync

    val ref: Ref[TestEffect, Vector[String]] = Ref.of[TestEffect, Vector[String]](Vector("initial")).unsafeRun()

    val previous: Vector[String] = ref.getAndSet(Vector("reset")).unsafeRun()
    val updated: Vector[String] = ref.updateAndGet(_ :+ "next").unsafeRun()
    val modificationResult: String = ref.modify { (current: Vector[String]) =>
      (current :+ "committed", current.mkString(","))
    }.unsafeRun()
    val access: (Vector[String], Vector[String] => TestEffect[Boolean]) = ref.access.unsafeRun()
    val snapshot: Vector[String] = access._1
    val setIfUnchanged: Vector[String] => TestEffect[Boolean] = access._2
    val firstAccessSet: Boolean = setIfUnchanged(snapshot :+ "access").unsafeRun()
    val staleAccessSet: Boolean = setIfUnchanged(snapshot :+ "stale").unsafeRun()
    val tryModificationResult: Option[String] = ref.tryModify { (current: Vector[String]) =>
      (current :+ "try", current.lastOption.getOrElse("empty"))
    }.unsafeRun()

    assertEquals(Vector("initial"), previous)
    assertEquals(Vector("reset", "next"), updated)
    assertEquals("reset,next", modificationResult)
    assertEquals(Vector("reset", "next", "committed"), snapshot)
    assertTrue(firstAccessSet)
    assertFalse(staleAccessSet)
    assertEquals(Some("access"), tryModificationResult)
    assertEquals(Vector("reset", "next", "committed", "access", "try"), ref.get.unsafeRun())
  }

  @Test
  def syncSuspendsSideEffectsAndCapturesNonFatalErrors(): Unit = {
    given Sync[TestEffect] = Cats_effect_kernel_3Test.testEffectSync

    val sync: Sync[TestEffect] = Sync[TestEffect]
    val failure: IllegalArgumentException = IllegalArgumentException("sync failure")
    var counter: Int = 0

    val delayed: TestEffect[String] = sync.delay {
      counter += 1
      s"delay:$counter"
    }
    val deferred: TestEffect[String] = sync.defer {
      counter += 1
      TestEffect.pure(s"defer:$counter")
    }
    val blocking: TestEffect[String] = sync.blocking {
      counter += 1
      s"blocking:$counter"
    }
    val interruptible: TestEffect[String] = sync.interruptible {
      counter += 1
      s"interruptible:$counter"
    }
    val interruptibleMany: TestEffect[String] = sync.interruptibleMany {
      counter += 1
      s"interruptibleMany:$counter"
    }
    val capturedFailure: TestEffect[Unit] = sync.delay(throw failure)

    assertEquals(0, counter)
    assertEquals("delay:1", delayed.unsafeRun())
    assertEquals("defer:2", deferred.unsafeRun())
    assertEquals("blocking:3", blocking.unsafeRun())
    assertEquals("interruptible:4", interruptible.unsafeRun())
    assertEquals("interruptibleMany:5", interruptibleMany.unsafeRun())
    assertTrue(capturedFailure.unsafeEither match {
      case Left(throwable) => throwable eq failure
      case Right(_) => false
    })
  }

  private def exitCaseName(exitCase: Resource.ExitCase): String = exitCase match {
    case Resource.ExitCase.Succeeded => "succeeded"
    case Resource.ExitCase.Canceled => "canceled"
    case Resource.ExitCase.Errored(error) => s"errored:${error.getMessage}"
  }
}

object Cats_effect_kernel_3Test {
  final case class TestEffect[A](private val thunk: () => Either[Throwable, A]) {
    def unsafeEither: Either[Throwable, A] = thunk()

    def unsafeRun(): A = unsafeEither.fold(throw _, identity)

    def mapValue[B](f: A => B): TestEffect[B] = TestEffect(() => unsafeEither.map(f))
  }

  object TestEffect {
    def pure[A](value: A): TestEffect[A] = TestEffect(() => Right(value))

    def raiseError[A](throwable: Throwable): TestEffect[A] = TestEffect(() => Left(throwable))

    def delay[A](body: => A): TestEffect[A] = TestEffect { () =>
      try Right(body)
      catch {
        case NonFatal(throwable) => Left(throwable)
      }
    }
  }

  final case class LoggedEffect[A](label: String, value: Either[Throwable, A])

  given testEffectMonadCancel: MonadCancel[TestEffect, Throwable] with {
    override def rootCancelScope: CancelScope = CancelScope.Cancelable

    override def pure[A](value: A): TestEffect[A] = TestEffect.pure(value)

    override def map[A, B](fa: TestEffect[A])(f: A => B): TestEffect[B] = fa.mapValue(f)

    override def flatMap[A, B](fa: TestEffect[A])(f: A => TestEffect[B]): TestEffect[B] =
      TestEffect(() => fa.unsafeEither.flatMap(value => f(value).unsafeEither))

    override def tailRecM[A, B](initial: A)(f: A => TestEffect[Either[A, B]]): TestEffect[B] = {
      def loop(current: A): Either[Throwable, B] = f(current).unsafeEither match {
        case Left(throwable) => Left(throwable)
        case Right(Left(next)) => loop(next)
        case Right(Right(value)) => Right(value)
      }

      TestEffect(() => loop(initial))
    }

    override def raiseError[A](throwable: Throwable): TestEffect[A] = TestEffect.raiseError(throwable)

    override def handleErrorWith[A](fa: TestEffect[A])(f: Throwable => TestEffect[A]): TestEffect[A] = TestEffect { () =>
      fa.unsafeEither match {
        case Left(throwable) => f(throwable).unsafeEither
        case Right(value) => Right(value)
      }
    }

    override def forceR[A, B](fa: TestEffect[A])(fb: TestEffect[B]): TestEffect[B] = TestEffect { () =>
      fa.unsafeEither
      fb.unsafeEither
    }

    override def uncancelable[A](body: Poll[TestEffect] => TestEffect[A]): TestEffect[A] = TestEffect { () =>
      body(new Poll[TestEffect] {
        override def apply[A](fa: TestEffect[A]): TestEffect[A] = fa
      }).unsafeEither
    }

    override def canceled: TestEffect[Unit] = TestEffect.pure(())

    override def onCancel[A](fa: TestEffect[A], finalizer: TestEffect[Unit]): TestEffect[A] = fa

    override def guaranteeCase[A](fa: TestEffect[A])(finalizer: Outcome[TestEffect, Throwable, A] => TestEffect[Unit]): TestEffect[A] = TestEffect { () =>
      val result: Either[Throwable, A] = fa.unsafeEither
      val outcome: Outcome[TestEffect, Throwable, A] = result match {
        case Right(value) => Outcome.succeeded(TestEffect.pure(value))
        case Left(throwable) => Outcome.errored(throwable)
      }
      finalizer(outcome).unsafeEither match {
        case Left(finalizerFailure) => Left(finalizerFailure)
        case Right(_) => result
      }
    }

    override def bracketCase[A, B](acquire: TestEffect[A])(use: A => TestEffect[B])(
        release: (A, Outcome[TestEffect, Throwable, B]) => TestEffect[Unit]
    ): TestEffect[B] = TestEffect { () =>
      acquire.unsafeEither match {
        case Left(throwable) => Left(throwable)
        case Right(resource) =>
          val used: TestEffect[B] = use(resource)
          val result: Either[Throwable, B] = used.unsafeEither
          val outcome: Outcome[TestEffect, Throwable, B] = result match {
            case Right(value) => Outcome.succeeded(TestEffect.pure(value))
            case Left(throwable) => Outcome.errored(throwable)
          }
          release(resource, outcome).unsafeEither match {
            case Left(finalizerFailure) => Left(finalizerFailure)
            case Right(_) => result
          }
      }
    }
  }

  given testEffectClock: Clock[TestEffect] with {
    private var monotonicNanos: Long = 0L

    override def applicative: MonadCancel[TestEffect, Throwable] = testEffectMonadCancel

    override def monotonic: TestEffect[FiniteDuration] = TestEffect.delay {
      monotonicNanos += 1000L
      FiniteDuration(monotonicNanos, NANOSECONDS)
    }

    override def realTime: TestEffect[FiniteDuration] = TestEffect.delay {
      FiniteDuration(System.currentTimeMillis(), MILLISECONDS)
    }
  }

  given testEffectUnique: Unique[TestEffect] with {
    override def applicative: MonadCancel[TestEffect, Throwable] = testEffectMonadCancel

    override def unique: TestEffect[Unique.Token] = TestEffect.pure(new Unique.Token)
  }

  private val testEffectSync: Sync[TestEffect] = new Sync[TestEffect] {
    override def rootCancelScope: CancelScope = testEffectMonadCancel.rootCancelScope

    override def pure[A](value: A): TestEffect[A] = testEffectMonadCancel.pure(value)

    override def map[A, B](fa: TestEffect[A])(f: A => B): TestEffect[B] = testEffectMonadCancel.map(fa)(f)

    override def flatMap[A, B](fa: TestEffect[A])(f: A => TestEffect[B]): TestEffect[B] = testEffectMonadCancel.flatMap(fa)(f)

    override def tailRecM[A, B](initial: A)(f: A => TestEffect[Either[A, B]]): TestEffect[B] =
      testEffectMonadCancel.tailRecM(initial)(f)

    override def raiseError[A](throwable: Throwable): TestEffect[A] = testEffectMonadCancel.raiseError(throwable)

    override def handleErrorWith[A](fa: TestEffect[A])(f: Throwable => TestEffect[A]): TestEffect[A] =
      testEffectMonadCancel.handleErrorWith(fa)(f)

    override def forceR[A, B](fa: TestEffect[A])(fb: TestEffect[B]): TestEffect[B] = testEffectMonadCancel.forceR(fa)(fb)

    override def uncancelable[A](body: Poll[TestEffect] => TestEffect[A]): TestEffect[A] =
      testEffectMonadCancel.uncancelable(body)

    override def canceled: TestEffect[Unit] = testEffectMonadCancel.canceled

    override def onCancel[A](fa: TestEffect[A], finalizer: TestEffect[Unit]): TestEffect[A] =
      testEffectMonadCancel.onCancel(fa, finalizer)

    override def guaranteeCase[A](fa: TestEffect[A])(finalizer: Outcome[TestEffect, Throwable, A] => TestEffect[Unit]): TestEffect[A] =
      testEffectMonadCancel.guaranteeCase(fa)(finalizer)

    override def bracketCase[A, B](acquire: TestEffect[A])(use: A => TestEffect[B])(
        release: (A, Outcome[TestEffect, Throwable, B]) => TestEffect[Unit]
    ): TestEffect[B] = testEffectMonadCancel.bracketCase(acquire)(use)(release)

    override def monotonic: TestEffect[FiniteDuration] = testEffectClock.monotonic

    override def realTime: TestEffect[FiniteDuration] = testEffectClock.realTime

    override def suspend[A](hint: Sync.Type)(body: => A): TestEffect[A] = TestEffect.delay(body)
  }
}
