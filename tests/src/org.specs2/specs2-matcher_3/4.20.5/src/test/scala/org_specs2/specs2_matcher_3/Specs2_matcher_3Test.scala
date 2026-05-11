/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_specs2.specs2_matcher_3

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Error
import org.specs2.execute.Failure as SpecsFailure
import org.specs2.execute.Pending
import org.specs2.execute.Result
import org.specs2.execute.Skipped
import org.specs2.execute.Success as SpecsSuccess
import org.specs2.matcher.DataTables
import org.specs2.matcher.MatchResult
import org.specs2.matcher.Matcher
import org.specs2.matcher.MustMatchers
import org.specs2.matcher.ResultMatchers

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success
import scala.util.Try

class Specs2_matcher_3Test extends MustMatchers with DataTables {
  @Test
  def equalityReferenceAndTypeMatchersReportSuccessAndFailure(): Unit = {
    val reference: String = new String("same")

    assertSuccessful(1 must be_==(1))
    assertFailed(1 must be_==(2))
    assertSuccessful(Seq(1, 2) must be_===(Seq(1, 2)))
    assertFailed(Seq(1, 2) must be_===(Seq(2, 1)))
    assertSuccessful(reference must beTheSameAs(reference))
    assertFailed(new String("same") must beTheSameAs(reference))
    assertSuccessful("native-image" must beAnInstanceOf[String])
    assertSuccessful(classOf[CharSequence] must beAssignableFrom[String])
  }

  @Test
  def stringAndNumericMatchersCoverAdaptedMatching(): Unit = {
    assertSuccessful(" Graal VM " must be_==/("graalvm"))
    assertSuccessful("GraalVM Native Image" must contain("Native"))
    assertSuccessful("ticket-1234" must find("ticket-(\\d+)").withGroup("1234"))
    assertSuccessful("abc-42" must beMatching("[a-z]+-\\d+"))
    assertFailed("abc" must beMatching("[0-9]+"))

    assertSuccessful(3.1415 must beCloseTo(3.14, 0.01))
    assertSuccessful(1000.4 must beCloseTo(1000.0 within 4.significantFigures))
    assertSuccessful(5 must beBetween(1, 10))
    assertFailed(11 must beBetween(1, 10))
  }

  @Test
  def collectionMapAndPartialFunctionMatchersValidateContents(): Unit = {
    val numbers: Seq[Int] = Seq(1, 2, 2, 3)
    val settings: Map[String, Any] = Map("host" -> "localhost", "port" -> 8080, "secure" -> true)
    val entries: Iterable[(Any, Any)] = settings.toList.map { case (key, value) => (key: Any) -> value }
    val parser: PartialFunction[String, Any] = {
      case "one" => 1
      case "two" => 2
    }

    assertSuccessful(numbers must contain(2))
    assertSuccessful(numbers must contain(allOf(1, 2, 3)))
    assertSuccessful(numbers must contain(be_>(1)).atLeast(3))
    assertSuccessful(numbers must haveSize(4))
    assertSuccessful(Seq(1, 2, 3) must beSorted)
    assertFailed(Seq(3, 1, 2) must beSorted)

    assertSuccessful(entries must haveKey[Any]("host"))
    assertSuccessful(entries must haveValue[Any](8080))
    assertSuccessful(entries must havePair[Any, Any]("secure" -> true))
    assertSuccessful(parser must beDefinedAt("one", "two"))
    assertSuccessful(parser must beDefinedBy[String, Any]("one" -> 1, "two" -> 2))
    assertFailed(parser must beDefinedAt("three"))
  }

  @Test
  def optionEitherTryAndExceptionMatchersCheckAlgebraicValues(): Unit = {
    val failedTry: Try[Int] = Failure(new IllegalArgumentException("bad input"))

    assertSuccessful(Option("graal") must beSome[String]((value: String) => value.startsWith("gra")))
    assertSuccessful(Option.empty[String] must beNone)
    assertFailed(Option("metadata") must beNone)

    assertSuccessful(Right[String, Int](42) must beRight(42))
    assertSuccessful(Left[String, Int]("missing") must beLeft("missing"))
    assertFailed(Right[String, Int](42) must beLeft("missing"))

    assertSuccessful(Success(42) must beSuccessfulTry[Int](42))
    assertSuccessful(failedTry must beFailedTry[Int].withThrowable[IllegalArgumentException]("bad input"))
    assertFailed(Success(42) must beFailedTry[Int])

    assertSuccessful(createExpectable[Any] { throw new IllegalStateException("boom") }.applyMatcher(throwA[IllegalStateException]("boom")))
    assertSuccessful(createExpectable[Any] { throw new IllegalArgumentException("invalid") }.applyMatcher(throwA(new IllegalArgumentException("invalid"))))
    assertFailed(createExpectable[Any](42).applyMatcher(throwA[IllegalStateException]))
  }

