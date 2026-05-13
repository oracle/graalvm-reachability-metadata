/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_diagrams_3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows as junitAssertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import org.scalatest.diagrams.DiagrammedExpr
import org.scalatest.diagrams.Diagrams
import org.scalatest.exceptions.TestCanceledException
import org.scalatest.exceptions.TestFailedException

class Scalatest_diagrams_3Test extends Diagrams:
  @Test
  def passingDiagramAssertionsEvaluateOrdinaryBooleanExpressions(): Unit =
    val numbers: Vector[Int] = Vector(2, 3, 5, 7)
    val label: String = "reachability-metadata"
    val calculator: Calculator = Calculator(40)

    assert(numbers.sum == 17)
    assert(numbers.filter(_ > 3).contains(7))
    assert(label.startsWith("reachability") && label.endsWith("metadata"))
    assert(calculator.incrementedBy(2) == 42, "calculator should add the requested delta")

  @Test
  def failingDiagramAssertionIncludesExpressionSourceAndCapturedValues(): Unit =
    val numbers: Vector[Int] = Vector(1, 2, 3)
    val requested: Int = 4

    val failure: TestFailedException = expectTestFailure {
      assert(numbers.contains(requested))
    }

    val message: String = failure.getMessage
    assertTrue(message.contains("numbers.contains(requested)"), message)
    assertTrue(message.contains("Vector(1, 2, 3)"), message)
    assertTrue(message.contains("4"), message)
    assertTrue(message.contains("false"), message)
    assertTrue(message.contains("|"), message)

  @Test
  def failingDiagramAssertionWithCluePreservesTheClueAndDiagramDetails(): Unit =
    val availableItems: Int = 2
    val requestedItems: Int = 5
    val clue: String = "inventory request should fit the available stock"

    val failure: TestFailedException = expectTestFailure {
      assert(requestedItems <= availableItems, clue)
    }

    val message: String = failure.getMessage
    assertTrue(message.contains(clue), message)
    assertTrue(message.contains("requestedItems <= availableItems"), message)
    assertTrue(message.contains("5"), message)
    assertTrue(message.contains("2"), message)
    assertTrue(message.contains("false"), message)
    assertTrue(message.contains("|"), message)

  @Test
  def failingDiagramAssertionRendersNestedSelectionsAndMethodApplications(): Unit =
    val calculator: Calculator = Calculator(6)
    val delta: Int = 5
    val expected: Int = 99

    val failure: TestFailedException = expectTestFailure {
      assert(calculator.incrementedBy(delta) == expected)
    }

    val message: String = failure.getMessage
    assertTrue(message.contains("calculator.incrementedBy(delta) == expected"), message)
    assertTrue(message.contains("Calculator(6)"), message)
    assertTrue(message.contains("11"), message)
    assertTrue(message.contains("5"), message)
    assertTrue(message.contains("99"), message)
    assertTrue(message.contains("false"), message)

  @Test
  def failingTripleEqualsDiagramAssertionRendersComparedValues(): Unit =
    val actual: Int = 3
    val expected: Int = 5

    assert(actual === 3)

    val failure: TestFailedException = expectTestFailure {
      assert(actual === expected)
    }

    val message: String = failure.getMessage
    assertTrue(message.contains("actual === expected"), message)
    assertTrue(message.contains("3"), message)
    assertTrue(message.contains("5"), message)
    assertTrue(message.contains("false"), message)
    assertTrue(message.contains("|"), message)

  @Test
  def failingDiagramAssumeThrowsCanceledExceptionWithDiagramDetails(): Unit =
    val serviceName: String = "metadata-service"
    val requestedPrefix: String = "native-image"

    val cancellation: TestCanceledException = expectTestCancellation {
      assume(serviceName.startsWith(requestedPrefix))
    }

    val message: String = cancellation.getMessage
    assertTrue(message.contains("serviceName.startsWith(requestedPrefix)"), message)
    assertTrue(message.contains("metadata-service"), message)
    assertTrue(message.contains("native-image"), message)
    assertTrue(message.contains("false"), message)

  @Test
  def diagrammedSimpleAndSelectExpressionsExposeAnchorValues(): Unit =
    val base: DiagrammedExpr[String] = DiagrammedExpr.simpleExpr("metadata", 2)
    val selected: DiagrammedExpr[Int] = DiagrammedExpr.selectExpr(base, 8, 12)

    assertEquals("metadata", base.value)
    assertEquals(2, base.anchor)
    assertEquals(List("AnchorValue(2,metadata)"), base.anchorValues.map(_.toString))

    assertEquals(8, selected.value)
    assertEquals(12, selected.anchor)
    assertEquals(
      List("AnchorValue(2,metadata)", "AnchorValue(12,8)"),
      selected.anchorValues.map(_.toString)
    )

  @Test
  def diagrammedApplyExpressionCombinesQualifierResultAndArguments(): Unit =
    val qualifier: DiagrammedExpr[String] = DiagrammedExpr.simpleExpr("scala", 1)
    val firstArgument: DiagrammedExpr[Int] = DiagrammedExpr.simpleExpr(3, 9)
    val secondArgument: DiagrammedExpr[Int] = DiagrammedExpr.simpleExpr(4, 14)
    val syntheticArgument: DiagrammedExpr[String] = DiagrammedExpr.simpleExpr("ignored", -1)
    val arguments: List[DiagrammedExpr[?]] = List(firstArgument, secondArgument, syntheticArgument)

    val applied: DiagrammedExpr[String] = DiagrammedExpr.applyExpr(qualifier, arguments, "scala-7", 20)
    val anchors: List[String] = applied.anchorValues.map(_.toString)

    assertEquals("scala-7", applied.value)
    assertEquals(20, applied.anchor)
    assertEquals("AnchorValue(1,scala)", anchors.head)
    assertTrue(anchors.contains("AnchorValue(20,scala-7)"), anchors.toString)
    assertTrue(anchors.contains("AnchorValue(9,3)"), anchors.toString)
    assertTrue(anchors.contains("AnchorValue(14,4)"), anchors.toString)
    assertFalse(anchors.exists(_.contains("ignored")), anchors.toString)

  @Test
  def byNameDiagrammedExpressionEvaluatesValueLazilyAndOnlyOnce(): Unit =
    var evaluations: Int = 0
    val expression: DiagrammedExpr[String] = DiagrammedExpr.byNameExpr(
      {
        evaluations += 1
        "computed-value"
      },
      31
    )

    assertEquals(31, expression.anchor)
    assertTrue(expression.anchorValues.isEmpty)
    assertEquals(0, evaluations)
    assertEquals("computed-value", expression.value)
    assertEquals("computed-value", expression.value)
    assertEquals(1, evaluations)

  @Test
  def useDiagramMarkerCanBeObtainedFromTrait(): Unit =
    assertNotNull(UseDiagram)

  private def expectTestFailure(block: => Unit): TestFailedException =
    junitAssertThrows(
      classOf[TestFailedException],
      new Executable:
        override def execute(): Unit = block
    )

  private def expectTestCancellation(block: => Unit): TestCanceledException =
    junitAssertThrows(
      classOf[TestCanceledException],
      new Executable:
        override def execute(): Unit = block
    )

  private final case class Calculator(base: Int):
    def incrementedBy(delta: Int): Int = base + delta
