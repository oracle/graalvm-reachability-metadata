/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_funsuite_3

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.Future
import scala.jdk.CollectionConverters.*
import scala.util.Try

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.scalatest.Args
import org.scalatest.ConfigMap
import org.scalatest.Filter
import org.scalatest.FutureOutcome
import org.scalatest.Outcome
import org.scalatest.Reporter
import org.scalatest.Suite
import org.scalatest.Tag
import org.scalatest.events.Event
import org.scalatest.events.InfoProvided
import org.scalatest.events.TestCanceled
import org.scalatest.events.TestFailed
import org.scalatest.events.TestIgnored
import org.scalatest.events.TestPending
import org.scalatest.events.TestStarting
import org.scalatest.events.TestSucceeded
import org.scalatest.exceptions.DuplicateTestNameException
import org.scalatest.exceptions.TestRegistrationClosedException
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.funsuite.FixtureAnyFunSuite
import org.scalatest.funsuite.FixtureAsyncFunSuite

class Scalatest_funsuite_3Test:
  @Test
  def anyFunSuiteRegistersAndRunsTestsInDeclarationOrder(): Unit =
    val suite: OrderedFunSuite = new OrderedFunSuite
    val result: RunResult = runSuite(suite)

    assert(result.succeeded)
    assert(suite.testNames.toVector == Vector("first test", "second test"))
    assert(suite.executionLog == Vector("first", "second"))
    assert(startingEvents(result.events).map(_.testName) == Vector("first test", "second test"))
    assert(succeededEvents(result.events).map(_.testName) == Vector("first test", "second test"))

  @Test
  def anyFunSuiteRunsASingleNamedTestWithoutExecutingTheOthers(): Unit =
    val suite: OrderedFunSuite = new OrderedFunSuite
    val result: RunResult = runSuite(suite, testName = Some("second test"))

    assert(result.succeeded)
    assert(suite.executionLog == Vector("second"))
    assert(startingEvents(result.events).map(_.testName) == Vector("second test"))
    assert(succeededEvents(result.events).map(_.testName) == Vector("second test"))

  @Test
  def anyFunSuiteReportsSucceededIgnoredPendingFailedAndInfoEvents(): Unit =
    val suite: OutcomeFunSuite = new OutcomeFunSuite
    val result: RunResult = runSuite(suite)

    assert(!result.succeeded)
    assert(succeededEvents(result.events).map(_.testName) == Vector("succeeds with info"))
    assert(ignoredEvents(result.events).map(_.testName) == Vector("ignored test"))
    assert(pendingEvents(result.events).map(_.testName) == Vector("pending test"))
    assert(failedEvents(result.events).map(_.testName) == Vector("failing test"))
    assert(suite.executionLog == Vector("succeeded", "pending", "failed"))

    val success: TestSucceeded = succeededEvents(result.events).head
    val messages: Vector[String] = success.recordedEvents.collect {
      case event: InfoProvided => event.message
    }.toVector
    assert(messages == Vector("diagnostic details"))

  @Test
  def anyFunSuiteReportsCanceledTestsAndContinuesTheSuite(): Unit =
    val suite: CancelingFunSuite = new CancelingFunSuite
    val result: RunResult = runSuite(suite)
    val canceled: Vector[TestCanceled] = canceledEvents(result.events)

    assert(result.succeeded)
    assert(suite.executionLog == Vector("before", "cancel", "after"))
    assert(succeededEvents(result.events).map(_.testName) == Vector("test before cancellation", "test after cancellation"))
    assert(canceled.map(_.testName) == Vector("canceled test"))
    assert(canceled.head.message.contains("external service unavailable"))
    assert(failedEvents(result.events).isEmpty)

  @Test
  def anyFunSuiteExposesTagsForCountingAndFiltering(): Unit =
    val suite: TaggedFunSuite = new TaggedFunSuite
    val includeFastOnly: Filter = Filter(tagsToInclude = Some(Set(FastTag.name)), tagsToExclude = Set.empty)
    val excludeDatabase: Filter = Filter(tagsToExclude = Set(DatabaseTag.name))

    assert(suite.tags("fast test") == Set(FastTag.name))
    assert(suite.tags("database test") == Set(DatabaseTag.name))
    assert(suite.expectedTestCount(includeFastOnly) == 1)
    assert(suite.expectedTestCount(excludeDatabase) == 1)

    val result: RunResult = runSuite(suite, filter = includeFastOnly)

    assert(result.succeeded)
    assert(suite.executionLog == Vector("fast"))
    assert(succeededEvents(result.events).map(_.testName) == Vector("fast test"))

  @Test
  def anyFunSuiteRegistersSharedBehaviorThroughTestsFor(): Unit =
    val suite: SharedBehaviorFunSuite = new SharedBehaviorFunSuite
    val result: RunResult = runSuite(suite)

    assert(result.succeeded)
    assert(suite.testNames.toVector == Vector(
      "stack created with one element is non-empty",
      "stack created with one element returns the top element"
    ))
    assert(succeededEvents(result.events).map(_.testName) == Vector(
      "stack created with one element is non-empty",
      "stack created with one element returns the top element"
    ))

  @Test
  def fixtureAnyFunSuiteProvidesFixturesNoArgTestsAndConfigMaps(): Unit =
    val suite: FixtureBackedFunSuite = new FixtureBackedFunSuite
    val result: RunResult = runSuite(suite, configMap = ConfigMap("seed" -> "configured value"))

    assert(result.succeeded)
    assert(suite.events == Vector(
      "create:uses fixture argument",
      "test:configured value",
      "cleanup:configured value",
      "test:no-arg"
    ))
    assert(succeededEvents(result.events).map(_.testName) == Vector(
      "uses fixture argument",
      "uses no-arg fixture wrapper"
    ))

  @Test
  def anyFunSuiteAppliesNoArgFixturesAroundEachTest(): Unit =
    val suite: NoArgFixtureFunSuite = new NoArgFixtureFunSuite
    val result: RunResult = runSuite(suite, configMap = ConfigMap("mode" -> "fixture value"))

    assert(result.succeeded)
    assert(suite.events == Vector(
      "before:first wrapped:fixture value",
      "body:first",
      "after:first wrapped",
      "before:second wrapped:fixture value",
      "body:second",
      "after:second wrapped"
    ))
    assert(succeededEvents(result.events).map(_.testName) == Vector("first wrapped", "second wrapped"))

  @Test
  def asyncFunSuiteRunsFutureBackedTestsAndReportsOutcomes(): Unit =
    val suite: FutureFunSuite = new FutureFunSuite
    val result: RunResult = runSuite(suite)

    assert(result.succeeded)
    assert(suite.executionLog == Vector("future-value", "recovered-failure"))
    assert(succeededEvents(result.events).map(_.testName) == Vector(
      "completes a successful future",
      "recovers an expected failed future"
    ))

  @Test
  def fixtureAsyncFunSuiteProvidesAsynchronousFixtures(): Unit =
    val suite: AsyncFixtureBackedFunSuite = new AsyncFixtureBackedFunSuite
    val result: RunResult = runSuite(suite)

    assert(result.succeeded)
    assert(suite.seenFixtures == Vector("async-fixture-used"))
    assert(succeededEvents(result.events).map(_.testName) == Vector("uses an asynchronous fixture"))

  @Test
  def anyFunSuiteRejectsDuplicateTestNames(): Unit =
    assertThrows(classOf[DuplicateTestNameException], () => new DuplicateNameFunSuite)
    ()

  @Test
  def anyFunSuiteClosesRegistrationAfterRunStarts(): Unit =
    val suite: LateRegistrationFunSuite = new LateRegistrationFunSuite
    val result: RunResult = runSuite(suite)

    assert(result.succeeded)
    assertThrows(classOf[TestRegistrationClosedException], () => suite.registerAdditionalTest())
    ()

  private final class OrderedFunSuite extends AnyFunSuite:
    var executionLog: Vector[String] = Vector.empty

    test("first test") {
      executionLog = executionLog :+ "first"
    }

    test("second test") {
      executionLog = executionLog :+ "second"
    }

  private final class OutcomeFunSuite extends AnyFunSuite:
    var executionLog: Vector[String] = Vector.empty

    test("succeeds with info") {
      executionLog = executionLog :+ "succeeded"
      info("diagnostic details")
    }

    ignore("ignored test") {
      executionLog = executionLog :+ "ignored"
    }

    test("pending test") {
      executionLog = executionLog :+ "pending"
      pending
    }

    test("failing test") {
      executionLog = executionLog :+ "failed"
      throw new IllegalStateException("intentional failure")
    }

  private final class CancelingFunSuite extends AnyFunSuite:
    var executionLog: Vector[String] = Vector.empty

    test("test before cancellation") {
      executionLog = executionLog :+ "before"
    }

    test("canceled test") {
      executionLog = executionLog :+ "cancel"
      cancel("external service unavailable")
    }

    test("test after cancellation") {
      executionLog = executionLog :+ "after"
    }

  private final class TaggedFunSuite extends AnyFunSuite:
    var executionLog: Vector[String] = Vector.empty

    test("fast test", FastTag) {
      executionLog = executionLog :+ "fast"
    }

    test("database test", DatabaseTag) {
      executionLog = executionLog :+ "database"
    }

  private final class SharedBehaviorFunSuite extends AnyFunSuite:
    testsFor(nonEmptyStack("stack created with one element", List(1)))

    private def nonEmptyStack(description: String, stack: List[Int]): Unit =
      test(s"$description is non-empty") {
        assert(stack.nonEmpty)
      }

      test(s"$description returns the top element") {
        assert(stack.head == 1)
      }

  private final class FixtureBackedFunSuite extends FixtureAnyFunSuite:
    type FixtureParam = String

    var events: Vector[String] = Vector.empty

    override protected def withFixture(test: OneArgTest): Outcome =
      events = events :+ s"create:${test.name}"
      val fixture: String = test.configMap("seed").asInstanceOf[String]
      try test(fixture)
      finally events = events :+ s"cleanup:$fixture"

    test("uses fixture argument") { (fixture: String) =>
      events = events :+ s"test:$fixture"
      assert(fixture == "configured value")
    }

    test("uses no-arg fixture wrapper") { () =>
      events = events :+ "test:no-arg"
    }

  private final class NoArgFixtureFunSuite extends AnyFunSuite:
    var events: Vector[String] = Vector.empty

    override protected def withFixture(test: NoArgTest): Outcome =
      events = events :+ s"before:${test.name}:${test.configMap("mode")}"
      try test()
      finally events = events :+ s"after:${test.name}"

    test("first wrapped") {
      events = events :+ "body:first"
    }

    test("second wrapped") {
      events = events :+ "body:second"
    }

  private final class FutureFunSuite extends AsyncFunSuite:
    var executionLog: Vector[String] = Vector.empty

    test("completes a successful future") {
      Future.successful {
        executionLog = executionLog :+ "future-value"
        assert(21 * 2 == 42)
        succeed
      }
    }

    test("recovers an expected failed future") {
      executionLog = executionLog :+ "recovered-failure"
      recoverToSucceededIf[IllegalArgumentException] {
        Future.failed(new IllegalArgumentException("invalid async input"))
      }
    }

  private final class AsyncFixtureBackedFunSuite extends FixtureAsyncFunSuite:
    type FixtureParam = StringBuilder

    var seenFixtures: Vector[String] = Vector.empty

    override def withFixture(test: OneArgAsyncTest): FutureOutcome =
      withFixture(test.toNoArgAsyncTest(new StringBuilder("async-fixture")))

    test("uses an asynchronous fixture") { (builder: StringBuilder) =>
      Future.successful {
        builder.append("-used")
        seenFixtures = seenFixtures :+ builder.toString()
        assert(builder.toString() == "async-fixture-used")
        succeed
      }
    }

  private final class DuplicateNameFunSuite extends AnyFunSuite:
    test("duplicate") {}
    test("duplicate") {}

  private final class LateRegistrationFunSuite extends AnyFunSuite:
    test("registered before run") {}

    def registerAdditionalTest(): Unit =
      test("registered after run") {}

  private def runSuite(
      suite: Suite,
      filter: Filter = Filter.default,
      testName: Option[String] = None,
      configMap: ConfigMap = ConfigMap()): RunResult =
    val reporter: RecordingReporter = new RecordingReporter
    val completion: AtomicReference[Try[Boolean]] = new AtomicReference[Try[Boolean]]()
    val completed: CountDownLatch = new CountDownLatch(1)
    val runArgs: Args = Args(reporter = reporter, filter = filter, configMap = configMap)
    val status = suite.run(testName, runArgs)
    status.whenCompleted { (result: Try[Boolean]) =>
      completion.set(result)
      completed.countDown()
    }

    assert(completed.await(30, TimeUnit.SECONDS), s"ScalaTest suite ${suite.suiteName} did not complete")
    RunResult(completion.get().get, reporter.events)

  private def startingEvents(events: Vector[Event]): Vector[TestStarting] =
    events.collect { case event: TestStarting => event }

  private def succeededEvents(events: Vector[Event]): Vector[TestSucceeded] =
    events.collect { case event: TestSucceeded => event }

  private def pendingEvents(events: Vector[Event]): Vector[TestPending] =
    events.collect { case event: TestPending => event }

  private def canceledEvents(events: Vector[Event]): Vector[TestCanceled] =
    events.collect { case event: TestCanceled => event }

  private def ignoredEvents(events: Vector[Event]): Vector[TestIgnored] =
    events.collect { case event: TestIgnored => event }

  private def failedEvents(events: Vector[Event]): Vector[TestFailed] =
    events.collect { case event: TestFailed => event }

  private final class RecordingReporter extends Reporter:
    private val recordedEvents: CopyOnWriteArrayList[Event] = new CopyOnWriteArrayList[Event]()

    override def apply(event: Event): Unit =
      recordedEvents.add(event)
      ()

    def events: Vector[Event] = recordedEvents.asScala.toVector

  private final case class RunResult(succeeded: Boolean, events: Vector[Event])

  private object FastTag extends Tag("org.scalatest.funsuite.generated.Fast")

  private object DatabaseTag extends Tag("org.scalatest.funsuite.generated.Database")
