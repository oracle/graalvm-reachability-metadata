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
import cats.effect.kernel.Resource
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
}
