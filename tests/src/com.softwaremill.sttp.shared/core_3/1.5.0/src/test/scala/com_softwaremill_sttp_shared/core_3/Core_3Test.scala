/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_shared.core_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sttp.attributes.AttributeKey
import sttp.attributes.AttributeMap
import sttp.capabilities.Effect
import sttp.capabilities.StreamMaxLengthExceededException
import sttp.capabilities.Streams
import sttp.capabilities.WebSockets
import sttp.monad.Canceler
import sttp.monad.EitherMonad
import sttp.monad.FutureMonad
import sttp.monad.IdentityMonad
import sttp.monad.MonadError
import sttp.monad.TryMonad
import sttp.monad.syntax.*
import sttp.shared.Identity

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success
import scala.util.Try

class Core_3Test {
  private type EitherEffect[A] = Either[Throwable, A]

  private def expectThrows[T <: Throwable](exceptionType: Class[T])(body: => Unit): T =
    assertThrows(exceptionType, () => body)

  private def awaitFuture[A](future: Future[A]): A =
    Await.result(future, 5.seconds)

  private def leftValue[A](either: Either[Throwable, A]): Throwable =
    either match {
      case Left(throwable) => throwable
      case Right(value)    => throw new AssertionError(s"Expected Left, but got Right($value)")
    }

  @Test
  def attributeKeysAndMapsAreTypedImmutableAndNameBased(): Unit = {
    val stringKey: AttributeKey[String] = AttributeKey[String]
    val sameStringKey: AttributeKey[String] = AttributeKey[String]
    val explicitStringKey: AttributeKey[String] = new AttributeKey[String](stringKey.typeName)
    val intKey: AttributeKey[Int] = AttributeKey[Int]

    assertEquals(stringKey, sameStringKey)
    assertEquals(stringKey, explicitStringKey)
    assertEquals(stringKey.hashCode(), explicitStringKey.hashCode())
    assertNotEquals(stringKey, intKey)

    val empty: AttributeMap = AttributeMap.Empty
    assertTrue(empty.isEmpty)
    assertFalse(empty.nonEmpty)
    assertEquals(None, empty.get(stringKey))

    val withString: AttributeMap = empty.put(stringKey, "original")
    assertTrue(withString.nonEmpty)
    assertFalse(withString.isEmpty)
    assertEquals(Some("original"), withString.get(sameStringKey))
    assertEquals(None, withString.get(intKey))
    assertEquals(None, empty.get(stringKey))

    val overwritten: AttributeMap = withString.put(explicitStringKey, "updated")
    assertEquals(Some("updated"), overwritten.get(stringKey))
    assertEquals(Some("original"), withString.get(stringKey))

    val withBothValues: AttributeMap = overwritten.put(intKey, 42)
    val withoutString: AttributeMap = withBothValues.remove(stringKey)
    assertEquals(None, withoutString.get(stringKey))
    assertEquals(Some(42), withoutString.get(intKey))
  }

  @Test
  def streamLimitExceptionReportsLimitAndSupportsCaseClassOperations(): Unit = {
    val exception: StreamMaxLengthExceededException = StreamMaxLengthExceededException(1024L)

    assertEquals(1024L, exception.maxBytes)
    assertEquals("Stream length limit of 1024 bytes exceeded", exception.getMessage())
    assertEquals(exception, StreamMaxLengthExceededException(1024L))
    assertEquals(StreamMaxLengthExceededException(2048L), exception.copy(maxBytes = 2048L))
  }

  @Test
  def capabilityMarkerTypesCanBeInstantiatedAndUsedAsPublicTypes(): Unit = {
    final class TestStreams extends Streams[TestStreams] {
      type BinaryStream = Array[Byte]
      type Pipe[A, B] = A => B
    }

    val streams: TestStreams = new TestStreams
    val bytes: streams.BinaryStream = Array[Byte](1, 2, 3)
    val lengthPipe: streams.Pipe[String, Int] = _.length
    val effect: Effect[Try] = new Effect[Try] {}
    val webSockets: WebSockets = new WebSockets {}

    assertEquals(3, bytes.length)
    assertEquals(5, lengthPipe("sttp3"))
    assertNotNull(effect)
    assertNotNull(webSockets)
  }

  @Test
  def cancelerInvokesTheRegisteredCancellationAction(): Unit = {
    val calls: AtomicInteger = new AtomicInteger(0)
    val canceler: Canceler = Canceler(() => {
      calls.incrementAndGet()
      ()
    })

    canceler.cancel()
    canceler.cancel()

    assertEquals(2, calls.get())

    val copiedCalls: AtomicInteger = new AtomicInteger(0)
    val copied: Canceler = canceler.copy(cancel = () => {
      copiedCalls.incrementAndGet()
      ()
    })
    copied.cancel()

    assertEquals(1, copiedCalls.get())
    assertEquals(2, calls.get())
  }

