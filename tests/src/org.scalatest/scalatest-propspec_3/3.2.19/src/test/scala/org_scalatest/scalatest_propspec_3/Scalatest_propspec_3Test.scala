/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_propspec_3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.scalatest.{Args, ConfigMap, Filter, Outcome, Reporter, Tag}
import org.scalatest.events.{Event, InfoProvided, TestCanceled, TestFailed, TestIgnored, TestPending, TestStarting, TestSucceeded}
import org.scalatest.exceptions.{DuplicateTestNameException, TestRegistrationClosedException}
import org.scalatest.propspec.{AnyPropSpec, FixtureAnyPropSpec}

import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*

class Scalatest_propspec_3Test {

  @Test
  def anyPropSpecRegistersAndRunsPropertiesInDeclarationOrder(): Unit = {
    val suite: OrderedPropSpec = new OrderedPropSpec
    val reporter: PropSpecRecordingReporter = new PropSpecRecordingReporter

    val status = suite.run(None, Args(reporter))

    assertThat(status.isCompleted()).isTrue()
    assertThat(status.succeeds()).isTrue()
    assertThat(suite.testNames.toList.asJava).containsExactly("first property", "second property")
    assertThat(suite.executionLog.asJava).containsExactly("first", "second")
    assertThat(reporter.eventsOf[TestStarting].map(_.testName).asJava)
      .containsExactly("first property", "second property")
    assertThat(reporter.eventsOf[TestSucceeded].map(_.testName).asJava)
      .containsExactly("first property", "second property")
  }

  @Test
  def anyPropSpecCanRunASingleNamedProperty(): Unit = {
    val suite: OrderedPropSpec = new OrderedPropSpec
    val reporter: PropSpecRecordingReporter = new PropSpecRecordingReporter

    val status = suite.run(Some("second property"), Args(reporter))

    assertThat(status.isCompleted()).isTrue()
    assertThat(status.succeeds()).isTrue()
    assertThat(suite.executionLog.asJava).containsExactly("second")
    assertThat(reporter.eventsOf[TestStarting].map(_.testName).asJava).containsExactly("second property")
    assertThat(reporter.eventsOf[TestSucceeded].map(_.testName).asJava).containsExactly("second property")
  }

  @Test
  def anyPropSpecReportsIgnoredPendingFailedAndRecordedInfoEvents(): Unit = {
    val suite: OutcomePropSpec = new OutcomePropSpec
    val reporter: PropSpecRecordingReporter = new PropSpecRecordingReporter

    val status = suite.run(None, Args(reporter))

    assertThat(status.isCompleted()).isTrue()
    assertThat(status.succeeds()).isFalse()
    assertThat(reporter.eventsOf[TestSucceeded].map(_.testName).asJava).containsExactly("succeeds with info")
    assertThat(reporter.eventsOf[TestIgnored].map(_.testName).asJava).containsExactly("ignored property")
    assertThat(reporter.eventsOf[TestPending].map(_.testName).asJava).containsExactly("pending property")
    assertThat(reporter.eventsOf[TestFailed].map(_.testName).asJava).containsExactly("failing property")

    val succeeded: TestSucceeded = reporter.eventsOf[TestSucceeded].head
    assertThat(succeeded.recordedEvents.collect { case event: InfoProvided => event.message }.asJava)
      .containsExactly("diagnostic details")
    assertThat(suite.executionLog.asJava).containsExactly("succeeded", "pending", "failed")
  }

  @Test
  def anyPropSpecReportsCanceledPropertiesAndContinuesTheSuite(): Unit = {
    val suite: CancelingPropSpec = new CancelingPropSpec
    val reporter: PropSpecRecordingReporter = new PropSpecRecordingReporter

    val status = suite.run(None, Args(reporter))

    assertThat(status.isCompleted()).isTrue()
    assertThat(status.succeeds()).isTrue()
    assertThat(suite.executionLog.asJava).containsExactly("before", "cancel", "after")
    assertThat(reporter.eventsOf[TestSucceeded].map(_.testName).asJava)
      .containsExactly("property before cancellation", "property after cancellation")

    val canceledEvents: List[TestCanceled] = reporter.eventsOf[TestCanceled]
    assertThat(canceledEvents.map(_.testName).asJava).containsExactly("canceled property")
    assertThat(canceledEvents.head.message).contains("external service unavailable")
    assertThat(reporter.eventsOf[TestFailed].asJava).isEmpty()
  }

