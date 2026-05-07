/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.keypool_3

import cats.Functor
import cats.effect.{Deferred, IO, Ref, Resource}
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import org.junit.jupiter.api.Assertions.{assertEquals, assertFalse, assertNotEquals, assertTrue}
import org.junit.jupiter.api.Test
import org.typelevel.keypool.{Fairness, KeyPool, Pool, Reusable}

import scala.concurrent.duration.*

class Keypool_3Test {
  @Test
  def unkeyedPoolReusesResourceAndRunsLifecycleHooks(): Unit = run {
    for {
      nextId <- Ref.of[IO, Int](0)
      created <- Ref.of[IO, Vector[Int]](Vector.empty)
      destroyed <- Ref.of[IO, Vector[Int]](Vector.empty)
      onCreate <- Ref.of[IO, Vector[Int]](Vector.empty)
      onDestroy <- Ref.of[IO, Vector[Int]](Vector.empty)
      _ <- Pool
        .Builder[IO, PooledValue](
          Resource.make(newValue(nextId, created))(value => destroyed.update(_ :+ value.id))
        )
        .doOnCreate(value => onCreate.update(_ :+ value.id))
        .doOnDestroy(value => onDestroy.update(_ :+ value.id))
        .withMaxIdle(5)
        .withMaxTotal(2)
        .withDurationBetweenEvictionRuns(Duration.Inf)
        .build
        .use { pool =>
          for {
            firstId <- pool.take.use { managed =>
              IO {
                assertFalse(managed.isReused)
                assertEquals(PooledValue(1), managed.value)
              }.as(managed.value.id)
            }
            afterFirstRelease <- pool.state
            secondId <- pool.take.use { managed =>
              IO {
                assertTrue(managed.isReused)
                assertEquals(PooledValue(firstId), managed.value)
              }.as(managed.value.id)
            }
            afterSecondRelease <- pool.state
            _ <- IO {
              assertEquals(firstId, secondId)
              assertEquals(1, afterFirstRelease.total)
              assertEquals(1, afterSecondRelease.total)
            }
          } yield ()
        }
      createdIds <- created.get
      destroyedIds <- destroyed.get
      createHookIds <- onCreate.get
      destroyHookIds <- onDestroy.get
      _ <- IO {
        assertEquals(Vector(1), createdIds)
        assertEquals(Vector(1), createHookIds)
        assertEquals(Vector(1), destroyHookIds)
        assertEquals(Vector(1), destroyedIds)
      }
    } yield ()
  }

  @Test
  def managedResourceCanOptOutOfReuse(): Unit = run {
    for {
      nextId <- Ref.of[IO, Int](0)
      created <- Ref.of[IO, Vector[Int]](Vector.empty)
      destroyed <- Ref.of[IO, Vector[Int]](Vector.empty)
      _ <- Pool
        .Builder[IO, PooledValue](
          Resource.make(newValue(nextId, created))(value => destroyed.update(_ :+ value.id))
        )
        .withMaxTotal(2)
        .withDurationBetweenEvictionRuns(Duration.Inf)
        .build
        .use { pool =>
          for {
            firstId <- pool.take.use { managed =>
              IO(assertFalse(managed.isReused)) >>
                managed.canBeReused.set(Reusable.DontReuse).as(managed.value.id)
            }
            afterDiscard <- pool.state
            destroyedAfterDiscard <- destroyed.get
            secondId <- pool.take.use { managed =>
              IO {
                assertFalse(managed.isReused)
                assertNotEquals(firstId, managed.value.id)
              }.as(managed.value.id)
            }
            afterSecondRelease <- pool.state
            _ <- IO {
              assertEquals(0, afterDiscard.total)
              assertEquals(Vector(firstId), destroyedAfterDiscard)
              assertEquals(2, secondId)
              assertEquals(1, afterSecondRelease.total)
            }
          } yield ()
        }
      createdIds <- created.get
      destroyedIds <- destroyed.get
      _ <- IO {
        assertEquals(Vector(1, 2), createdIds)
        assertEquals(Vector(1, 2), destroyedIds)
      }
    } yield ()
  }

