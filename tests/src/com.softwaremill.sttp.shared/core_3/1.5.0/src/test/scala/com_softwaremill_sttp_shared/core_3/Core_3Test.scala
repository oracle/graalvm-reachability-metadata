/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_shared.core_3

import org.junit.jupiter.api.Assertions.assertArrayEquals
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContextExecutorService
import scala.concurrent.Future
import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success
import scala.util.Try

class Core_3Test {
  @Test
  def attributeKeysAndMapsProvideTypedImmutableStorage(): Unit = {
    val stringKey: AttributeKey[String] = AttributeKey[String]
    val sameStringKey: AttributeKey[String] = new AttributeKey[String](stringKey.typeName)
    val intKey: AttributeKey[Int] = AttributeKey[Int]

    assertTrue(stringKey.typeName.nonEmpty)
    assertTrue(intKey.typeName.nonEmpty)
    assertEquals(stringKey, sameStringKey)
    assertEquals(stringKey.hashCode(), sameStringKey.hashCode())
    assertNotEquals(stringKey, intKey)
    assertNotEquals(stringKey, "not-a-key")

    val empty: AttributeMap = AttributeMap.Empty
    assertTrue(empty.isEmpty)
    assertFalse(empty.nonEmpty)
    assertEquals(None, empty.get(stringKey))

    val withValues: AttributeMap = empty
      .put(stringKey, "alice")
      .put(intKey, 42)
    assertTrue(withValues.nonEmpty)
    assertFalse(withValues.isEmpty)
    assertEquals(Some("alice"), withValues.get(stringKey))
    assertEquals(Some("alice"), withValues.get(sameStringKey))
    assertEquals(Some(42), withValues.get(intKey))
    assertTrue(empty.isEmpty)

    val overwritten: AttributeMap = withValues.put(sameStringKey, "bob")
    assertEquals(Some("bob"), overwritten.get(stringKey))
    assertEquals(Some(42), overwritten.get(intKey))

    val withoutString: AttributeMap = overwritten.remove(stringKey)
    assertEquals(None, withoutString.get(stringKey))
    assertEquals(Some(42), withoutString.get(intKey))
  }

  @Test
  def attributeMapsCanBeBuiltAndCopiedFromUnderlyingEntries(): Unit = {
    val userKey: AttributeKey[String] = new AttributeKey[String]("user")
    val attemptsKey: AttributeKey[Int] = new AttributeKey[Int]("attempts")
    val entries: Map[String, Any] = Map(
      userKey.typeName -> "alice",
      attemptsKey.typeName -> 2
    )

    val fromEntries: AttributeMap = AttributeMap(entries)
    assertFalse(fromEntries.isEmpty)
    assertEquals(Some("alice"), fromEntries.get(userKey))
    assertEquals(Some(2), fromEntries.get(attemptsKey))

    val copied: AttributeMap = fromEntries.copy(entries.updated(userKey.typeName, "bob"))
    assertEquals(Some("bob"), copied.get(userKey))
    assertEquals(Some(2), copied.get(attemptsKey))
    assertEquals(Some("alice"), fromEntries.get(userKey))
  }

