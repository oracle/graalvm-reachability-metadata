/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_sbt.test_interface

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.ThrowableAssert.ThrowingCallable
import org.junit.jupiter.api.Test
import org.scalatools.testing.{Result => LegacyResult}
import sbt.testing.AnnotatedFingerprint
import sbt.testing.Event
import sbt.testing.EventHandler
import sbt.testing.Fingerprint
import sbt.testing.Framework
import sbt.testing.Logger
import sbt.testing.NestedSuiteSelector
import sbt.testing.NestedTestSelector
import sbt.testing.OptionalThrowable
import sbt.testing.Runner
import sbt.testing.Selector
import sbt.testing.Status
import sbt.testing.SubclassFingerprint
import sbt.testing.SuiteSelector
import sbt.testing.Task
import sbt.testing.TaskDef
import sbt.testing.TestSelector
import sbt.testing.TestWildcardSelector

import java.util.ArrayList

final class Test_interfaceTest {
  @Test
  def selectorsExposeTheirTargetsAndValueSemantics(): Unit = {
    val suite = new SuiteSelector()
    val anotherSuite = new SuiteSelector()
    val testName = "calculates totals"
    val wildcard = "integration-*"
    val suiteId = "nested-suite-1"
    val nestedTestName = "accepts parameters"
    val testSelector = new TestSelector(testName)
    val wildcardSelector = new TestWildcardSelector(wildcard)
    val nestedSuiteSelector = new NestedSuiteSelector(suiteId)
    val nestedTestSelector = new NestedTestSelector(suiteId, nestedTestName)

    assertThat(suite).isEqualTo(anotherSuite)
    assertThat(suite.hashCode()).isEqualTo(anotherSuite.hashCode())
    assertThat(suite.toString).isEqualTo("SuiteSelector")

    assertThat(testSelector.testName()).isEqualTo(testName)
    assertThat(testSelector).isEqualTo(new TestSelector(testName))
    assertThat(testSelector.hashCode()).isEqualTo(new TestSelector(testName).hashCode())
    assertThat(testSelector.toString).isEqualTo("TestSelector(calculates totals)")

    assertThat(wildcardSelector.testWildcard()).isEqualTo(wildcard)
    assertThat(wildcardSelector).isEqualTo(new TestWildcardSelector(wildcard))
    assertThat(wildcardSelector.toString).isEqualTo("TestWildcardSelector(integration-*)")

    assertThat(nestedSuiteSelector.suiteId()).isEqualTo(suiteId)
    assertThat(nestedSuiteSelector).isEqualTo(new NestedSuiteSelector(suiteId))
    assertThat(nestedSuiteSelector.toString).isEqualTo("NestedSuiteSelector(nested-suite-1)")

    assertThat(nestedTestSelector.suiteId()).isEqualTo(suiteId)
    assertThat(nestedTestSelector.testName()).isEqualTo(nestedTestName)
    assertThat(nestedTestSelector).isEqualTo(new NestedTestSelector(suiteId, nestedTestName))
    assertThat(nestedTestSelector.toString).isEqualTo("NestedTestSelector(nested-suite-1, accepts parameters)")
  }

  @Test
  def optionalThrowableDistinguishesEmptyAndDefinedValues(): Unit = {
    val empty = new OptionalThrowable()
    val failure = new IllegalArgumentException("bad input")
    val defined = new OptionalThrowable(failure)
    val sameDefined = new OptionalThrowable(failure)

    assertThat(empty.isEmpty()).isTrue()
    assertThat(empty.isDefined()).isFalse()
    assertThat(empty.toString).isEqualTo("OptionalThrowable()")
    assertThatThrownBy(new ThrowingCallable {
      override def call(): Unit = empty.get()
    }).isInstanceOf(classOf[IllegalStateException])
      .hasMessage("This OptionalThrowable is not defined")

    assertThat(defined.isDefined()).isTrue()
    assertThat(defined.isEmpty()).isFalse()
    assertThat(defined.get()).isSameAs(failure)
    assertThat(defined).isEqualTo(sameDefined)
    assertThat(defined.hashCode()).isEqualTo(sameDefined.hashCode())
    assertThat(defined.toString).isEqualTo("OptionalThrowable(java.lang.IllegalArgumentException: bad input)")

    assertThatThrownBy(new ThrowingCallable {
      override def call(): Unit = new OptionalThrowable(null)
    }).isInstanceOf(classOf[NullPointerException])
      .hasMessage("Cannot pass a null exception to OptionalThrowable's constructor.")
  }

