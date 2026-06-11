/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_propspec_2_13

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import scala.jdk.CollectionConverters._
import scala.util.Try

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.scalatest.Args
import org.scalatest.ConfigMap
import org.scalatest.Filter
import org.scalatest.Outcome
import org.scalatest.Reporter
import org.scalatest.Suite
import org.scalatest.Tag
import org.scalatest.TestData
import org.scalatest.events.AlertProvided
import org.scalatest.events.Event
import org.scalatest.events.InfoProvided
import org.scalatest.events.MarkupProvided
import org.scalatest.events.NoteProvided
import org.scalatest.events.TestCanceled
import org.scalatest.events.TestFailed
import org.scalatest.events.TestIgnored
import org.scalatest.events.TestPending
import org.scalatest.events.TestStarting
import org.scalatest.events.TestSucceeded
import org.scalatest.exceptions.DuplicateTestNameException
import org.scalatest.exceptions.TestRegistrationClosedException
import org.scalatest.propspec.AnyPropSpec
import org.scalatest.propspec.FixtureAnyPropSpec

class Scalatest_propspec_2_13Test {
  @Test
  def registersAndRunsPropertiesInDeclarationOrder(): Unit = {
    val suite: OrderedPropertySpec = new OrderedPropertySpec
    val result: RunResult = runSuite(suite)

    assert(result.succeeded)
    assert(suite.testNames.toVector == Vector("first property", "second property"))
    assert(suite.executed == Vector("first", "second"))
    assert(startingEvents(result.events).map(_.testName) == Vector("first property", "second property"))
    assert(succeededEvents(result.events).map(_.testName) == Vector("first property", "second property"))
  }

  @Test
  def runsOnlySelectedPropertyByName(): Unit = {
    val suite: OrderedPropertySpec = new OrderedPropertySpec
    val result: RunResult = runSuite(suite, testName = Some("second property"))

    assert(result.succeeded)
    assert(suite.executed == Vector("second"))
    assert(startingEvents(result.events).map(_.testName) == Vector("second property"))
    assert(succeededEvents(result.events).map(_.testName) == Vector("second property"))
  }

  @Test
  def reportsSucceededPendingCanceledIgnoredAndFailedProperties(): Unit = {
    val suite: LifecyclePropertySpec = new LifecyclePropertySpec
    val result: RunResult = runSuite(suite)

    assert(!result.succeeded)
    assert(succeededEvents(result.events).map(_.testName) == Vector("successful property"))
    assert(pendingEvents(result.events).map(_.testName) == Vector("pending property"))
    assert(canceledEvents(result.events).map(_.testName) == Vector("canceled property"))
    assert(ignoredEvents(result.events).map(_.testName) == Vector("ignored property"))
    assert(failedEvents(result.events).map(_.testName) == Vector("failing property"))
    assert(failedEvents(result.events).head.message.contains("intentional property failure"))
    assert(suite.executed == Vector("success", "pending", "canceled", "failure"))
  }

  @Test
  def recordsInfoEventsWithSuccessfulProperties(): Unit = {
    val suite: InformingPropertySpec = new InformingPropertySpec
    val result: RunResult = runSuite(suite)
    val success: TestSucceeded = succeededEvents(result.events).head
    val messages: Vector[String] = success.recordedEvents.collect {
      case event: InfoProvided => event.message
    }.toVector

    assert(result.succeeded)
    assert(success.testName == "property records diagnostic information")
    assert(messages == Vector("created sample data", "verified transformed data"))
    assert(suite.observed == Vector("start", "end"))
  }

  @Test
  def emitsNotificationsAlertsAndMarkupFromProperties(): Unit = {
    val suite: NotificationPropertySpec = new NotificationPropertySpec
    val result: RunResult = runSuite(suite)
    val success: TestSucceeded = succeededEvents(result.events).head
    val notes: Vector[String] = result.events.collect {
      case event: NoteProvided => event.message
    }
    val alerts: Vector[String] = result.events.collect {
      case event: AlertProvided => event.message
    }
    val markupBlocks: Vector[String] = success.recordedEvents.collect {
      case event: MarkupProvided => event.text
    }.toVector

    assert(result.succeeded)
    assert(success.testName == "property emits supplemental reporter output")
    assert(notes == Vector("prepared externalized test data"))
    assert(alerts == Vector("using fallback validation path"))
    assert(markupBlocks == Vector("**generated property report**"))
    assert(suite.observed == Vector("start", "end"))
  }

  @Test
  def exposesTagsAndHonorsTagFilters(): Unit = {
    val suite: TaggedPropertySpec = new TaggedPropertySpec
    val fastOnly: Filter = Filter(tagsToInclude = Some(Set(FastTag.name)), tagsToExclude = Set.empty)
    val excludeSlow: Filter = Filter(tagsToExclude = Set(SlowTag.name))

    assert(suite.tags("fast property") == Set(FastTag.name))
    assert(suite.tags("slow property") == Set(SlowTag.name))
    assert(suite.tags("ignored slow property").contains(SlowTag.name))
    assert(suite.tags("ignored slow property").contains("org.scalatest.Ignore"))
    assert(suite.expectedTestCount(fastOnly) == 1)
    assert(suite.expectedTestCount(excludeSlow) == 1)

    val result: RunResult = runSuite(suite, filter = fastOnly)

    assert(result.succeeded)
    assert(suite.executed == Vector("fast"))
    assert(succeededEvents(result.events).map(_.testName) == Vector("fast property"))
  }

