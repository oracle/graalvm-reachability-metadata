/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_wordspec_2_13

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.util.Try

import org.junit.jupiter.api.Test
import org.scalatest.Args
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
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.wordspec.FixtureAnyWordSpec
import org.scalatest.wordspec.FixtureAsyncWordSpec

class Scalatest_wordspec_2_13Test {
  @Test
  def registersNestedWordSpecClausesAndRunsSelectedTestByName(): Unit = {
    val suite: CalculatorWordSpec = new CalculatorWordSpec
    val names: Vector[String] = suite.testNames.toVector

    assert(names.exists(_.contains("return the sum")))
    assert(names.exists(_.contains("formatted total")))
    assert(names.exists(_.contains("support shorthand subjects")))
    assert(names.exists(_.contains("report pending work")))
    assert(names.exists(_.contains("not execute ignored examples")))

    val selectedName: String = names.find(_.contains("return the sum")).get
    val result: RunResult = runSuite(suite, testName = Some(selectedName))

    assert(result.succeeded)
    assert(suite.executed == Vector("sum"))
    assert(succeededEvents(result.events).map(_.testName) == Vector(selectedName))
  }

  @Test
  def reportsSucceededPendingCanceledAndIgnoredTests(): Unit = {
    val suite: LifecycleWordSpec = new LifecycleWordSpec
    val result: RunResult = runSuite(suite)

    assert(result.succeeded)
    assert(succeededEvents(result.events).exists(_.testName.contains("ordinary successes")))
    assert(pendingEvents(result.events).exists(_.testName.contains("document future behaviour")))
    assert(canceledEvents(result.events).exists(_.testName.contains("cancel unavailable work")))
    assert(ignoredEvents(result.events).exists(_.testName.contains("remain registered while ignored")))
    assert(suite.executed == Vector("success", "canceled"))
  }

  @Test
  def reportsFailedTestsThroughEventsAndStatus(): Unit = {
    val suite: FailingWordSpec = new FailingWordSpec
    val result: RunResult = runSuite(suite)
    val failures: Vector[TestFailed] = failedEvents(result.events)

    assert(!result.succeeded)
    assert(failures.size == 1)
    assert(failures.head.testName.contains("publish assertion failures"))
    assert(failures.head.message.contains("numbers did not match"))
  }

