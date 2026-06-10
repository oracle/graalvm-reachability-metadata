/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_flatspec_2_13

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.util.Try

import org.junit.jupiter.api.Test
import org.scalatest.Args
import org.scalatest.BeforeAndAfter
import org.scalatest.BeforeAndAfterAll
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
import org.scalatest.events.TestSucceeded
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.flatspec.FixtureAnyFlatSpec
import org.scalatest.flatspec.FixtureAsyncFlatSpec

class Scalatest_flatspec_2_13Test {
  @Test
  def registersFlatSpecClausesAndRunsSelectedTestByName(): Unit = {
    val suite: CalculatorFlatSpec = new CalculatorFlatSpec
    val names: Vector[String] = suite.testNames.toVector

    assert(names.exists(_.contains("return the sum")))
    assert(names.exists(_.contains("format display output")))
    assert(names.exists(_.contains("support must clauses")))
    assert(names.exists(_.contains("document future work")))
    assert(names.exists(_.contains("not execute ignored examples")))

    val selectedName: String = names.find(_.contains("return the sum")).get
    val result: RunResult = runSuite(suite, testName = Some(selectedName))

    assert(result.succeeded)
    assert(suite.executed == Vector("sum"))
    assert(succeededEvents(result.events).map(_.testName) == Vector(selectedName))
  }

  @Test
  def reportsSucceededPendingCanceledAndIgnoredTests(): Unit = {
    val suite: LifecycleFlatSpec = new LifecycleFlatSpec
    val result: RunResult = runSuite(suite)

    assert(result.succeeded)
    assert(succeededEvents(result.events).exists(_.testName.contains("record ordinary successes")))
    assert(pendingEvents(result.events).exists(_.testName.contains("document future behaviour")))
    assert(canceledEvents(result.events).exists(_.testName.contains("cancel unavailable work")))
    assert(ignoredEvents(result.events).exists(_.testName.contains("remain registered while ignored")))
    assert(suite.executed == Vector("success", "canceled"))
  }

  @Test
  def reportsFailedTestsThroughEventsAndStatus(): Unit = {
    val suite: FailingFlatSpec = new FailingFlatSpec
    val result: RunResult = runSuite(suite)
    val failures: Vector[TestFailed] = failedEvents(result.events)

    assert(!result.succeeded)
    assert(failures.size == 1)
    assert(failures.head.testName.contains("publish assertion failures"))
    assert(failures.head.message.contains("numbers did not match"))
  }

  @Test
  def recordsInformationalMessagesWithSucceededFlatSpecTests(): Unit = {
    val suite: InformingFlatSpec = new InformingFlatSpec
    val result: RunResult = runSuite(suite)
    val success: TestSucceeded = succeededEvents(result.events).head
    val messages: Vector[String] = success.recordedEvents.collect {
      case event: InfoProvided => event.message
    }.toVector

    assert(result.succeeded)
    assert(success.testName.contains("attach progress messages"))
    assert(messages == Vector("prepared a sample value", "verified transformed value"))
    assert(suite.observed == Vector("message-start", "message-end"))
  }

  @Test
  def exposesTagsAndHonorsTagFilters(): Unit = {
    val suite: TaggedFlatSpec = new TaggedFlatSpec
    val taggedName: String = suite.testNames.find(_.contains("run selected fast examples")).get
    val slowName: String = suite.testNames.find(_.contains("run slow examples")).get
    val ignoredName: String = suite.testNames.find(_.contains("keep ignored tagged examples registered")).get

    assert(suite.tags(taggedName).contains(FastTag.name))
    assert(suite.tags(slowName).contains(SlowTag.name))
    assert(suite.tags(ignoredName).contains(SlowTag.name))
    assert(suite.tags(ignoredName).contains("org.scalatest.Ignore"))

    val result: RunResult = runSuite(
      suite,
      filter = Filter(tagsToInclude = Some(Set(FastTag.name)), tagsToExclude = Set.empty)
    )

    assert(result.succeeded)
    assert(succeededEvents(result.events).map(_.testName) == Vector(taggedName))
    assert(suite.executed == Vector("fast"))
  }

  @Test
  def passesFreshFixturesToFixtureAnyFlatSpecTests(): Unit = {
    val suite: MutableBufferFixtureFlatSpec = new MutableBufferFixtureFlatSpec
    val result: RunResult = runSuite(suite)

    assert(result.succeeded)
    assert(succeededEvents(result.events).size == 2)
    assert(suite.finishedFixtures == Vector("seed-alpha", "seed-beta"))
  }