  @Test
  def taskDefinitionsKeepClassFingerprintExplicitFlagAndSelectors(): Unit = {
    val fingerprint = ModernSubclassFingerprint(
      module = false,
      superclass = "example.BaseSpec",
      requiresNoArgConstructor = true
    )
    val selectors = Array[Selector](new SuiteSelector(), new TestSelector("runs selected example"))
    val taskDef = new TaskDef("example.CalculatorSpec", fingerprint, true, selectors)
    val equivalentTaskDef = new TaskDef(
      "example.CalculatorSpec",
      fingerprint,
      true,
      Array[Selector](new SuiteSelector(), new TestSelector("runs selected example"))
    )

    assertThat(taskDef.fullyQualifiedName()).isEqualTo("example.CalculatorSpec")
    assertThat(taskDef.fingerprint()).isSameAs(fingerprint)
    assertThat(taskDef.explicitlySpecified()).isTrue()
    assertThat(taskDef.selectors()).isSameAs(selectors)
    assertThat(taskDef.selectors()).containsExactly(selectors: _*)
    assertThat(taskDef).isEqualTo(equivalentTaskDef)
    assertThat(taskDef.hashCode()).isEqualTo(equivalentTaskDef.hashCode())
    assertThat(taskDef.toString).contains("TaskDef(example.CalculatorSpec")
      .contains("ModernSubclassFingerprint")
      .contains("SuiteSelector")
      .contains("TestSelector(runs selected example)")
  }

  @Test
  def fingerprintsExposeDiscoveryCriteriaForAnnotatedAndSubclassTests(): Unit = {
    val annotated = ModernAnnotatedFingerprint(module = true, annotation = "example.IntegrationTest")
    val subclass = ModernSubclassFingerprint(
      module = false,
      superclass = "example.AbstractSpec",
      requiresNoArgConstructor = false
    )

    assertThat(annotated.isModule()).isTrue()
    assertThat(annotated.annotationName()).isEqualTo("example.IntegrationTest")

    assertThat(subclass.isModule()).isFalse()
    assertThat(subclass.superclassName()).isEqualTo("example.AbstractSpec")
    assertThat(subclass.requireNoArgConstructor()).isFalse()
  }

  @Test
  def statusEnumContainsAllDocumentedTaskOutcomes(): Unit = {
    assertThat(Status.values()).containsExactly(
      Status.Success,
      Status.Error,
      Status.Failure,
      Status.Skipped,
      Status.Ignored,
      Status.Canceled,
      Status.Pending
    )
    assertThat(Status.valueOf("Success")).isEqualTo(Status.Success)
    assertThat(Status.valueOf("Failure")).isEqualTo(Status.Failure)
    assertThat(Status.valueOf("Pending")).isEqualTo(Status.Pending)
  }

