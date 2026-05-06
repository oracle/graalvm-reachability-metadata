/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_funsuite_2_13

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.scalatest.{Args, ConfigMap, Filter, Outcome, Reporter, Tag}
import org.scalatest.events.{Event, InfoProvided, TestFailed, TestIgnored, TestPending, TestStarting, TestSucceeded}
import org.scalatest.exceptions.{DuplicateTestNameException, TestRegistrationClosedException}
import org.scalatest.funsuite.{AnyFunSuite, FixtureAnyFunSuite}

import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._

class Scalatest_funsuite_2_13Test {

  @Test
  def anyFunSuiteRegistersAndRunsTestsInDeclarationOrder(): Unit = {
    val suite: OrderedFunSuite = new OrderedFunSuite
    val reporter: RecordingReporter = new RecordingReporter

    val status = suite.run(None, Args(reporter))

    assertThat(status.isCompleted).isTrue()
    assertThat(status.succeeds()).isTrue()
    assertThat(suite.testNames.toList.asJava).containsExactly("first test", "second test")
    assertThat(suite.executionLog.asJava).containsExactly("first", "second")
    assertThat(reporter.eventsOf[TestStarting].map(_.testName).asJava).containsExactly("first test", "second test")
    assertThat(reporter.eventsOf[TestSucceeded].map(_.testName).asJava).containsExactly("first test", "second test")
  }

  @Test
  def anyFunSuiteCanRunASingleNamedTest(): Unit = {
    val suite: OrderedFunSuite = new OrderedFunSuite
    val reporter: RecordingReporter = new RecordingReporter

    val status = suite.run(Some("second test"), Args(reporter))

    assertThat(status.isCompleted).isTrue()
    assertThat(status.succeeds()).isTrue()
    assertThat(suite.executionLog.asJava).containsExactly("second")
    assertThat(reporter.eventsOf[TestStarting].map(_.testName).asJava).containsExactly("second test")
    assertThat(reporter.eventsOf[TestSucceeded].map(_.testName).asJava).containsExactly("second test")
  }

  @Test
  def anyFunSuiteReportsIgnoredPendingFailedAndRecordedInfoEvents(): Unit = {
    val suite: OutcomeFunSuite = new OutcomeFunSuite
    val reporter: RecordingReporter = new RecordingReporter

    val status = suite.run(None, Args(reporter))

    assertThat(status.isCompleted).isTrue()
    assertThat(status.succeeds()).isFalse()
    assertThat(reporter.eventsOf[TestSucceeded].map(_.testName).asJava).containsExactly("succeeds with info")
    assertThat(reporter.eventsOf[TestIgnored].map(_.testName).asJava).containsExactly("ignored test")
    assertThat(reporter.eventsOf[TestPending].map(_.testName).asJava).containsExactly("pending test")
    assertThat(reporter.eventsOf[TestFailed].map(_.testName).asJava).containsExactly("failing test")

    val succeeded: TestSucceeded = reporter.eventsOf[TestSucceeded].head
    assertThat(succeeded.recordedEvents.collect { case event: InfoProvided => event.message }.asJava)
      .containsExactly("diagnostic details")
    assertThat(suite.executionLog.asJava).containsExactly("succeeded", "pending", "failed")
  }

  @Test
  def anyFunSuiteAppliesTagsWhenCountingAndFilteringTests(): Unit = {
    val suite: TaggedFunSuite = new TaggedFunSuite
    val includeFastOnly: Filter = Filter(tagsToInclude = Some(Set(FastTag.name)), tagsToExclude = Set.empty)
    val excludeDatabase: Filter = Filter(tagsToExclude = Set(DatabaseTag.name))
    val reporter: RecordingReporter = new RecordingReporter

    assertThat(suite.tags("fast test").asJava).containsExactly(FastTag.name)
    assertThat(suite.tags("database test").asJava).containsExactly(DatabaseTag.name)
    assertThat(suite.expectedTestCount(includeFastOnly)).isEqualTo(1)
    assertThat(suite.expectedTestCount(excludeDatabase)).isEqualTo(1)

    val status = suite.run(None, Args(reporter, filter = includeFastOnly))

    assertThat(status.isCompleted).isTrue()
    assertThat(status.succeeds()).isTrue()
    assertThat(suite.executionLog.asJava).containsExactly("fast")
    assertThat(reporter.eventsOf[TestSucceeded].map(_.testName).asJava).containsExactly("fast test")
  }

