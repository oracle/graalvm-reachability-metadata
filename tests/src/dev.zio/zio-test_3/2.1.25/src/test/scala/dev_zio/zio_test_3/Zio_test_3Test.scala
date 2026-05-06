/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_test_3

import java.time.Instant
import java.time.ZoneId

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import zio.Clock
import zio.Duration
import zio.Runtime
import zio.Unsafe
import zio.ZIO
import zio.test.Annotations
import zio.test.Assertion
import zio.test.Assertion.*
import zio.test.Gen
import zio.test.TestAnnotation
import zio.test.TestAnnotationMap
import zio.test.TestClock
import zio.test.TestConsole
import zio.test.TestRandom
import zio.test.TestResult
import zio.test.TestSystem
import zio.test.assertTrue
import zio.test.checkN
import zio.test.diff.Diff
import zio.test.testClockWith
import zio.test.testConsoleWith
import zio.test.testEnvironment
import zio.test.testRandomWith
import zio.test.testSystemWith

import scala.util.Success

@Timeout(10)
class Zio_test_3Test {
  @Test
  def assertionsEvaluateNestedValuesCollectionsAndExits(): Unit = {
    val user: User = User("zio-native", 21, List("scala", "native", "testing"))
    val userAssertion: Assertion[User] =
      hasField("name", (value: User) => value.name, startsWithString("zio")) &&
        hasField("age", (value: User) => value.age, isGreaterThanEqualTo(18)) &&
        hasField("tags", (value: User) => value.tags, hasSize(equalTo(3)) && contains("native"))

    assertThat(userAssertion.test(user)).isTrue()
    assertThat(userAssertion.test(user.copy(age = 17))).isFalse()
    assertThat(isSome(equalTo("present")).test(Some("present"))).isTrue()
    assertThat(isRight(equalTo(42)).test(Right(42): Either[Any, Int])).isTrue()
    assertThat(isSuccess(equalTo("ok")).test(Success("ok"))).isTrue()
    assertThat(forall(isGreaterThan(0)).test(List(1, 2, 3))).isTrue()
    assertThat(hasSameElements(List(3, 2, 1)).test(List(1, 2, 3))).isTrue()
    assertThat(matchesRegex("zio-[a-z]+").test("zio-test")).isTrue()

    assertThat(succeeds(equalTo(7)).test(zio.Exit.succeed(7))).isTrue()
    assertThat(fails(equalTo("bad")).test(zio.Exit.fail("bad"))).isTrue()
    assertThat(dies(hasMessage(containsString("boom"))).test(zio.Exit.die(new IllegalStateException("boom")))).isTrue()
  }

  @Test
  def assertionResultsComposeAndPreserveFailureState(): Unit = {
    val first: TestResult = Assertion.equalTo(1).run(1)
    val second: TestResult = Assertion.containsString("native").run("graal native image")
    val failure: TestResult = Assertion.equalTo(99).run(1).label("mismatched integer")

    assertThat(first.isSuccess).isTrue()
    assertThat(second.isSuccess).isTrue()
    assertThat((first && second).isSuccess).isTrue()
    assertThat((first && failure).isFailure).isTrue()
    assertThat((failure || second).isSuccess).isTrue()
    assertThat(failure.failures.isDefined).isTrue()
    assertThat((!failure).isSuccess).isTrue()
  }

  @Test
  def generatorsProduceBoundedStructuredAndTransformedValues(): Unit = {
    val numbers: List[Int] = unsafeRun(Gen.int(1, 5).runCollectN(30))
    assertThat(numbers.size).isEqualTo(30)
    numbers.foreach { number =>
      assertThat(number).isBetween(1, 5)
    }

    val strings: List[String] = unsafeRun(Gen.alphaNumericStringBounded(3, 8).runCollectN(12))
    assertThat(strings.size).isEqualTo(12)
    strings.foreach { value =>
      assertThat(value.length).isBetween(3, 8)
      assertThat(value.forall(_.isLetterOrDigit)).isTrue()
    }

    val chunks = unsafeRun(Gen.chunkOfBounded(2, 4)(Gen.int(0, 9)).runCollectN(8))
    assertThat(chunks.size).isEqualTo(8)
    chunks.foreach { chunk =>
      assertThat(chunk.length).isBetween(2, 4)
      chunk.foreach { value => assertThat(value).isBetween(0, 9) }
    }

    val descriptions: List[String] = unsafeRun {
      val descriptionGen: Gen[Any, String] = for {
        color <- Gen.elements("red", "green", "blue")
        size <- Gen.int(1, 3)
      } yield s"$color-$size"
      descriptionGen.filter(_.contains("-")).map(_.toUpperCase).runCollectN(10)
    }

    assertThat(descriptions.size).isEqualTo(10)
    descriptions.foreach { description =>
      assertThat(description).contains("-")
      assertThat(description).isEqualTo(description.toUpperCase)
    }
  }

  @Test
  def propertyChecksEvaluateGeneratedSamplesAndReportFailures(): Unit = {
    val commutativeAddition: TestResult = unsafeRun {
      checkN(25)(Gen.int(-20, 20), Gen.int(-20, 20)) { (left, right) =>
        assertTrue(left + right == right + left)
      }
    }
    val failingProperty: TestResult = unsafeRun {
      checkN(1)(Gen.const(0)) { value =>
        assertTrue(value > 0)
      }
    }

    assertThat(commutativeAddition.isSuccess).isTrue()
    assertThat(failingProperty.isFailure).isTrue()
    assertThat(failingProperty.failures.isDefined).isTrue()
  }