  @Test
  def frameworkRunnerTasksLogAndPublishModernEvents(): Unit = {
    val fingerprint = ModernAnnotatedFingerprint(module = false, annotation = "example.Fast")
    val taskDef = new TaskDef(
      "example.FastSpec",
      fingerprint,
      false,
      Array[Selector](new TestSelector("emits event"))
    )
    val framework = new RecordingModernFramework(Array[Fingerprint](fingerprint))
    val logger = new RecordingLogger(ansi = true)
    val eventHandler = new RecordingModernEventHandler()

    val runner = framework.runner(Array("-Dcolor=true"), Array("--remote-cache"), getClass.getClassLoader)
    val tasks = runner.tasks(Array(taskDef))
    val followUpTasks = tasks(0).execute(eventHandler, Array[Logger](logger))

    assertThat(framework.name()).isEqualTo("recording-modern")
    assertThat(framework.fingerprints()).containsExactly(fingerprint)
    assertThat(runner.args()).containsExactly("-Dcolor=true")
    assertThat(runner.remoteArgs()).containsExactly("--remote-cache")
    assertThat(tasks).hasSize(1)
    assertThat(tasks(0).taskDef()).isSameAs(taskDef)
    assertThat(tasks(0).tags()).containsExactly("fast", "native-image-safe")
    assertThat(followUpTasks).isEmpty()
    assertThat(runner.done()).isEqualTo("executed 1 task(s)")

    assertThat(logger.messages).containsExactly(
      "info:starting example.FastSpec",
      "debug:selector TestSelector(emits event)",
      "warn:completed example.FastSpec"
    )
    assertThat(eventHandler.events).hasSize(1)
    val event = eventHandler.events.get(0)
    assertThat(event.fullyQualifiedName()).isEqualTo("example.FastSpec")
    assertThat(event.fingerprint()).isSameAs(fingerprint)
    assertThat(event.selector()).isEqualTo(new TestSelector("emits event"))
    assertThat(event.status()).isEqualTo(Status.Success)
    assertThat(event.throwable().isEmpty()).isTrue()
    assertThat(event.duration()).isEqualTo(42L)
  }

  @Test
  def modernTasksCanReturnFollowUpTasksForIncrementalExecution(): Unit = {
    val fingerprint = ModernSubclassFingerprint(
      module = false,
      superclass = "example.ParentSuite",
      requiresNoArgConstructor = true
    )
    val parentDefinition = new TaskDef(
      "example.ParentSuite",
      fingerprint,
      true,
      Array[Selector](new SuiteSelector())
    )
    val childDefinition = new TaskDef(
      "example.ParentSuite.child",
      fingerprint,
      false,
      Array[Selector](new NestedSuiteSelector("child-suite"))
    )
    val childFailure = new AssertionError("child failed")
    val childTask = new FollowUpModernTask(
      childDefinition,
      Status.Failure,
      new OptionalThrowable(childFailure),
      Array.empty[Task]
    )
    val parentTask = new FollowUpModernTask(
      parentDefinition,
      Status.Success,
      new OptionalThrowable(),
      Array[Task](childTask)
    )
    val eventHandler = new RecordingModernEventHandler()
    val logger = new RecordingLogger(ansi = false)

    val followUpTasks = parentTask.execute(eventHandler, Array[Logger](logger))
    val finalTasks = followUpTasks(0).execute(eventHandler, Array[Logger](logger))

    assertThat(followUpTasks).containsExactly(childTask)
    assertThat(followUpTasks(0).taskDef()).isSameAs(childDefinition)
    assertThat(finalTasks).isEmpty()

    assertThat(eventHandler.events).hasSize(2)
    assertThat(eventHandler.events.get(0).fullyQualifiedName()).isEqualTo("example.ParentSuite")
    assertThat(eventHandler.events.get(0).status()).isEqualTo(Status.Success)
    assertThat(eventHandler.events.get(0).throwable().isEmpty()).isTrue()
    assertThat(eventHandler.events.get(1).fullyQualifiedName()).isEqualTo("example.ParentSuite.child")
    assertThat(eventHandler.events.get(1).status()).isEqualTo(Status.Failure)
    assertThat(eventHandler.events.get(1).selector()).isEqualTo(new NestedSuiteSelector("child-suite"))
    assertThat(eventHandler.events.get(1).throwable().get()).isSameAs(childFailure)
    assertThat(logger.messages).containsExactly(
      "info:executing example.ParentSuite",
      "info:executing example.ParentSuite.child"
    )
  }