  @Test
  def anyFunSuiteRegistersSharedTestsThroughTestsFor(): Unit = {
    val suite: SharedBehaviorFunSuite = new SharedBehaviorFunSuite
    val reporter: RecordingReporter = new RecordingReporter

    val status = suite.run(None, Args(reporter))

    assertThat(status.isCompleted).isTrue()
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
  def fixtureAnyFunSuiteProvidesFixtureAndConfigMapToEachTest(): Unit = {
    val suite: FixtureBackedFunSuite = new FixtureBackedFunSuite
    val reporter: RecordingReporter = new RecordingReporter
    val args: Args = Args(reporter, configMap = ConfigMap("seed" -> "configured value"))

    val status = suite.run(None, args)

    assertThat(status.isCompleted).isTrue()
    assertThat(status.succeeds()).isTrue()
    assertThat(suite.events.asJava).containsExactly(
      "create:uses fixture argument",
      "test:configured value",
      "cleanup:configured value",
      "test:no-arg"
    )
    assertThat(reporter.eventsOf[TestSucceeded].map(_.testName).asJava).containsExactly(
      "uses fixture argument",
      "uses no-arg fixture wrapper"
    )
  }

  @Test
  def anyFunSuiteRejectsDuplicateTestNames(): Unit = {
    assertThrows(
      classOf[DuplicateTestNameException],
      () => new DuplicateNameFunSuite
    )
  }

  @Test
  def anyFunSuiteClosesRegistrationAfterRunStarts(): Unit = {
    val suite: LateRegistrationFunSuite = new LateRegistrationFunSuite
    val reporter: RecordingReporter = new RecordingReporter

    val status = suite.run(None, Args(reporter))

    assertThat(status.isCompleted).isTrue()
    assertThat(status.succeeds()).isTrue()
    assertThrows(classOf[TestRegistrationClosedException], () => suite.registerAdditionalTest())
  }
}

class RecordingReporter extends Reporter {
  private val recordedEvents: ListBuffer[Event] = ListBuffer.empty

  override def apply(event: Event): Unit = {
    recordedEvents += event
  }

  def events: List[Event] = recordedEvents.toList

  def eventsOf[A <: Event](implicit classTag: scala.reflect.ClassTag[A]): List[A] = {
    events.collect { case event: A => event }
  }
}

class OrderedFunSuite extends AnyFunSuite {
  val executionLog: ListBuffer[String] = ListBuffer.empty

  test("first test") {
    executionLog += "first"
  }

  test("second test") {
    executionLog += "second"
  }
}

class OutcomeFunSuite extends AnyFunSuite {
  val executionLog: ListBuffer[String] = ListBuffer.empty

  test("succeeds with info") {
    executionLog += "succeeded"
    info("diagnostic details")
  }

  ignore("ignored test") {
    executionLog += "ignored"
  }

  test("pending test") {
    executionLog += "pending"
    pending
  }

  test("failing test") {
    executionLog += "failed"
    throw new IllegalStateException("intentional failure")
  }
}

object FastTag extends Tag("org.scalatest.examples.Fast")

object DatabaseTag extends Tag("org.scalatest.examples.Database")

class TaggedFunSuite extends AnyFunSuite {
  val executionLog: ListBuffer[String] = ListBuffer.empty

  test("fast test", FastTag) {
    executionLog += "fast"
  }

  test("database test", DatabaseTag) {
    executionLog += "database"
  }
}

class SharedBehaviorFunSuite extends AnyFunSuite {
  testsFor(nonEmptyStack("stack created with one element", List(1)))

  private def nonEmptyStack(description: String, stack: List[Int]): Unit = {
    test(s"$description is non-empty") {
      assert(stack.nonEmpty)
    }

    test(s"$description returns the top element") {
      assert(stack.head == 1)
    }
  }
}

class FixtureBackedFunSuite extends FixtureAnyFunSuite {
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

  test("uses fixture argument") { fixture: String =>
    events += s"test:$fixture"
    assert(fixture == "configured value")
  }

  test("uses no-arg fixture wrapper") { () =>
    events += "test:no-arg"
  }
}

class DuplicateNameFunSuite extends AnyFunSuite {
  test("duplicate") {}
  test("duplicate") {}
}

class LateRegistrationFunSuite extends AnyFunSuite {
  test("registered before run") {}

  def registerAdditionalTest(): Unit = {
    test("registered after run") {}
  }
}
