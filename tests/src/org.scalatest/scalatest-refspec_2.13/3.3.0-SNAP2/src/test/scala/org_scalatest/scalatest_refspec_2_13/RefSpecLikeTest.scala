/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_refspec_2_13

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import scala.jdk.CollectionConverters._
import scala.util.Try

import org.junit.jupiter.api.Test
import org.scalatest.Args
import org.scalatest.Ignore
import org.scalatest.Reporter
import org.scalatest.Suite
import org.scalatest.events.Event
import org.scalatest.events.SuiteAborted
import org.scalatest.events.TestCanceled
import org.scalatest.events.TestFailed
import org.scalatest.events.TestIgnored
import org.scalatest.events.TestSucceeded
import org.scalatest.refspec.RefSpec

class RefSpecLikeTest {
  @Test
  def discoversNestedScopesAndInvokesRegisteredTests(): Unit = {
    val suite: ReflectiveRefSpec = new ReflectiveRefSpec
    val testNames: Vector[String] = suite.testNames.toVector

    val sumTestName: String = testNames.find(_.contains("should produce their sum")).get
    val productTestName: String = testNames.find(_.contains("should produce their product")).get
    val rootTestName: String = testNames.find(_.contains("root level test")).get
    val ignoredTestName: String = testNames.find(_.contains("ignored tests registered")).get

    assert(sumTestName == "A calculator when adding numbers should produce their sum")
    assert(productTestName == "A calculator when multiplying numbers should produce their product")
    assert(rootTestName == "A calculator should expose a root level test")
    assert(suite.tags(ignoredTestName).contains("org.scalatest.Ignore"))

    val result: RunResult = runSuite(suite)

    assert(result.completed.isSuccess)
    assert(failureEvents(result.events).isEmpty)
    assert(suite.executed.toSet == Set("sum", "product", "root"))
    assert(succeededEvents(result.events).map(_.testName).toSet == Set(sumTestName, productTestName, rootTestName))
    assert(ignoredEvents(result.events).map(_.testName) == Vector(ignoredTestName))
  }

  final class ReflectiveRefSpec extends RefSpec {
    var executed: Vector[String] = Vector.empty

    object `A calculator` {
      object `when adding numbers` {
        def `should produce their sum`: Unit = {
          executed = executed :+ "sum"
          assert(2 + 3 == 5)
        }
      }

      object `when multiplying numbers` {
        def `should produce their product`: Unit = {
          executed = executed :+ "product"
          assert(4 * 6 == 24)
        }
      }

      @Ignore
      def `should keep ignored tests registered`: Unit = {
        executed = executed :+ "ignored"
        fail("ignored RefSpec test was executed")
      }
    }

    def `A calculator should expose a root level test`: Unit = {
      executed = executed :+ "root"
      assert("refspec".startsWith("ref"))
    }
  }

  private def runSuite(suite: Suite): RunResult = {
    val reporter: RecordingReporter = new RecordingReporter
    val completion: AtomicReference[Try[Boolean]] = new AtomicReference[Try[Boolean]]()
    val completed: CountDownLatch = new CountDownLatch(1)
    val status = suite.run(None, Args(reporter = reporter))
    status.whenCompleted { result: Try[Boolean] =>
      completion.set(result)
      completed.countDown()
    }

    assert(completed.await(30, TimeUnit.SECONDS), s"ScalaTest suite ${suite.suiteName} did not complete")
    RunResult(completion.get(), reporter.events)
  }

  private def succeededEvents(events: Vector[Event]): Vector[TestSucceeded] =
    events.collect { case event: TestSucceeded => event }

  private def ignoredEvents(events: Vector[Event]): Vector[TestIgnored] =
    events.collect { case event: TestIgnored => event }

  private def failureEvents(events: Vector[Event]): Vector[Event] =
    events.filter {
      case _: SuiteAborted => true
      case _: TestCanceled => true
      case _: TestFailed => true
      case _ => false
    }

  private final class RecordingReporter extends Reporter {
    private val recordedEvents: CopyOnWriteArrayList[Event] = new CopyOnWriteArrayList[Event]()

    override def apply(event: Event): Unit = {
      recordedEvents.add(event)
      ()
    }

    def events: Vector[Event] = recordedEvents.asScala.toVector
  }

  private final case class RunResult(completed: Try[Boolean], events: Vector[Event])
}