  @Test
  def legacyFrameworkRunner2BridgesTestFingerprintRunToGeneralFingerprintRun(): Unit = {
    val fingerprint = LegacyTestFingerprint(module = false, superclass = "legacy.BaseSuite")
    val logger = new RecordingLegacyLogger(ansi = false)
    val framework = new RecordingLegacyFramework(Array[org.scalatools.testing.Fingerprint](fingerprint), Array(logger))
    val eventHandler = new RecordingLegacyEventHandler()
    val runner = framework.testRunner(getClass.getClassLoader, Array[org.scalatools.testing.Logger](logger))

    runner.run("legacy.CalculatorSuite", fingerprint, eventHandler, Array("--include", "fast"))

    assertThat(framework.name()).isEqualTo("recording-legacy")
    assertThat(framework.tests()).containsExactly(fingerprint)
    assertThat(fingerprint.isModule()).isFalse()
    assertThat(fingerprint.superClassName()).isEqualTo("legacy.BaseSuite")
    assertThat(eventHandler.events).hasSize(1)
    assertThat(eventHandler.events.get(0).testName()).isEqualTo("legacy.CalculatorSuite")
    assertThat(eventHandler.events.get(0).description()).isEqualTo("legacy.CalculatorSuite --include fast")
    assertThat(eventHandler.events.get(0).result()).isEqualTo(LegacyResult.Success)
    assertThat(eventHandler.events.get(0).error()).isNull()
    assertThat(logger.messages).containsExactly(
      "info:legacy.CalculatorSuite",
      "debug:--include,fast"
    )
  }

  @Test
  def legacyAnnotatedFingerprintAndResultsExposeOriginalInterfaceContracts(): Unit = {
    val annotated = LegacyAnnotatedFingerprint(module = true, annotation = "legacy.Slow")

    assertThat(annotated.isModule()).isTrue()
    assertThat(annotated.annotationName()).isEqualTo("legacy.Slow")
    assertThat(LegacyResult.values()).containsExactly(
      LegacyResult.Success,
      LegacyResult.Error,
      LegacyResult.Failure,
      LegacyResult.Skipped
    )
    assertThat(LegacyResult.valueOf("Skipped")).isEqualTo(LegacyResult.Skipped)
  }

  @Test
  def legacyRunnerExecutesTestFingerprintDirectlyAndReportsFailures(): Unit = {
    val fingerprint = LegacyTestFingerprint(module = true, superclass = "legacy.DirectBase")
    val failure = new IllegalStateException("direct runner failed")
    val logger = new RecordingLegacyLogger(ansi = true)
    val runner: org.scalatools.testing.Runner = new DirectLegacyRunner(
      Array[org.scalatools.testing.Logger](logger),
      LegacyResult.Failure,
      failure
    )
    val eventHandler = new RecordingLegacyEventHandler()

    runner.run("legacy.DirectSuite", fingerprint, eventHandler, Array("--test", "direct path"))

    assertThat(fingerprint.isModule()).isTrue()
    assertThat(fingerprint.superClassName()).isEqualTo("legacy.DirectBase")
    assertThat(logger.ansiCodesSupported()).isTrue()
    assertThat(logger.messages).containsExactly(
      "warn:legacy.DirectSuite uses legacy.DirectBase",
      "trace:java.lang.IllegalStateException:direct runner failed"
    )
    assertThat(eventHandler.events).hasSize(1)
    assertThat(eventHandler.events.get(0).testName()).isEqualTo("legacy.DirectSuite")
    assertThat(eventHandler.events.get(0).description()).isEqualTo("legacy.DirectSuite --test direct path")
    assertThat(eventHandler.events.get(0).result()).isEqualTo(LegacyResult.Failure)
    assertThat(eventHandler.events.get(0).error()).isSameAs(failure)
  }

  private final case class ModernAnnotatedFingerprint(module: Boolean, annotation: String) extends AnnotatedFingerprint {
    override def isModule(): Boolean = module

    override def annotationName(): String = annotation
  }

  private final case class ModernSubclassFingerprint(
    module: Boolean,
    superclass: String,
    requiresNoArgConstructor: Boolean
  ) extends SubclassFingerprint {
    override def isModule(): Boolean = module

    override def superclassName(): String = superclass

    override def requireNoArgConstructor(): Boolean = requiresNoArgConstructor
  }

