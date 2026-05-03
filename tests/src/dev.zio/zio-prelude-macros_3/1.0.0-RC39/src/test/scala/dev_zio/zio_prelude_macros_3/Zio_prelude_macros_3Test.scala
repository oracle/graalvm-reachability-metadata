/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_zio.zio_prelude_macros_3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import zio.prelude.Assertion
import zio.prelude.Assertion.Regex
import zio.prelude.AssertionError
import zio.prelude.macros.BuildInfo

import scala.jdk.CollectionConverters.*

class Zio_prelude_macros_3Test {
  @Test
  def numericAssertionsValidateBoundariesOrderingAndPowers(): Unit = {
    assertPasses(Assertion.between(10, 20), 10)
    assertPasses(Assertion.between(10, 20), 20)
    assertFails(Assertion.between(10, 20), 9, "9 did not satisfy between(10, 20)")

    assertPasses(Assertion.greaterThan(5), 6)
    assertFails(Assertion.greaterThan(5), 5, "5 did not satisfy greaterThan(5)")
    assertPasses(Assertion.greaterThanOrEqualTo(5), 5)
    assertFails(Assertion.greaterThanOrEqualTo(5), 4, "4 did not satisfy greaterThanOrEqualTo(5)")

    assertPasses(Assertion.lessThan(5), 4)
    assertFails(Assertion.lessThan(5), 5, "5 did not satisfy lessThan(5)")
    assertPasses(Assertion.lessThanOrEqualTo(5), 5)
    assertFails(Assertion.lessThanOrEqualTo(5), 6, "6 did not satisfy lessThanOrEqualTo(5)")

    assertPasses(Assertion.divisibleBy(4), 12)
    assertFails(Assertion.divisibleBy(4), 10, "10 did not satisfy divisibleBy(4)")
    assertPasses(Assertion.powerOf(2), 32)
    assertFails(Assertion.powerOf(2), 48, "48 did not satisfy powerOf(2)")

    assertPasses(Assertion.equalTo("same"), "same")
    assertFails(Assertion.notEqualTo("same"), "same", "same did not satisfy notEqualTo(same)")
    assertPasses(Assertion.notEqualTo("same"), "different")
  }

  @Test
  def stringAssertionsCoverCaseSensitivityLengthAndRegexEntryPoints(): Unit = {
    assertPasses(Assertion.contains("pre"), "zio-prelude")
    assertFails(Assertion.contains("macro"), "zio-prelude", "zio-prelude did not satisfy contains(macro)")

    assertPasses(Assertion.startsWith("zio"), "zio-prelude")
    assertFails(Assertion.startsWith("pre"), "zio-prelude", "zio-prelude did not satisfy startsWith(pre)")
    assertPasses(Assertion.endsWith("PRELUDE"), "zio-PRELUDE")
    assertFails(Assertion.endsWith("prelude"), "zio-PRELUDE", "zio-PRELUDE did not satisfy endsWith(prelude)")

    assertPasses(Assertion.startsWithIgnoreCase("ZIO"), "zio-prelude")
    assertPasses(Assertion.endsWithIgnoreCase("prelude"), "zio-PRELUDE")
    assertFails(
      Assertion.startsWithIgnoreCase("prelude"),
      "zio-prelude",
      "zio-prelude did not satisfy startsWithIgnoreCase(prelude)"
    )

    assertPasses(Assertion.hasLength(Assertion.between(3, 5)), "four")
    assertFails(Assertion.hasLength(Assertion.between(3, 5)), "toolong", "toolong did not satisfy hasLength(between(3, 5))")
    assertPasses(Assertion.isEmptyString, "")
    assertFails(Assertion.isEmptyString, "nonempty", "nonempty did not satisfy hasLength(equalTo(0))")

    assertPasses(Assertion.matches("[a-z]+-[0-9]+"), "build-39")
    assertFails(Assertion.matches("[a-z]+-[0-9]+"), "build-RC", "build-RC did not satisfy matches([a-z]+-[0-9]+)")
    assertPasses(Assertion.matches("[A-Z]{2}".r), "RC")

    val identifier: Regex = Regex.start ~ Regex.literal("id-") ~ Regex.inRange('a', 'z').min(2).max(4) ~ Regex.end
    assertPasses(Assertion.matches(identifier), "id-abcd")
    assertFails(Assertion.matches(identifier), "id-a", "id-a did not satisfy matches(^id-([a-z]){2,4}$)")
  }

