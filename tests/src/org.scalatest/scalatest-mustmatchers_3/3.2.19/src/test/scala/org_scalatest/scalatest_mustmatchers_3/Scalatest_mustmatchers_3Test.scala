/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_mustmatchers_3

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.Test
import org.scalactic.Equality
import org.scalatest.exceptions.TestFailedException
import org.scalatest.matchers.{MatchResult, Matcher}
import org.scalatest.matchers.must.Matchers

import java.util.ArrayList

class Scalatest_mustmatchers_3Test extends Matchers {
  @Test
  def checksEqualityNumericToleranceAndOrderingMatchers(): Unit = {
    val exactCount: Int = 12
    exactCount mustBe 12
    exactCount mustEqual 12
    exactCount must be >= 10
    exactCount must be < 20

    val measuredDistance: Double = 10.004
    measuredDistance mustBe 10.0 +- 0.01
    measuredDistance mustEqual 10.0 +- 0.01

    Array(1, 2, 3) must be (Array(1, 2, 3))
  }

  @Test
  def appliesCustomEqualityToMustEqualAndContain(): Unit = {
    given caseInsensitiveStringEquality: Equality[String] with
      override def areEqual(a: String, b: Any): Boolean =
        b match {
          case right: String => a.equalsIgnoreCase(right)
          case _ => false
        }

    "ScalaTest" mustEqual "scalatest"
    List("Native", "Image", "Matchers") must contain ("image")
  }

  @Test
  def checksCollectionContainmentCardinalityAndOrder(): Unit = {
    val numbers: Vector[Int] = Vector(1, 2, 3, 2)

    numbers must contain (2)
    numbers must contain allOf (1, 3)
    numbers must contain oneOf (0, 3, 5)
    numbers must contain noneOf (7, 8, 9)
    numbers must contain only (1, 2, 3)
    numbers must contain theSameElementsAs List(2, 1, 2, 3)
    numbers must contain theSameElementsInOrderAs List(1, 2, 3, 2)
    numbers must not contain (4)
  }

  @Test
  def checksMapsOptionsAndJavaCollections(): Unit = {
    val scores: Map[String, Int] = Map("alpha" -> 1, "beta" -> 2)
    scores must contain key ("alpha")
    scores must contain value (2)
    scores must contain ("beta" -> 2)

    Some("native-image") must contain ("native-image")
    Option.empty[String] mustBe empty

    val names: ArrayList[String] = new ArrayList[String]()
    names.add("graal")
    names.add("scala")
    names must contain ("scala")
    names must have size (2)
  }

  @Test
  def checksStringAndRegexMatchers(): Unit = {
    val message: String = "native image exercises scalatest matchers"

    message must startWith ("native")
    message must include ("scalatest")
    message must endWith ("matchers")
    message must fullyMatch regex ("native (\\w+) exercises scalatest (\\w+)" withGroups ("image", "matchers"))
    message must not include ("reflection-only")
  }

  @Test
  def checksInspectorQuantifiers(): Unit = {
    val readings: Vector[Int] = Vector(1, 2, 2, 3, 4)

    all (readings) must be > 0
    atLeast (3, readings) must be <= 2
    atMost (2, readings) must be >= 3
    exactly (2, readings) mustEqual 2
    no (readings) must be < 0
  }

  @Test
  def checksPartialFunctionDefinedAtMatcher(): Unit = {
    val routeStatuses: PartialFunction[String, Int] = {
      case "/health" => 200
      case path if path.startsWith("/assets/") => path.length
    }

    routeStatuses must be definedAt ("/health")
    routeStatuses must be definedAt ("/assets/app.js")
    routeStatuses must not be definedAt ("/metrics")

    val failure: TestFailedException = expectMatcherFailure {
      routeStatuses must be definedAt ("/metrics")
    }
    assertTrue(failure.getMessage.contains("was not defined at"))
    assertTrue(failure.getMessage.contains("/metrics"))
  }

  @Test
  def checksExceptionMatchers(): Unit = {
    an[IllegalArgumentException] must be thrownBy {
      Integer.parseInt("not-a-number")
    }

    val thrown: ArithmeticException = the[ArithmeticException] thrownBy {
      1 / 0
    }
    thrown.getMessage mustBe "/ by zero"
  }

  @Test
  def supportsCustomMatcherImplementations(): Unit = {
    val bePalindrome: Matcher[String] = Matcher { (value: String) =>
      MatchResult(
        value == value.reverse,
        s"$value was not a palindrome",
        s"$value was a palindrome"
      )
    }

    "radar" must bePalindrome

    val failure: TestFailedException = expectMatcherFailure {
      "scala" must bePalindrome
    }
    assertTrue(failure.getMessage.contains("scala was not a palindrome"))
  }

  @Test
  def reportsUsefulFailuresFromBuiltInMatchers(): Unit = {
    val failure: TestFailedException = expectMatcherFailure {
      List(1, 2) must contain (3)
    }

    assertTrue(failure.getMessage.contains("3"))
    assertTrue(failure.getMessage.contains("did not contain"))
    assertEquals(classOf[TestFailedException], failure.getClass)
  }

  private def expectMatcherFailure(assertion: => Unit): TestFailedException =
    try {
      assertion
      throw new AssertionError("Expected TestFailedException")
    } catch {
      case failure: TestFailedException => failure
    }
}