  @Test
  def anyPropSpecAppliesTagsWhenCountingAndFilteringProperties(): Unit = {
    val suite: TaggedPropSpec = new TaggedPropSpec
    val includeFastOnly: Filter = Filter(tagsToInclude = Some(Set(FastPropTag.name)), tagsToExclude = Set.empty)
    val excludeDatabase: Filter = Filter(tagsToExclude = Set(DatabasePropTag.name))
    val reporter: PropSpecRecordingReporter = new PropSpecRecordingReporter

    assertThat(suite.tags("fast property").asJava).containsExactly(FastPropTag.name)
    assertThat(suite.tags("database property").asJava).containsExactly(DatabasePropTag.name)
    assertThat(suite.expectedTestCount(includeFastOnly)).isEqualTo(1)
    assertThat(suite.expectedTestCount(excludeDatabase)).isEqualTo(1)

    val status = suite.run(None, Args(reporter, filter = includeFastOnly))

    assertThat(status.isCompleted()).isTrue()
    assertThat(status.succeeds()).isTrue()
    assertThat(suite.executionLog.asJava).containsExactly("fast")
    assertThat(reporter.eventsOf[TestSucceeded].map(_.testName).asJava).containsExactly("fast property")
  }

  @Test
  def anyPropSpecRegistersSharedPropertiesThroughPropertiesFor(): Unit = {
    val suite: SharedBehaviorPropSpec = new SharedBehaviorPropSpec
    val reporter: PropSpecRecordingReporter = new PropSpecRecordingReporter

    val status = suite.run(None, Args(reporter))

    assertThat(status.isCompleted()).isTrue()
    assertThat(status.succeeds()).isTrue()
    assertThat(suite.testNames.toList.asJava).containsExactly(
      "stack created with one element is non-empty",
      "stack created with one element returns the top element"
    )
    assertThat(reporter.eventsOf[TestSucceeded].map(_.testName).asJava).containsExactly(
      "stack created with one element is non-empty",
      "stack created with one element returns the top element"
    )
  }

  @Test
  def fixtureAnyPropSpecProvidesFixtureAndConfigMapToEachProperty(): Unit = {
    val suite: FixtureBackedPropSpec = new FixtureBackedPropSpec
    val reporter: PropSpecRecordingReporter = new PropSpecRecordingReporter
    val args: Args = Args(reporter, configMap = ConfigMap("seed" -> "configured value"))

    val status = suite.run(None, args)

    assertThat(status.isCompleted()).isTrue()
    assertThat(status.succeeds()).isTrue()
    assertThat(suite.events.asJava).containsExactly(
      "create:uses fixture argument",
      "property:configured value",
      "cleanup:configured value",
      "property:no-arg"
    )
    assertThat(reporter.eventsOf[TestSucceeded].map(_.testName).asJava).containsExactly(
      "uses fixture argument",
      "uses no-arg fixture wrapper"
    )
  }

  @Test
  def anyPropSpecAppliesNoArgFixtureAroundEachProperty(): Unit = {
    val suite: NoArgFixturePropSpec = new NoArgFixturePropSpec
    val reporter: PropSpecRecordingReporter = new PropSpecRecordingReporter
    val args: Args = Args(reporter, configMap = ConfigMap("mode" -> "fixture value"))

    val status = suite.run(None, args)

    assertThat(status.isCompleted()).isTrue()
    assertThat(status.succeeds()).isTrue()
    assertThat(suite.events.asJava).containsExactly(
      "before:first wrapped:fixture value",
      "body:first",
      "after:first wrapped",
      "before:second wrapped:fixture value",
      "body:second",
      "after:second wrapped"
    )
    assertThat(reporter.eventsOf[TestSucceeded].map(_.testName).asJava).containsExactly("first wrapped", "second wrapped")
  }

  @Test
  def anyPropSpecExposesTestDataForRegisteredProperties(): Unit = {
    val suite: TaggedPropSpec = new TaggedPropSpec
    val data = suite.testDataFor("fast property", ConfigMap("run" -> 42))

    assertThat(data.name).isEqualTo("fast property")
    assertThat(data.configMap("run")).isEqualTo(42)
    assertThat(data.tags.asJava).containsExactly(FastPropTag.name)
  }