  @Test
  def eitherMonadMapsRecoversConvertsAndRunsFinalizers(): Unit = {
    val boom: IllegalStateException = new IllegalStateException("boom")
    val finalizers: AtomicInteger = new AtomicInteger(0)

    assertEquals(Right(1), EitherMonad.unit(1))
    assertEquals(Right(4), EitherMonad.map(Right(2))(_ * 2))
    assertEquals(Right("value-3"), EitherMonad.flatMap(Right(3))(value => Right(s"value-$value")))
    assertEquals(Left(boom), EitherMonad.error[Int](boom))
    assertEquals(Right(10), EitherMonad.fromTry(Success(10)))
    assertEquals(Left(boom), EitherMonad.fromTry(Failure(boom)))
    assertEquals(Right(99), EitherMonad.handleError[Int](Left(boom)) { case _: IllegalStateException => Right(99) })
    assertEquals(Right(8), EitherMonad.flatten(Right(Right(8))))
    assertEquals(Right(7), EitherMonad.flatTap(Right(7))(_ => Right("side-effect")))

    val success: Either[Throwable, String] = EitherMonad.ensure2(
      Right("ok"),
      Right(finalizers.incrementAndGet()).map(_ => ())
    )
    assertEquals(Right("ok"), success)
    assertEquals(1, finalizers.get())

    val failed: Either[Throwable, String] = EitherMonad.ensure2(
      Left(boom),
      Right(finalizers.incrementAndGet()).map(_ => ())
    )
    assertEquals(Left(boom), failed)
    assertEquals(2, finalizers.get())

    val constructionFailure: IllegalArgumentException = new IllegalArgumentException("constructed eagerly")
    val thrown: IllegalArgumentException = assertThrows(
      classOf[IllegalArgumentException],
      () => EitherMonad.ensure2(throw constructionFailure, Right(finalizers.incrementAndGet()).map(_ => ()))
    )
    assertSame(constructionFailure, thrown)
    assertEquals(3, finalizers.get())
  }

  @Test
  def tryMonadEvaluatesFailuresRecoversAndPreservesFinalizerSemantics(): Unit = {
    val boom: IllegalStateException = new IllegalStateException("boom")
    val finalizers: AtomicInteger = new AtomicInteger(0)

    assertEquals(Success(2), TryMonad.unit(2))
    assertEquals(Success(6), TryMonad.map(Success(3))(_ * 2))
    assertEquals(Success("value-4"), TryMonad.flatMap(Success(4))(value => Success(s"value-$value")))
    assertEquals(Failure(boom), TryMonad.error[Int](boom))
    assertEquals(Failure(boom), TryMonad.eval(throw boom))
    assertEquals(Success(11), TryMonad.handleError[Int](Failure(boom)) { case _: IllegalStateException => Success(11) })
    assertEquals(Success(12), TryMonad.fromTry(Success(12)))
    assertEquals(Failure(boom), TryMonad.fromTry(Failure(boom)))
    assertEquals(Success(5), TryMonad.flatten(Success(Success(5))))
    assertEquals(Success(9), TryMonad.flatTap(Success(9))(_ => Success("side-effect")))

    val success: Try[String] = TryMonad.ensure2(
      Success("ok"),
      Try(finalizers.incrementAndGet()).map(_ => ())
    )
    assertEquals(Success("ok"), success)
    assertEquals(1, finalizers.get())

    val failed: Try[String] = TryMonad.ensure2(
      Failure(boom),
      Try(finalizers.incrementAndGet()).map(_ => ())
    )
    assertEquals(Failure(boom), failed)
    assertEquals(2, finalizers.get())

    val finalizerFailure: IllegalArgumentException = new IllegalArgumentException("cleanup")
    assertEquals(
      Failure(finalizerFailure),
      TryMonad.ensure2(Success("ignored"), Failure(finalizerFailure))
    )

    val constructionFailure: IllegalStateException = new IllegalStateException("constructed eagerly")
    val thrown: IllegalStateException = assertThrows(
      classOf[IllegalStateException],
      () => TryMonad.ensure2(throw constructionFailure, Try(finalizers.incrementAndGet()).map(_ => ()))
    )
    assertSame(constructionFailure, thrown)
    assertEquals(3, finalizers.get())
  }