  @Test
  def registersAndRunsSharedFlatSpecBehaviorsInDifferentContexts(): Unit = {
    val suite: SharedBehaviorFlatSpec = new SharedBehaviorFlatSpec
    val names: Vector[String] = suite.testNames.toVector
    val result: RunResult = runSuite(suite)

    assert(names.count(_.contains("keep the first element available")) == 2)
    assert(names.exists(_.contains("A List-based collection")))
    assert(names.exists(_.contains("A Vector-based collection")))
    assert(result.succeeded)
    assert(succeededEvents(result.events).size == 4)
    assert(suite.observed == Vector("list:first", "list:size", "vector:first", "vector:size"))
  }

  @Test
  def runsAsyncFlatSpecAndFixtureAsyncFlatSpecTests(): Unit = {
    val asyncSuite: FutureFlatSpec = new FutureFlatSpec
    val asyncResult: RunResult = runSuite(asyncSuite)
    val fixtureSuite: AsyncFixtureFlatSpec = new AsyncFixtureFlatSpec
    val fixtureResult: RunResult = runSuite(fixtureSuite)

    assert(asyncResult.succeeded)
    assert(succeededEvents(asyncResult.events).size == 2)
    assert(asyncSuite.executed == Vector("future-value", "recovered-failure"))
    assert(fixtureResult.succeeded)
    assert(succeededEvents(fixtureResult.events).size == 1)
    assert(fixtureSuite.seenFixtures == Vector("async-fixture-used"))
  }

  @Test
  def supportsCapabilityStyleCanClauses(): Unit = {
    val suite: CapabilityFlatSpec = new CapabilityFlatSpec
    val names: Vector[String] = suite.testNames.toVector
    val result: RunResult = runSuite(suite)

    assert(names.exists(_.contains("return a cached value")))
    assert(names.exists(_.contains("evict the oldest value")))
    assert(names.forall(_.contains(" can ")))
    assert(result.succeeded)
    assert(succeededEvents(result.events).size == 2)
    assert(suite.executed == Vector("return", "evict"))
  }

  @Test
  def supportsPluralTheyClauses(): Unit = {
    val suite: PluralSubjectFlatSpec = new PluralSubjectFlatSpec
    val names: Vector[String] = suite.testNames.toVector
    val result: RunResult = runSuite(suite)

    assert(names.exists(_.contains("return their head element")))
    assert(names.exists(_.contains("preserve insertion order")))
    assert(result.succeeded)
    assert(succeededEvents(result.events).size == 2)
    assert(suite.executed == Vector("head", "order"))
  }

  @Test
  def supportsBehaviorOfClausesForDeclaredSubjects(): Unit = {
    val suite: BehaviorOfFlatSpec = new BehaviorOfFlatSpec
    val names: Vector[String] = suite.testNames.toVector
    val result: RunResult = runSuite(suite)

    assert(names.exists(_ == "A configured text normalizer should trim surrounding whitespace"))
    assert(names.exists(_ == "A configured text normalizer must preserve internal spaces"))
    assert(result.succeeded)
    assert(succeededEvents(result.events).size == 2)
    assert(suite.executed == Vector("trim", "preserve"))
  }

  @Test
  def appliesBeforeAndAfterHooksAroundEachFlatSpecTest(): Unit = {
    val suite: HookedFlatSpec = new HookedFlatSpec
    val result: RunResult = runSuite(suite)

    assert(result.succeeded)
    assert(succeededEvents(result.events).size == 2)
    assert(suite.events == Vector("before", "first", "after", "before", "second", "after"))
  }

  @Test
  def appliesBeforeAndAfterAllHooksAroundFlatSpecSuite(): Unit = {
    val suite: SuiteHookedFlatSpec = new SuiteHookedFlatSpec
    val result: RunResult = runSuite(suite)

    assert(result.succeeded)
    assert(succeededEvents(result.events).size == 2)
    assert(suite.events == Vector("beforeAll", "first", "second", "afterAll"))
  }

  private final class CalculatorFlatSpec extends AnyFlatSpec {
    var executed: Vector[String] = Vector.empty

    "A calculator" should "return the sum" in {
      executed = executed :+ "sum"
      assert(2 + 3 == 5)
    }

