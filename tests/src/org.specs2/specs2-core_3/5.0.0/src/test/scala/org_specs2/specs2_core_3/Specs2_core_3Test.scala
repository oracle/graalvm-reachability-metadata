/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_specs2.specs2_core_3

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.DurationInt
import scala.util.Failure as TryFailure
import scala.util.Success as TrySuccess
import scala.util.Try

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.specs2.Specification
import org.specs2.control.ExecuteActions.ActionRunOps
import org.specs2.execute.DecoratedResult
import org.specs2.execute.Failure
import org.specs2.execute.PendingUntilFixed
import org.specs2.execute.Result
import org.specs2.execute.ResultLogicalCombinators.combineResult
import org.specs2.execute.Skipped
import org.specs2.execute.Success
import org.specs2.matcher.DataTable
import org.specs2.matcher.MatchResult
import org.specs2.matcher.MustMatchers
import org.specs2.reporter.LineLogger
import org.specs2.specification.After
import org.specs2.specification.Before
import org.specs2.specification.BeforeAfter
import org.specs2.specification.Tables
import org.specs2.specification.core.Env
import org.specs2.specification.core.Execution
import org.specs2.specification.core.Fragment
import org.specs2.specification.core.Fragments
import org.specs2.specification.core.SpecificationStructure as CoreSpecificationStructure
import org.specs2.specification.core.SpecStructure
import org.specs2.specification.core.Text
import org.specs2.specification.process.DefaultExecutor
import org.specs2.specification.process.Stats

class Specs2_core_3Test {
  @Test
  def buildsAndExecutesImmutableSpecificationsFromTheAcceptanceDsl(): Unit = {
    val specification: CalculatorAcceptanceSpecification = new CalculatorAcceptanceSpecification

    withEnv { env =>
      val fragments: List[Fragment] = specification.structure(env).fragmentsList(env.specs2ExecutionEnv)
      val renderedText: String = fragments.map(_.description.show).mkString("\n")
      val examples: List[Fragment] = fragments.filter(Fragment.isExample)

      assertTrue(renderedText.contains("Calculator acceptance"))
      assertTrue(examples.exists(_.description.show.contains("adds positive integers")))
      assertTrue(examples.exists(_.description.show.contains("finds a maximum")))
    }

    val results: List[Result] = executeExamples(specification)

    assertEquals(2, results.size)
    results.foreach { result =>
      assertTrue(result.isSuccess, result.message)
      assertEquals("success", result.statusName)
    }
  }

  @Test
  def buildsAndExecutesMutableSpecificationsWithNestedBlocks(): Unit = {
    val specification: MutableCollectionSpecification = new MutableCollectionSpecification

    withEnv { env =>
      val fragments: List[Fragment] = specification.structure(env).fragmentsList(env.specs2ExecutionEnv)
      val renderedText: String = fragments.map(_.description.show).mkString("\n")

      assertTrue(renderedText.contains("A mutable collection specification"))
      assertTrue(renderedText.contains("preserve insertion order"))
      assertEquals(2, fragments.count(Fragment.isExample))
    }

    val results: List[Result] = executeExamples(specification)

    assertEquals(2, results.size)
    assertTrue(results.forall(_.isSuccess), results.map(_.message).mkString("; "))
  }

  @Test
  def evaluatesCoreMatchersAndReportsFailuresWithoutThrowing(): Unit = {
    val combinedSuccess: MatchResult[Any] = Specs2CoreExpectations.combinedCollectionAndStringExpectation
    val failingResult: Result = Specs2CoreExpectations.failingStringExpectation.toResult
    val trySuccess: MatchResult[Try[Int]] = Specs2CoreExpectations.tryExpectation(TrySuccess(7))
    val tryFailure: MatchResult[Try[Int]] = Specs2CoreExpectations.tryFailureExpectation(TryFailure(new IllegalArgumentException("boom")))

    assertTrue(combinedSuccess.toResult.isSuccess)
    assertTrue(trySuccess.toResult.isSuccess)
    assertTrue(tryFailure.toResult.isSuccess)
    assertTrue(failingResult.isFailure)
    assertTrue(failingResult.message.contains("gamma"), failingResult.message)
  }

