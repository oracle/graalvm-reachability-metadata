/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_managed_3

import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import zio.Cause
import zio.Duration
import zio.Exit
import zio.Promise
import zio.Ref
import zio.Runtime
import zio.Unsafe
import zio.ZIO
import zio.ZLayer
import zio.managed.*
import zio.stream.ZSink
import zio.stream.ZStream

@Timeout(10)
class Zio_managed_3Test {
  @Test
  def managedResourcesAcquireUseAndReleaseInDeterministicOrder(): Unit = {
    val events: List[String] = unsafeRun {
      for {
        ref <- Ref.make(List.empty[String])
        result <- nestedResource(ref).use { value =>
          ref.update(s"use-$value" :: _) *> ZIO.succeed(value)
        }
        recorded <- ref.get
      } yield {
        assertThat(result).isEqualTo("outer+inner")
        recorded.reverse
      }
    }

    assertThat(events).isEqualTo(List(
      "acquire-outer",
      "acquire-inner",
      "use-outer+inner",
      "release-inner-success",
      "release-outer-success"
    ))
  }

  @Test
  def finalizersReceiveFailureExitsAndErrorCombinatorsRecoverValues(): Unit = {
    val values = unsafeRun {
      for {
        ref <- Ref.make(List.empty[String])
        failed <- nestedResource(ref).use(_ => ZIO.fail("boom")).either
        events <- ref.get.map(_.reverse)
        recovered <- ZManaged
          .fail("bad")
          .mapError(_.length)
          .catchAll(length => ZManaged.succeed(length + 1))
          .useNow
        folded <- ZManaged.fail("folded").fold(_.length, (_: Int) => 0).useNow
        causeMessage <- ZManaged
          .failCause(Cause.fail("typed"))
          .sandbox
          .catchAllCause(cause => ZManaged.succeed(cause.prettyPrint.contains("typed")))
          .useNow
        attempted <- ZManaged.attempt(throw new IllegalStateException("attempted")).either.useNow
        optionValue <- ZManaged.fromOption(Some(42)).option.useNow
        missingValue <- ZManaged.fromOption(None).either.useNow
        absolved <- ZManaged.absolve(ZManaged.succeed(Right(7): Either[String, Int])).useNow
      } yield (failed, events, recovered, folded, causeMessage, attempted, optionValue, missingValue, absolved)
    }

    assertThat(values._1).isEqualTo(Left("boom"))
    assertThat(values._2).isEqualTo(List(
      "acquire-outer",
      "acquire-inner",
      "release-inner-failure",
      "release-outer-failure"
    ))
    assertThat(values._3).isEqualTo(4)
    assertThat(values._4).isEqualTo(6)
    assertThat(values._5).isTrue()
    assertThat(values._6.isLeft).isTrue()
    assertThat(values._7).isEqualTo(Some(42))
    assertThat(values._8).isEqualTo(Left(None))
    assertThat(values._9).isEqualTo(7)
  }

  @Test
  def collectionAndParallelCombinatorsTraverseManagedValues(): Unit = {
    val values = unsafeRun {
      for {
        collected <- ZManaged.collect(List(1, 2, 3, 4)) { number =>
          if (number % 2 == 0) ZManaged.succeed(number * 10) else ZManaged.fail(None)
        }.useNow
        collectedAll <- ZManaged.collectAll(List(ZManaged.succeed("a"), ZManaged.succeed("b"))).useNow
        foreachValues <- ZManaged.foreach(List(1, 2, 3))(number => ZManaged.succeed(number + 1)).useNow
        first <- ZManaged.collectFirst(List(1, 2, 3, 4)) { number =>
          ZManaged.succeed(Option.when(number > 2)(number * 100))
        }.useNow
        exists <- ZManaged.exists(List(1, 3, 5, 8))(number => ZManaged.succeed(number % 2 == 0)).useNow
        forall <- ZManaged.forall(List(2, 4, 6))(number => ZManaged.succeed(number % 2 == 0)).useNow
        folded <- ZManaged.foldLeft(List("a", "b", "c"))(new StringBuilder) { (builder, value) =>
          ZManaged.succeed(builder.append(value))
        }.map(_.toString).useNow
        merged <- ZManaged.mergeAll(List(ZManaged.succeed(1), ZManaged.succeed(2), ZManaged.succeed(3)))(0)(_ + _).useNow
      } yield (collected, collectedAll, foreachValues, first, exists, forall, folded, merged)
    }

    assertThat(values._1).isEqualTo(List(20, 40))
    assertThat(values._2).isEqualTo(List("a", "b"))
    assertThat(values._3).isEqualTo(List(2, 3, 4))
    assertThat(values._4).isEqualTo(Some(300))
    assertThat(values._5).isTrue()
    assertThat(values._6).isTrue()
    assertThat(values._7).isEqualTo("abc")
    assertThat(values._8).isEqualTo(6)
  }