  @Test
  def recordsInformationalMessagesWithSucceededWordSpecTests(): Unit = {
    val suite: InformingWordSpec = new InformingWordSpec
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
    val suite: TaggedWordSpec = new TaggedWordSpec
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
  def passesFreshFixturesToFixtureAnyWordSpecTests(): Unit = {
    val suite: MutableBufferFixtureWordSpec = new MutableBufferFixtureWordSpec
    val result: RunResult = runSuite(suite)

    assert(result.succeeded)
    assert(succeededEvents(result.events).size == 2)
    assert(suite.finishedFixtures == Vector("seed-alpha", "seed-beta"))
  }

  @Test
  def registersAndRunsSharedWordSpecBehaviorsInDifferentContexts(): Unit = {
    val suite: SharedBehaviorWordSpec = new SharedBehaviorWordSpec
    val names: Vector[String] = suite.testNames.toVector
    val result: RunResult = runSuite(suite)

    assert(names.count(_.contains("keep the first element available")) == 2)
    assert(names.exists(_.contains("List-based collection")))
    assert(names.exists(_.contains("Vector-based collection")))
    assert(result.succeeded)
    assert(succeededEvents(result.events).size == 4)
    assert(suite.observed == Vector("list:first", "list:size", "vector:first", "vector:size"))
  }

  @Test
  def runsAsyncWordSpecAndFixtureAsyncWordSpecTests(): Unit = {
    val asyncSuite: FutureWordSpec = new FutureWordSpec
    val asyncResult: RunResult = runSuite(asyncSuite)
    val fixtureSuite: AsyncFixtureWordSpec = new AsyncFixtureWordSpec
    val fixtureResult: RunResult = runSuite(fixtureSuite)

    assert(asyncResult.succeeded)
    assert(succeededEvents(asyncResult.events).size == 2)
    assert(asyncSuite.executed == Vector("future-value", "recovered-failure"))
    assert(fixtureResult.succeeded)
    assert(succeededEvents(fixtureResult.events).size == 1)
    assert(fixtureSuite.seenFixtures == Vector("async-fixture-used"))
  }

  private final class CalculatorWordSpec extends AnyWordSpec {
    var executed: Vector[String] = Vector.empty

    private val display = afterWord("display output")

    "A calculator" when {
      "adding numbers" should {
        "return the sum" in {
          executed = executed :+ "sum"
          assert(2 + 3 == 5)
        }

        "report pending work" is pending

        "not execute ignored examples" ignore {
          executed = executed :+ "ignored"
          fail("ignored example was executed")
        }
      }

      "the display" should display {
        "formatted total" in {
          executed = executed :+ "display"
          assert(f"${2.5}%.1f" == "2.5")
        }
      }
    }

    it must {
      "support shorthand subjects" in {
        executed = executed :+ "shorthand"
        assert("scala".startsWith("sca"))
      }
    }
  }

  private final class LifecycleWordSpec extends AnyWordSpec {
    var executed: Vector[String] = Vector.empty

    "A lifecycle word spec" should {
      "record ordinary successes" in {
        executed = executed :+ "success"
        assert(List(1, 2, 3).sum == 6)
      }

      "document future behaviour" is pending

      "cancel unavailable work" in {
        executed = executed :+ "canceled"
        cancel("external service is intentionally unavailable")
      }

      "remain registered while ignored" ignore {
        executed = executed :+ "ignored"
        fail("ignored test body ran")
      }
    }
  }

  private final class FailingWordSpec extends AnyWordSpec {
    "A failing word spec" should {
      "publish assertion failures" in {
        assert(1 == 2, "numbers did not match")
      }
    }
  }

  private final class InformingWordSpec extends AnyWordSpec {
    var observed: Vector[String] = Vector.empty

    "An informing word spec" should {
      "attach progress messages to a successful test" in {
        observed = observed :+ "message-start"
        info("prepared a sample value")
        val transformed: String = "scala".reverse.reverse
        info("verified transformed value")
        observed = observed :+ "message-end"
        assert(transformed == "scala")
      }
    }
  }

  private final class TaggedWordSpec extends AnyWordSpec {
    var executed: Vector[String] = Vector.empty

    "A tagged word spec" should {
      "run selected fast examples" taggedAs FastTag in {
        executed = executed :+ "fast"
        assert(true)
      }

      "run slow examples" taggedAs SlowTag in {
        executed = executed :+ "slow"
        assert(true)
      }

      "keep ignored tagged examples registered" taggedAs SlowTag ignore {
        executed = executed :+ "ignored"
        fail("ignored tagged test body ran")
      }
    }
  }

  private final class MutableBufferFixtureWordSpec extends FixtureAnyWordSpec {
    type FixtureParam = StringBuilder

    var finishedFixtures: Vector[String] = Vector.empty

    override protected def withFixture(test: OneArgTest): Outcome = {
      val fixture: StringBuilder = new StringBuilder("seed")
      try {
        withFixture(test.toNoArgTest(fixture))
      } finally {
        finishedFixtures = finishedFixtures :+ fixture.toString()
      }
    }

    "A fixture word spec" should {
      "receive a mutable builder" in { builder: StringBuilder =>
        builder.append("-alpha")
        assert(builder.toString() == "seed-alpha")
      }

      "receive a fresh builder for each test" in { builder: StringBuilder =>
        builder.append("-beta")
        assert(builder.toString() == "seed-beta")
      }
    }
  }

  private final class SharedBehaviorWordSpec extends AnyWordSpec {
    var observed: Vector[String] = Vector.empty

    private def behaveLikeNonEmptyCollection(label: String, values: Seq[String]): Unit = {
      "keep the first element available" in {
        observed = observed :+ s"$label:first"
        assert(values.headOption.contains("alpha"))
      }

      "report the collection size" in {
        observed = observed :+ s"$label:size"
        assert(values.size == 2)
      }
    }

    "A List-based collection" should {
      behave like behaveLikeNonEmptyCollection("list", List("alpha", "beta"))
    }

    "A Vector-based collection" should {
      behave like behaveLikeNonEmptyCollection("vector", Vector("alpha", "beta"))
    }
  }

  private final class FutureWordSpec extends AsyncWordSpec {
    var executed: Vector[String] = Vector.empty

    "An async word spec" should {
      "complete successful futures" in {
        Future.successful {
          executed = executed :+ "future-value"
          assert(21 * 2 == 42)
          succeed
        }
      }

      "recover expected failed futures" in {
        executed = executed :+ "recovered-failure"
        recoverToSucceededIf[IllegalArgumentException] {
          Future.failed(new IllegalArgumentException("invalid async input"))
        }
      }
    }
  }

  private final class AsyncFixtureWordSpec extends FixtureAsyncWordSpec {
    type FixtureParam = StringBuilder

    var seenFixtures: Vector[String] = Vector.empty

    override def withFixture(test: OneArgAsyncTest): FutureOutcome = {
      withFixture(test.toNoArgAsyncTest(new StringBuilder("async-fixture")))
    }

    "An async fixture word spec" should {
      "receive an asynchronous fixture" in { builder: StringBuilder =>
        Future.successful {
          builder.append("-used")
          seenFixtures = seenFixtures :+ builder.toString()
          assert(builder.toString() == "async-fixture-used")
          succeed
        }
      }
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

  private object FastTag extends Tag("org.scalatest.wordspec.generated.Fast")

  private object SlowTag extends Tag("org.scalatest.wordspec.generated.Slow")
}