  @Test
  def identityMonadUsesDirectStyleValuesAndStillHandlesErrors(): Unit = {
    val boom: IllegalStateException = new IllegalStateException("boom")
    val finalizers: AtomicInteger = new AtomicInteger(0)

    val unitValue: Identity[Int] = IdentityMonad.unit(3)
    val mapped: Identity[Int] = IdentityMonad.map(unitValue)(_ + 4)
    val flatMapped: Identity[String] = IdentityMonad.flatMap(mapped)(value => s"value-$value")
    val evaluated: Identity[Int] = IdentityMonad.eval(10)
    val suspended: Identity[String] = IdentityMonad.suspend("suspended")
    val flattened: Identity[String] = IdentityMonad.flatten("flattened")
    val tapped: Identity[Int] = IdentityMonad.flatTap(5)(_ => finalizers.incrementAndGet())
    val recovered: Identity[Int] = IdentityMonad.handleError[Int](throw boom) { case _: IllegalStateException => 99 }

    assertEquals(3, unitValue)
    assertEquals(7, mapped)
    assertEquals("value-7", flatMapped)
    assertEquals(10, evaluated)
    assertEquals("suspended", suspended)
    assertEquals("flattened", flattened)
    assertEquals(5, tapped)
    assertEquals(1, finalizers.get())
    assertEquals(99, recovered)
    assertEquals(12, IdentityMonad.fromTry(Success(12)))
    assertEquals(13, IdentityMonad.blocking(13))

    val error: IllegalStateException = assertThrows(classOf[IllegalStateException], () => IdentityMonad.error[Int](boom))
    assertSame(boom, error)

    val ensured: Identity[String] = IdentityMonad.ensure2(
      "ok",
      finalizers.incrementAndGet()
    )
    assertEquals("ok", ensured)
    assertEquals(2, finalizers.get())

    val ensuredFailure: IllegalStateException = assertThrows(
      classOf[IllegalStateException],
      () => IdentityMonad.ensure2(throw boom, finalizers.incrementAndGet())
    )
    assertSame(boom, ensuredFailure)
    assertEquals(3, finalizers.get())
  }

  @Test
  def futureMonadCompletesAsyncOperationsRecoversAndRunsFinalizers(): Unit = {
    val executor: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(2))
    implicit val executionContext: ExecutionContext = executor
    val monad: FutureMonad = new FutureMonad()