  @Test
  def composesResultsWithBooleanResultAlgebra(): Unit = {
    val allSuccessful: Result = Success("validated first condition").and(Success("validated second condition"))
    val failedConjunction: Result = Success("validated first condition").and(Failure("second condition was not met"))
    val recoveredDisjunction: Result =
      Failure("primary condition was not met").or(Success("fallback condition was met"))
    val failedDisjunction: Result =
      Failure("primary condition was not met").or(Failure("fallback condition was not met"))

    assertTrue(allSuccessful.isSuccess, allSuccessful.message)
    assertEquals("success", allSuccessful.statusName)
    assertTrue(failedConjunction.isFailure)
    assertEquals("failure", failedConjunction.statusName)
    assertTrue(recoveredDisjunction.isSuccess, recoveredDisjunction.message)
    assertTrue(failedDisjunction.isFailure)
  }

  @Test
  def executesDataTablesAndDecoratesAggregatedResults(): Unit = {
    val decorated: DecoratedResult[DataTable] = Specs2CoreExpectations.multiplicationTableResult
    val dataTable: DataTable = decorated.decorator

    assertTrue(decorated.isSuccess, decorated.message)
    assertEquals(Seq("value", "double"), dataTable.titles)
    assertEquals(3, dataTable.rows.size)
    assertTrue(dataTable.isSuccess)
    assertTrue(dataTable.show.contains("double"))
  }

  @Test
  def composesContextsAndAlwaysRunsAfterActions(): Unit = {
    val events: ArrayBuffer[String] = ArrayBuffer.empty[String]
    val before: Before = Before.create(events += "before")
    val after: After = After.create(events += "after")
    val beforeAfter: BeforeAfter = BeforeAfter.create(events += "setup", events += "cleanup")

    val successful: Result = before.andThen(Before.create(events += "second-before"))(Success("body"))
    val cleaned: Result = beforeAfter.andThen(BeforeAfter.create(events += "inner-setup", events += "inner-cleanup")) {
      Specs2CoreExpectations.equalExpectation(4, 4)
    }
    val afterFailure: Result = after(Failure("checked failure", "expected success"))

    assertTrue(successful.isSuccess)
    assertTrue(cleaned.isSuccess)
    assertTrue(afterFailure.isFailure)
    assertEquals(
      Seq("before", "second-before", "setup", "inner-setup", "cleanup", "inner-cleanup", "after"),
      events.toSeq
    )
  }

  @Test
  def manipulatesFragmentsExecutionsAndStatistics(): Unit = {
    withEnv { env =>
      val firstText: Fragment = Fragment(Text("| first line\n"))
      val secondText: Fragment = Fragment(Text("| second line"))
      val successFragment: Fragment = Fragment(Text("successful example"), Execution.result(Success("done"))).setTimeout(2.seconds)
      val failureFragment: Fragment = Fragment(Text("failing example"), Execution.result(Failure("not done")))
      val fragments: Fragments = Fragments(firstText, secondText, successFragment, failureFragment).stripMargin.compact
      val fragmentList: List[Fragment] = fragments.fragmentsList(env.specs2ExecutionEnv)
      val successResult: Result = successFragment.startExecution(env).executionResult.run(env.specs2ExecutionEnv)
      val skippedResult: Result = successFragment.skip.executionResult.run(env.specs2ExecutionEnv)
      val aggregatedStats: Stats = Stats.StatsMonoid.append(Stats(successResult), Stats(Failure("not done")))
      val trendedStats: Stats = aggregatedStats.updateFrom(Stats(successes = 2, expectations = 2))

      assertEquals(3, fragmentList.size)
      assertEquals(" first line\n second line", fragmentList.head.description.show)
      assertTrue(successResult.isSuccess)
      assertTrue(skippedResult.isSkipped)
      assertTrue(successFragment.stopOnFail.mustStopOn(Failure("stop")))
      assertFalse(successFragment.stopOnFail.mustStopOn(Success("continue")))
      assertEquals(2, aggregatedStats.examples)
      assertEquals(1, aggregatedStats.successes)
      assertEquals(1, aggregatedStats.failures)
      assertTrue(aggregatedStats.hasFailures)
      assertTrue(trendedStats.trend.exists(_.successes == -1))
    }
  }

  @Test
  def tracksPendingUntilFixedExpectations(): Unit = {
    val pendingResult: Result = Specs2CoreExpectations.pendingUntilFixedForUnmetExpectation
    val fixedResult: Result = Specs2CoreExpectations.pendingUntilFixedForMetExpectation

    assertEquals("pending", pendingResult.statusName)
    assertTrue(pendingResult.message.contains("Pending until fixed"), pendingResult.message)
    assertTrue(fixedResult.isFailure)
    assertTrue(fixedResult.message.contains("Fixed now"), fixedResult.message)
  }

