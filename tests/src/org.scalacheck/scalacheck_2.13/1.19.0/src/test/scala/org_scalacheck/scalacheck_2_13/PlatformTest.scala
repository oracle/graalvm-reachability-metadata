/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalacheck.scalacheck_2_13

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.scalacheck.Prop
import org.scalacheck.Properties
import org.scalacheck.ScalaCheckFramework
import sbt.testing.Event
import sbt.testing.EventHandler
import sbt.testing.Fingerprint
import sbt.testing.Logger
import sbt.testing.Selector
import sbt.testing.Status
import sbt.testing.SubclassFingerprint
import sbt.testing.Task
import sbt.testing.TaskDef

import scala.collection.mutable.ListBuffer

class PlatformTest {
  @Test
  def frameworkLoadsPropertiesModule(): Unit = {
    val statuses: Seq[Status] = runDiscoveredProperties(
      "org_scalacheck.scalacheck_2_13.PlatformModuleProperties",
      isModule = true
    )

    assertEquals(Seq(Status.Success), statuses)
  }

  @Test
  def frameworkInstantiatesPropertiesClass(): Unit = {
    val statuses: Seq[Status] = runDiscoveredProperties(
      "org_scalacheck.scalacheck_2_13.PlatformClassProperties",
      isModule = false
    )

    assertEquals(Seq(Status.Success), statuses)
  }

  private def runDiscoveredProperties(fullyQualifiedName: String, isModule: Boolean): Seq[Status] = {
    val framework: ScalaCheckFramework = new ScalaCheckFramework
    val fingerprint: Fingerprint = propertiesFingerprint(framework, isModule)
    val loader: ClassLoader = getClass.getClassLoader
    val runner: sbt.testing.Runner = framework.runner(
      Array("-minSuccessfulTests", "1"),
      Array.empty[String],
      loader
    )
    val taskDef: TaskDef = new TaskDef(fullyQualifiedName, fingerprint, true, Array.empty[Selector])
    val rootTasks: Array[Task] = runner.tasks(Array(taskDef))
    val statuses: ListBuffer[Status] = ListBuffer.empty[Status]
    val handler: EventHandler = (event: Event) => statuses += event.status()

    assertEquals(1, rootTasks.length)
    val propertyTasks: Array[Task] = rootTasks.flatMap(_.execute(handler, Array(NoOpLogger)))
    assertEquals(1, propertyTasks.length)
    val remainingTasks: Array[Task] = propertyTasks.flatMap(_.execute(handler, Array(NoOpLogger)))

    assertTrue(remainingTasks.isEmpty)
    assertEquals("Passed: Total 1, Failed 0, Errors 0, Passed 1", runner.done())
    statuses.toSeq
  }

  private def propertiesFingerprint(framework: ScalaCheckFramework, isModule: Boolean): Fingerprint =
    framework.fingerprints().collectFirst {
      case fingerprint: SubclassFingerprint
          if fingerprint.isModule() == isModule && fingerprint.superclassName() == "org.scalacheck.Properties" =>
        fingerprint
    }.getOrElse(throw new AssertionError(s"No ScalaCheck Properties fingerprint found for module=$isModule"))
}

object NoOpLogger extends Logger {
  override def ansiCodesSupported(): Boolean = false

  override def error(msg: String): Unit = ()

  override def warn(msg: String): Unit = ()

  override def info(msg: String): Unit = ()

  override def debug(msg: String): Unit = ()

  override def trace(t: Throwable): Unit = ()
}

object PlatformModuleProperties extends Properties("platformModuleProperties") {
  property("passes") = Prop.proved
}

class PlatformClassProperties extends Properties("platformClassProperties") {
  property("passes") = Prop.proved
}
