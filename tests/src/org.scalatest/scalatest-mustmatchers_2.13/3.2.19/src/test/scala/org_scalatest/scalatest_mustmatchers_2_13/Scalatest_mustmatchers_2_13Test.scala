/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_mustmatchers_2_13

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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
import org.scalatest.matchers.must.Matchers

final case class ServiceStatus(name: String, active: Boolean, endpoint: String, replicas: Int)

class Scalatest_mustmatchers_2_13Test extends Matchers {
  @Test
  def mustDslSupportsEqualityIdentityAndNumericMatchers(): Unit = {
    val serviceName: String = "metadata-forge"
    val alias: String = serviceName
    val measuredLatency: Double = 98.6

    serviceName mustBe "metadata-forge"
    serviceName must equal("metadata-forge")
    alias must be theSameInstanceAs serviceName
    measuredLatency must be(98.7 +- 0.2)
    7 must be > 3
    7 must be >= 7
    7 must be < 11
    7 must be <= 7
    serviceName must not be empty

    val failure: TestFailedException = expectTestFailure {
      measuredLatency must be(100.0 +- 0.1)
    }
    assertTrue(failure.getMessage.contains("98.6"))
    assertTrue(failure.getMessage.contains("100.0"))
  }

  @Test
  def mustDslSupportsStringAndRegexMatchers(): Unit = {
    val description: String = "ScalaTest must matchers exercise integration paths"

    description must startWith("ScalaTest")
    description must endWith("paths")
    description must include("must matchers")
    description must fullyMatch regex ("ScalaTest must matchers exercise integration paths")
    description must include regex ("must [a-z]+ers")
    description must (startWith("Scala") and include("integration"))
    description must (endWith("paths") or endWith("routes"))
    description must not include ("deprecated")

    val failure: TestFailedException = expectTestFailure {
      description must startWith("JUnit")
    }
    assertTrue(failure.getMessage.contains("JUnit"))
  }

  @Test
  def mustDslSupportsCollectionArrayAndMapMatchers(): Unit = {
    val numbers: List[Int] = List(1, 2, 3, 4, 5)
    val orderedLabels: Vector[String] = Vector("discover", "exercise", "verify")
    val labelArray: Array[String] = Array("alpha", "beta", "gamma")
    val settings: Map[String, Int] = Map("port" -> 8080, "workers" -> 4)

    numbers must contain(3)
    numbers must contain allOf (1, 3, 5)
    numbers must contain atLeastOneOf (0, 4, 9)
    numbers must contain noneOf (7, 8, 9)
    numbers must contain theSameElementsAs List(5, 4, 3, 2, 1)
    numbers must contain inOrder (1, 2, 3)
    numbers must have size 5
    numbers must have length 5

    orderedLabels must contain inOrderOnly ("discover", "exercise", "verify")
    labelArray must contain theSameElementsInOrderAs Seq("alpha", "beta", "gamma")
    settings must contain key ("port")
    settings must contain value (4)
    settings must contain("workers" -> 4)

    val failure: TestFailedException = expectTestFailure {
      numbers must contain only (1, 2, 3)
    }
    assertTrue(failure.getMessage.contains("4"))
    assertTrue(failure.getMessage.contains("5"))
  }

  @Test
  def mustDslSupportsOptionEitherAndInspectionMatchers(): Unit = {
    val optionalPort: Option[Int] = Some(8443)
    val missingPort: Option[Int] = None
    val results: List[Either[String, Int]] = List(Right(1), Right(2), Right(3))
    val replicatedCounts: Vector[Int] = Vector(3, 4, 5, 6)

    optionalPort must contain(8443)
    missingPort mustBe empty
    results must contain only (Right(1), Right(2), Right(3))
    all(replicatedCounts) must be >= 3
    atLeast(2, replicatedCounts) must be > 4
    atMost(2, replicatedCounts) must be <= 4
    exactly(1, replicatedCounts) must be(6)
    between(2, 3, replicatedCounts) must be >= 5
    every(List("agent", "builder", "runner")) must fullyMatch regex ("[a-z]+")

    val failure: TestFailedException = expectTestFailure {
      all(replicatedCounts) must be < 6
    }
    assertTrue(failure.getMessage.contains("6"))
  }

  @Test
  def mustDslSupportsExceptionMatchers(): Unit = {
    an[IllegalArgumentException] must be thrownBy {
      parsePositive("-4")
    }
    noException must be thrownBy {
      parsePositive("12")
    }

    val failure: IllegalArgumentException = the[IllegalArgumentException] thrownBy {
      parsePositive("0")
    }
    failure must have message "value must be positive: 0"
  }