  @Test
  def resourceHelpersCloseResourcesAndSupportEarlyRelease(): Unit = {
    val closeCounter: AtomicInteger = new AtomicInteger(0)
    val earlyReleaseCounter: AtomicInteger = new AtomicInteger(0)

    val values = unsafeRun {
      for {
        readBytes <- ZManaged
          .fromAutoCloseable(ZIO.succeed(new CountingInputStream(Array[Byte](1, 2, 3), closeCounter)))
          .use(stream => ZIO.attempt(stream.readAllBytes().toList.map(_.toInt)))
        earlyValue <- ZManaged
          .acquireReleaseSucceedWith("resource")(_ => earlyReleaseCounter.incrementAndGet())
          .withEarlyRelease
          .use { case (release, value) => release *> release *> ZIO.succeed(value) }
        preallocated <- ZManaged.acquireReleaseSucceedWith("preallocated")(_ => earlyReleaseCounter.incrementAndGet()).preallocate
        preallocatedValue <- preallocated.useNow
        finalizerRefValue <- ZManaged.finalizerRef[Any](_ => ZIO.succeed(earlyReleaseCounter.incrementAndGet())).use { ref =>
          ref.set(_ => ZIO.succeed(earlyReleaseCounter.addAndGet(10))) *> ZIO.succeed("updated")
        }
      } yield (readBytes, earlyValue, preallocatedValue, finalizerRefValue)
    }

    assertThat(values._1).isEqualTo(List(1, 2, 3))
    assertThat(values._2).isEqualTo("resource")
    assertThat(values._3).isEqualTo("preallocated")
    assertThat(values._4).isEqualTo("updated")
    assertThat(closeCounter.get()).isEqualTo(1)
    assertThat(earlyReleaseCounter.get()).isEqualTo(12)
  }

  @Test
  def environmentServiceLayerAndScopedConversionsProvideDependencies(): Unit = {
    val layer: ZLayer[Any, Nothing, GreetingService] = ZLayer.succeed(new GreetingService {
      override def greet(name: String): String = s"hello-$name"
    })

    val values = unsafeRun {
      val managed: ZManaged[GreetingService, Nothing, (String, String, String, String, String)] = for {
        environment <- ZManaged.environment[GreetingService]
        fromEnvironment <- ZManaged.environmentWith[GreetingService](_.get[GreetingService].greet("environment"))
        service <- ZManaged.service[GreetingService]
        withService <- ZManaged.serviceWith[GreetingService](_.greet("service"))
        withZio <- ZManaged.serviceWithZIO[GreetingService](service => ZIO.succeed(service.greet("zio")))
        withManaged <- ZManaged.serviceWithManaged[GreetingService](service => ZManaged.succeed(service.greet("managed")))
      } yield (
        environment.get[GreetingService].greet("direct"),
        fromEnvironment,
        service.greet("instance"),
        withService,
        s"$withZio-$withManaged"
      )

      for {
        managedValues <- managed.provideLayer(layer).useNow
        layerValues <- ZIO.scoped {
          ZLayer.fromManaged(ZManaged.succeed[GreetingService](new GreetingService {
            override def greet(name: String): String = s"managed-layer-$name"
          })).build.flatMap(environment => ZIO.succeed(environment.get[GreetingService].greet("value")))
        }
        environmentFromLayer <- layer.toManaged.use(environment => ZIO.succeed(environment.get[GreetingService].greet("converted")))
      } yield (managedValues, layerValues, environmentFromLayer)
    }

    assertThat(values._1).isEqualTo(("hello-direct", "hello-environment", "hello-instance", "hello-service", "hello-zio-hello-managed"))
    assertThat(values._2).isEqualTo("managed-layer-value")
    assertThat(values._3).isEqualTo("hello-converted")
  }