  @Test
  def assertionsComposeWithBooleanOperatorsAndAccumulateFailures(): Unit = {
    val identifier: Assertion[String] =
      Assertion.startsWith("id-") &&
        Assertion.hasLength(Assertion.greaterThanOrEqualTo(5)) &&
        Assertion.matches(Regex.start ~ Regex.literal("id-") ~ Regex.inRange('a', 'z').+ ~ Regex.end)

    assertPasses(identifier, "id-abc")
    assertFails(
      identifier,
      "no",
      "no did not satisfy startsWith(id-)",
      "no did not satisfy hasLength(greaterThanOrEqualTo(5))",
      "no did not satisfy matches(^id-([a-z])+$)"
    )

    val temporaryOrConfig: Assertion[String] = Assertion.startsWith("tmp-") || Assertion.endsWith(".conf")
    assertPasses(temporaryOrConfig, "tmp-cache")
    assertPasses(temporaryOrConfig, "application.conf")
    assertFails(
      temporaryOrConfig,
      "notes.txt",
      "notes.txt did not satisfy startsWith(tmp-)",
      "notes.txt did not satisfy endsWith(.conf)"
    )

    val notSensitiveProductionName: Assertion[String] = !(Assertion.startsWith("prod-") || Assertion.endsWith(".secret"))
    assertPasses(notSensitiveProductionName, "dev-public")
    assertFails(notSensitiveProductionName, "prod-public", "prod-public did not satisfy doesNotStartWith(prod-)")
    assertFails(notSensitiveProductionName, "dev.secret", "dev.secret did not satisfy doesNotEndsWith(.secret)")
  }

  @Test
  def regexDslCompilesComposablePatternsAndCharacterClasses(): Unit = {
    val animalCode: Regex = Regex.start ~ (Regex.literal("cat") | Regex.literal("dog")) ~ Regex.digit.between(2, 3) ~ Regex.end
    assertThat(animalCode.compile).isEqualTo("^(cat|dog)(\\d){2,3}$")
    assertPasses(Assertion.matches(animalCode), "cat12")
    assertPasses(Assertion.matches(animalCode), "dog987")
    assertFails(Assertion.matches(animalCode), "bird12", "bird12 did not satisfy matches(^(cat|dog)(\\d){2,3}$)")

    val singleVowel: Regex = Regex.start ~ Regex.anyCharOf('a', 'e', 'i', 'o', 'u') ~ Regex.end
    assertPasses(Assertion.matches(singleVowel), "e")
    assertFails(Assertion.matches(singleVowel), "b", failureMessageStartsWith = "b did not satisfy matches(^[")

    val consonant: Regex = Regex.start ~ Regex.notAnyCharOf('a', 'e', 'i', 'o', 'u') ~ Regex.end
    assertPasses(Assertion.matches(consonant), "z")
    assertFails(Assertion.matches(consonant), "a", failureMessageStartsWith = "a did not satisfy matches(^[^")

    val token: Regex =
      Regex.start ~ Regex.alphanumeric.+ ~ Regex.whitespace.? ~ Regex.nonWhitespace.+ ~ Regex.anyChar.max(1) ~ Regex.end
    assertPasses(Assertion.matches(token), "abc XYZ!")
    assertPasses(Assertion.matches(token), "abcXYZ!")
    assertFails(Assertion.matches(token), "", failureMessageStartsWith = " did not satisfy matches(^")

    assertThat((Regex.anything ~ Regex.literal("abc") ~ Regex.anything).compile).isEqualTo("abc")
    assertThat((Regex.nonDigit ~ Regex.nonAlphanumeric ~ Regex.notInRange('0', '9')).compile).isEqualTo("\\D\\W[^0-9]")
  }

