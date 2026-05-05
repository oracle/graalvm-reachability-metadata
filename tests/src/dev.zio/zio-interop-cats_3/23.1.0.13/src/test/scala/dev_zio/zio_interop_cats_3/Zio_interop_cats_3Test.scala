/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_interop_cats_3

import cats.Bifunctor
import cats.Defer
import cats.MonadError
import cats.Parallel
import cats.SemigroupK
import cats.data.Ior
import cats.effect.IO
import cats.effect.Ref
import cats.effect.Resource
import cats.effect.kernel.Async
import cats.effect.unsafe.implicits.global
import cats.kernel.Eq
import cats.kernel.Hash
import cats.kernel.Monoid
import cats.kernel.Order
import cats.kernel.PartialOrder
import cats.kernel.Semigroup
import cats.syntax.all.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import zio.Chunk
import zio.NonEmptyChunk
import zio.Runtime
import zio.Scope
import zio.Task
import zio.Unsafe
import zio.ZIO
import zio.interop.Hub
import zio.interop.Queue
import zio.interop.catz.*
import zio.stream.ZStream
import zio.stream.interop.fs2z.*

import java.util.concurrent.atomic.AtomicInteger

class Zio_interop_cats_3Test {
  private given Runtime[Any] = Runtime.default

  @Test
  def catsTypeClassesOperateOnZioEffects(): Unit = {
    val monadError = MonadError[Task, Throwable]
    val recovered = monadError.handleErrorWith(
      monadError.raiseError[Int](new IllegalArgumentException("boom"))
    ) { error =>
      ZIO.succeed(error.getMessage.length)
    }
    assertEquals(4, runZio(recovered))

    val evaluations = new AtomicInteger(0)
    val deferred = Defer[Task].defer {
      ZIO.succeed(evaluations.incrementAndGet())
    }
    assertEquals(0, evaluations.get())
    assertEquals(1, runZio(deferred))
    assertEquals(2, runZio(deferred))

    val attempted = Async[Task].async_[String] { callback =>
      callback(Right("completed by cats Async"))
    }
    assertEquals("completed by cats Async", runZio(attempted))

    val fallback = SemigroupK[Task].combineK(
      ZIO.fail(new RuntimeException("first branch failed")),
      ZIO.succeed(42)
    )
    assertEquals(42, runZio(fallback))

    val bifunctor = Bifunctor[[error, value] =>> ZIO[Any, error, value]]
    val mapped = bifunctor.bimap(ZIO.fail("bad input"))(
      message => new IllegalStateException(message),
      (value: Int) => value + 1
    ).either
    assertEquals("bad input", runZio(mapped).left.toOption.get.getMessage)
  }

  @Test
  def catsEffectRefUsesZioRefSemanticsForAtomicUpdates(): Unit = {
    val program = for {
      ref <- Ref.of[Task, Int](1)
      initial <- ref.get
      previous <- ref.modify(current => (current + 4, current))
      updated <- ref.get
      tryUpdated <- ref.tryModify(current => (current * 2, s"saw-$current"))
      finalValue <- ref.get
    } yield (initial, previous, updated, tryUpdated, finalValue)

    assertEquals((1, 1, 5, Some("saw-5"), 10), runZio(program))
  }

  @Test
  def catsParallelRunsIndependentZioEffectsAndAccumulatesValidatedErrors(): Unit = {
    val sum = (ZIO.succeed(2), ZIO.succeed(3), ZIO.succeed(5)).parMapN(_ + _ + _)
    assertEquals(10, runZio(sum))

    val parallel = Parallel[Task]
    val sequential = parallel.sequential(
      parallel.applicative.product(
        parallel.parallel(ZIO.succeed("left")),
        parallel.parallel(ZIO.succeed("right"))
      )
    )
    assertEquals(("left", "right"), runZio(sequential))
  }