  @Test
  def registersSharedPropertyBehaviorsThroughPropertiesFor(): Unit = {
    val suite: SharedBehaviorPropertySpec = new SharedBehaviorPropertySpec
    val result: RunResult = runSuite(suite)

    assert(result.succeeded)
    assert(suite.testNames.toVector == Vector(
      "List collection keeps the first element available",
      "List collection reports its size",
      "Vector collection keeps the first element available",
      "Vector collection reports its size"
    ))
    assert(suite.observed == Vector("list:first", "list:size", "vector:first", "vector:size"))
    assert(succeededEvents(result.events).size == 4)
  }

  @Test
  def exposesPropertyTestDataIncludingConfigMapAndTags(): Unit = {
    val suite: TestDataPropertySpec = new TestDataPropertySpec
    val configMap: ConfigMap = ConfigMap("profile" -> "native-image", "limit" -> 3)
    val testData: TestData = suite.dataForProperty(configMap)

    assert(testData.name == "property with queryable test data")
    assert(testData.configMap == configMap)
    assert(testData.tags == Set(FastTag.name, SlowTag.name))
    assert(testData.scopes.isEmpty)
    assert(testData.text == "property with queryable test data")
  }

  @Test
  def wrapsEachAnyPropSpecPropertyWithNoArgFixture(): Unit = {
    val suite: WrappedPropertySpec = new WrappedPropertySpec
    val result: RunResult = runSuite(suite, configMap = ConfigMap("mode" -> "configured"))

    assert(result.succeeded)
    assert(suite.events == Vector(
      "before:first wrapped property:configured",
      "body:first",
      "after:first wrapped property",
      "before:second wrapped property:configured",
      "body:second",
      "after:second wrapped property"
    ))
    assert(succeededEvents(result.events).map(_.testName) == Vector("first wrapped property", "second wrapped property"))
  }

  @Test
  def fixtureAnyPropSpecProvidesFreshFixturesAndConfigMap(): Unit = {
    val suite: MutableFixturePropertySpec = new MutableFixturePropertySpec
    val result: RunResult = runSuite(suite, configMap = ConfigMap("suffix" -> "configured"))

    assert(result.succeeded)
    assert(suite.finishedFixtures == Vector("seed-configured-alpha", "seed-configured-beta"))
    assert(suite.noArgProperties == Vector("no-arg body"))
    assert(succeededEvents(result.events).map(_.testName) == Vector(
      "fixture property receives a mutable builder",
      "fixture property receives a fresh builder",
      "fixture spec can run a no-arg property"
    ))
  }

  @Test
  def duplicatePropertyNamesAreRejectedDuringRegistration(): Unit = {
    assertThrows(classOf[DuplicateTestNameException], () => new DuplicateNamePropertySpec)
    ()
  }

  @Test
  def registrationIsClosedAfterRunStarts(): Unit = {
    val suite: LateRegistrationPropertySpec = new LateRegistrationPropertySpec
    val result: RunResult = runSuite(suite)

    assert(result.succeeded)
    assertThrows(classOf[TestRegistrationClosedException], () => suite.registerAdditionalProperty())
    ()
  }

  private final class OrderedPropertySpec extends AnyPropSpec {
    var executed: Vector[String] = Vector.empty

    property("first property") {
      executed = executed :+ "first"
      assert(List(1, 2, 3).sum == 6)
    }

    property("second property") {
      executed = executed :+ "second"
      assert("scalatest".startsWith("scala"))
    }
  }

  private final class LifecyclePropertySpec extends AnyPropSpec {
    var executed: Vector[String] = Vector.empty

    property("successful property") {
      executed = executed :+ "success"
      assert(3 * 7 == 21)
    }

    property("pending property") {
      executed = executed :+ "pending"
      pending
    }

    property("canceled property") {
      executed = executed :+ "canceled"
      cancel("external service is intentionally unavailable")
    }

    ignore("ignored property") {
      executed = executed :+ "ignored"
      fail("ignored property body ran")
    }

    property("failing property") {
      executed = executed :+ "failure"
      fail("intentional property failure")
    }
  }

  private final class InformingPropertySpec extends AnyPropSpec {
    var observed: Vector[String] = Vector.empty

    property("property records diagnostic information") {
      observed = observed :+ "start"
      info("created sample data")
      val transformed: String = "metadata".reverse.reverse
      info("verified transformed data")
      observed = observed :+ "end"
      assert(transformed == "metadata")
    }
  }

  private final class NotificationPropertySpec extends AnyPropSpec {
    var observed: Vector[String] = Vector.empty