  private final class RecordingModernFramework(discoveredFingerprints: Array[Fingerprint]) extends Framework {
    override def name(): String = "recording-modern"

    override def fingerprints(): Array[Fingerprint] = discoveredFingerprints

    override def runner(args: Array[String], remoteArgs: Array[String], testClassLoader: ClassLoader): Runner =
      new RecordingModernRunner(args, remoteArgs)
  }

  private final class RecordingModernRunner(
    runnerArgs: Array[String],
    runnerRemoteArgs: Array[String]
  ) extends Runner {
    private var executedTasks: Int = 0

    override def tasks(taskDefs: Array[TaskDef]): Array[Task] = taskDefs.map(new RecordingModernTask(_, markExecuted))

    override def done(): String = s"executed $executedTasks task(s)"

    override def remoteArgs(): Array[String] = runnerRemoteArgs

    override def args(): Array[String] = runnerArgs

    private def markExecuted(): Unit = {
      executedTasks += 1
    }
  }

  private final class RecordingModernTask(definition: TaskDef, markExecuted: () => Unit) extends Task {
    override def tags(): Array[String] = Array("fast", "native-image-safe")

    override def execute(eventHandler: EventHandler, loggers: Array[Logger]): Array[Task] = {
      loggers.foreach { logger =>
        logger.info(s"starting ${definition.fullyQualifiedName()}")
        definition.selectors().foreach(selector => logger.debug(s"selector $selector"))
      }
      eventHandler.handle(ModernEvent(
        name = definition.fullyQualifiedName(),
        testFingerprint = definition.fingerprint(),
        testSelector = definition.selectors().head,
        testStatus = Status.Success,
        testThrowable = new OptionalThrowable(),
        testDuration = 42L
      ))
      loggers.foreach(_.warn(s"completed ${definition.fullyQualifiedName()}"))
      markExecuted()
      Array.empty[Task]
    }

    override def taskDef(): TaskDef = definition
  }

  private final class FollowUpModernTask(
    definition: TaskDef,
    result: Status,
    failure: OptionalThrowable,
    followUpTasks: Array[Task]
  ) extends Task {
    override def tags(): Array[String] = Array.empty[String]

    override def execute(eventHandler: EventHandler, loggers: Array[Logger]): Array[Task] = {
      loggers.foreach(_.info(s"executing ${definition.fullyQualifiedName()}"))
      eventHandler.handle(ModernEvent(
        name = definition.fullyQualifiedName(),
        testFingerprint = definition.fingerprint(),
        testSelector = definition.selectors().head,
        testStatus = result,
        testThrowable = failure,
        testDuration = 7L
      ))
      followUpTasks
    }

    override def taskDef(): TaskDef = definition
  }

  private final case class ModernEvent(
    name: String,
    testFingerprint: Fingerprint,
    testSelector: Selector,
    testStatus: Status,
    testThrowable: OptionalThrowable,
    testDuration: Long
  ) extends Event {
    override def fullyQualifiedName(): String = name

    override def fingerprint(): Fingerprint = testFingerprint

    override def selector(): Selector = testSelector

    override def status(): Status = testStatus

    override def throwable(): OptionalThrowable = testThrowable

    override def duration(): Long = testDuration
  }

  private final class RecordingModernEventHandler extends EventHandler {
    val events: ArrayList[Event] = new ArrayList[Event]()

    override def handle(event: Event): Unit = events.add(event)
  }

  private final class RecordingLogger(ansi: Boolean) extends Logger {
    val messages: ArrayList[String] = new ArrayList[String]()

    override def ansiCodesSupported(): Boolean = ansi

    override def error(message: String): Unit = messages.add(s"error:$message")

    override def warn(message: String): Unit = messages.add(s"warn:$message")

    override def info(message: String): Unit = messages.add(s"info:$message")

    override def debug(message: String): Unit = messages.add(s"debug:$message")