  @Test
  def regexDslSupportsCharacterSetsBuiltFromRegexFragments(): Unit = {
    val hexadecimalDigit: Regex =
      Regex.start ~ Regex.anyRegexOf(Regex.digit, Regex.inRange('a', 'f'), Regex.inRange('A', 'F')) ~ Regex.end

    assertPasses(Assertion.matches(hexadecimalDigit), "7")
    assertPasses(Assertion.matches(hexadecimalDigit), "c")
    assertPasses(Assertion.matches(hexadecimalDigit), "E")
    assertFails(Assertion.matches(hexadecimalDigit), "g", failureMessageStartsWith = "g did not satisfy matches(^[")

    val nonDigitOrUnderscore: Regex =
      Regex.start ~ Regex.notAnyRegexOf(Regex.digit, Regex.literal("_"), Regex.inRange('a', 'f')) ~ Regex.end

    assertPasses(Assertion.matches(nonDigitOrUnderscore), ".")
    assertFails(Assertion.matches(nonDigitOrUnderscore), "_", failureMessageStartsWith = "_ did not satisfy matches(^[^")
    assertFails(Assertion.matches(nonDigitOrUnderscore), "5", failureMessageStartsWith = "5 did not satisfy matches(^[^")
  }

  @Test
  def terminalAssertionsAlwaysPassOrFailForAnyInputType(): Unit = {
    assertPasses(Assertion.anything, "accepted")
    assertPasses(Assertion.anything, 42)

    assertFails(Assertion.never, "rejected", "rejected did not satisfy never")
    assertFails(Assertion.never, 42, "42 did not satisfy never")
  }

  @Test
  def assertionErrorsCanBeCombinedRenderedAndConvertedToNonEmptyLists(): Unit = {
    val combined: AssertionError =
      AssertionError.failure("first") ++ AssertionError.failure("second") ++ AssertionError.failure("third")

    assertThat(combined.toNel("input").asJava).containsExactly(
      "input did not satisfy first",
      "input did not satisfy second",
      "input did not satisfy third"
    )

    val rendered: String = combined.render("input")
    assertThat(rendered).contains("input", "first", "second", "third", "\n")
  }

  @Test
  def buildInfoExposesPublishedModuleMetadataWithoutPinningTheArtifactVersion(): Unit = {
    assertThat(BuildInfo.organization).isEqualTo("dev.zio")
    assertThat(BuildInfo.moduleName).isEqualTo("zio-prelude-macros")
    assertThat(BuildInfo.name).isEqualTo(BuildInfo.moduleName)
    assertThat(BuildInfo.version).isNotBlank()
    assertThat(BuildInfo.scalaVersion).startsWith("3.")
    assertThat(BuildInfo.sbtVersion).isNotBlank()
    assertThat(BuildInfo.isSnapshot).isFalse()
    assertThat(BuildInfo.toString).contains(
      "organization: dev.zio",
      "moduleName: zio-prelude-macros",
      "name: zio-prelude-macros",
      "version:",
      "scalaVersion:",
      "sbtVersion:",
      "isSnapshot: false"
    )
  }

  private def assertPasses[A](assertion: Assertion[A], value: A): Unit = {
    assertThat(assertion(value)).isEqualTo(Right(()))
  }

  private def assertFails[A](assertion: Assertion[A], value: A, expectedMessages: String*): Unit = {
    val result: Either[AssertionError, Unit] = assertion(value)
    val error: AssertionError = result match {
      case Left(error) => error
      case Right(_)    => throw new java.lang.AssertionError(s"Expected assertion to fail for value $value")
    }

    assertThat(error.toNel(value.toString).asJava).containsExactly(expectedMessages*)
  }

  private def assertFails[A](assertion: Assertion[A], value: A, failureMessageStartsWith: String): Unit = {
    val result: Either[AssertionError, Unit] = assertion(value)
    val error: AssertionError = result match {
      case Left(error) => error
      case Right(_)    => throw new java.lang.AssertionError(s"Expected assertion to fail for value $value")
    }

    assertThat(error.toNel(value.toString).head).startsWith(failureMessageStartsWith)
  }
}
