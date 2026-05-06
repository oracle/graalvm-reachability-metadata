/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_shouldmatchers_2_13

import java.util

import scala.util.Success
import scala.util.Try

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.scalatest.exceptions.TestFailedException
import org.scalatest.matchers.MatchResult
import org.scalatest.matchers.Matcher
import org.scalatest.matchers.should.Matchers

class Scalatest_shouldmatchers_2_13Test extends Matchers {
  @Test
  def matchesEqualityOrderingTypesAndNumericTolerance(): Unit = {
    val computedTotal: Int = List(2, 3, 5).sum
    val observedRatio: Double = 22.0 / 7.0
    val thrown: Throwable = new IllegalArgumentException("invalid input")

    computedTotal shouldBe 10
    computedTotal should (be >= 10 and be < 20)
    computedTotal should not be 11
    observedRatio shouldBe (3.142 +- 0.001)
    Vector(1, 2, 3, 4) shouldBe sorted
    thrown shouldBe a[RuntimeException]
  }

  @Test
  def matchesStringsWithSubstringPrefixSuffixAndRegexGroups(): Unit = {
    val message: String = "order-123: shipped to Vienna"

    message should startWith("order-")
    message should include("shipped")
    message should endWith("Vienna")
    "order-123" should fullyMatch regex ("order-(\\d+)" withGroup "123")
    "scala" should not fullyMatch regex("java-[0-9]+")
  }

  @Test
  def matchesScalaCollectionsWithElementAndSequenceConstraints(): Unit = {
    val colors: List[String] = List("red", "green", "blue", "green")
    val orderedNumbers: Vector[Int] = Vector(1, 2, 3, 4, 5)

    colors should contain("red")
    colors should contain allOf("red", "blue")
    colors should contain atLeastOneOf("purple", "green")
    colors should contain noneOf("black", "white")
    colors should contain only("red", "green", "blue")
    orderedNumbers should contain inOrder(2, 3, 5)
    orderedNumbers should contain theSameElementsAs Vector(5, 4, 3, 2, 1)
    orderedNumbers should have length 5
    colors.toSet should have size 3
  }

  @Test
  def matchesOptionsArraysMapsAndJavaCollections(): Unit = {
    val configured: Option[String] = Some("enabled")
    val absent: Option[String] = None
    val byteValues: Array[Byte] = Array[Byte](1, 2, 3)
    val scalaMap: Map[String, Int] = Map("http" -> 80, "https" -> 443)
    val javaList: util.ArrayList[String] = new util.ArrayList[String]()
    javaList.add("alpha")
    javaList.add("beta")
    val javaMap: util.HashMap[String, Int] = new util.HashMap[String, Int]()
    javaMap.put("retries", 3)
    javaMap.put("timeoutSeconds", 5)

    configured shouldBe defined
    configured should contain("enabled")
    absent shouldBe empty
    byteValues should contain theSameElementsInOrderAs Array[Byte](1, 2, 3)
    scalaMap should contain key "https"
    scalaMap should contain value 80
    javaList should contain("beta")
    javaList should have length 2
    javaMap should contain key "retries"
    javaMap should contain value 5
  }

  @Test
  def appliesMatchersAcrossCollectionsWithQuantifiers(): Unit = {
    val evenNumbers: List[Int] = List(2, 4, 6, 8)
    val statusLines: List[String] = List("200 OK", "201 CREATED", "500 ERROR")

    all(evenNumbers) should be < 10
    all(evenNumbers.map(_ % 2)) shouldBe 0
    atLeast(2, evenNumbers) should be > 4
    atMost(1, statusLines) should include("ERROR")
    exactly(2, statusLines) should startWith("20")
    no(statusLines) should endWith("PENDING")
  }

  @Test
  def matchesValuesWithPatternMatching(): Unit = {
    val parsedNumber: Try[Int] = Try("42".toInt)
    val response: Either[String, (String, Int)] = Right(("created", 201))
    val measurements: Vector[Int] = Vector(1, 2, 3, 5)

    parsedNumber should matchPattern { case Success(42) => }
    response should matchPattern { case Right(("created", status: Int)) if status >= 200 && status < 300 => }
    measurements should matchPattern { case Vector(1, 2, _*) => }
  }

  @Test
  def supportsCustomPublicMatchersAndNegation(): Unit = {
    val bePalindrome: Matcher[String] = Matcher { (left: String) =>
      val normalized: String = left.filter(_.isLetterOrDigit).toLowerCase
      MatchResult(
        normalized == normalized.reverse,
        s"$left was not a palindrome",
        s"$left was a palindrome"
      )
    }

    "Never odd or even" should bePalindrome
    "ScalaTest" should not(bePalindrome)
  }

  @Test
  def reportsMatcherFailuresAsTestFailedExceptions(): Unit = {
    val failure: TestFailedException = expectMatcherFailure {
      List(1, 2, 3) should contain only(1, 2)
    }

    failure.getMessage should not be empty
    failure.failedCodeFileName shouldBe Some("Scalatest_shouldmatchers_2_13Test.scala")
  }

  private def expectMatcherFailure(assertion: => Unit): TestFailedException =
    Assertions.assertThrows(classOf[TestFailedException], () => assertion)
}