    override def trace(throwable: Throwable): Unit = messages.add(s"trace:${throwable.getClass.getName}:${throwable.getMessage}")
  }

  private final case class LegacyAnnotatedFingerprint(module: Boolean, annotation: String)
      extends org.scalatools.testing.AnnotatedFingerprint {
    override def isModule(): Boolean = module

    override def annotationName(): String = annotation
  }

  private final case class LegacyTestFingerprint(module: Boolean, superclass: String)
      extends org.scalatools.testing.TestFingerprint {
    override def isModule(): Boolean = module

    override def superClassName(): String = superclass
  }

  private final class RecordingLegacyFramework(
    discoveredFingerprints: Array[org.scalatools.testing.Fingerprint],
    initialLoggers: Array[org.scalatools.testing.Logger]
  ) extends org.scalatools.testing.Framework {
    override def name(): String = "recording-legacy"

    override def tests(): Array[org.scalatools.testing.Fingerprint] = discoveredFingerprints

    override def testRunner(
      testClassLoader: ClassLoader,
      loggers: Array[org.scalatools.testing.Logger]
    ): org.scalatools.testing.Runner = new RecordingLegacyRunner(if (loggers.nonEmpty) loggers else initialLoggers)
  }

  private final class RecordingLegacyRunner(loggers: Array[org.scalatools.testing.Logger])
      extends org.scalatools.testing.Runner2 {
    override def run(
      testClassName: String,
      fingerprint: org.scalatools.testing.Fingerprint,
      eventHandler: org.scalatools.testing.EventHandler,
      args: Array[String]
    ): Unit = {
      loggers.foreach { logger =>
        logger.info(testClassName)
        logger.debug(args.mkString(","))
      }
      eventHandler.handle(LegacyEvent(
        testNameValue = testClassName,
        descriptionValue = s"$testClassName ${args.mkString(" ")}",
        resultValue = LegacyResult.Success,
        errorValue = null
      ))
    }
  }

  private final class DirectLegacyRunner(
    loggers: Array[org.scalatools.testing.Logger],
    result: LegacyResult,
    failure: Throwable
  ) extends org.scalatools.testing.Runner {
    override def run(
      testClassName: String,
      fingerprint: org.scalatools.testing.TestFingerprint,
      eventHandler: org.scalatools.testing.EventHandler,
      args: Array[String]
    ): Unit = {
      loggers.foreach { logger =>
        logger.warn(s"$testClassName uses ${fingerprint.superClassName()}")
        logger.trace(failure)
      }
      eventHandler.handle(LegacyEvent(
        testNameValue = testClassName,
        descriptionValue = s"$testClassName ${args.mkString(" ")}",
        resultValue = result,
        errorValue = failure
      ))
    }
  }

  private final case class LegacyEvent(
    testNameValue: String,
    descriptionValue: String,
    resultValue: LegacyResult,
    errorValue: Throwable
  ) extends org.scalatools.testing.Event {
    override def testName(): String = testNameValue

    override def description(): String = descriptionValue

    override def result(): LegacyResult = resultValue

    override def error(): Throwable = errorValue
  }

  private final class RecordingLegacyEventHandler extends org.scalatools.testing.EventHandler {
    val events: ArrayList[org.scalatools.testing.Event] = new ArrayList[org.scalatools.testing.Event]()

    override def handle(event: org.scalatools.testing.Event): Unit = events.add(event)
  }

  private final class RecordingLegacyLogger(ansi: Boolean) extends org.scalatools.testing.Logger {
    val messages: ArrayList[String] = new ArrayList[String]()

    override def ansiCodesSupported(): Boolean = ansi

    override def error(message: String): Unit = messages.add(s"error:$message")

    override def warn(message: String): Unit = messages.add(s"warn:$message")

    override def info(message: String): Unit = messages.add(s"info:$message")

    override def debug(message: String): Unit = messages.add(s"debug:$message")

    override def trace(throwable: Throwable): Unit = messages.add(s"trace:${throwable.getClass.getName}:${throwable.getMessage}")
  }
}