  @Test
  def anyPropSpecRejectsDuplicatePropertyNames(): Unit = {
    assertThrows(
      classOf[DuplicateTestNameException],
      () => new DuplicateNamePropSpec
    )
  }

  @Test
  def anyPropSpecClosesRegistrationAfterRunStarts(): Unit = {
    val suite: LateRegistrationPropSpec = new LateRegistrationPropSpec
    val reporter: PropSpecRecordingReporter = new PropSpecRecordingReporter

    val status = suite.run(None, Args(reporter))

    assertThat(status.isCompleted()).isTrue()
    assertThat(status.succeeds()).isTrue()
    assertThrows(classOf[TestRegistrationClosedException], () => suite.registerAdditionalProperty())
  }
}

class PropSpecRecordingReporter extends Reporter {
  private val recordedEvents: ListBuffer[Event] = ListBuffer.empty

  override def apply(event: Event): Unit = {
    recordedEvents += event
  }

  def events: List[Event] = recordedEvents.toList

  def eventsOf[A <: Event](using classTag: scala.reflect.ClassTag[A]): List[A] = {
    events.collect { case event: A => event }
  }
}

class OrderedPropSpec extends AnyPropSpec {
  val executionLog: ListBuffer[String] = ListBuffer.empty

  property("first property") {
    executionLog += "first"
  }

  property("second property") {
    executionLog += "second"
  }
}

class OutcomePropSpec extends AnyPropSpec {
  val executionLog: ListBuffer[String] = ListBuffer.empty

  property("succeeds with info") {
    executionLog += "succeeded"
    info("diagnostic details")
  }

  ignore("ignored property") {
    executionLog += "ignored"
  }

  property("pending property") {
    executionLog += "pending"
    pending
  }

  property("failing property") {
    executionLog += "failed"
    throw new IllegalStateException("intentional failure")
  }
}

class CancelingPropSpec extends AnyPropSpec {
  val executionLog: ListBuffer[String] = ListBuffer.empty

  property("property before cancellation") {
    executionLog += "before"
  }

  property("canceled property") {
    executionLog += "cancel"
    cancel("external service unavailable")
  }

  property("property after cancellation") {
    executionLog += "after"
  }
}

object FastPropTag extends Tag("org.scalatest.examples.FastProperty")

object DatabasePropTag extends Tag("org.scalatest.examples.DatabaseProperty")

class TaggedPropSpec extends AnyPropSpec {
  val executionLog: ListBuffer[String] = ListBuffer.empty

  property("fast property", FastPropTag) {
    executionLog += "fast"
  }

  property("database property", DatabasePropTag) {
    executionLog += "database"
  }
}

class SharedBehaviorPropSpec extends AnyPropSpec {
  propertiesFor(nonEmptyStack("stack created with one element", List(1)))

  private def nonEmptyStack(description: String, stack: List[Int]): Unit = {
    property(s"$description is non-empty") {
      assert(stack.nonEmpty)
    }

    property(s"$description returns the top element") {
      assert(stack.head == 1)
    }
  }
}

class FixtureBackedPropSpec extends FixtureAnyPropSpec {
  type FixtureParam = String

  val events: ListBuffer[String] = ListBuffer.empty

  override protected def withFixture(test: OneArgTest): Outcome = {
    events += s"create:${test.name}"
    val fixture: String = test.configMap("seed").asInstanceOf[String]
    try {
      test(fixture)
    } finally {
      events += s"cleanup:$fixture"
    }
  }

  property("uses fixture argument") { (fixture: String) =>
    events += s"property:$fixture"
    assert(fixture == "configured value")
  }

  property("uses no-arg fixture wrapper") { () =>
    events += "property:no-arg"
  }
}

class NoArgFixturePropSpec extends AnyPropSpec {
  val events: ListBuffer[String] = ListBuffer.empty

  override protected def withFixture(test: NoArgTest): Outcome = {
    events += s"before:${test.name}:${test.configMap("mode")}"
    try {
      test()
    } finally {
      events += s"after:${test.name}"
    }
  }

  property("first wrapped") {
    events += "body:first"
  }

  property("second wrapped") {
    events += "body:second"
  }
}

class DuplicateNamePropSpec extends AnyPropSpec {
  property("duplicate") {}
  property("duplicate") {}
}

class LateRegistrationPropSpec extends AnyPropSpec {
  property("registered before run") {}

  def registerAdditionalProperty(): Unit = {
    property("registered after run") {}
  }
}