  @Test
  def defaultReuseStateCanDiscardOrOptIntoReuse(): Unit = run {
    for {
      nextId <- Ref.of[IO, Int](0)
      created <- Ref.of[IO, Vector[Int]](Vector.empty)
      destroyed <- Ref.of[IO, Vector[Int]](Vector.empty)
      _ <- Pool
        .Builder[IO, PooledValue](
          Resource.make(newValue(nextId, created))(value => destroyed.update(_ :+ value.id))
        )
        .withDefaultReuseState(Reusable.DontReuse)
        .withMaxTotal(1)
        .withDurationBetweenEvictionRuns(Duration.Inf)
        .build
        .use { pool =>
          for {
            firstId <- pool.take.use { managed =>
              IO {
                assertFalse(managed.isReused)
                assertEquals(PooledValue(1), managed.value)
              }.as(managed.value.id)
            }
            afterDefaultDiscard <- pool.state
            destroyedAfterDefaultDiscard <- destroyed.get
            secondId <- pool.take.use { managed =>
              IO {
                assertFalse(managed.isReused)
                assertEquals(PooledValue(2), managed.value)
              } >> managed.canBeReused.set(Reusable.Reuse).as(managed.value.id)
            }
            afterExplicitReuse <- pool.state
            thirdId <- pool.take.use { managed =>
              IO {
                assertTrue(managed.isReused)
                assertEquals(PooledValue(secondId), managed.value)
              }.as(managed.value.id)
            }
            afterReusedDefaultDiscard <- pool.state
            _ <- IO {
              assertEquals(1, firstId)
              assertEquals(0, afterDefaultDiscard.total)
              assertEquals(Vector(firstId), destroyedAfterDefaultDiscard)
              assertEquals(2, secondId)
              assertEquals(1, afterExplicitReuse.total)
              assertEquals(secondId, thirdId)
              assertEquals(0, afterReusedDefaultDiscard.total)
            }
          } yield ()
        }
      createdIds <- created.get
      destroyedIds <- destroyed.get
      _ <- IO {
        assertEquals(Vector(1, 2), createdIds)
        assertEquals(Vector(1, 2), destroyedIds)
      }
    } yield ()
  }

  @Test
  def keyedPoolMaintainsIndependentIdleResourcesAndPerKeyLimits(): Unit = run {
    for {
      nextId <- Ref.of[IO, Int](0)
      created <- Ref.of[IO, Vector[KeyedValue]](Vector.empty)
      destroyed <- Ref.of[IO, Vector[KeyedValue]](Vector.empty)
      _ <- KeyPool
        .Builder[IO, String, KeyedValue](key =>
          Resource.make(newKeyedValue(key, nextId, created))(value => destroyed.update(_ :+ value))
        )
        .withMaxPerKey(_ => 1)
        .withMaxIdle(5)
        .withMaxTotal(2)
        .withDurationBetweenEvictionRuns(Duration.Inf)
        .build
        .use { pool =>
          for {
            firstPair <- pool.take("tenant-a").use { first =>
              pool.take("tenant-a").use { second =>
                IO {
                  assertFalse(first.isReused)
                  assertFalse(second.isReused)
                  assertNotEquals(first.value.id, second.value.id)
                }.as((first.value, second.value))
              }
            }
            afterTwoTenantAReleases <- pool.state
            destroyedAfterLimit <- destroyed.get
            reusedTenantA <- pool.take("tenant-a").use { managed =>
              IO(assertTrue(managed.isReused)).as(managed.value)
            }
            _ <- pool.take("tenant-b").use { managed =>
              IO {
                assertFalse(managed.isReused)
                assertEquals("tenant-b", managed.value.key)
              }
            }
            finalState <- pool.state
            _ <- IO {
              assertEquals(1, afterTwoTenantAReleases._1)
              assertEquals(Map("tenant-a" -> 1), afterTwoTenantAReleases._2)
              assertEquals(Vector(firstPair._1), destroyedAfterLimit)
              assertEquals(firstPair._2, reusedTenantA)
              assertEquals(2, finalState._1)
              assertEquals(Map("tenant-a" -> 1, "tenant-b" -> 1), finalState._2)
            }
          } yield ()
        }
      createdValues <- created.get
      destroyedValues <- destroyed.get
      _ <- IO {
        assertEquals(Vector("tenant-a", "tenant-a", "tenant-b"), createdValues.map(_.key))
        assertEquals(createdValues.toSet, destroyedValues.toSet)
      }
    } yield ()
  }

