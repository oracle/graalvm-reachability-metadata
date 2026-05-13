/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_shouldmatchers_3

import java.util

import scala.util.Success
import scala.util.Try

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import org.scalatest.exceptions.TestFailedException
import org.scalatest.matchers.BeMatcher
import org.scalatest.matchers.BePropertyMatchResult
import org.scalatest.matchers.BePropertyMatcher
import org.scalatest.matchers.HavePropertyMatchResult
import org.scalatest.matchers.HavePropertyMatcher
import org.scalatest.matchers.MatchResult
import org.scalatest.matchers.Matcher
import org.scalatest.matchers.should.Matchers

final case class ServiceEndpoint(name: String, active: Boolean, uri: String, replicas: Int)

class Scalatest_shouldmatchers_3Test extends Matchers {
  @Test
  def shouldDslSupportsEqualityIdentityOrderingAndNumericTolerance(): Unit = {
    val serviceName: String = "reachability-metadata"
    val alias: String = serviceName
    val totalWeight: Int = List(2, 3, 5).sum
    val observedRatio: Double = 22.0 / 7.0
    val thrown: Throwable = new IllegalArgumentException("invalid input")

    serviceName shouldBe "reachability-metadata"
    serviceName should equal("reachability-metadata")
    alias should be theSameInstanceAs serviceName
    totalWeight should (be >= 10 and be < 20)
    totalWeight should not be 11
    observedRatio shouldBe (3.142 +- 0.001)
    Vector(1, 2, 3, 4) shouldBe sorted
    thrown shouldBe a[RuntimeException]

    val failure: TestFailedException = expectMatcherFailure {
      observedRatio shouldBe (3.0 +- 0.01)
    }
    failure.getMessage should include("3.142857")
    failure.getMessage should include("3.0")
  }

  @Test
  def shouldDslSupportsStringSubstringPrefixSuffixAndRegexMatchers(): Unit = {
    val message: String = "order-123: shipped to Vienna"

    message should startWith("order-")
    message should include("shipped")
    message should endWith("Vienna")
    message should (startWith("order") and include("Vienna"))
    message should (endWith("Vienna") or endWith("Graz"))
    message should not include ("cancelled")
    "order-123" should fullyMatch regex ("order-(\\d+)" withGroup "123")
    "status=201" should include regex ("\\d{3}")
    "scala" should not fullyMatch regex("java-[0-9]+")

    val failure: TestFailedException = expectMatcherFailure {
      message should startWith("invoice-")
    }
    failure.getMessage should include("invoice-")
  }

  @Test
  def shouldDslSupportsScalaCollectionsAndSequenceConstraints(): Unit = {
    val colors: List[String] = List("red", "green", "blue", "green")
    val orderedNumbers: Vector[Int] = Vector(1, 2, 3, 4, 5)

    colors should contain("red")
    colors should contain allOf ("red", "blue")
    colors should contain atLeastOneOf ("purple", "green")
    colors should contain noneOf ("black", "white")
    colors should contain only ("red", "green", "blue")
    orderedNumbers should contain inOrder (2, 3, 5)
    orderedNumbers should contain inOrderOnly (1, 2, 3, 4, 5)
    orderedNumbers should contain theSameElementsAs Vector(5, 4, 3, 2, 1)
    orderedNumbers should have length 5
    colors.toSet should have size 3

    val failure: TestFailedException = expectMatcherFailure {
      orderedNumbers should contain only (1, 2, 3)
    }
    failure.getMessage should include("4")
    failure.getMessage should include("5")
  }

  @Test
  def shouldDslSupportsOptionsArraysMapsAndJavaCollections(): Unit = {
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
    scalaMap should contain key ("https")
    scalaMap should contain value (80)
    scalaMap should contain("http" -> 80)
    javaList should contain("beta")
    javaList should have length 2
    javaMap should contain key ("retries")
    javaMap should contain value (5)
  }

  @Test
  def shouldDslSupportsCollectionInspectorsAndQuantifiers(): Unit = {
    val evenNumbers: List[Int] = List(2, 4, 6, 8)
    val statusLines: List[String] = List("200 OK", "201 CREATED", "500 ERROR")

    all(evenNumbers) should be < 10
    all(evenNumbers.map(_ % 2)) shouldBe 0
    atLeast(2, evenNumbers) should be > 4
    atMost(1, statusLines) should include("ERROR")
    exactly(2, statusLines) should startWith("20")
    between(2, 3, evenNumbers) should be >= 4
    every(List("agent", "builder", "runner")) should fullyMatch regex ("[a-z]+")
    no(statusLines) should endWith("PENDING")

    val failure: TestFailedException = expectMatcherFailure {
      all(evenNumbers) should be < 8
    }
    failure.getMessage should include("8")
  }

