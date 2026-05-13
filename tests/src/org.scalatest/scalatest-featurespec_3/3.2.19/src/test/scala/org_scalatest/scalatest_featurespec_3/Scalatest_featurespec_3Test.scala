/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_featurespec_3

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.Future
import scala.jdk.CollectionConverters.*
import scala.util.Try

import org.junit.jupiter.api.Test
import org.scalatest.Args
import org.scalatest.Filter
import org.scalatest.FutureOutcome
import org.scalatest.GivenWhenThen
import org.scalatest.Outcome
import org.scalatest.Reporter
import org.scalatest.Suite
import org.scalatest.Tag
import org.scalatest.events.AlertProvided
import org.scalatest.events.Event
import org.scalatest.events.InfoProvided
import org.scalatest.events.MarkupProvided
import org.scalatest.events.NoteProvided
import org.scalatest.events.TestCanceled
import org.scalatest.events.TestFailed
import org.scalatest.events.TestIgnored
import org.scalatest.events.TestPending
import org.scalatest.events.TestSucceeded
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.featurespec.AsyncFeatureSpec
import org.scalatest.featurespec.FixtureAnyFeatureSpec
import org.scalatest.featurespec.FixtureAsyncFeatureSpec

class Scalatest_featurespec_3Test:
  @Test
  def registersFeatureScenariosTagsAndIgnoredScenarios(): Unit =
    val suite: ShoppingCartFeatureSpec = new ShoppingCartFeatureSpec
    val addItemTestName: String = findTestName(suite, "adds an item")
    val removeItemTestName: String = findTestName(suite, "removes an item")
    val ignoredTestName: String = findTestName(suite, "checks out without inventory")

    assert(suite.testNames.size == 3)
    assert(suite.tags(addItemTestName).contains(ShoppingCartFeatureSpec.Fast.name))
    assert(suite.tags(ignoredTestName).contains(ShoppingCartFeatureSpec.Slow.name))
    assert(suite.tags(ignoredTestName).contains("org.scalatest.Ignore"))

    val result: RunResult = runSuite(suite)

    assert(result.succeeded)
    assert(suite.executed.toSet == Set("add", "remove"))
    assert(succeededEvents(result.events).map(_.testName).toSet == Set(addItemTestName, removeItemTestName))
    assert(ignoredEvents(result.events).map(_.testName) == Vector(ignoredTestName))

  @Test
  def honorsSelectedScenarioNameWhenRunningFeatureSpec(): Unit =
    val suite: ShoppingCartFeatureSpec = new ShoppingCartFeatureSpec
    val selectedName: String = findTestName(suite, "removes an item")

    val result: RunResult = runSuite(suite, testName = Some(selectedName))

    assert(result.succeeded)
    assert(suite.executed == Vector("remove"))
    assert(succeededEvents(result.events).map(_.testName) == Vector(selectedName))
    assert(ignoredEvents(result.events).isEmpty)

  @Test
  def honorsTagFiltersForFeatureSpecScenarios(): Unit =
    val suite: ShoppingCartFeatureSpec = new ShoppingCartFeatureSpec
    val addItemTestName: String = findTestName(suite, "adds an item")

    val result: RunResult = runSuite(
      suite,
      filter = Filter(tagsToInclude = Some(Set(ShoppingCartFeatureSpec.Fast.name)), tagsToExclude = Set.empty)
    )

    assert(result.succeeded)
    assert(suite.executed == Vector("add"))
    assert(succeededEvents(result.events).map(_.testName) == Vector(addItemTestName))
    assert(ignoredEvents(result.events).isEmpty)

  @Test
  def reportsPendingCanceledAndFailedScenarioLifecycleEvents(): Unit =
    val lifecycleSuite: LifecycleFeatureSpec = new LifecycleFeatureSpec
    val lifecycleResult: RunResult = runSuite(lifecycleSuite)

    assert(lifecycleResult.succeeded)
    assert(lifecycleSuite.executed == Vector("success", "pending", "canceled"))
    assert(succeededEvents(lifecycleResult.events).exists(_.testName.contains("records ordinary success")))
    assert(pendingEvents(lifecycleResult.events).exists(_.testName.contains("documents unfinished behavior")))
    assert(canceledEvents(lifecycleResult.events).exists(_.testName.contains("cancels unavailable behavior")))

    val failingSuite: FailingFeatureSpec = new FailingFeatureSpec
    val failingResult: RunResult = runSuite(failingSuite)
    val failures: Vector[TestFailed] = failedEvents(failingResult.events)

    assert(!failingResult.succeeded)
    assert(failures.size == 1)
    assert(failures.head.testName.contains("publishes assertion failures"))
    assert(failures.head.message.contains("inventory count did not match"))

  @Test
  def publishesGivenWhenThenAndInfoMessagesWithSucceededScenario(): Unit =
    val suite: CheckoutNarrativeFeatureSpec = new CheckoutNarrativeFeatureSpec
    val scenarioTestName: String = findTestName(suite, "describes checkout steps")

    val result: RunResult = runSuite(suite)

    assert(result.succeeded)
    assert(suite.executed == Vector("narrative"))
    assert(succeededEvents(result.events).map(_.testName) == Vector(scenarioTestName))
    assert(recordedInfoMessages(result.events) == Vector(
      "Given a cart containing a guide",
      "When the customer checks out",
      "Then the order summary contains the guide",
      "checkout summary verified"
    ))

  @Test
  def publishesNoteAlertAndMarkupEventsFromSucceededScenario(): Unit =
    val suite: NotificationFeatureSpec = new NotificationFeatureSpec
    val scenarioTestName: String = findTestName(suite, "publishes notifications and markup")

    val result: RunResult = runSuite(suite)

    assert(result.succeeded)
    assert(suite.executed == Vector("notifications"))
    assert(succeededEvents(result.events).map(_.testName) == Vector(scenarioTestName))
    assert(noteMessages(result.events) == Vector("checkout note published"))
    assert(alertMessages(result.events) == Vector("checkout alert published"))
    assert(recordedMarkupText(result.events) == Vector("<p>checkout documentation published</p>"))

  @Test
  def fixtureAnyFeatureSpecProvidesFreshFixturesAndReportsIgnoredScenarios(): Unit =
    val suite: TextFixtureFeatureSpec = new TextFixtureFeatureSpec
    val upperCaseTestName: String = findTestName(suite, "upper-cases fixture text")
    val suffixTestName: String = findTestName(suite, "appends to a fresh fixture")
    val ignoredTestName: String = findTestName(suite, "keeps ignored fixture scenarios registered")

    val result: RunResult = runSuite(suite)

    assert(result.succeeded)
    assert(suite.fixtureNames.asScala.toVector == Vector(upperCaseTestName, suffixTestName))
    assert(suite.finishedFixtures.asScala.toVector == Vector("feature-spec-upper", "feature-spec-suffix"))
    assert(succeededEvents(result.events).map(_.testName) == Vector(upperCaseTestName, suffixTestName))
    assert(ignoredEvents(result.events).map(_.testName) == Vector(ignoredTestName))

  @Test
  def asyncFeatureSpecCompletesFutureScenariosAndExpectedRecoveries(): Unit =
    val suite: AsyncCalculationFeatureSpec = new AsyncCalculationFeatureSpec
    val additionTestName: String = findTestName(suite, "adds numbers asynchronously")
    val recoveryTestName: String = findTestName(suite, "recovers expected failed futures")

    val result: RunResult = runSuite(suite)

    assert(result.succeeded)
    assert(suite.executed == Vector("addition", "recovery"))
    assert(succeededEvents(result.events).map(_.testName) == Vector(additionTestName, recoveryTestName))
    assert(ignoredEvents(result.events).isEmpty)

  @Test
  def fixtureAsyncFeatureSpecProvidesFixturesToFutureScenarios(): Unit =
    val suite: AsyncTextFixtureFeatureSpec = new AsyncTextFixtureFeatureSpec
    val scenarioTestName: String = findTestName(suite, "decorates fixture text asynchronously")

    val result: RunResult = runSuite(suite)

    assert(result.succeeded)
    assert(suite.fixtureNames.asScala.toVector == Vector(scenarioTestName))
    assert(suite.observedFixtures.asScala.toVector == Vector("async-feature-spec"))
    assert(succeededEvents(result.events).map(_.testName) == Vector(scenarioTestName))

  private final class ShoppingCartFeatureSpec extends AnyFeatureSpec:
    var executed: Vector[String] = Vector.empty

    Feature("Shopping cart") {
      Scenario("adds an item", ShoppingCartFeatureSpec.Fast) {
        executed = executed :+ "add"
        assert(Vector("book", "pen").contains("book"))
      }

      Scenario("removes an item", ShoppingCartFeatureSpec.Slow) {
        executed = executed :+ "remove"
        assert(Vector("book", "pen").filterNot(_ == "pen") == Vector("book"))
      }

      ignore("checks out without inventory", ShoppingCartFeatureSpec.Slow) {
        executed = executed :+ "ignored"
        fail("ignored AnyFeatureSpec scenario was executed")
      }
    }

  private object ShoppingCartFeatureSpec:
    val Fast: Tag = Tag("org.scalatest.featurespec.generated.Fast")
    val Slow: Tag = Tag("org.scalatest.featurespec.generated.Slow")

  private final class LifecycleFeatureSpec extends AnyFeatureSpec:
    var executed: Vector[String] = Vector.empty

    Feature("Scenario lifecycle") {
      Scenario("records ordinary success") {
        executed = executed :+ "success"
        assert(List(1, 2, 3).sum == 6)
      }

      Scenario("documents unfinished behavior") {
        executed = executed :+ "pending"
        pending
      }

      Scenario("cancels unavailable behavior") {
        executed = executed :+ "canceled"
        cancel("external dependency intentionally unavailable")
      }
    }

  private final class FailingFeatureSpec extends AnyFeatureSpec:
    Feature("Failing scenario reporting") {
      Scenario("publishes assertion failures") {
        assert(1 == 2, "inventory count did not match")
      }
    }

  private final class CheckoutNarrativeFeatureSpec extends AnyFeatureSpec with GivenWhenThen:
    var executed: Vector[String] = Vector.empty

    Feature("Checkout narrative") {
      Scenario("describes checkout steps") {
        Given("a cart containing a guide")
        When("the customer checks out")
        Then("the order summary contains the guide")
        info("checkout summary verified")
        executed = executed :+ "narrative"
        assert(Vector("guide").mkString(",") == "guide")
      }
    }

  private final class NotificationFeatureSpec extends AnyFeatureSpec:
    var executed: Vector[String] = Vector.empty

    Feature("Scenario notifications and documentation") {
      Scenario("publishes notifications and markup") {
        note("checkout note published")
        alert("checkout alert published")
        markup("<p>checkout documentation published</p>")
        executed = executed :+ "notifications"
        assert("cart".reverse.reverse == "cart")
      }
    }

  private final class TextFixtureFeatureSpec extends FixtureAnyFeatureSpec:
    type FixtureParam = StringBuilder

    val fixtureNames: CopyOnWriteArrayList[String] = new CopyOnWriteArrayList[String]()
    val finishedFixtures: CopyOnWriteArrayList[String] = new CopyOnWriteArrayList[String]()

    override protected def withFixture(test: OneArgTest): Outcome =
      val fixture: StringBuilder = new StringBuilder("feature-spec")
      fixtureNames.add(test.name)
      try withFixture(test.toNoArgTest(fixture))
      finally finishedFixtures.add(fixture.toString())

    Feature("Synchronous fixture scenarios") {
      Scenario("upper-cases fixture text") { (builder: StringBuilder) =>
        builder.append("-upper")
        assert(builder.toString().toUpperCase == "FEATURE-SPEC-UPPER")
      }

      Scenario("appends to a fresh fixture") { (builder: StringBuilder) =>
        builder.append("-suffix")
        assert(builder.toString() == "feature-spec-suffix")
      }

      ignore("keeps ignored fixture scenarios registered") { (builder: StringBuilder) =>
        builder.append("-ignored")
        fail("ignored FixtureAnyFeatureSpec scenario was executed")
      }
    }

  private final class AsyncCalculationFeatureSpec extends AsyncFeatureSpec:
    var executed: Vector[String] = Vector.empty

    Feature("Asynchronous calculations") {
      Scenario("adds numbers asynchronously") {
        Future.successful {
          executed = executed :+ "addition"
          assert(2 + 3 == 5)
        }
      }

      Scenario("recovers expected failed futures") {
        executed = executed :+ "recovery"
        recoverToSucceededIf[IllegalArgumentException] {
          Future.failed(new IllegalArgumentException("invalid async input"))
        }
      }
    }

  private final class AsyncTextFixtureFeatureSpec extends FixtureAsyncFeatureSpec:
    type FixtureParam = StringBuilder

    val fixtureNames: CopyOnWriteArrayList[String] = new CopyOnWriteArrayList[String]()
    val observedFixtures: CopyOnWriteArrayList[String] = new CopyOnWriteArrayList[String]()

    override def withFixture(test: OneArgAsyncTest): FutureOutcome =
      fixtureNames.add(test.name)
      withFixture(test.toNoArgAsyncTest(new StringBuilder("async-feature-spec")))

    Feature("Asynchronous fixture scenarios") {
      Scenario("decorates fixture text asynchronously") { (builder: StringBuilder) =>
        Future.successful {
          observedFixtures.add(builder.toString())
          builder.append("-decorated")
          assert(builder.toString() == "async-feature-spec-decorated")
        }
      }
    }

  private def findTestName(suite: Suite, fragment: String): String =
    suite.testNames.find(_.contains(fragment)).getOrElse {
      throw new AssertionError(s"Could not find test name containing '$fragment'")
    }

  private def runSuite(
      suite: Suite,
      filter: Filter = Filter.default,
      testName: Option[String] = None): RunResult =
    val reporter: RecordingReporter = new RecordingReporter
    val completion: AtomicReference[Try[Boolean]] = new AtomicReference[Try[Boolean]]()
    val completed: CountDownLatch = new CountDownLatch(1)
    val status = suite.run(testName, Args(reporter = reporter, filter = filter))
    status.whenCompleted { (result: Try[Boolean]) =>
      completion.set(result)
      completed.countDown()
    }

    assert(completed.await(30, TimeUnit.SECONDS), s"ScalaTest suite ${suite.suiteName} did not complete")
    RunResult(completion.get().get, reporter.events)

  private def succeededEvents(events: Vector[Event]): Vector[TestSucceeded] =
    events.collect { case event: TestSucceeded => event }

  private def ignoredEvents(events: Vector[Event]): Vector[TestIgnored] =
    events.collect { case event: TestIgnored => event }

  private def pendingEvents(events: Vector[Event]): Vector[TestPending] =
    events.collect { case event: TestPending => event }

  private def canceledEvents(events: Vector[Event]): Vector[TestCanceled] =
    events.collect { case event: TestCanceled => event }

  private def failedEvents(events: Vector[Event]): Vector[TestFailed] =
    events.collect { case event: TestFailed => event }

  private def recordedInfoMessages(events: Vector[Event]): Vector[String] =
    succeededEvents(events).flatMap { (event: TestSucceeded) =>
      event.recordedEvents.collect { case info: InfoProvided => info.message }.toVector
    }

  private def noteMessages(events: Vector[Event]): Vector[String] =
    events.collect { case event: NoteProvided => event.message }

  private def alertMessages(events: Vector[Event]): Vector[String] =
    events.collect { case event: AlertProvided => event.message }

  private def recordedMarkupText(events: Vector[Event]): Vector[String] =
    succeededEvents(events).flatMap { (event: TestSucceeded) =>
      event.recordedEvents.collect { case markup: MarkupProvided => markup.text }.toVector
    }

  private final class RecordingReporter extends Reporter:
    private val recordedEvents: CopyOnWriteArrayList[Event] = new CopyOnWriteArrayList[Event]()

    override def apply(event: Event): Unit =
      recordedEvents.add(event)
      ()

    def events: Vector[Event] = recordedEvents.asScala.toVector

  private final case class RunResult(succeeded: Boolean, events: Vector[Event])