  @Test
  def functorAndInvariantInstancesTransformValuesAndKeys(): Unit = run {
    KeyPool
      .Builder[IO, Int, String](key => Resource.pure[IO, String](s"key:$key"))
      .withMaxTotal(3)
      .withDurationBetweenEvictionRuns(Duration.Inf)
      .build
      .use { basePool =>
        val valueMapped: KeyPool[IO, Int, Int] =
          Functor[[Value] =>> KeyPool[IO, Int, Value]].map(basePool)(_.length)
        val keyMapped: KeyPool[IO, String, String] =
          KeyPool.keypoolInvariant[IO, String].imap[Int, String](basePool)(_.toString)(_.toInt)

        for {
          mappedLength <- valueMapped.take(12).use { managed =>
            IO {
              assertFalse(managed.isReused)
              assertEquals("key:12".length, managed.value)
            }.as(managed.value)
          }
          remappedValue <- keyMapped.take("7").use { managed =>
            IO {
              assertFalse(managed.isReused)
              assertEquals("key:7", managed.value)
            }.as(managed.value)
          }
          stateFromMappedKeys <- keyMapped.state
          _ <- IO {
            assertEquals("key:12".length, mappedLength)
            assertEquals("key:7", remappedValue)
            assertEquals(2, stateFromMappedKeys._1)
            assertEquals(Map("12" -> 1, "7" -> 1), stateFromMappedKeys._2)
          }
        } yield ()
      }
  }

  @Test
  def maxTotalBoundsConcurrentAcquisitionAndCancellationReleasesWaiters(): Unit = run {
    for {
      enteredFirstTake <- Deferred[IO, Unit]
      releaseFirstTake <- Deferred[IO, Unit]
      nextId <- Ref.of[IO, Int](0)
      created <- Ref.of[IO, Vector[Int]](Vector.empty)
      _ <- Pool
        .Builder[IO, PooledValue](
          Resource.make(newValue(nextId, created))(_ => IO.unit)
        )
        .withMaxTotal(1)
        .withFairness(Fairness.Fifo)
        .withDurationBetweenEvictionRuns(Duration.Inf)
        .build
        .use { pool =>
          for {
            holder <- pool.take.use(_ => enteredFirstTake.complete(()) >> releaseFirstTake.get).start
            _ <- enteredFirstTake.get
            blockedAttempt <- pool.take.use_.timeout(100.millis).attempt
            _ <- releaseFirstTake.complete(())
            _ <- holder.join.void
            successfulAttempt <- pool.take.use_.timeout(1.second).attempt
            _ <- IO {
              assertTrue(blockedAttempt.isLeft)
              assertTrue(successfulAttempt.isRight)
            }
          } yield ()
        }
    } yield ()
  }