  @Test
  def testRandomFeedsDeterministicValuesAndFallsBackToBoundedGeneration(): Unit = {
    val values = unsafeRun {
      testRandomWith { random =>
        for {
          _ <- random.feedInts(4, 8, 15)
          _ <- random.feedBooleans(true, false)
          _ <- random.feedStrings("alpha", "beta")
          firstInt <- random.nextInt
          secondInt <- random.nextInt
          firstBoolean <- random.nextBoolean
          secondBoolean <- random.nextBoolean
          firstString <- random.nextString(5)
          secondString <- random.nextString(4)
          _ <- random.clearInts
          boundedInt <- random.nextIntBetween(10, 20)
        } yield (firstInt, secondInt, firstBoolean, secondBoolean, firstString, secondString, boundedInt)
      }.provideLayer(TestRandom.deterministic)
    }

    assertThat(values._1).isEqualTo(4)
    assertThat(values._2).isEqualTo(8)
    assertThat(values._3).isTrue()
    assertThat(values._4).isFalse()
    assertThat(values._5).isEqualTo("alpha")
    assertThat(values._6).isEqualTo("beta")
    assertThat(values._7).isBetween(10, 19)
  }

  @Test
  def testConsoleCapturesInputStandardOutputAndErrorOutput(): Unit = {
    val values = unsafeRun {
      testConsoleWith { console =>
        for {
          _ <- console.feedLines("Ada", "Lovelace")
          first <- console.readLine
          second <- console.readLine
          _ <- console.printLine(s"$first $second")
          _ <- console.printLineError("warn")
          output <- console.output
          errorOutput <- console.outputErr
          _ <- console.clearOutput
          clearedOutput <- console.output
        } yield (first, second, output, errorOutput, clearedOutput)
      }.provideLayer(testEnvironment)
    }

    assertThat(values._1).isEqualTo("Ada")
    assertThat(values._2).isEqualTo("Lovelace")
    assertThat(values._3.mkString).contains("Ada Lovelace")
    assertThat(values._4.mkString).contains("warn")
    assertThat(values._5).isEqualTo(Vector.empty)
  }

  @Test
  def testSystemOverridesEnvironmentPropertiesAndLineSeparator(): Unit = {
    val values = unsafeRun {
      testSystemWith { system =>
        for {
          _ <- system.putEnv("ZIO_TEST_MODE", "native")
          _ <- system.putProperty("zio.test.property", "configured")
          _ <- system.setLineSeparator("|")
          env <- system.env("ZIO_TEST_MODE")
          property <- system.property("zio.test.property")
          separator <- system.lineSeparator
          _ <- system.clearEnv("ZIO_TEST_MODE")
          clearedEnv <- system.env("ZIO_TEST_MODE")
        } yield (env, property, separator, clearedEnv)
      }.provideLayer(TestSystem.default)
    }

    assertThat(values._1).isEqualTo(Some("native"))
    assertThat(values._2).isEqualTo(Some("configured"))
    assertThat(values._3).isEqualTo("|")
    assertThat(values._4).isEqualTo(None)
  }

  @Test
  def testClockControlsInstantTimeZoneAndRecordedSleeps(): Unit = {
    val start: Instant = Instant.parse("2024-01-01T00:00:00Z")
    val values = unsafeRun {
      testClockWith { clock =>
        for {
          _ <- clock.setTime(start)
          before <- Clock.instant
          _ <- clock.adjust(Duration.fromSeconds(5))
          after <- Clock.instant
          sleeps <- clock.sleeps
          _ <- clock.setTimeZone(ZoneId.of("UTC"))
          zone <- clock.timeZone
        } yield (before, after, sleeps, zone)
      }.provideLayer(testEnvironment)
    }

    assertThat(values._1).isEqualTo(start)
    assertThat(values._2).isEqualTo(start.plusSeconds(5))
    assertThat(values._3).isEqualTo(Nil)
    assertThat(values._4).isEqualTo(ZoneId.of("UTC"))
  }

  @Test
  def annotationsAndDiffsAccumulateMetadataAndRenderDifferences(): Unit = {
    val retriedMap: TestAnnotationMap = TestAnnotationMap.empty
      .annotate(TestAnnotation.retried, 1)
      .annotate(TestAnnotation.retried, 2)
    val taggedMap: TestAnnotationMap = TestAnnotationMap.empty
      .annotate(TestAnnotation.tagged, Set("fast"))
      .annotate(TestAnnotation.tagged, Set("native"))

    assertThat(retriedMap.get(TestAnnotation.retried)).isEqualTo(3)
    assertThat(taggedMap.get(TestAnnotation.tagged)).isEqualTo(Set("fast", "native"))

    val identical = Diff.stringDiff.diff("zio", "zio")
    val changed = Diff.stringDiff.diff("zio", "zio-test")
    val listChanged = Diff.listDiff(Diff.stringDiff).diff(List("a", "b"), List("a", "c", "d"))

    assertThat(identical.noDiff).isTrue()
    assertThat(changed.hasDiff).isTrue()
    assertThat(changed.render).contains("zio")
    assertThat(listChanged.hasDiff).isTrue()
    assertThat(listChanged.render).contains("c")
  }

  private def unsafeRun[A](effect: ZIO[Any, Any, A]): A = {
    val bounded: ZIO[Any, Any, A] = effect.timeoutFail(new RuntimeException("ZIO effect timed out"))(Duration.fromSeconds(5))
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(bounded).getOrThrowFiberFailure()
    }
  }

  private case class User(name: String, age: Int, tags: List[String])
}