    property("property emits supplemental reporter output") {
      observed = observed :+ "start"
      note("prepared externalized test data")
      alert("using fallback validation path")
      markup("**generated property report**")
      observed = observed :+ "end"
      assert(observed.nonEmpty)
    }
  }

  private final class TaggedPropertySpec extends AnyPropSpec {
    var executed: Vector[String] = Vector.empty

    property("fast property", FastTag) {
      executed = executed :+ "fast"
      assert(true)
    }

    property("slow property", SlowTag) {
      executed = executed :+ "slow"
      assert(true)
    }

    ignore("ignored slow property", SlowTag) {
      executed = executed :+ "ignored"
      fail("ignored tagged property body ran")
    }
  }

  private final class SharedBehaviorPropertySpec extends AnyPropSpec {
    var observed: Vector[String] = Vector.empty

    propertiesFor(nonEmptyCollection("List collection", "list", List("alpha", "beta")))
    propertiesFor(nonEmptyCollection("Vector collection", "vector", Vector("alpha", "beta")))

    private def nonEmptyCollection(description: String, label: String, values: Seq[String]): Unit = {
      property(s"$description keeps the first element available") {
        observed = observed :+ s"$label:first"
        assert(values.headOption.contains("alpha"))
      }

      property(s"$description reports its size") {
        observed = observed :+ s"$label:size"
        assert(values.size == 2)
      }
    }
  }

  private final class TestDataPropertySpec extends AnyPropSpec {
    property("property with queryable test data", FastTag, SlowTag) {
      assert(true)
    }

    def dataForProperty(configMap: ConfigMap): TestData = {
      testDataFor("property with queryable test data", configMap)
    }
  }

  private final class WrappedPropertySpec extends AnyPropSpec {
    var events: Vector[String] = Vector.empty

    override protected def withFixture(test: NoArgTest): Outcome = {
      events = events :+ s"before:${test.name}:${test.configMap("mode")}"
      try {
        test()
      } finally {
        events = events :+ s"after:${test.name}"
      }
    }

    property("first wrapped property") {
      events = events :+ "body:first"
    }

    property("second wrapped property") {
      events = events :+ "body:second"
    }
  }

  private final class MutableFixturePropertySpec extends FixtureAnyPropSpec {
    type FixtureParam = StringBuilder

    var finishedFixtures: Vector[String] = Vector.empty
    var noArgProperties: Vector[String] = Vector.empty

    override protected def withFixture(test: OneArgTest): Outcome = {
      val suffix: String = test.configMap("suffix").asInstanceOf[String]
      val builder: StringBuilder = new StringBuilder(s"seed-$suffix")
      try {
        withFixture(test.toNoArgTest(builder))
      } finally {
        finishedFixtures = finishedFixtures :+ builder.toString()
      }
    }

    property("fixture property receives a mutable builder") { builder: StringBuilder =>
      builder.append("-alpha")
      assert(builder.toString() == "seed-configured-alpha")
    }

    property("fixture property receives a fresh builder") { builder: StringBuilder =>
      builder.append("-beta")
      assert(builder.toString() == "seed-configured-beta")
    }

    property("fixture spec can run a no-arg property") { () =>
      noArgProperties = noArgProperties :+ "no-arg body"
      assert(noArgProperties.nonEmpty)
    }

  }

  private final class DuplicateNamePropertySpec extends AnyPropSpec {
    property("duplicate property") {}
    property("duplicate property") {}
  }

  private final class LateRegistrationPropertySpec extends AnyPropSpec {
    property("registered before run") {}

    def registerAdditionalProperty(): Unit = {
      property("registered after run") {}
    }
  }

  private def runSuite(
      suite: Suite,
      filter: Filter = Filter.default,
      testName: Option[String] = None,
      configMap: ConfigMap = ConfigMap()): RunResult = {
    val reporter: RecordingReporter = new RecordingReporter
    val completion: AtomicReference[Try[Boolean]] = new AtomicReference[Try[Boolean]]()
    val completed: CountDownLatch = new CountDownLatch(1)
    val status = suite.run(testName, Args(reporter = reporter, filter = filter, configMap = configMap))
    status.whenCompleted { result: Try[Boolean] =>
      completion.set(result)
      completed.countDown()
    }

    assert(completed.await(30, TimeUnit.SECONDS), s"ScalaTest suite ${suite.suiteName} did not complete")
    RunResult(completion.get().get, reporter.events)
  }

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

  private final class RecordingReporter extends Reporter {
    private val recordedEvents: CopyOnWriteArrayList[Event] = new CopyOnWriteArrayList[Event]()

    override def apply(event: Event): Unit = {
      recordedEvents.add(event)
      ()
    }

    def events: Vector[Event] = recordedEvents.asScala.toVector
  }

  private final case class RunResult(succeeded: Boolean, events: Vector[Event])

  private object FastTag extends Tag("org.scalatest.propspec.generated.Fast")

  private object SlowTag extends Tag("org.scalatest.propspec.generated.Slow")
}