  @Test
  def packageSyntaxIntegratesZioPromisesRefsStreamsAndLayers(): Unit = {
    val values = unsafeRun {
      for {
        managedFromZio <- ZIO.succeed("zio-value").toManagedWith(_ => ZIO.unit).useNow
        refValue <- Ref.makeManaged(10).use(ref => ref.updateAndGet(_ + 5))
        promiseCompleted <- Promise.makeManaged[Nothing, String].use { promise =>
          promise.succeed("done") *> promise.await
        }
        streamSum <- ZStream.fromIterable(List(1, 2, 3, 4)).runFoldManaged(0)(_ + _).useNow
        streamViaSink <- ZStream.fromIterable(List("a", "b", "c")).runManaged(ZSink.collectAll[String]).map(_.mkString).useNow
        managedStream <- ZStream.managed(ZManaged.succeed("single")).runCollect
        iteratorStream <- ZStream.fromIteratorManaged(ZManaged.succeed(List(4, 5, 6).iterator)).runCollect
      } yield (managedFromZio, refValue, promiseCompleted, streamSum, streamViaSink, managedStream.toList, iteratorStream.toList)
    }

    assertThat(values._1).isEqualTo("zio-value")
    assertThat(values._2).isEqualTo(15)
    assertThat(values._3).isEqualTo("done")
    assertThat(values._4).isEqualTo(10)
    assertThat(values._5).isEqualTo("abc")
    assertThat(values._6).isEqualTo(List("single"))
    assertThat(values._7).isEqualTo(List(4, 5, 6))
  }

  @Test
  def releaseMapOperationsRegisterReplaceRemoveAndCloseFinalizers(): Unit = {
    val events = unsafeRun {
      for {
        ref <- Ref.make(List.empty[String])
        releaseMap <- ZManaged.ReleaseMap.make
        firstKey <- releaseMap.addIfOpen(record(ref, "first"))
        secondKey <- releaseMap.addIfOpen(record(ref, "second"))
        firstPresent <- ZIO.foreach(firstKey)(releaseMap.get).map(_.flatten.isDefined)
        removedSecond <- ZIO.foreach(secondKey)(releaseMap.remove).map(_.flatten.isDefined)
        _ <- ZIO.foreachDiscard(firstKey)(key => releaseMap.replace(key, record(ref, "first-replaced")))
        _ <- ZIO.foreachDiscard(firstKey)(key => releaseMap.release(key, Exit.succeed("manual")))
        afterManualRelease <- ref.get.map(_.reverse)
        thirdKey <- releaseMap.addIfOpen(record(ref, "third"))
        _ <- releaseMap.updateAll { finalizer => exit =>
          ref.update("wrapped-before" :: _) *> finalizer(exit) *> ref.update("wrapped-after" :: _)
        }
        _ <- releaseMap.releaseAll(Exit.fail("scope-failed"), zio.ExecutionStrategy.Sequential)
        lateKey <- releaseMap.addIfOpen(record(ref, "late"))
        finalEvents <- ref.get.map(_.reverse)
      } yield (firstPresent, removedSecond, afterManualRelease, thirdKey.isDefined, lateKey.isDefined, finalEvents)
    }

    assertThat(events._1).isTrue()
    assertThat(events._2).isTrue()
    assertThat(events._3).isEqualTo(List("first-replaced-success"))
    assertThat(events._4).isTrue()
    assertThat(events._5).isFalse()
    assertThat(events._6).isEqualTo(List(
      "first-replaced-success",
      "wrapped-before",
      "third-failure",
      "wrapped-after",
      "late-failure"
    ))
  }

  private def nestedResource(ref: Ref[List[String]]): ZManaged[Any, Nothing, String] = {
    def resource(name: String): ZManaged[Any, Nothing, String] =
      ZManaged.acquireReleaseExitWith(ref.update(s"acquire-$name" :: _).as(name)) { (_, exit) =>
        ref.update(s"release-$name-${exitLabel(exit)}" :: _)
      }

    resource("outer").zipWith(resource("inner"))((outer, inner) => s"$outer+$inner")
  }

  private def record(ref: Ref[List[String]], name: String): ZManaged.Finalizer = { exit =>
    ref.update(s"$name-${exitLabel(exit)}" :: _)
  }

  private def exitLabel(exit: Exit[Any, Any]): String =
    if (exit.isSuccess) "success" else "failure"

  private def unsafeRun[A](effect: ZIO[Any, Any, A]): A = {
    val bounded: ZIO[Any, Any, A] = effect.timeoutFail(new RuntimeException("ZIO effect timed out"))(Duration.fromSeconds(5))
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(bounded).getOrThrowFiberFailure()
    }
  }

  private final class CountingInputStream(bytes: Array[Byte], closeCounter: AtomicInteger)
      extends ByteArrayInputStream(bytes) {
    private val closed: AtomicBoolean = new AtomicBoolean(false)

    override def close(): Unit = {
      if (closed.compareAndSet(false, true)) {
        closeCounter.incrementAndGet()
      }
      super.close()
    }
  }

  private trait GreetingService {
    def greet(name: String): String
  }
}
