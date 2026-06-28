/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_propspec_3

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import scala.jdk.CollectionConverters.*
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

class Scalatest_propspec_3Test:
  @Test
  def anyPropSpecRegistersAndRunsPropertiesInDeclarationOrder(): Unit =
    val suite: OrderedPropSpec = new OrderedPropSpec
    val result: RunResult = runSuite(suite)

    assert(result.succeeded)
    assert(suite.testNames.toVector == Vector("first property", "second property"))
    assert(suite.executionLog == Vector("first", "second"))
    assert(startingEvents(result.events).map(_.testName) == Vector("first property", "second property"))
    assert(succeededEvents(result.events).map(_.testName) == Vector("first property", "second property"))

  @Test
  def anyPropSpecRunsASingleNamedPropertyWithoutExecutingTheOthers(): Unit =
    val suite: OrderedPropSpec = new OrderedPropSpec
    val result: RunResult = runSuite(suite, testName = Some("second property"))

    assert(result.succeeded)
    assert(suite.executionLog == Vector("second"))
    assert(startingEvents(result.events).map(_.testName) == Vector("second property"))
    assert(succeededEvents(result.events).map(_.testName) == Vector("second property"))

  @Test
  def anyPropSpecReportsSucceededIgnoredPendingFailedAndInfoEvents(): Unit =
    val suite: OutcomePropSpec = new OutcomePropSpec
    val result: RunResult = runSuite(suite)

    assert(!result.succeeded)
    assert(succeededEvents(result.events).map(_.testName) == Vector("successful property with info"))
    assert(ignoredEvents(result.events).map(_.testName) == Vector("ignored property"))
    assert(pendingEvents(result.events).map(_.testName) == Vector("pending property"))
    assert(failedEvents(result.events).map(_.testName) == Vector("failing property"))
    assert(suite.executionLog == Vector("succeeded", "pending", "failed"))

    val success: TestSucceeded = succeededEvents(result.events).head
    val messages: Vector[String] = success.recordedEvents.collect {
      case event: InfoProvided => event.message
    }.toVector
    assert(messages == Vector("property diagnostics"))

  @Test
  def anyPropSpecReportsCanceledPropertiesAndContinuesTheSuite(): Unit =
    val suite: CancelingPropSpec = new CancelingPropSpec
    val result: RunResult = runSuite(suite)
    val canceled: Vector[TestCanceled] = canceledEvents(result.events)

    assert(result.succeeded)
    assert(suite.executionLog == Vector("before", "cancel", "after"))
    assert(succeededEvents(result.events).map(_.testName) == Vector("property before cancellation", "property after cancellation"))
    assert(canceled.map(_.testName) == Vector("canceled property"))
    assert(canceled.head.message.contains("input generator unavailable"))
    assert(failedEvents(result.events).isEmpty)

  @Test
  def anyPropSpecReportsNotificationsAndRecordedMarkup(): Unit =
    val suite: DocumentationPropSpec = new DocumentationPropSpec
    val result: RunResult = runSuite(suite)
    val success: TestSucceeded = succeededEvents(result.events).head
    val markup: Vector[String] = success.recordedEvents.collect {
      case event: MarkupProvided => event.text
    }.toVector

    assert(result.succeeded)
    assert(noteEvents(result.events).map(_.message) == Vector("note for the property runner"))
    assert(alertEvents(result.events).map(_.message) == Vector("alert for the property runner"))
    assert(markup == Vector("### property evidence"))

  @Test
  def anyPropSpecExposesTagsForCountingAndFiltering(): Unit =
    val suite: TaggedPropSpec = new TaggedPropSpec
    val includeFastOnly: Filter = Filter(tagsToInclude = Some(Set(FastTag.name)), tagsToExclude = Set.empty)
    val excludeDatabase: Filter = Filter(tagsToExclude = Set(DatabaseTag.name))

    assert(suite.tags("fast property") == Set(FastTag.name))
    assert(suite.tags("database property") == Set(DatabaseTag.name))
    assert(suite.expectedTestCount(includeFastOnly) == 1)
    assert(suite.expectedTestCount(excludeDatabase) == 1)

    val result: RunResult = runSuite(suite, filter = includeFastOnly)

    assert(result.succeeded)
    assert(suite.executionLog == Vector("fast"))
    assert(succeededEvents(result.events).map(_.testName) == Vector("fast property"))

  @Test
  def anyPropSpecRegistersSharedPropertiesThroughPropertiesFor(): Unit =
    val suite: SharedPropertyPropSpec = new SharedPropertyPropSpec
    val result: RunResult = runSuite(suite)

    assert(result.succeeded)
    assert(suite.testNames.toVector == Vector(
      "non-empty stack created with one element reports non-empty",
      "non-empty stack created with one element exposes the top element"
    ))
    assert(succeededEvents(result.events).map(_.testName) == Vector(
      "non-empty stack created with one element reports non-empty",
      "non-empty stack created with one element exposes the top element"
    ))

  @Test
  def anyPropSpecSupportsRegisterTestAndRegisterIgnoredTestAliases(): Unit =
    val suite: RegisteredAliasPropSpec = new RegisteredAliasPropSpec
    val result: RunResult = runSuite(suite)

    assert(result.succeeded)
    assert(suite.executionLog == Vector("registered"))
    assert(suite.testNames.toVector == Vector("registered property", "ignored registered property"))
    assert(succeededEvents(result.events).map(_.testName) == Vector("registered property"))
    assert(ignoredEvents(result.events).map(_.testName) == Vector("ignored registered property"))

  @Test
  def fixtureAnyPropSpecProvidesFixturesNoArgPropertiesAndConfigMaps(): Unit =
    val suite: FixtureBackedPropSpec = new FixtureBackedPropSpec
    val result: RunResult = runSuite(suite, configMap = ConfigMap("seed" -> "configured value"))

    assert(result.succeeded)
    assert(suite.events == Vector(
      "create:uses fixture argument",
      "property:configured value",
      "cleanup:configured value",
      "property:no-arg"
    ))
    assert(succeededEvents(result.events).map(_.testName) == Vector(
      "uses fixture argument",
      "uses no-arg fixture wrapper"
    ))

  @Test
  def fixtureAnyPropSpecIgnoresFixtureBackedPropertiesWithoutCreatingFixtures(): Unit =
    val suite: IgnoredFixturePropSpec = new IgnoredFixturePropSpec
    val result: RunResult = runSuite(suite)

    assert(result.succeeded)
    assert(suite.events == Vector(
      "create:active fixture property",
      "property:active fixture",
      "cleanup:active fixture"
    ))
    assert(suite.testNames.toVector == Vector("ignored fixture property", "active fixture property"))
    assert(ignoredEvents(result.events).map(_.testName) == Vector("ignored fixture property"))
    assert(succeededEvents(result.events).map(_.testName) == Vector("active fixture property"))

  @Test
  def anyPropSpecAppliesNoArgFixturesAroundEachProperty(): Unit =
    val suite: NoArgFixturePropSpec = new NoArgFixturePropSpec
    val result: RunResult = runSuite(suite, configMap = ConfigMap("mode" -> "fixture value"))

    assert(result.succeeded)
    assert(suite.events == Vector(
      "before:first wrapped property:fixture value",
      "body:first",
      "after:first wrapped property",
      "before:second wrapped property:fixture value",
      "body:second",
      "after:second wrapped property"
    ))
    assert(succeededEvents(result.events).map(_.testName) == Vector("first wrapped property", "second wrapped property"))

  @Test
  def anyPropSpecRejectsDuplicatePropertyNames(): Unit =
    assertThrows(classOf[DuplicateTestNameException], () => new DuplicateNamePropSpec)
    ()

  @Test
  def anyPropSpecClosesRegistrationAfterRunStarts(): Unit =
    val suite: LateRegistrationPropSpec = new LateRegistrationPropSpec
    val result: RunResult = runSuite(suite)

    assert(result.succeeded)
    assertThrows(classOf[TestRegistrationClosedException], () => suite.registerAdditionalProperty())
    ()

  private final class OrderedPropSpec extends AnyPropSpec:
    var executionLog: Vector[String] = Vector.empty

    property("first property") {
      executionLog = executionLog :+ "first"
    }

    property("second property") {
      executionLog = executionLog :+ "second"
    }

  private final class OutcomePropSpec extends AnyPropSpec:
    var executionLog: Vector[String] = Vector.empty

    property("successful property with info") {
      executionLog = executionLog :+ "succeeded"
      info("property diagnostics")
    }

    ignore("ignored property") {
      executionLog = executionLog :+ "ignored"
    }

    property("pending property") {
      executionLog = executionLog :+ "pending"
      pending
    }

    property("failing property") {
      executionLog = executionLog :+ "failed"
      throw new IllegalStateException("intentional property failure")
    }

  private final class CancelingPropSpec extends AnyPropSpec:
    var executionLog: Vector[String] = Vector.empty

    property("property before cancellation") {
      executionLog = executionLog :+ "before"
    }

    property("canceled property") {
      executionLog = executionLog :+ "cancel"
      cancel("input generator unavailable")
    }

    property("property after cancellation") {
      executionLog = executionLog :+ "after"
    }

  private final class DocumentationPropSpec extends AnyPropSpec:
    property("emits notifications and markup") {
      note("note for the property runner")
      alert("alert for the property runner")
      markup("### property evidence")
    }

  private final class TaggedPropSpec extends AnyPropSpec:
    var executionLog: Vector[String] = Vector.empty

    property("fast property", FastTag) {
      executionLog = executionLog :+ "fast"
    }

    property("database property", DatabaseTag) {
      executionLog = executionLog :+ "database"
    }

  private final class SharedPropertyPropSpec extends AnyPropSpec:
    propertiesFor(nonEmptyStack("non-empty stack created with one element", List(1)))

    private def nonEmptyStack(description: String, stack: List[Int]): Unit =
      property(s"$description reports non-empty") {
        assert(stack.nonEmpty)
      }

      property(s"$description exposes the top element") {
        assert(stack.head == 1)
      }

  private final class RegisteredAliasPropSpec extends AnyPropSpec:
    var executionLog: Vector[String] = Vector.empty

    registerTest("registered property") {
      executionLog = executionLog :+ "registered"
      assert(2 + 2 == 4)
    }

    registerIgnoredTest("ignored registered property") {
      executionLog = executionLog :+ "ignored"
    }

  private final class FixtureBackedPropSpec extends FixtureAnyPropSpec:
    type FixtureParam = String

    var events: Vector[String] = Vector.empty

    override protected def withFixture(test: OneArgTest): Outcome =
      events = events :+ s"create:${test.name}"
      val fixture: String = test.configMap("seed").asInstanceOf[String]
      try test(fixture)
      finally events = events :+ s"cleanup:$fixture"

    property("uses fixture argument") { (fixture: String) =>
      events = events :+ s"property:$fixture"
      assert(fixture == "configured value")
    }

    property("uses no-arg fixture wrapper") { () =>
      events = events :+ "property:no-arg"
    }

  private final class IgnoredFixturePropSpec extends FixtureAnyPropSpec:
    type FixtureParam = String

    var events: Vector[String] = Vector.empty

    override protected def withFixture(test: OneArgTest): Outcome =
      events = events :+ s"create:${test.name}"
      val fixture: String = test.name.stripSuffix(" property")
      try test(fixture)
      finally events = events :+ s"cleanup:$fixture"

    ignore("ignored fixture property") { (fixture: String) =>
      events = events :+ s"ignored:$fixture"
    }

    property("active fixture property") { (fixture: String) =>
      events = events :+ s"property:$fixture"
      assert(fixture == "active fixture")
    }

  private final class NoArgFixturePropSpec extends AnyPropSpec:
    var events: Vector[String] = Vector.empty

    override protected def withFixture(test: NoArgTest): Outcome =
      events = events :+ s"before:${test.name}:${test.configMap("mode")}"
      try test()
      finally events = events :+ s"after:${test.name}"

    property("first wrapped property") {
      events = events :+ "body:first"
    }

    property("second wrapped property") {
      events = events :+ "body:second"
    }

  private final class DuplicateNamePropSpec extends AnyPropSpec:
    property("duplicate property") {}
    property("duplicate property") {}

  private final class LateRegistrationPropSpec extends AnyPropSpec:
    property("registered before run") {}

    def registerAdditionalProperty(): Unit =
      property("registered after run") {}

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

  private def noteEvents(events: Vector[Event]): Vector[NoteProvided] =
    events.collect { case event: NoteProvided => event }

  private def alertEvents(events: Vector[Event]): Vector[AlertProvided] =
    events.collect { case event: AlertProvided => event }

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

  private object FastTag extends Tag("org.scalatest.propspec.generated.Fast")

  private object DatabaseTag extends Tag("org.scalatest.propspec.generated.Database")