    it should "format display output" in {
      executed = executed :+ "display"
      assert(f"${2.5}%.1f" == "2.5")
    }

    it must "support must clauses" in {
      executed = executed :+ "must"
      assert("scala".startsWith("sca"))
    }

    it should "document future work" is (pending)

    ignore should "not execute ignored examples" in {
      executed = executed :+ "ignored"
      fail("ignored example was executed")
    }
  }

  private final class LifecycleFlatSpec extends AnyFlatSpec {
    var executed: Vector[String] = Vector.empty

    "A lifecycle flat spec" should "record ordinary successes" in {
      executed = executed :+ "success"
      assert(List(1, 2, 3).sum == 6)
    }

    it should "document future behaviour" is (pending)

    it should "cancel unavailable work" in {
      executed = executed :+ "canceled"
      cancel("external service is intentionally unavailable")
    }

    ignore should "remain registered while ignored" in {
      executed = executed :+ "ignored"
      fail("ignored test body ran")
    }
  }

  private final class FailingFlatSpec extends AnyFlatSpec {
    "A failing flat spec" should "publish assertion failures" in {
      assert(1 == 2, "numbers did not match")
    }
  }

  private final class InformingFlatSpec extends AnyFlatSpec {
    var observed: Vector[String] = Vector.empty

    "An informing flat spec" should "attach progress messages to a successful test" in {
      observed = observed :+ "message-start"
      info("prepared a sample value")
      val transformed: String = "scala".reverse.reverse
      info("verified transformed value")
      observed = observed :+ "message-end"
      assert(transformed == "scala")
    }
  }

  private final class TaggedFlatSpec extends AnyFlatSpec {
    var executed: Vector[String] = Vector.empty

    "A tagged flat spec" should "run selected fast examples" taggedAs FastTag in {
      executed = executed :+ "fast"
      assert(true)
    }

    it should "run slow examples" taggedAs SlowTag in {
      executed = executed :+ "slow"
      assert(true)
    }

    ignore should "keep ignored tagged examples registered" taggedAs SlowTag in {
      executed = executed :+ "ignored"
      fail("ignored tagged test body ran")
    }
  }

  private final class MutableBufferFixtureFlatSpec extends FixtureAnyFlatSpec {
    type FixtureParam = StringBuilder

    var finishedFixtures: Vector[String] = Vector.empty

    override protected def withFixture(test: OneArgTest): Outcome = {
      val fixture: StringBuilder = new StringBuilder("seed")
      try withFixture(test.toNoArgTest(fixture))
      finally finishedFixtures = finishedFixtures :+ fixture.toString()
    }

    "A fixture flat spec" should "receive a mutable builder" in { builder: StringBuilder =>
      builder.append("-alpha")
      assert(builder.toString() == "seed-alpha")
    }

    it should "receive a fresh builder for each test" in { builder: StringBuilder =>
      builder.append("-beta")
      assert(builder.toString() == "seed-beta")
    }
  }

  private final class SharedBehaviorFlatSpec extends AnyFlatSpec {
    var observed: Vector[String] = Vector.empty

    private def behaveLikeNonEmptyCollection(label: String, values: Seq[String]): Unit = {
      it should "keep the first element available" in {
        observed = observed :+ s"$label:first"
        assert(values.headOption.contains("alpha"))
      }

      it should "report the collection size" in {
        observed = observed :+ s"$label:size"
        assert(values.size == 2)
      }
    }

    "A List-based collection" should behave like behaveLikeNonEmptyCollection("list", List("alpha", "beta"))

    "A Vector-based collection" should behave like behaveLikeNonEmptyCollection("vector", Vector("alpha", "beta"))
  }

  private final class FutureFlatSpec extends AsyncFlatSpec {
    var executed: Vector[String] = Vector.empty

    "An async flat spec" should "complete successful futures" in {
      Future.successful {
        executed = executed :+ "future-value"
        assert(21 * 2 == 42)
        succeed
      }
    }

    it should "recover expected failed futures" in {
      executed = executed :+ "recovered-failure"
      recoverToSucceededIf[IllegalArgumentException] {
        Future.failed(new IllegalArgumentException("invalid async input"))
      }
    }
  }

  private final class AsyncFixtureFlatSpec extends FixtureAsyncFlatSpec {
    type FixtureParam = StringBuilder

    var seenFixtures: Vector[String] = Vector.empty

