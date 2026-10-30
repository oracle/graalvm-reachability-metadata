/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_core_3

import java.nio.file.{Files, Path}
import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import org.scalatest.events.{RunCompleted, RunStarting}

@Timeout(120)
class RunnerTest {
  @Test
  def runsANamedSuiteAndSelectsAnIndividualTest(): Unit = {
    val events = RunnerFixtures.run(classOf[PassingSuite], "-t", "adds two values")
    assertEquals(Set("adds two values"), RunnerFixtures.succeeded(events))
    assertEquals(1, events.collectFirst { case e: RunStarting => e.testCount }.get)
    assertTrue(events.last.isInstanceOf[RunCompleted])
  }

  @Test
  def constructsTheAnnotatedWrapperForANamedScenario(): Unit = {
    val events = RunnerFixtures.run(classOf[WrappedScenario])
    assertEquals(Set("receives the wrapped scenario"), RunnerFixtures.succeeded(events))
  }

  @Test
  def runsAJUnitClassThroughTheCommandLineBridge(): Unit = {
    val events = RunnerFixtures.runArguments("-j", classOf[JUnitInvoiceScenario].getName)
    val succeeded: Set[String] = RunnerFixtures.succeeded(events)
    assertEquals(1, succeeded.size)
    assertTrue(succeeded.head.contains("sumsAnInvoice"))
    assertEquals(1, events.collectFirst { case event: RunStarting => event.testCount }.get)
    val completed: RunCompleted = events.last.asInstanceOf[RunCompleted]
    assertEquals(1, completed.summary.get.testsSucceededCount)
    assertEquals(0, completed.summary.get.testsFailedCount)
  }

  @Test
  def runsATestNGXmlSuiteThroughTheCommandLineBridge(): Unit = {
    val xml: Path = Files.createTempFile("scalatest-testng-", ".xml")
    try {
      Files.writeString(xml, s"""<?xml version="1.0" encoding="UTF-8"?>
        |<suite name="text processing" verbose="0">
        |  <test name="prepared words">
        |    <classes>
        |      <class name="${classOf[TestNGTextScenario].getName}"/>
        |    </classes>
        |  </test>
        |</suite>
        |""".stripMargin)
      val events = RunnerFixtures.runArguments("-b", xml.toString)
      assertEquals(Set("joinsPreparedWords"), RunnerFixtures.succeeded(events))
      val completed: RunCompleted = events.last.asInstanceOf[RunCompleted]
      assertEquals(1, completed.summary.get.testsSucceededCount)
      assertEquals(0, completed.summary.get.testsFailedCount)
    } finally Files.deleteIfExists(xml)
  }
}