    try {
      val boom: IllegalStateException = new IllegalStateException("boom")
      val finalizers: AtomicInteger = new AtomicInteger(0)

      assertEquals(2, await(monad.unit(2)))
      assertEquals(6, await(monad.map(Future.successful(3))(_ * 2)))
      assertEquals("value-4", await(monad.flatMap(Future.successful(4))(value => Future.successful(s"value-$value"))))
      assertEquals(5, await(monad.eval(5)))
      assertEquals(6, await(monad.suspend(Future.successful(6))))
      assertEquals(7, await(monad.flatten(Future.successful(Future.successful(7)))))
      assertEquals(8, await(monad.flatTap(Future.successful(8))(_ => Future.successful(finalizers.incrementAndGet()))))
      assertEquals(1, finalizers.get())
      assertEquals(9, await(monad.fromTry(Success(9))))
      assertEquals(10, await(monad.blocking(10)))
      assertEquals(11, await(monad.handleError[Int](Future.failed(boom)) { case _: IllegalStateException => Future.successful(11) }))
      assertEquals(12, await(monad.handleError[Int](throw boom) { case _: IllegalStateException => Future.successful(12) }))

      val failedFuture: IllegalStateException = assertThrows(classOf[IllegalStateException], () => await(monad.error[Int](boom)))
      assertSame(boom, failedFuture)

      val success: Future[String] = monad.ensure2(
        Future.successful("ok"),
        Future.successful(finalizers.incrementAndGet()).map(_ => ())
      )
      assertEquals("ok", await(success))
      assertEquals(2, finalizers.get())

      val failed: Future[String] = monad.ensure2(
        Future.failed(boom),
        Future.successful(finalizers.incrementAndGet()).map(_ => ())
      )
      val ensuredFailure: IllegalStateException = assertThrows(classOf[IllegalStateException], () => await(failed))
      assertSame(boom, ensuredFailure)
      assertEquals(3, finalizers.get())

      val asyncSuccess: Future[Int] = monad.async[Int] { callback =>
        callback(Right(14))
        Canceler(() => finalizers.incrementAndGet())
      }
      assertEquals(14, await(asyncSuccess))
      assertEquals(3, finalizers.get())

      val asyncFailure: Future[Int] = monad.async[Int] { callback =>
        callback(Left(boom))
        Canceler(() => finalizers.incrementAndGet())
      }
      val asyncThrown: IllegalStateException = assertThrows(classOf[IllegalStateException], () => await(asyncFailure))
      assertSame(boom, asyncThrown)
      assertEquals(3, finalizers.get())
    } finally {
      executor.shutdownNow()
    }
  }

  @Test
  def syntaxExtensionsAndMonadSummonerUseImplicitMonadErrorInstances(): Unit = {
    implicit val monad: MonadError[Try] = TryMonad
    val observed: AtomicInteger = new AtomicInteger(0)

    assertSame(TryMonad, MonadError[Try])

    val lifted: Try[Int] = 10.unit
    val transformed: Try[String] = lifted
      .flatTap(value => Try(observed.set(value)))
      .map(value => s"value-${value + 1}")
    val recovered: Try[Int] = TryMonad.error[Int](new IllegalStateException("handled"))
      .handleError { case _: IllegalStateException => Success(99) }

    assertEquals(Success(10), lifted)
    assertEquals(Success("value-11"), transformed)
    assertEquals(10, observed.get())
    assertEquals(Success(99), recovered)
  }

  @Test
  def eitherSyntaxEnsureRunsFinalizersAndReportsCleanupFailures(): Unit = {
    implicit val monad: MonadError[[A] =>> Either[Throwable, A]] = EitherMonad
    val cleanupRuns: AtomicInteger = new AtomicInteger(0)
    val boom: IllegalStateException = new IllegalStateException("boom")

    val success: Either[Throwable, String] = (Right("ok"): Either[Throwable, String]).ensure {
      Right(cleanupRuns.incrementAndGet()).map(_ => ())
    }
    assertEquals(Right("ok"), success)
    assertEquals(1, cleanupRuns.get())

    val failed: Either[Throwable, String] = (Left(boom): Either[Throwable, String]).ensure {
      Right(cleanupRuns.incrementAndGet()).map(_ => ())
    }
    assertEquals(Left(boom), failed)
    assertEquals(2, cleanupRuns.get())

    val cleanupFailure: IllegalArgumentException = new IllegalArgumentException("cleanup")
    val cleanupFailed: Either[Throwable, String] = (Right("ignored"): Either[Throwable, String]).ensure {
      Left(cleanupFailure)
    }
    assertEquals(Left(cleanupFailure), cleanupFailed)
  }

  @Test
  def capabilitiesExposeMarkerTypesTypedStreamMembersAndCancelableCallbacks(): Unit = {
    class ByteArrayStreams extends Streams[ByteArrayStreams] {
      type BinaryStream = Array[Byte]
      type Pipe[A, B] = A => B
    }

    val streams: ByteArrayStreams = new ByteArrayStreams
    val binary: streams.BinaryStream = Array[Byte](1, 2, 3)
    val pipe: streams.Pipe[Int, String] = value => s"bytes-$value"
    val effect: Effect[Try] = new Effect[Try] {}
    val webSockets: WebSockets = new WebSockets {}
    val canceled: AtomicBoolean = new AtomicBoolean(false)
    val canceler: Canceler = Canceler(() => canceled.set(true))

    assertArrayEquals(Array[Byte](1, 2, 3), binary)
    assertEquals("bytes-3", pipe(3))
    assertNotNull(effect)
    assertNotNull(webSockets)
    assertFalse(canceled.get())
    canceler.cancel()
    assertTrue(canceled.get())
  }

  @Test
  def streamMaxLengthExceededExceptionReportsLimitAndSupportsCaseClassOperations(): Unit = {
    val exception: StreamMaxLengthExceededException = StreamMaxLengthExceededException(1024L)
    val StreamMaxLengthExceededException(limit) = exception

    assertEquals(1024L, limit)
    assertEquals("Stream length limit of 1024 bytes exceeded", exception.getMessage)
    assertEquals(StreamMaxLengthExceededException(2048L), exception.copy(maxBytes = 2048L))
    assertEquals("StreamMaxLengthExceededException", exception.productPrefix)
    assertEquals(1, exception.productArity)
    assertEquals(1024L, exception.productElement(0))
  }

  private def await[A](future: Future[A]): A = Await.result(future, 5.seconds)
}