  @Test
  def identityMonadRunsDirectStyleComputationsAndFinalizers(): Unit = {
    val failure: IllegalArgumentException = new IllegalArgumentException("identity failure")
    val sideEffect: AtomicInteger = new AtomicInteger(0)

    assertEquals(2, IdentityMonad.unit(2))
    assertEquals(6, IdentityMonad.map(3)(_ * 2))
    assertEquals(7, IdentityMonad.flatMap(3)(value => value + 4))
    assertEquals(5, IdentityMonad.eval(5))
    assertEquals(8, IdentityMonad.suspend(8))
    assertEquals(9, IdentityMonad.flatten(9))
    assertEquals(10, IdentityMonad.flatTap(10)(value => sideEffect.addAndGet(value)))
    assertEquals(10, sideEffect.get())
    assertEquals(11, IdentityMonad.fromTry(Success(11)))

    assertSame(failure, expectThrows(classOf[IllegalArgumentException]) {
      IdentityMonad.error[Int](failure)
    })

    val recovered: Int = IdentityMonad.handleError(throw failure) {
      case _: IllegalArgumentException => 14
    }
    assertEquals(14, recovered)

    val finalizerRanAfterSuccess: AtomicBoolean = new AtomicBoolean(false)
    val ensured: Int = IdentityMonad.ensure2(21, {
      finalizerRanAfterSuccess.set(true)
      ()
    })
    assertEquals(21, ensured)
    assertTrue(finalizerRanAfterSuccess.get())

    val finalizerRanAfterFailure: AtomicBoolean = new AtomicBoolean(false)
    val thrown: RuntimeException = new RuntimeException("construction failed")
    assertSame(thrown, expectThrows(classOf[RuntimeException]) {
      IdentityMonad.ensure2[Int](throw thrown, {
        finalizerRanAfterFailure.set(true)
        ()
      })
    })
    assertTrue(finalizerRanAfterFailure.get())
  }

  @Test
  def eitherMonadPropagatesFailuresAndRunsFinalizersWithCorrectPrecedence(): Unit = {
    val failure: IllegalStateException = new IllegalStateException("either failure")
    val finalizerFailure: IllegalArgumentException = new IllegalArgumentException("finalizer failure")

    assertEquals(Right("value"), EitherMonad.unit("value"))
    assertEquals(Right(4), EitherMonad.map(Right(2))(_ * 2))
    assertEquals(Right(7), EitherMonad.flatMap(Right(3))(value => Right(value + 4)))
    assertSame(failure, leftValue(EitherMonad.map(Left(failure): EitherEffect[Int])(_ + 1)))
    assertSame(failure, leftValue(EitherMonad.error[Int](failure)))
    assertEquals(Right(12), EitherMonad.eval(12))
    assertEquals(Right(13), EitherMonad.suspend(Right(13)))
    assertEquals(Right(14), EitherMonad.flatten(Right(Right(14))))
    assertEquals(Right(15), EitherMonad.fromTry(Success(15)))
    assertSame(failure, leftValue(EitherMonad.fromTry(Failure[Int](failure))))

    val tappedValue: AtomicInteger = new AtomicInteger(0)
    assertEquals(Right(16), EitherMonad.flatTap(Right(16))(value => Right(tappedValue.set(value))))
    assertEquals(16, tappedValue.get())

    val recoveredFromLeft: EitherEffect[Int] = EitherMonad.handleError(Left(failure)) {
      case _: IllegalStateException => Right(17)
    }
    assertEquals(Right(17), recoveredFromLeft)

    val recoveredFromThrownConstruction: EitherEffect[Int] = EitherMonad.handleError(throw failure) {
      case _: IllegalStateException => Right(18)
    }
    assertEquals(Right(18), recoveredFromThrownConstruction)

    val successFinalizerCalls: AtomicInteger = new AtomicInteger(0)
    assertEquals(Right(19), EitherMonad.ensure2(Right(19), Right(successFinalizerCalls.incrementAndGet()).map(_ => ())))
    assertEquals(1, successFinalizerCalls.get())

    assertSame(failure, leftValue(EitherMonad.ensure2(Left(failure): EitherEffect[Int], Right(()))))
    assertSame(finalizerFailure, leftValue(EitherMonad.ensure2(Right(20), Left(finalizerFailure))))

    val constructionFinalizerCalls: AtomicInteger = new AtomicInteger(0)
    assertSame(failure, expectThrows(classOf[IllegalStateException]) {
      EitherMonad.ensure2[Int](throw failure, Right(constructionFinalizerCalls.incrementAndGet()).map(_ => ()))
    })
    assertEquals(1, constructionFinalizerCalls.get())
  }

