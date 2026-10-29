/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalacheck.scalacheck_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.scalacheck.Prop
import org.scalacheck.Properties
import org.scalacheck.ScalaCheckFramework
import sbt.testing.Event
import sbt.testing.EventHandler
import sbt.testing.Logger
import sbt.testing.Runner
import sbt.testing.Status
import sbt.testing.SubclassFingerprint
import sbt.testing.SuiteSelector
import sbt.testing.Task
import sbt.testing.TaskDef
import sbt.testing.TestSelector

import scala.collection.mutable.ListBuffer

class PlatformTest {
  @Test
  def discoversAndExecutesPropertyClass(): Unit = {
    executeSuite(
      "org_scalacheck.scalacheck_3.ReversalProperties",
      false,
      "reversal.twice restores the input"
    )
  }

  @Test
  def discoversAndExecutesPropertySingleton(): Unit = {
    executeSuite(
      "org_scalacheck.scalacheck_3.AdditionProperties",
      true,
      "addition.order does not matter"
    )
  }

  private def executeSuite(suiteName: String, module: Boolean, propertyName: String): Unit = {
    val framework: ScalaCheckFramework = new ScalaCheckFramework
    val fingerprint: SubclassFingerprint = framework.fingerprints().collectFirst {
      case candidate: SubclassFingerprint
          if candidate.isModule() == module && candidate.superclassName() == "org.scalacheck.Properties" =>
        candidate
    }.getOrElse(throw new AssertionError("Missing Properties fingerprint"))
    val runner: Runner = framework.runner(
      Array("-minSuccessfulTests", "20", "-workers", "1"),
      Array.empty[String],
      getClass.getClassLoader
    )
    val definition: TaskDef = new TaskDef(suiteName, fingerprint, true, Array(new SuiteSelector))
    val events: ListBuffer[Event] = ListBuffer.empty[Event]
    val handler: EventHandler = new EventHandler {
      override def handle(event: Event): Unit = {
        events += event
      }
    }
    val loggers: Array[Logger] = Array.empty[Logger]
    val suites: Array[Task] = runner.tasks(Array(definition))

    assertEquals(1, suites.length)
    assertEquals(suiteName, suites.head.taskDef().fullyQualifiedName())
    val properties: Array[Task] = suites.head.execute(handler, loggers)
    assertEquals(1, properties.length)
    assertTrue(events.isEmpty)
    assertTrue(properties.head.execute(handler, loggers).isEmpty)

    assertEquals(1, events.size)
    val event: Event = events.head
    assertEquals(suiteName, event.fullyQualifiedName())
    assertEquals(Status.Success, event.status())
    assertEquals(propertyName, event.selector().asInstanceOf[TestSelector].testName())
    assertFalse(event.throwable().isDefined())
    assertEquals("Passed: Total 1, Failed 0, Errors 0, Passed 1", runner.done())
  }
}

class ReversalProperties extends Properties("reversal") {
  property("twice restores the input") = Prop.forAll { (values: List[Int]) =>
    values.reverse.reverse == values
  }
}

object AdditionProperties extends Properties("addition") {
  property("order does not matter") = Prop.forAll { (left: Int, right: Int) =>
    left + right == right + left
  }
}
