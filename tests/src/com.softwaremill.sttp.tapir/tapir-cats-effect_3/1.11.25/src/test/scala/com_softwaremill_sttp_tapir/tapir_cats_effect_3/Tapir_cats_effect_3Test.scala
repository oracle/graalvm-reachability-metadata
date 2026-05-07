/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_softwaremill_sttp_tapir.tapir_cats_effect_3

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sttp.tapir.integ.cats.effect.CatsMonadError

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters._
import scala.util.Failure
import scala.util.Success

class Tapir_cats_effect_3Test {
  private val monad: CatsMonadError[IO] = new CatsMonadError[IO]

  @Test
  def unitMapFlatMapFlattenAndFlatTapComposeIoPrograms(): Unit = {
    val sideEffects: AtomicInteger = new AtomicInteger(0)
    val nested: IO[IO[String]] = monad.unit(monad.unit("nested"))

    val program: IO[String] = monad.flatMap(monad.map(monad.unit(21))(_ + 1)) { (value: Int) =>
      monad.flatMap(monad.flatten(nested)) { (prefix: String) =>
        monad.flatMap(monad.flatTap(monad.unit(value * 2)) { (computed: Int) =>
          monad.eval(sideEffects.addAndGet(computed))
        }) { (computed: Int) =>
          monad.unit(s"$prefix:$computed")
        }
      }
    }

    assertEquals("nested:44", await(program))
    assertEquals(44, sideEffects.get())
  }

  @Test
  def fromTryErrorAndHandleErrorPreserveThrowableSemantics(): Unit = {
    val boom: IllegalStateException = new IllegalStateException("boom")
    val recoveredFromFailure: IO[String] = monad.handleError(monad.fromTry(Failure[String](boom))) {
      case e: IllegalStateException if e.getMessage == "boom" => monad.unit("recovered-from-try")
    }
    val recoveredFromRaisedError: IO[String] = monad.handleError(monad.error[String](boom)) {
      case e: IllegalStateException if e eq boom => monad.unit("recovered-from-error")
    }

    assertEquals(7, await(monad.fromTry(Success(7))))
    assertEquals("recovered-from-try", await(recoveredFromFailure))
    assertEquals("recovered-from-error", await(recoveredFromRaisedError))

    val thrown: IllegalStateException = assertThrows(classOf[IllegalStateException], () => await(monad.error[String](boom)))
    assertSame(boom, thrown)
  }

  @Test
  def handleErrorCatchesExceptionsThrownWhileConstructingTheEffect(): Unit = {
    val boom: IllegalArgumentException = new IllegalArgumentException("constructed too early")

    val recovered: IO[String] = monad.handleError[String]({
      throw boom
    }) {
      case e: IllegalArgumentException if e eq boom => monad.unit("recovered")
    }

    assertEquals("recovered", await(recovered))
  }

  @Test
  def evalAndSuspendAreLazyAndRunInsideIo(): Unit = {
    val evalWasRun: AtomicBoolean = new AtomicBoolean(false)
    val suspendWasConstructed: AtomicBoolean = new AtomicBoolean(false)

    val delayed: IO[String] = monad.eval {
      evalWasRun.set(true)
      "evaluated"
    }
    val suspended: IO[String] = monad.suspend {
      suspendWasConstructed.set(true)
      monad.unit("suspended")
    }

    assertFalse(evalWasRun.get())
    assertFalse(suspendWasConstructed.get())
    assertEquals("evaluated", await(delayed))
    assertEquals("suspended", await(suspended))
    assertTrue(evalWasRun.get())
    assertTrue(suspendWasConstructed.get())
  }

  @Test
  def suspendTurnsEffectConstructionFailuresIntoRecoverableIoFailures(): Unit = {
    val boom: RuntimeException = new RuntimeException("deferred failure")
    val program: IO[String] = monad.handleError(monad.suspend[String] {
      throw boom
    }) {
      case e: RuntimeException if e eq boom => monad.unit("recovered")
    }

    assertEquals("recovered", await(program))
  }

  @Test
  def ensure2RunsFinalizerAfterSuccessfulProgram(): Unit = {
    val events: CopyOnWriteArrayList[String] = new CopyOnWriteArrayList[String]()
    val program: IO[String] = monad.ensure2(
      monad.eval {
        events.add("body")
        "ok"
      },
      append(events, "finalizer")
    )

    assertTrue(events.isEmpty)
    assertEquals("ok", await(program))
    assertEquals(List("body", "finalizer"), events.asScala.toList)
  }

  @Test
  def ensure2RunsFinalizerAfterFailedProgram(): Unit = {
    val events: CopyOnWriteArrayList[String] = new CopyOnWriteArrayList[String]()
    val boom: IllegalStateException = new IllegalStateException("failed body")
    val failed: IO[String] = monad.ensure2(monad.error[String](boom), append(events, "finalizer"))
    val recovered: IO[String] = monad.handleError(failed) {
      case e: IllegalStateException if e eq boom => monad.unit("recovered")
    }

    assertEquals("recovered", await(recovered))
    assertEquals(List("finalizer"), events.asScala.toList)
  }

  @Test
  def ensure2RunsFinalizerWhenEffectConstructionThrows(): Unit = {
    val events: CopyOnWriteArrayList[String] = new CopyOnWriteArrayList[String]()
    val boom: IllegalArgumentException = new IllegalArgumentException("construction failure")
    val failed: IO[String] = monad.ensure2[String]({
      events.add("construct")
      throw boom
    }, append(events, "finalizer"))
    val recovered: IO[String] = monad.handleError(failed) {
      case e: IllegalArgumentException if e eq boom => monad.unit("recovered")
    }

    assertTrue(events.isEmpty)
    assertEquals("recovered", await(recovered))
    assertEquals(List("construct", "finalizer"), events.asScala.toList)
  }

  @Test
  def blockingRunsSynchronousComputationInIo(): Unit = {
    val program: IO[String] = monad.map(monad.blocking {
      val result: Int = 40 + 2
      s"blocking:$result"
    })(identity)

    assertEquals("blocking:42", await(program))
  }

  private def append(events: CopyOnWriteArrayList[String], event: String): IO[Unit] =
    monad.eval {
      events.add(event)
      ()
    }

  private def await[A](io: IO[A]): A =
    io.timeout(5.seconds).unsafeRunSync()
}