  @Test
  def shouldDslSupportsPatternTypeAndPartialFunctionDefinedAtMatchers(): Unit = {
    val parsedNumber: Try[Int] = Try("42".toInt)
    val response: Either[String, (String, Int)] = Right(("created", 201))
    val endpoint: ServiceEndpoint = ServiceEndpoint(
      name = "metadata",
      active = true,
      uri = "https://metadata.example.test",
      replicas = 3
    )
    val routes: PartialFunction[String, ServiceEndpoint] = {
      case route if route.startsWith("/services/") =>
        val serviceName: String = route.stripPrefix("/services/")
        ServiceEndpoint(serviceName, active = true, s"https://$serviceName.example.test", replicas = 2)
    }

    parsedNumber should matchPattern { case Success(42) => }
    response should matchPattern { case Right(("created", status: Int)) if status >= 200 && status < 300 => }
    endpoint shouldBe a[ServiceEndpoint]
    endpoint.uri shouldBe a[String]
    endpoint should not be a[String]
    endpoint should matchPattern {
      case ServiceEndpoint("metadata", true, uri, replicas)
        if uri.startsWith("https://") && replicas >= 3 =>
    }
    routes should be definedAt ("/services/metadata")
    routes should not be definedAt ("/health")
    routes("/services/search").name shouldBe "search"
  }

  @Test
  def shouldDslSupportsExceptionMatchersAndMessageAssertions(): Unit = {
    an[IllegalArgumentException] should be thrownBy {
      parsePositive("-4")
    }
    noException should be thrownBy {
      parsePositive("12")
    }

    val failure: IllegalArgumentException = the[IllegalArgumentException] thrownBy {
      throw new IllegalArgumentException("invalid port", new NumberFormatException("not numeric"))
    }

    failure should have message "invalid port"
    failure.getCause shouldBe a[NumberFormatException]

    val matcherFailure: TestFailedException = expectMatcherFailure {
      noException should be thrownBy {
        parsePositive("0")
      }
    }
    matcherFailure.getMessage should include("IllegalArgumentException")
  }

  @Test
  def customMatchersIntegrateWithShouldSyntaxAndExposeMatchResults(): Unit = {
    val palindrome: Matcher[String] = Matcher { (left: String) =>
      val normalized: String = left.filter(_.isLetterOrDigit).toLowerCase
      MatchResult(
        normalized == normalized.reverse,
        s"$left was not a palindrome",
        s"$left was a palindrome"
      )
    }
    val stableIdentifier: BeMatcher[String] = BeMatcher { (value: String) =>
      MatchResult(
        value.matches("[a-z][a-z0-9_]*"),
        s"$value was not a stable identifier",
        s"$value was a stable identifier"
      )
    }

    "Never odd or even" should palindrome
    "ScalaTest" should not(palindrome)
    "worker_17" should be(stableIdentifier)
    "Worker-17" should not be (stableIdentifier)

    val directResult: MatchResult = palindrome("not one")
    directResult.matches shouldBe false
    directResult.rawFailureMessage shouldBe "not one was not a palindrome"

    val failure: TestFailedException = expectMatcherFailure {
      "metadata" should palindrome
    }
    failure.getMessage should include("metadata was not a palindrome")
  }

  @Test
  def propertyMatchersIntegrateWithShouldBeAndShouldHaveSyntax(): Unit = {
    val active: BePropertyMatcher[ServiceEndpoint] = new BePropertyMatcher[ServiceEndpoint] {
      override def apply(left: ServiceEndpoint): BePropertyMatchResult = {
        BePropertyMatchResult(left.active, "active")
      }
    }
    val uri: HavePropertyMatcher[ServiceEndpoint, String] = new HavePropertyMatcher[ServiceEndpoint, String] {
      override def apply(left: ServiceEndpoint): HavePropertyMatchResult[String] = {
        HavePropertyMatchResult(
          left.uri == "https://metadata.example.test",
          "uri",
          "https://metadata.example.test",
          left.uri
        )
      }
    }
    val replicas: HavePropertyMatcher[ServiceEndpoint, Int] = new HavePropertyMatcher[ServiceEndpoint, Int] {
      override def apply(left: ServiceEndpoint): HavePropertyMatchResult[Int] = {
        HavePropertyMatchResult(left.replicas == 3, "replicas", 3, left.replicas)
      }
    }
    val healthyService: ServiceEndpoint = ServiceEndpoint(
      name = "reachability-metadata",
      active = true,
      uri = "https://metadata.example.test",
      replicas = 3
    )

    healthyService should be(active)
    healthyService should have(uri)
    healthyService should have(replicas)
    healthyService should have(uri, replicas)

    val failure: TestFailedException = expectMatcherFailure {
      healthyService.copy(replicas = 2) should have(replicas)
    }
    failure.getMessage should include("replicas")
    failure.getMessage should include("3")
    failure.getMessage should include("2")
  }

  private def parsePositive(input: String): Int = {
    val parsed: Int = input.toInt
    if (parsed <= 0) {
      throw new IllegalArgumentException(s"value must be positive: $input")
    }
    parsed
  }

  private def expectMatcherFailure(assertion: => Unit): TestFailedException = {
    Assertions.assertThrows(
      classOf[TestFailedException],
      new Executable {
        override def execute(): Unit = assertion
      }
    )
  }
}
