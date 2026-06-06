/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_featurespec_2_13

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.util.Try

import org.junit.jupiter.api.Test
import org.scalatest.Args
import org.scalatest.FutureOutcome
import org.scalatest.GivenWhenThen
import org.scalatest.Outcome
import org.scalatest.Reporter
import org.scalatest.Suite
import org.scalatest.Succeeded
import org.scalatest.Tag
import org.scalatest.events.Event
import org.scalatest.events.InfoProvided
import org.scalatest.events.SuiteAborted
import org.scalatest.events.TestCanceled
import org.scalatest.events.TestFailed
import org.scalatest.events.TestIgnored
import org.scalatest.events.TestPending
import org.scalatest.events.TestSucceeded
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.featurespec.AsyncFeatureSpec
import org.scalatest.featurespec.FixtureAnyFeatureSpec
import org.scalatest.featurespec.FixtureAsyncFeatureSpec

class Scalatest_featurespec_2_13Test {
  @Test
  def anyFeatureSpecRegistersTaggedAndIgnoredScenarios(): Unit = {
    val suite: ShoppingCartFeatureSpec = new ShoppingCartFeatureSpec
    val addItemTestName: String = findTestName(suite, "adds an item")
    val removeItemTestName: String = findTestName(suite, "removes an item")
    val ignoredTestName: String = findTestName(suite, "checks out without inventory")

    assert(suite.testNames.size == 3)
    assert(suite.tags(addItemTestName).contains(ShoppingCartFeatureSpec.Fast.name))
    assert(suite.tags(ignoredTestName).contains(ShoppingCartFeatureSpec.Slow.name))

    val result: RunResult = runSuite(suite)

    assert(failureEvents(result.events).isEmpty)
    assert(suite.executed.toSet == Set("add", "remove"))
    assert(succeededEvents(result.events).map(_.testName).toSet == Set(addItemTestName, removeItemTestName))
    assert(ignoredEvents(result.events).map(_.testName) == Vector(ignoredTestName))
  }

  @Test
  def asyncFeatureSpecCompletesSuccessfulFutureScenarios(): Unit = {
    val suite: AsyncCalculationFeatureSpec = new AsyncCalculationFeatureSpec
    val additionTestName: String = findTestName(suite, "adds numbers asynchronously")
    val multiplicationTestName: String = findTestName(suite, "multiplies numbers asynchronously")

    val result: RunResult = runSuite(suite)

    assert(failureEvents(result.events).isEmpty)
    assert(suite.executed.toSet == Set("addition", "multiplication"))
    assert(succeededEvents(result.events).map(_.testName).toSet == Set(additionTestName, multiplicationTestName))
    assert(ignoredEvents(result.events).isEmpty)
  }

  @Test
  def anyFeatureSpecReportsPendingScenariosWithoutFailingTheSuite(): Unit = {
    val suite: PendingScenarioFeatureSpec = new PendingScenarioFeatureSpec
    val pendingTestName: String = findTestName(suite, "captures an unfinished payment workflow")

    val result: RunResult = runSuite(suite)

    assert(failureEvents(result.events).isEmpty)
    assert(suite.executed == Vector("pending"))
    assert(pendingEvents(result.events).map(_.testName) == Vector(pendingTestName))
    assert(succeededEvents(result.events).isEmpty)
    assert(ignoredEvents(result.events).isEmpty)
  }

  @Test
  def fixtureAnyFeatureSpecProvidesFixturesAndReportsIgnoredScenarios(): Unit = {
    val suite: TextFixtureFeatureSpec = new TextFixtureFeatureSpec
    val upperCaseTestName: String = findTestName(suite, "upper-cases fixture text")
    val ignoredTestName: String = findTestName(suite, "keeps ignored fixture scenarios registered")

    val result: RunResult = runSuite(suite)

    assert(failureEvents(result.events).isEmpty)
    assert(suite.fixtureNames.asScala.toVector == Vector(upperCaseTestName))
    assert(suite.observedFixtures.asScala.toVector == Vector("feature-spec"))
    assert(succeededEvents(result.events).map(_.testName) == Vector(upperCaseTestName))
    assert(ignoredEvents(result.events).map(_.testName) == Vector(ignoredTestName))
  }

  @Test
  def fixtureAsyncFeatureSpecProvidesFixturesToFutureScenarios(): Unit = {
    val suite: AsyncTextFixtureFeatureSpec = new AsyncTextFixtureFeatureSpec
    val scenarioTestName: String = findTestName(suite, "decorates fixture text asynchronously")

    val result: RunResult = runSuite(suite)

    assert(failureEvents(result.events).isEmpty)
    assert(suite.fixtureNames.asScala.toVector == Vector(scenarioTestName))
    assert(suite.observedFixtures.asScala.toVector == Vector("async-feature-spec"))
    assert(succeededEvents(result.events).map(_.testName) == Vector(scenarioTestName))
  }

  @Test
  def anyFeatureSpecPublishesGivenWhenThenMessages(): Unit = {
    val suite: CheckoutNarrativeFeatureSpec = new CheckoutNarrativeFeatureSpec
    val scenarioTestName: String = findTestName(suite, "describes checkout steps")

    val result: RunResult = runSuite(suite)

    assert(failureEvents(result.events).isEmpty)
    assert(suite.executed == Vector("narrative"))
    assert(succeededEvents(result.events).map(_.testName) == Vector(scenarioTestName))
    assert(recordedInfoMessages(result.events) == Vector(
      "Given a cart containing a guide",
      "When the customer checks out",
      "Then the order summary contains the guide"
    ))
  }

