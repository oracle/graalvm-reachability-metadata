/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_refspec_3

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import scala.jdk.CollectionConverters.*
import scala.util.Try

import org.junit.jupiter.api.Test
import org.scalatest.Args
import org.scalatest.Reporter
import org.scalatest.Status
import org.scalatest.events.Event
import org.scalatest.events.TestSucceeded
import org.scalatest.refspec.RefSpec

class RefSpecLikeTest:
  @Test
  def discoversAndRunsTopLevelAndNestedTests(): Unit =
    val suite: MessageSpec = new MessageSpec
    val expectedNames: Set[String] = Set(
      "adds two numbers",
      "A message buffer when populated joins its entries"
    )

    assert(suite.testNames == expectedNames)
    assert(suite.testNames == expectedNames)
    assert(suite.executed.isEmpty)

    val events: Vector[Event] = runSuite(suite, None)
    val succeededNames: Vector[String] = events.collect {
      case event: TestSucceeded => event.testName
    }

    assert(succeededNames.toSet == expectedNames)
    assert(succeededNames.size == 2)
    assert(suite.executed == Vector("joined", "added"))

  @Test
  def runsOnlyTheSelectedNestedTest(): Unit =
    val suite: MessageSpec = new MessageSpec
    val selectedName: String = "A message buffer when populated joins its entries"
    val events: Vector[Event] = runSuite(suite, Some(selectedName))
    val succeededNames: Vector[String] = events.collect {
      case event: TestSucceeded => event.testName
    }

    assert(succeededNames == Vector(selectedName))
    assert(suite.executed == Vector("joined"))

  final class MessageSpec extends RefSpec:
    var executed: Vector[String] = Vector.empty

    def `adds two numbers`: Unit =
      assert(List(7, 8).sum == 15)
      executed = executed :+ "added"

    object `A message buffer`:
      object `when populated`:
        def `joins its entries`: Unit =
          val entries: Vector[String] = Vector("hello", "world")
          assert(entries.mkString(" ") == "hello world")
          executed = executed :+ "joined"

  private def runSuite(suite: RefSpec, testName: Option[String]): Vector[Event] =
    val events: CopyOnWriteArrayList[Event] = new CopyOnWriteArrayList[Event]()
    val reporter: Reporter = new Reporter:
      override def apply(event: Event): Unit =
        events.add(event)
        ()

    val completed: CountDownLatch = new CountDownLatch(1)
    val outcome: AtomicReference[Try[Boolean]] = new AtomicReference[Try[Boolean]]()
    val status: Status = suite.run(testName, Args(reporter = reporter))
    status.whenCompleted { (result: Try[Boolean]) =>
      outcome.set(result)
      completed.countDown()
    }

    assert(completed.await(30, TimeUnit.SECONDS), "RefSpec execution did not complete")
    assert(outcome.get().get, "RefSpec reported an unsuccessful run")
    events.asScala.toVector
