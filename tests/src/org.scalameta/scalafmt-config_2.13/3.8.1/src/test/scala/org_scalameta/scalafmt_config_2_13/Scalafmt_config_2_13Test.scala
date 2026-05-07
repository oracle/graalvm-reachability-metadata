/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalameta.scalafmt_config_2_13

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import scala.io.Codec

import metaconfig.ConfDecoderExT._
import metaconfig.Configured
import metaconfig.Input
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.scalafmt.config.ConfParsed
import org.scalafmt.config.PlatformConfig

@Timeout(60)
class Scalafmt_config_2_13Test {
  @Test
  def parsesCommonScalafmtSettingsFromString(): Unit = {
    val formatterVersion: String = "test-formatter-version"
    val parsed: ConfParsed = ConfParsed.fromString(
      s"""
         |version = "$formatterVersion"
         |encoding = "UTF-8"
         |onTestFailure = "warn"
         |project {
         |  git = true
         |}
         |runner {
         |  fatalWarnings = true
         |  ignoreWarnings = false
         |}
         |maxColumn = 100
         |""".stripMargin
    )

    assertThat(parsed.conf.isOk).isTrue()
    assertThat(expectRight(parsed.version)).isEqualTo(formatterVersion)
    assertThat(expectRight(parsed.onTestFailure)).isEqualTo("warn")
    assertThat(expectRight(parsed.isGit)).isTrue()
    assertThat(expectRight(parsed.fatalWarnings)).isTrue()
    assertThat(expectRight(parsed.ignoreWarnings)).isFalse()
    assertThat(expectRight(parsed.getHoconValueOpt[Int]("maxColumn"))).isEqualTo(100)

    val codec: Codec = expectRight(parsed.encoding)
    assertThat(codec.charSet.name()).isEqualTo(StandardCharsets.UTF_8.name())
  }

  @Test
  def resolvesHoconSubstitutionsBeforeReadingConfigValues(): Unit = {
    val parsed: ConfParsed = ConfParsed.fromString(
      """
        |defaults {
        |  formatterVersion = "substituted-version"
        |  useGit = true
        |  maxColumn = 88
        |}
        |version = ${defaults.formatterVersion}
        |project.git = ${defaults.useGit}
        |formatting.maxColumn = ${defaults.maxColumn}
        |""".stripMargin
    )

    assertThat(parsed.conf.isOk).isTrue()
    assertThat(expectRight(parsed.version)).isEqualTo("substituted-version")
    assertThat(expectRight(parsed.isGit)).isTrue()
    assertThat(expectRight(parsed.getHoconValueOpt[Int]("formatting", "maxColumn"))).isEqualTo(88)
  }

  @Test
  def returnsNoneForAbsentOptionalSettings(): Unit = {
    val parsed: ConfParsed = ConfParsed.fromString("maxColumn = 80")

    assertThat(parsed.conf.isOk).isTrue()
    assertThat(parsed.version.isEmpty).isTrue()
    assertThat(parsed.encoding.isEmpty).isTrue()
    assertThat(parsed.onTestFailure.isEmpty).isTrue()
    assertThat(parsed.isGit.isEmpty).isTrue()
    assertThat(parsed.fatalWarnings.isEmpty).isTrue()
    assertThat(parsed.ignoreWarnings.isEmpty).isTrue()
    assertThat(parsed.getHoconValueOpt[String]("missing", "nested").isEmpty).isTrue()
  }

  @Test
  def reportsTypedErrorsForValuesWithUnexpectedTypes(): Unit = {
    val parsed: ConfParsed = ConfParsed.fromString(
      """
        |version = ["not", "a", "single", "string"]
        |runner {
        |  fatalWarnings = 7
        |}
        |""".stripMargin
    )

    assertThat(parsed.conf.isOk).isTrue()
    assertThat(expectLeft(parsed.version)).isNotBlank()
    assertThat(expectLeft(parsed.fatalWarnings)).isNotBlank()
  }

  @Test
  def reportsInvalidCharacterEncodings(): Unit = {
    val parsed: ConfParsed = ConfParsed.fromString("encoding = \"not-a-real-charset\"")

    val error: String = expectLeft(parsed.encoding)
    assertThat(error).contains("not-a-real-charset")
  }

