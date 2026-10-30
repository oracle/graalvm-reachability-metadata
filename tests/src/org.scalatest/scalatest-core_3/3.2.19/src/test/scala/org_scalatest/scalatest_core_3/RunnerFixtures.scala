/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_core_3

import java.nio.file.{Files, Path}
import java.util.concurrent.CopyOnWriteArrayList
import java.util.jar.{JarEntry, JarOutputStream}
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.scalatest.{Args, Reporter, Suite, WrapWith}
import org.scalatest.events.{Event, TestSucceeded}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.tools.Runner
import scala.jdk.CollectionConverters.*

class PassingSuite extends AnyFunSuite {
  test("adds two values") {
    assertEquals(9, 4 + 5)
  }
  test("joins two words") {
    assertEquals("reachability metadata", Seq("reachability", "metadata").mkString(" "))
  }
}

@WrapWith(classOf[ConfiguringSuite])
class WrappedScenario

class ConfiguringSuite(scenario: Class[?]) extends AnyFunSuite {
  test("receives the wrapped scenario") {
    assertEquals(classOf[WrappedScenario], scenario)
  }
}

class CollectingReporter extends Reporter {
  override def apply(event: Event): Unit = CollectingReporter.events.add(event)
}

object CollectingReporter {
  val events: CopyOnWriteArrayList[Event] = new CopyOnWriteArrayList[Event]()
}

class RecordingReporter extends Reporter {
  private val received: CopyOnWriteArrayList[Event] = new CopyOnWriteArrayList[Event]()
  override def apply(event: Event): Unit = received.add(event)
  def events: Vector[Event] = received.asScala.toVector
}

object RunnerFixtures {
  def run(suite: Class[?], extra: String*): Vector[Event] = {
    CollectingReporter.events.clear()
    assertTrue(Runner.run(Array("-C", classOf[CollectingReporter].getName,
      "-s", suite.getName) ++ extra))
    CollectingReporter.events.asScala.toVector
  }

  def discover(suites: Seq[Class[?]], extra: String*): Vector[Event] = {
    val jar: Path = Files.createTempFile("scalatest-discovery-", ".jar")
    try {
      val entries: JarOutputStream = new JarOutputStream(Files.newOutputStream(jar))
      try {
        // Discovery reads entry names; implementations are already on the application classpath.
        suites.foreach { suite =>
          entries.putNextEntry(new JarEntry(suite.getName.replace('.', '/') + ".class"))
          entries.closeEntry()
        }
      } finally entries.close()
      CollectingReporter.events.clear()
      assertTrue(Runner.run(Array("-R", jar.toString, "-C",
        classOf[CollectingReporter].getName) ++ extra))
      CollectingReporter.events.asScala.toVector
    } finally Files.deleteIfExists(jar)
  }

  def succeeded(events: Seq[Event]): Set[String] =
    events.collect { case event: TestSucceeded => event.testName }.toSet

  def runSuite(suite: Suite): RecordingReporter = {
    val reporter: RecordingReporter = new RecordingReporter
    val status = suite.run(None, Args(reporter))
    assertTrue(status.isCompleted())
    assertTrue(status.succeeds())
    reporter
  }
}