  @Test
  def customMatchersExpectablesAndCombinatorsProduceReusableResults(): Unit = {
    val evenMatcher: Matcher[Int] = ((value: Int) => value % 2 == 0, "be even")
    val lengthMatcher: Matcher[String] = ((value: String) => value.length) ^^ be_==(6)
    val describedResult: MatchResult[String] = createExpectable("metadata", "artifact name").applyMatcher(contain("data"))

    assertSuccessful(4 must evenMatcher)
    assertFailed(5 must evenMatcher)
    assertSuccessful("native" must lengthMatcher)
    assertSuccessful((5 must be_>(1)) and (5 must be_<(10)))
    assertFailed((5 must be_>(10)) or (5 must be_<(0)))
    assertSuccessful(describedResult)
    assertTrue(describedResult.message.contains("artifact name"), describedResult.message)
  }

  @Test
  def futureMatchersAwaitValuesAndResultsWithinBoundedTimeouts(): Unit = {
    given executionEnv: ExecutionEnv = ExecutionEnv.fromGlobalExecutionContext

    val successfulValue: Future[Int] = Future.successful(42)
    val wrongValue: Future[Int] = Future.successful(41)
    val computedResult: Future[MatchResult[Int]] = Future {
      6 * 7 must be_==(42)
    }(executionEnv.executionContext)

    assertSuccessful(successfulValue must be_==(42).awaitFor(1.second))
    assertFailed(wrongValue must be_==(42).awaitFor(1.second))
    assertSuccessfulResult(computedResult.awaitFor(1.second))
  }

  @Test
  def resultMatchersDistinguishExecutionStatusesAndMessages(): Unit = {
    assertSuccessful(SpecsSuccess("calculation completed") must ResultMatchers.beSuccessful[Result])
    assertSuccessful(SpecsFailure("expected a positive total") must ResultMatchers.beFailing[Result](".*positive total.*"))
    assertSuccessful(Error("socket timeout") must ResultMatchers.beError[Result](".*socket timeout.*"))
    assertSuccessful(Skipped("external service unavailable") must ResultMatchers.beSkipped[Result](".*service unavailable.*"))
    assertSuccessful(Pending("scenario not implemented") must ResultMatchers.bePending[Result](".*not implemented.*"))

    assertFailed(SpecsSuccess("calculation completed") must ResultMatchers.beFailing[Result])
    assertFailed(SpecsFailure("expected a positive total") must ResultMatchers.beSuccessful[Result])
    assertFailed(Skipped("external service unavailable") must ResultMatchers.bePending[Result])
  }

  @Test
  def dataTablesAggregateRowResultsAndExposeRenderedMessages(): Unit = {
    val result: Result =
      ("left" | "right" | "sum" |>
        1 ! 2 ! 3 |
        2 ! 3 ! 5) | { (left: Int, right: Int, sum: Int) =>
          left + right must be_==(sum)
        }

    assertSuccessfulResult(result)
    assertTrue(result.message.contains("left"), result.message)
    assertTrue(result.message.contains("right"), result.message)
  }

  private def assertSuccessful(result: MatchResult[?]): Unit = {
    assertTrue(result.isSuccess, s"expected specs2 match to succeed, but got: ${result.message}")
  }

  private def assertFailed(result: MatchResult[?]): Unit = {
    assertFalse(result.isSuccess, s"expected specs2 match to fail, but got: ${result.message}")
  }

  private def assertSuccessfulResult(result: Result): Unit = {
    assertTrue(result.isSuccess, s"expected specs2 result to succeed, but got: ${result.message}")
  }
}