  @Test
  def mustDslSupportsPatternAndTypeMatchers(): Unit = {
    val metadataStatus: ServiceStatus = ServiceStatus(
      name = "metadata",
      active = true,
      endpoint = "https://metadata.example.test",
      replicas = 3
    )

    metadataStatus mustBe a [ServiceStatus]
    metadataStatus.endpoint mustBe a [String]
    metadataStatus must not be a [String]
    metadataStatus must matchPattern {
      case ServiceStatus("metadata", true, endpoint, replicas)
        if endpoint.startsWith("https://") && replicas >= 3 =>
    }

    val failure: TestFailedException = expectTestFailure {
      metadataStatus must matchPattern {
        case ServiceStatus("metrics", true, _, _) =>
      }
    }
    assertTrue(failure.getMessage.contains("ServiceStatus"))
    assertTrue(failure.getMessage.contains("did not match"))
  }

  @Test
  def mustDslSupportsPartialFunctionDefinedAtMatchers(): Unit = {
    val routeStatuses: PartialFunction[String, ServiceStatus] = {
      case route if route.startsWith("/services/") =>
        val serviceName: String = route.stripPrefix("/services/")
        ServiceStatus(
          name = serviceName,
          active = true,
          endpoint = s"https://$serviceName.example.test",
          replicas = 2
        )
    }

    routeStatuses must be definedAt ("/services/metadata")
    routeStatuses must not be definedAt ("/health")

    val metadataStatus: ServiceStatus = routeStatuses("/services/metadata")
    assertEquals("metadata", metadataStatus.name)
    assertTrue(metadataStatus.active)

    val failure: TestFailedException = expectTestFailure {
      routeStatuses must be definedAt ("/metrics")
    }
    assertTrue(failure.getMessage.contains("was not defined at"))
    assertTrue(failure.getMessage.contains("/metrics"))
  }

  @Test
  def customMatchersIntegrateWithMustSyntaxAndExposeMatchResults(): Unit = {
    val evenNumber: Matcher[Int] = Matcher { (value: Int) =>
      MatchResult(
        value % 2 == 0,
        s"$value was not even",
        s"$value was even"
      )
    }
    val stableIdentifier: BeMatcher[String] = BeMatcher { (value: String) =>
      MatchResult(
        value.matches("[a-z][a-z0-9_]*"),
        s"$value was not a stable identifier",
        s"$value was a stable identifier"
      )
    }

    12 must evenNumber
    "worker_17" must be(stableIdentifier)
    "Worker-17" must not be (stableIdentifier)

    val directResult: MatchResult = evenNumber(11)
    assertFalse(directResult.matches)
    assertEquals("11 was not even", directResult.rawFailureMessage)

    val failure: TestFailedException = expectTestFailure {
      11 must evenNumber
    }
    assertTrue(failure.getMessage.contains("11 was not even"))
  }

  @Test
  def propertyMatchersIntegrateWithMustBeAndMustHaveSyntax(): Unit = {
    val active: BePropertyMatcher[ServiceStatus] = new BePropertyMatcher[ServiceStatus] {
      override def apply(left: ServiceStatus): BePropertyMatchResult = {
        BePropertyMatchResult(left.active, "active")
      }
    }
    val endpoint: HavePropertyMatcher[ServiceStatus, String] = new HavePropertyMatcher[ServiceStatus, String] {
      override def apply(left: ServiceStatus): HavePropertyMatchResult[String] = {
        HavePropertyMatchResult(
          left.endpoint == "https://metadata.example.test",
          "endpoint",
          "https://metadata.example.test",
          left.endpoint
        )
      }
    }
    val replicas: HavePropertyMatcher[ServiceStatus, Int] = new HavePropertyMatcher[ServiceStatus, Int] {
      override def apply(left: ServiceStatus): HavePropertyMatchResult[Int] = {
        HavePropertyMatchResult(left.replicas == 3, "replicas", 3, left.replicas)
      }
    }
    val healthyService: ServiceStatus = ServiceStatus(
      name = "reachability-metadata",
      active = true,
      endpoint = "https://metadata.example.test",
      replicas = 3
    )

    healthyService must be(active)
    healthyService must have(endpoint)
    healthyService must have(replicas)
    healthyService must have(endpoint, replicas)

    val failure: TestFailedException = expectTestFailure {
      healthyService.copy(replicas = 2) must have(replicas)
    }
    assertTrue(failure.getMessage.contains("replicas"))
    assertTrue(failure.getMessage.contains("3"))
    assertTrue(failure.getMessage.contains("2"))
  }

  private def parsePositive(input: String): Int = {
    val parsed: Int = input.toInt
    if (parsed <= 0) {
      throw new IllegalArgumentException(s"value must be positive: $input")
    }
    parsed
  }

  private def expectTestFailure(action: => Unit): TestFailedException = {
    org.junit.jupiter.api.Assertions.assertThrows(
      classOf[TestFailedException],
      new Executable {
        override def execute(): Unit = action
      }
    )
  }
}