  final class ShoppingCartFeatureSpec extends AnyFeatureSpec {
    var executed: Vector[String] = Vector.empty

    Feature("Shopping cart") {
      Scenario("adds an item", ShoppingCartFeatureSpec.Fast) {
        executed = executed :+ "add"
        scala.Predef.assert(Vector("book", "pen").contains("book"))
      }

      Scenario("removes an item") {
        executed = executed :+ "remove"
        scala.Predef.assert(Vector("book", "pen").filterNot(_ == "pen") == Vector("book"))
      }

      ignore("checks out without inventory", ShoppingCartFeatureSpec.Slow) {
        executed = executed :+ "ignored"
        fail("ignored AnyFeatureSpec scenario was executed")
      }
    }
  }

  object ShoppingCartFeatureSpec {
    val Fast: Tag = Tag("org.scalatest.featurespec.fast")
    val Slow: Tag = Tag("org.scalatest.featurespec.slow")
  }

  final class AsyncCalculationFeatureSpec extends AsyncFeatureSpec {
    var executed: Vector[String] = Vector.empty

    Feature("Asynchronous calculations") {
      Scenario("adds numbers asynchronously") {
        Future.successful {
          executed = executed :+ "addition"
          scala.Predef.assert(2 + 3 == 5)
          Succeeded
        }
      }

      Scenario("multiplies numbers asynchronously") {
        Future.successful {
          executed = executed :+ "multiplication"
          scala.Predef.assert(4 * 6 == 24)
          Succeeded
        }
      }
    }
  }

  final class PendingScenarioFeatureSpec extends AnyFeatureSpec {
    var executed: Vector[String] = Vector.empty

    Feature("Pending payment workflow") {
      Scenario("captures an unfinished payment workflow") {
        executed = executed :+ "pending"
        pending
      }
    }
  }

  final class TextFixtureFeatureSpec extends FixtureAnyFeatureSpec {
    type FixtureParam = String

    val fixtureNames: CopyOnWriteArrayList[String] = new CopyOnWriteArrayList[String]()
    val observedFixtures: CopyOnWriteArrayList[String] = new CopyOnWriteArrayList[String]()

    override protected def withFixture(test: OneArgTest): Outcome = {
      fixtureNames.add(test.name)
      withFixture(test.toNoArgTest("feature-spec"))
    }

    Feature("Synchronous fixture scenarios") {
      Scenario("upper-cases fixture text") { text: String =>
        observedFixtures.add(text)
        scala.Predef.assert(text.toUpperCase == "FEATURE-SPEC")
      }

      ignore("keeps ignored fixture scenarios registered") { text: String =>
        observedFixtures.add(text)
        fail("ignored FixtureAnyFeatureSpec scenario was executed")
      }
    }
  }

  final class AsyncTextFixtureFeatureSpec extends FixtureAsyncFeatureSpec {
    type FixtureParam = String

    val fixtureNames: CopyOnWriteArrayList[String] = new CopyOnWriteArrayList[String]()
    val observedFixtures: CopyOnWriteArrayList[String] = new CopyOnWriteArrayList[String]()

    override def withFixture(test: OneArgAsyncTest): FutureOutcome = {
      fixtureNames.add(test.name)
      withFixture(test.toNoArgAsyncTest("async-feature-spec"))
    }

    Feature("Asynchronous fixture scenarios") {
      Scenario("decorates fixture text asynchronously") { text: String =>
        Future.successful {
          observedFixtures.add(text)
          scala.Predef.assert(s"[$text]" == "[async-feature-spec]")
          Succeeded
        }
      }
    }
  }

  final class CheckoutNarrativeFeatureSpec extends AnyFeatureSpec with GivenWhenThen {
    var executed: Vector[String] = Vector.empty

    Feature("Checkout narrative") {
      Scenario("describes checkout steps") {
        Given("a cart containing a guide")
        When("the customer checks out")
        Then("the order summary contains the guide")
        executed = executed :+ "narrative"
        scala.Predef.assert(Vector("guide").mkString(",") == "guide")
      }
    }
  }

  private def findTestName(suite: Suite, fragment: String): String = {
    suite.testNames.find(_.contains(fragment)).getOrElse {
      throw new AssertionError(s"Could not find test name containing '$fragment'")
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
    completion.get().get
    RunResult(reporter.events)
  }

  private def failureEvents(events: Vector[Event]): Vector[Event] =
    events.collect {
      case event: TestFailed => event
      case event: TestCanceled => event
      case event: SuiteAborted => event
    }

  private def succeededEvents(events: Vector[Event]): Vector[TestSucceeded] =
    events.collect { case event: TestSucceeded => event }

  private def ignoredEvents(events: Vector[Event]): Vector[TestIgnored] =
    events.collect { case event: TestIgnored => event }

  private def pendingEvents(events: Vector[Event]): Vector[TestPending] =
    events.collect { case event: TestPending => event }

  private def recordedInfoMessages(events: Vector[Event]): Vector[String] =
    succeededEvents(events).flatMap { event: TestSucceeded =>
      event.recordedEvents.collect { case info: InfoProvided => info.message }.toVector
    }

  private final class RecordingReporter extends Reporter {
    private val recordedEvents: CopyOnWriteArrayList[Event] = new CopyOnWriteArrayList[Event]()

    override def apply(event: Event): Unit = {
      recordedEvents.add(event)
      ()
    }

    def events: Vector[Event] = recordedEvents.asScala.toVector
  }

  private final case class RunResult(events: Vector[Event])
}