  @Test
  def lifoFairnessServesMostRecentWaiterFirst(): Unit = run {
    for {
      holderEntered <- Deferred[IO, Unit]
      releaseHolder <- Deferred[IO, Unit]
      firstAcquired <- Deferred[IO, Unit]
      secondAcquired <- Deferred[IO, Unit]
      acquisitionOrder <- Ref.of[IO, Vector[String]](Vector.empty)
      _ <- Pool
        .Builder[IO, PooledValue](Resource.pure(PooledValue(1)))
        .withMaxTotal(1)
        .withFairness(Fairness.Lifo)
        .withDurationBetweenEvictionRuns(Duration.Inf)
        .build
        .use { pool =>
          def waiter(name: String, acquired: Deferred[IO, Unit]): IO[Unit] =
            pool.take.use(_ => acquisitionOrder.update(_ :+ name) >> acquired.complete(()).void)

          for {
            holder <- pool.take.use(_ => holderEntered.complete(()) >> releaseHolder.get).start
            _ <- holderEntered.get
            first <- waiter("first", firstAcquired).start
            firstWhileHeld <- firstAcquired.get.timeout(100.millis).attempt
            second <- waiter("second", secondAcquired).start
            secondWhileHeld <- secondAcquired.get.timeout(100.millis).attempt
            _ <- IO {
              assertTrue(firstWhileHeld.isLeft)
              assertTrue(secondWhileHeld.isLeft)
            }
            _ <- releaseHolder.complete(())
            _ <- first.join.timeout(1.second)
            _ <- second.join.timeout(1.second)
            _ <- holder.join.timeout(1.second)
            observedOrder <- acquisitionOrder.get
            _ <- IO(assertEquals(Vector("second", "first"), observedOrder))
          } yield ()
        }
    } yield ()
  }

  @Test
  def reaperEvictsIdleResourcesAndReportsDestroyFailures(): Unit = run {
    for {
      reported <- Ref.of[IO, Vector[String]](Vector.empty)
      _ <- KeyPool
        .Builder[IO, String, PooledValue](key =>
          Resource.make(IO.pure(PooledValue(key.length)))(_ => IO.raiseError(new IllegalStateException(s"destroy failed for $key")))
        )
        .withIdleTimeAllowedInPool(Duration.Zero)
        .withDurationBetweenEvictionRuns(20.millis)
        .withMaxPerKey(_ => 1)
        .withMaxTotal(1)
        .withOnReaperException(error => reported.update(_ :+ error.getMessage))
        .build
        .use { pool =>
          for {
            _ <- pool.take("stale").use { managed =>
              IO {
                assertEquals(PooledValue(5), managed.value)
                assertFalse(managed.isReused)
              }
            }
            _ <- eventually("idle resource to be evicted")(pool.state.map(_._1 == 0))
            _ <- eventually("reaper exception to be reported")(
              reported.get.map(_.contains("destroy failed for stale"))
            )
          } yield ()
        }
    } yield ()
  }

  private def run[A](program: IO[A]): A =
    program.timeout(PerTestTimeout).unsafeRunSync()

  private def newValue(nextId: Ref[IO, Int], created: Ref[IO, Vector[Int]]): IO[PooledValue] =
    nextId.modify(current => (current + 1, current + 1)).flatTap(id => created.update(_ :+ id)).map(PooledValue.apply)

  private def newKeyedValue(
      key: String,
      nextId: Ref[IO, Int],
      created: Ref[IO, Vector[KeyedValue]]
  ): IO[KeyedValue] =
    nextId
      .modify(current => (current + 1, current + 1))
      .map(id => KeyedValue(key, id))
      .flatTap(value => created.update(_ :+ value))

  private def eventually(description: String)(condition: IO[Boolean]): IO[Unit] = {
    def loop(remainingAttempts: Int): IO[Unit] =
      condition.flatMap {
        case true => IO.unit
        case false if remainingAttempts <= 0 =>
          IO.raiseError(new AssertionError(s"Timed out waiting for $description"))
        case false => IO.sleep(25.millis) >> loop(remainingAttempts - 1)
      }

    loop(120)
  }

  private val PerTestTimeout: FiniteDuration = 10.seconds
}

final case class PooledValue(id: Int)

final case class KeyedValue(key: String, id: Int)