  @Test
  def tryMonadCapturesExceptionsAndRunsFinalizersWithCorrectPrecedence(): Unit = {
    val failure: IllegalStateException = new IllegalStateException("try failure")
    val finalizerFailure: IllegalArgumentException = new IllegalArgumentException("try finalizer failure")

    assertEquals(Success("value"), TryMonad.unit("value"))
    assertEquals(Success(4), TryMonad.map(Success(2))(_ * 2))
    assertEquals(Success(7), TryMonad.flatMap(Success(3))(value => Success(value + 4)))
    assertSame(failure, TryMonad.map(Failure[Int](failure))(_ + 1).failed.get)
    assertSame(failure, TryMonad.error[Int](failure).failed.get)
    assertEquals(Success(12), TryMonad.eval(12))
    assertSame(failure, TryMonad.eval(throw failure).failed.get)
    assertEquals(Success(13), TryMonad.suspend(Success(13)))
    assertEquals(Success(14), TryMonad.flatten(Success(Success(14))))
    assertEquals(Success(15), TryMonad.fromTry(Success(15)))
    assertSame(failure, TryMonad.fromTry(Failure[Int](failure)).failed.get)

    val tappedValue: AtomicInteger = new AtomicInteger(0)
    assertEquals(Success(16), TryMonad.flatTap(Success(16))(value => Success(tappedValue.set(value))))
    assertEquals(16, tappedValue.get())

    val recoveredFromFailure: Try[Int] = TryMonad.handleError(Failure(failure)) {
      case _: IllegalStateException => Success(17)
    }
    assertEquals(Success(17), recoveredFromFailure)

    val successFinalizerCalls: AtomicInteger = new AtomicInteger(0)
    assertEquals(Success(18), TryMonad.ensure2(Success(18), Success(successFinalizerCalls.incrementAndGet()).map(_ => ())))
    assertEquals(1, successFinalizerCalls.get())

    assertSame(failure, TryMonad.ensure2(Failure[Int](failure), Success(())).failed.get)
    assertSame(finalizerFailure, TryMonad.ensure2(Success(19), Failure[Unit](finalizerFailure)).failed.get)

    val constructionFinalizerCalls: AtomicInteger = new AtomicInteger(0)
    assertSame(failure, expectThrows(classOf[IllegalStateException]) {
      TryMonad.ensure2[Int](throw failure, Success(constructionFinalizerCalls.incrementAndGet()).map(_ => ()))
    })
    assertEquals(1, constructionFinalizerCalls.get())
  }

  @Test
  def futureMonadCompletesAsyncComputationsAndFinalizers(): Unit = {
    val executor = Executors.newSingleThreadExecutor()
    given executionContext: ExecutionContext = ExecutionContext.fromExecutor(executor)
    val monad: FutureMonad = new FutureMonad()

    try {
      val failure: IllegalStateException = new IllegalStateException("future failure")
      val finalizerFailure: IllegalArgumentException = new IllegalArgumentException("future finalizer failure")

      assertEquals("value", awaitFuture(monad.unit("value")))
      assertEquals(4, awaitFuture(monad.map(monad.unit(2))(_ * 2)))
      assertEquals(7, awaitFuture(monad.flatMap(monad.unit(3))(value => monad.unit(value + 4))))
      assertEquals(12, awaitFuture(monad.eval(12)))
      assertEquals(13, awaitFuture(monad.suspend(monad.unit(13))))
      assertEquals(14, awaitFuture(monad.flatten(monad.unit(monad.unit(14)))))
      assertEquals(15, awaitFuture(monad.fromTry(Success(15))))
      assertEquals(16, awaitFuture(monad.blocking(16)))

      val recovered: Int = awaitFuture(monad.handleError(monad.error[Int](failure)) {
        case _: IllegalStateException => monad.unit(17)
      })
      assertEquals(17, recovered)

      val tappedValue: AtomicInteger = new AtomicInteger(0)
      assertEquals(18, awaitFuture(monad.flatTap(monad.unit(18))(value => monad.unit(tappedValue.set(value)))))
      assertEquals(18, tappedValue.get())

      assertSame(failure, expectThrows(classOf[IllegalStateException]) {
        awaitFuture(monad.error[Int](failure))
      })
      assertSame(failure, expectThrows(classOf[IllegalStateException]) {
        awaitFuture(monad.fromTry(Failure[Int](failure)))
      })

      val asyncResult: Future[Int] = monad.async[Int] { callback =>
        callback(Right(19))
        Canceler(() => ())
      }
      assertEquals(19, awaitFuture(asyncResult))

      val asyncFailure: Future[Int] = monad.async[Int] { callback =>
        callback(Left(failure))
        Canceler(() => ())
      }
      assertSame(failure, expectThrows(classOf[IllegalStateException]) {
        awaitFuture(asyncFailure)
      })

      val successFinalizerCalls: AtomicInteger = new AtomicInteger(0)
      assertEquals(20, awaitFuture(monad.ensure2(monad.unit(20), monad.eval {
        successFinalizerCalls.incrementAndGet()
        ()
      })))
      assertEquals(1, successFinalizerCalls.get())

      assertSame(failure, expectThrows(classOf[IllegalStateException]) {
        awaitFuture(monad.ensure2(monad.error[Int](failure), monad.unit(())))
      })
      assertSame(finalizerFailure, expectThrows(classOf[IllegalArgumentException]) {
        awaitFuture(monad.ensure2(monad.unit(21), monad.error[Unit](finalizerFailure)))
      })

      val constructionFinalizerCalls: AtomicInteger = new AtomicInteger(0)
      assertSame(failure, expectThrows(classOf[IllegalStateException]) {
        awaitFuture(monad.ensure2[Int](throw failure, monad.eval {
          constructionFinalizerCalls.incrementAndGet()
          ()
        }))
      })
      assertEquals(1, constructionFinalizerCalls.get())
    } finally {
      executor.shutdownNow()
      executor.awaitTermination(5, TimeUnit.SECONDS)
      ()
    }
  }