  @Test
  def readsConfigurationFromPath(): Unit = {
    val path = Files.createTempFile("scalafmt-config-public-api", ".conf")
    try {
      Files.writeString(
        path,
        """
          |version = "from-file"
          |encoding = "UTF-16"
          |project.git = false
          |""".stripMargin,
        StandardCharsets.UTF_8
      )

      val parsed: ConfParsed = ConfParsed.fromPath(path)

      assertThat(parsed.conf.isOk).isTrue()
      assertThat(expectRight(parsed.version)).isEqualTo("from-file")
      assertThat(expectRight(parsed.encoding).charSet.name())
        .isEqualTo(StandardCharsets.UTF_16.name())
      assertThat(expectRight(parsed.isGit)).isFalse()
    } finally {
      Files.deleteIfExists(path)
      ()
    }
  }

  @Test
  def resolvesRelativeIncludesFromConfigurationFiles(): Unit = {
    val directory: Path = Files.createTempDirectory("scalafmt-config-includes")
    val includedConfig: Path = directory.resolve("shared.conf")
    val mainConfig: Path = directory.resolve(".scalafmt.conf")

    try {
      Files.writeString(
        includedConfig,
        """
          |version = "from-included-file"
          |runner.ignoreWarnings = true
          |project.git = true
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      Files.writeString(
        mainConfig,
        """
          |include "shared.conf"
          |runner.fatalWarnings = false
          |""".stripMargin,
        StandardCharsets.UTF_8
      )

      val parsed: ConfParsed = ConfParsed.fromPath(mainConfig)

      assertThat(parsed.conf.isOk).isTrue()
      assertThat(expectRight(parsed.version)).isEqualTo("from-included-file")
      assertThat(expectRight(parsed.ignoreWarnings)).isTrue()
      assertThat(expectRight(parsed.isGit)).isTrue()
      assertThat(expectRight(parsed.fatalWarnings)).isFalse()
    } finally {
      Files.deleteIfExists(mainConfig)
      Files.deleteIfExists(includedConfig)
      Files.deleteIfExists(directory)
      ()
    }
  }

  @Test
  def acceptsExplicitInputAndNestedConfigPath(): Unit = {
    val formatterVersion: String = "nested-config-version"
    val input: Input = Input.String(
      s"""
         |workspace = "ignored by the selected path"
         |scalafmt {
         |  version = "$formatterVersion"
         |  runner.ignoreWarnings = true
         |}
         |""".stripMargin
    )

    val parsed: ConfParsed = ConfParsed(Configured.Ok(input), Some("scalafmt"))

    assertThat(parsed.conf.isOk).isTrue()
    assertThat(expectRight(parsed.version)).isEqualTo(formatterVersion)
    assertThat(expectRight(parsed.ignoreWarnings)).isTrue()
    assertThat(parsed.onTestFailure.isEmpty).isTrue()
  }

  @Test
  def parsesVirtualInputFilesAndDirectParserResults(): Unit = {
    val virtualFile: Input = Input.VirtualFile(
      "project/.scalafmt.conf",
      """
        |onTestFailure = "error"
        |project.git = true
        |custom.values.answer = 42
        |""".stripMargin
    )
    val parsedFromInput: ConfParsed = ConfParsed.fromInput(virtualFile)

    assertThat(parsedFromInput.conf.isOk).isTrue()
    assertThat(expectRight(parsedFromInput.onTestFailure)).isEqualTo("error")
    assertThat(expectRight(parsedFromInput.isGit)).isTrue()
    assertThat(expectRight(parsedFromInput.getHoconValueOpt[Int]("custom", "values", "answer")))
      .isEqualTo(42)

    val parsedFromParser: ConfParsed = new ConfParsed(
      PlatformConfig.parser.fromString("formatter { maxColumn = 120 }")
    )
    assertThat(expectRight(parsedFromParser.getHoconValueOpt[Int]("formatter", "maxColumn"))).isEqualTo(120)
  }

  @Test
  def exposesConfiguredFailuresThroughAccessors(): Unit = {
    val parsed: ConfParsed = new ConfParsed(Configured.error("synthetic config failure"))

    assertThat(parsed.conf.isNotOk).isTrue()
    assertThat(expectLeft(parsed.version)).contains("synthetic config failure")
  }

  private def expectRight[A](actual: Option[Either[String, A]]): A = {
    assertThat(actual.isDefined).isTrue()
    actual.get match {
      case Right(value) => value
      case Left(error) => throw new AssertionError(s"Expected Right value but got Left($error)")
    }
  }

  private def expectLeft[A](actual: Option[Either[String, A]]): String = {
    assertThat(actual.isDefined).isTrue()
    actual.get match {
      case Left(error) => error
      case Right(value) => throw new AssertionError(s"Expected Left value but got Right($value)")
    }
  }
}