  @Test
  def buffersReporterLinesAndClassifiesResultStatistics(): Unit = {
    val logger = LineLogger.stringLogger
    logger.infoLog("starting specs2")
    logger.infoLog(" core\n")
    logger.warnLog("watch this")
    logger.failureLog("expectation failed")
    logger.errorLog("unexpected error")
    logger.close()

    val pendingStats: Stats = Stats(Skipped("not selected"))
    val failureStats: Stats = Stats(Failure("bad value"))
    val errorStats: Stats = Stats(org.specs2.execute.Error("boom"))

    assertTrue(logger.output.contains("[info] starting specs2 core"), logger.output)
    assertTrue(logger.output.contains("[warn] watch this"), logger.output)
    assertTrue(logger.output.contains("[error] expectation failed"), logger.output)
    assertTrue(pendingStats.hasSuspended)
    assertTrue(failureStats.hasIssues)
    assertTrue(errorStats.hasErrors)
    assertEquals("failure", failureStats.result.statusName)
  }

  private def executeExamples(specification: CoreSpecificationStructure): List[Result] = {
    withEnv { env =>
      val fragments: List[Fragment] = DefaultExecutor.runSpecificationAction(specification, env).run(env.specs2ExecutionEnv)
      fragments.filter(Fragment.isExample).map(_.executionResult.run(env.specs2ExecutionEnv))
    }
  }

  private def withEnv[A](f: Env => A): A = {
    val env: Env = Env().setWithoutIsolation
    try f(env)
    finally env.shutdown()
  }
}

final class CalculatorAcceptanceSpecification extends Specification {
  def is: SpecStructure = s2"""
 Calculator acceptance
   adds positive integers $addsPositiveIntegers
   finds a maximum       $findsAMaximum
  """

  def addsPositiveIntegers: MatchResult[Any] =
    (2 + 3) must_== 5

  def findsAMaximum: MatchResult[Any] =
    List(1, 9, 4).max must_== 9
}

final class MutableCollectionSpecification extends org.specs2.mutable.Specification {
  "A mutable collection specification" should {
    "preserve insertion order" in {
      val values: Vector[String] = Vector("first", "second", "third")

      values.mkString(",") must_== "first,second,third"
    }

    "support option expectations" in {
      val maybeValue: Option[Int] = Some(42)

      maybeValue must beSome(42)
    }
  }
}

object Specs2CoreExpectations extends MustMatchers with Tables with PendingUntilFixed {
  def combinedCollectionAndStringExpectation: MatchResult[Any] = {
    (List("alpha", "beta", "gamma") must contain("beta")) and
      ("specs2-core" must startWith("specs2")) and
      (Option("metadata") must beSome("metadata")) and
      (Right(42) must beRight(42))
  }

  def failingStringExpectation: MatchResult[String] =
    "alpha beta" must contain("gamma")

  def tryExpectation(value: Try[Int]): MatchResult[Try[Int]] =
    value must beSuccessfulTry(7)

  def tryFailureExpectation(value: Try[Int]): MatchResult[Try[Int]] =
    value must beFailedTry.like { case e: IllegalArgumentException => e.getMessage must_== "boom" }

  def equalExpectation(actual: Int, expected: Int): MatchResult[Any] =
    actual must_== expected

  def pendingUntilFixedForUnmetExpectation: Result =
    pendingUntilFixed("feature is intentionally pending") {
      "accepted" must_== "implemented"
    }

  def pendingUntilFixedForMetExpectation: Result =
    pendingUntilFixed("feature is ready") {
      1 + 1 must_== 2
    }

  def multiplicationTableResult: DecoratedResult[DataTable] = {
    val firstRow: DataRow2[Int, Int] = toDataRow(1).!(2)
    val secondRow: DataRow2[Int, Int] = toDataRow(4).!(8)
    val thirdRow: DataRow2[Int, Int] = toDataRow(7).!(14)
    val table: Table2[Int, Int] = toTableHeader("value").|("double").|>(firstRow).|(secondRow).|(thirdRow)

    table.|> { (value: Int, double: Int) =>
      value * 2 must_== double
    }
  }
}