  @Test
  def catsKernelInstancesCompareHashAndCombineZioChunks(): Unit = {
    val combined = Monoid[Chunk[Int]].combine(Chunk(1, 2), Chunk(3, 4))
    assertEquals(List(1, 2, 3, 4), combined.toList)
    assertEquals(Chunk.empty[Int], Monoid[Chunk[Int]].empty)

    assertTrue(Eq[Chunk[Int]].eqv(Chunk(1, 2, 3), Chunk(1, 2, 3)))
    assertFalse(Eq[Chunk[Int]].eqv(Chunk(1, 2), Chunk(2, 1)))
    assertTrue(Order[Chunk[Int]].compare(Chunk(1, 2), Chunk(1, 3)) < 0)
    assertEquals(0.0, PartialOrder[Chunk[Int]].partialCompare(Chunk(2), Chunk(2)))
    assertEquals(Hash[Chunk[Int]].hash(Chunk(5, 6)), Hash[Chunk[Int]].hash(Chunk(5, 6)))

    val aligned = cats.Align[Chunk].align(Chunk(1, 2), Chunk("a")).toList
    assertEquals(List(Ior.Both(1, "a"), Ior.Left(2)), aligned)

    val nonEmptyCombined = Semigroup[NonEmptyChunk[String]].combine(
      NonEmptyChunk("a", "b"),
      NonEmptyChunk("c")
    )
    assertEquals(List("a", "b", "c"), nonEmptyCombined.toList)
    assertTrue(Order[NonEmptyChunk[Int]].compare(NonEmptyChunk(1, 2), NonEmptyChunk(1, 3)) < 0)
  }

  @Test
  def resourcesConvertBetweenCatsAndZioScopesWithFinalizers(): Unit = {
    val releases = new AtomicInteger(0)
    val zioResource: Resource[Task, String] = Resource.scopedZIO(
      ZIO.acquireRelease(ZIO.succeed("zio-resource"))(_ => ZIO.succeed(releases.incrementAndGet()).unit)
    )

    val length = zioResource.use(value => ZIO.succeed(value.length))
    assertEquals("zio-resource".length, runZio(length))
    assertEquals(1, releases.get())

    val acquired = new AtomicInteger(0)
    val released = new AtomicInteger(0)
    val catsResource: Resource[Task, String] = Resource.make(
      ZIO.succeed {
        acquired.incrementAndGet()
        "cats-resource"
      }
    )(_ => ZIO.succeed(released.incrementAndGet()).unit)

    val scoped = catsResource.toScopedZIO.map(value => value.reverse)
    assertEquals("ecruoser-stac", runZio(ZIO.scoped(scoped)))
    assertEquals(1, acquired.get())
    assertEquals(1, released.get())
  }

  @Test
  def queueAndHubExposeZioConcurrentStructuresAsCatsEffects(): Unit = {
    val queueResult = Resource.make(Queue.bounded[IO, Int](2))(_.shutdown).use { queue =>
      for {
        firstOffered <- queue.offer(1)
        secondOffered <- queue.offerAll(List(2))
        full <- queue.isFull
        sizeBeforeTake <- queue.size
        taken <- queue.takeAll
        emptyAfterTake <- queue.isEmpty
      } yield (firstOffered, secondOffered, full, sizeBeforeTake, taken.toList, emptyAfterTake)
    }.unsafeRunSync()

    assertEquals((true, true, true, 2, List(1, 2), true), queueResult)

    val hubResult = Resource.make(Hub.bounded[IO, String](4))(_.shutdown).use { hub =>
      hub.subscribe.use { subscriber =>
        for {
          firstPublished <- hub.publish("first")
          allPublished <- hub.publishAll(List("second", "third"))
          firstTwo <- subscriber.takeN(2)
          remaining <- subscriber.takeAll
        } yield (firstPublished, allPublished, firstTwo.toList, remaining.toList)
      }
    }.unsafeRunSync()

    assertEquals((true, true, List("first", "second"), List("third")), hubResult)
  }

  @Test
  def fs2AndZioStreamsConvertInBothDirections(): Unit = {
    val zioToFs2 = ZStream.fromIterable(List(1, 2, 3))
      .map(_ * 2)
      .toFs2Stream
      .compile
      .toList
    assertEquals(List(2, 4, 6), runZio(zioToFs2))

    val fs2ToZio = fs2.Stream
      .emits[Task, Int](List(4, 5, 6))
      .evalMap(value => ZIO.succeed(value + 10))
      .toZStream()
      .runCollect
      .map(_.toList)
    assertEquals(List(14, 15, 16), runZio(fs2ToZio))
  }

  private def runZio[A](effect: ZIO[Any, Throwable, A]): A = {
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
  }
}