    override def withFixture(test: OneArgAsyncTest): FutureOutcome = {
      withFixture(test.toNoArgAsyncTest(new StringBuilder("async-fixture")))
    }

    "An async fixture flat spec" should "receive an asynchronous fixture" in { builder: StringBuilder =>
      Future.successful {
        builder.append("-used")
        seenFixtures = seenFixtures :+ builder.toString()
        assert(builder.toString() == "async-fixture-used")
        succeed
      }
    }
  }

  private final class CapabilityFlatSpec extends AnyFlatSpec {
    var executed: Vector[String] = Vector.empty

    "A small cache" can "return a cached value" in {
      executed = executed :+ "return"
      val cache: Map[String, Int] = Map("answer" -> 42)
      assert(cache.get("answer").contains(42))
    }

    it can "evict the oldest value" in {
      executed = executed :+ "evict"
      val cacheAfterEviction: Vector[String] = Vector("second", "third")
      assert(!cacheAfterEviction.contains("first"))
    }
  }

  private final class PluralSubjectFlatSpec extends AnyFlatSpec {
    var executed: Vector[String] = Vector.empty

    "Ordered collections" should "return their head element" in {
      executed = executed :+ "head"
      assert(Vector("alpha", "beta").head == "alpha")
    }

    they must "preserve insertion order" in {
      executed = executed :+ "order"
      assert(List(1, 2, 3).mkString(",") == "1,2,3")
    }
  }

  private final class BehaviorOfFlatSpec extends AnyFlatSpec {
    var executed: Vector[String] = Vector.empty

    behavior of "A configured text normalizer"

    it should "trim surrounding whitespace" in {
      executed = executed :+ "trim"
      assert("  scala  ".trim == "scala")
    }

    it must "preserve internal spaces" in {
      executed = executed :+ "preserve"
      assert("native image".replace(" ", " ") == "native image")
    }
  }

  private final class HookedFlatSpec extends AnyFlatSpec with BeforeAndAfter {
    var events: Vector[String] = Vector.empty

    before {
      events = events :+ "before"
    }

    after {
      events = events :+ "after"
    }

    "A flat spec with hooks" should "wrap the first example" in {
      events = events :+ "first"
      assert(events.endsWith(Vector("before", "first")))
    }

    it should "wrap the second example independently" in {
      events = events :+ "second"
      assert(events.endsWith(Vector("before", "second")))
    }
  }

  private final class SuiteHookedFlatSpec extends AnyFlatSpec with BeforeAndAfterAll {
    var events: Vector[String] = Vector.empty

    override protected def beforeAll(): Unit = {
      super.beforeAll()
      events = events :+ "beforeAll"
    }

    override protected def afterAll(): Unit = {
      events = events :+ "afterAll"
      super.afterAll()
    }

    "A flat spec with suite hooks" should "run the first example after setup" in {
      events = events :+ "first"
      assert(events == Vector("beforeAll", "first"))
    }

    it should "run the second example before teardown" in {
      events = events :+ "second"
      assert(events == Vector("beforeAll", "first", "second"))
    }
  }

  private def runSuite(
      suite: Suite,
      filter: Filter = Filter.default,
      testName: Option[String] = None): RunResult = {
    val reporter: RecordingReporter = new RecordingReporter
    val completion: AtomicReference[Try[Boolean]] = new AtomicReference[Try[Boolean]]()
    val completed: CountDownLatch = new CountDownLatch(1)
    val status = suite.run(testName, Args(reporter = reporter, filter = filter))
    status.whenCompleted { result: Try[Boolean] =>
      completion.set(result)
      completed.countDown()
    }

    assert(completed.await(30, TimeUnit.SECONDS), s"ScalaTest suite ${suite.suiteName} did not complete")
    RunResult(completion.get().get, reporter.events)
  }

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

  private final class RecordingReporter extends Reporter {
    private val recordedEvents: CopyOnWriteArrayList[Event] = new CopyOnWriteArrayList[Event]()

    override def apply(event: Event): Unit = {
      recordedEvents.add(event)
      ()
    }

    def events: Vector[Event] = recordedEvents.asScala.toVector
  }

  private final case class RunResult(succeeded: Boolean, events: Vector[Event])

  private object FastTag extends Tag("org.scalatest.flatspec.generated.Fast")

  private object SlowTag extends Tag("org.scalatest.flatspec.generated.Slow")
}