  @Test
  def synchronousMonadsEvaluateBlockingComputationsInTheirEffect(): Unit = {
    val calls: AtomicInteger = new AtomicInteger(0)
    val eitherFailure: IllegalStateException = new IllegalStateException("either blocking failure")
    val tryFailure: IllegalArgumentException = new IllegalArgumentException("try blocking failure")

    val identityResult: Int = IdentityMonad.blocking {
      calls.incrementAndGet()
      25
    }
    assertEquals(25, identityResult)

    val eitherResult: EitherEffect[Int] = EitherMonad.blocking {
      calls.incrementAndGet()
      26
    }
    assertEquals(Right(26), eitherResult)
    assertSame(eitherFailure, expectThrows(classOf[IllegalStateException]) {
      EitherMonad.blocking[Int] {
        calls.incrementAndGet()
        throw eitherFailure
      }
      ()
    })

    val tryResult: Try[Int] = TryMonad.blocking {
      calls.incrementAndGet()
      27
    }
    assertEquals(Success(27), tryResult)
    assertSame(tryFailure, TryMonad.blocking[Int] {
      calls.incrementAndGet()
      throw tryFailure
    }.failed.get)
    assertEquals(5, calls.get())
  }

  @Test
  def monadSyntaxUsesTheImplicitMonadErrorInstance(): Unit = {
    given MonadError[EitherEffect] = EitherMonad

    val summoned: MonadError[EitherEffect] = MonadError[EitherEffect]
    assertSame(EitherMonad, summoned)

    val lifted: EitherEffect[String] = "lifted".unit
    assertEquals(Right("lifted"), lifted)

    val finalizerCalls: AtomicInteger = new AtomicInteger(0)
    val ensured: EitherEffect[Int] = (Right(22): EitherEffect[Int]).ensure {
      Right(finalizerCalls.incrementAndGet()).map(_ => ())
    }
    assertEquals(Right(22), ensured)
    assertEquals(1, finalizerCalls.get())

    val tappedValue: AtomicInteger = new AtomicInteger(0)
    val tapped: EitherEffect[Int] = (Right(23): EitherEffect[Int]).flatTap { value =>
      Right(tappedValue.set(value))
    }
    assertEquals(Right(23), tapped)
    assertEquals(23, tappedValue.get())

    val failure: IllegalArgumentException = new IllegalArgumentException("syntax failure")
    val recovered: EitherEffect[Int] = (Left(failure): EitherEffect[Int]).handleError {
      case _: IllegalArgumentException => Right(24)
    }
    assertEquals(Right(24), recovered)
  }

  @Test
  def identityAliasWorksWhereAnEffectTypeConstructorIsExpected(): Unit = {
    val identityMonad: MonadError[Identity] = IdentityMonad

    assertEquals("direct", identityMonad.unit("direct"))
    assertEquals("DIRECT", identityMonad.map("direct")(_.toUpperCase()))
  }
}
