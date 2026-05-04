/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_scopt.scopt_3

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import scopt.DefaultOParserSetup
import scopt.OEffect
import scopt.OEffectSetup
import scopt.OParser
import scopt.OParserBuilder
import scopt.Read
import scopt.RenderingMode

import scala.jdk.CollectionConverters.*
import scala.util.Either

class Scopt_3Test {
  @Test
  def parsesNestedCommandLineWithBuiltInAndCustomReaders(): Unit = {
    val parsedConfig = OParser.parse(
      commandParser,
      Seq(
        "ingest",
        "--label",
        "red,blue",
        "--define",
        "user=alice,role=admin",
        "--limit",
        "rows=500",
        "--port",
        "8080",
        "--retries",
        "3",
        "--verbose",
        "--format",
        "csv",
        "orders.csv",
        "customers.csv"
      ),
      CliConfig()
    )

    assertThat(parsedConfig.isDefined).isTrue()
    val config: CliConfig = parsedConfig.get
    assertThat(config.command).isEqualTo(Some("ingest"))
    assertThat(config.format).isEqualTo(Some("csv"))
    assertThat(config.inputs.asJava).containsExactly("orders.csv", "customers.csv")
    assertThat(config.labels.asJava).containsExactly("red", "blue")
    assertThat(config.defines.asJava).containsEntry("user", "alice").containsEntry("role", "admin")
    assertThat(config.limits.asJava).containsEntry("rows", 500)
    assertThat(config.port).isEqualTo(Some(Port(8080)))
    assertThat(config.retries).isEqualTo(3)
    assertThat(config.threads).isEqualTo(2)
    assertThat(config.verbose).isTrue()
  }

  @Test
  def reportsValidationRequiredAndConfigurationFailuresAsEffects(): Unit = {
    val invalidRetries = runParser(Seq("--verbose", "--retries", "99"))
    assertThat(invalidRetries._1.isEmpty).isTrue()
    assertThat(errorMessages(invalidRetries._2).exists(_.contains("retries must be between 0 and 10"))).isTrue()

    val missingRequiredChildOption = runParser(Seq("ingest", "--verbose", "orders.csv"))
    assertThat(missingRequiredChildOption._1.isEmpty).isTrue()
    assertThat(errorMessages(missingRequiredChildOption._2).exists(_.contains("--format"))).isTrue()

    val rejectedByConfigCheck = runParser(Seq("ingest", "--format", "json", "orders.json"))
    assertThat(rejectedByConfigCheck._1.isEmpty).isTrue()
    assertThat(errorMessages(rejectedByConfigCheck._2).exists(_.contains("at least one label or verbose mode is required"))).isTrue()
  }

  @Test
  def rendersUsageHelpVersionAndHiddenOptionsThroughPublicEffects(): Unit = {
    val twoColumnUsage: String = OParser.usage(commandParser, RenderingMode.TwoColumns)
    assertThat(twoColumnUsage)
      .contains("data-tool")
      .contains("--label")
      .contains("ingest")
      .doesNotContain("--secret")

    val oneColumnUsage: String = OParser.usage(commandParser, RenderingMode.OneColumn)
    assertThat(oneColumnUsage)
      .contains("number of retry attempts")
      .contains("input files")
      .doesNotContain("classified test hook")

    val helpRun = runParser(Seq("--help"))
    assertThat(helpRun._1.isEmpty).isTrue()
    val helpOutput: String = displayToOutMessages(helpRun._2).mkString("\n")
    assertThat(helpOutput).contains("Usage:").contains("--format").doesNotContain("--secret")
    assertThat(terminatedSuccessfully(helpRun._2)).isTrue()

    val versionRun = runParser(Seq("--version"))
    assertThat(versionRun._1.isEmpty).isTrue()
    assertThat(displayToOutMessages(versionRun._2).mkString("\n")).contains("data-tool", "1.0")
    assertThat(terminatedSuccessfully(versionRun._2)).isTrue()
  }

  @Test
  def honorsCustomParserSetupForUnknownArgumentsAndUsageOnError(): Unit = {
    val lenientSetup = new DefaultOParserSetup {
      override def errorOnUnknownArgument: Boolean = false
      override def showUsageOnError: Option[Boolean] = Some(false)
      override def renderingMode: RenderingMode = RenderingMode.OneColumn
    }

    val (config, effects) = OParser.runParser(
      commandParser,
      Seq("--unknown-switch", "--verbose"),
      CliConfig(),
      lenientSetup
    )

    assertThat(config.isDefined).isTrue()
    assertThat(config.get.verbose).isTrue()
    assertThat(errorMessages(effects).asJava).isEmpty()
  }

  @Test
  def treatsTokensAfterDoubleDashAsPositionalArguments(): Unit = {
    val (parsedConfig, effects) = OParser.runParser(
      delimiterParser,
      Seq("--verbose", "--", "--not-an-option", "-v", "plain-value"),
      DelimiterConfig()
    )

    assertThat(parsedConfig.isDefined).isTrue()
    val config: DelimiterConfig = parsedConfig.get
    assertThat(config.verbose).isTrue()
    assertThat(config.values.asJava).containsExactly("--not-an-option", "-v", "plain-value")
    assertThat(errorMessages(effects).asJava).isEmpty()
  }

  @Test
  def enforcesExplicitOccurrenceBoundsForRepeatableOptions(): Unit = {
    val acceptedRun = OParser.runParser(
      occurrenceBoundParser,
      Seq("--tag", "alpha", "--tag", "beta", "--tag", "stable"),
      OccurrenceConfig()
    )
    assertThat(acceptedRun._1.isDefined).isTrue()
    assertThat(acceptedRun._1.get.tags.asJava).containsExactly("alpha", "beta", "stable")
    assertThat(errorMessages(acceptedRun._2).asJava).isEmpty()

    val tooFewRun = OParser.runParser(occurrenceBoundParser, Seq("--tag", "alpha"), OccurrenceConfig())
    assertThat(tooFewRun._1.isEmpty).isTrue()
    assertThat(errorMessages(tooFewRun._2).exists(_.contains("Option --tag must be given 2 times"))).isTrue()

    val tooManyRun = OParser.runParser(
      occurrenceBoundParser,
      Seq("--tag", "alpha", "--tag", "beta", "--tag", "stable", "--tag", "extra"),
      OccurrenceConfig()
    )
    assertThat(tooManyRun._1.isEmpty).isTrue()
    assertThat(errorMessages(tooManyRun._2).exists(_.contains("Unknown option --tag"))).isTrue()
  }

  @Test
  def runsEffectsWithCallerSuppliedEffectSetup(): Unit = {
    val setup = new CapturingEffectSetup

    OParser.runEffects(
      List(
        OEffect.DisplayToOut("usage text"),
        OEffect.DisplayToErr("diagnostic text"),
        OEffect.ReportWarning("careful"),
        OEffect.ReportError("broken"),
        OEffect.Terminate(Right(()))
      ),
      setup
    )

    assertThat(setup.out.asJava).containsExactly("usage text")
    assertThat(setup.err.asJava).containsExactly("diagnostic text")
    assertThat(setup.warnings.asJava).containsExactly("careful")
    assertThat(setup.errors.asJava).containsExactly("broken")
    assertThat(setup.terminations.map(_.isRight).asJava).containsExactly(true)
  }

  private given portRead: Read[Port] with {
    override def arity: Int = 1

    override def reads: String => Port = (text: String) => Port(text.toInt)
  }

  private def commandParser: OParser[?, CliConfig] = {
    val builder: OParserBuilder[CliConfig] = OParser.builder[CliConfig]
    import builder.*

    OParser.sequence(
      programName("data-tool"),
      head("data-tool", "1.0"),
      note("Transforms input files for integration-test coverage."),
      opt[Int]('r', "retries")
        .valueName("<n>")
        .action((value: Int, config: CliConfig) => config.copy(retries = value))
        .validate((value: Int) =>
          if value >= 0 && value <= 10 then success
          else failure("retries must be between 0 and 10")
        )
        .text("number of retry attempts"),
      opt[Port]("port")
        .valueName("<port>")
        .action((value: Port, config: CliConfig) => config.copy(port = Some(value)))
        .validate((value: Port) =>
          if value.value > 0 && value.value <= 65535 then success
          else failure("port must be in the TCP range")
        )
        .text("TCP port read through a custom Read instance"),
      opt[Unit]('v', "verbose")
        .action((_: Unit, config: CliConfig) => config.copy(verbose = true))
        .text("enable verbose logging"),
      opt[Seq[String]]("label")
        .valueName("a,b")
        .action((values: Seq[String], config: CliConfig) => config.copy(labels = config.labels ++ values))
        .text("labels to attach"),
      opt[Map[String, String]]("define")
        .valueName("k=v,k2=v2")
        .action((values: Map[String, String], config: CliConfig) => config.copy(defines = config.defines ++ values))
        .text("key-value definitions"),
      opt[(String, Int)]("limit")
        .keyValueName("<name>", "<max>")
        .action((value: (String, Int), config: CliConfig) => config.copy(limits = config.limits + value))
        .text("named integer limit"),
      opt[Int]("threads")
        .withFallback(() => 2)
        .action((value: Int, config: CliConfig) => config.copy(threads = value))
        .text("worker threads"),
      opt[String]("secret")
        .hidden()
        .action((value: String, config: CliConfig) => config.copy(secret = Some(value)))
        .text("classified test hook"),
      help("help").text("prints this usage text"),
      version("version").text("prints the version"),
      cmd("ingest")
        .action((_: Unit, config: CliConfig) => config.copy(command = Some("ingest")))
        .text("ingest is a nested command")
        .children(
          opt[String]("format")
            .required()
            .action((value: String, config: CliConfig) => config.copy(format = Some(value)))
            .text("input format"),
          arg[String]("<input>...")
            .unbounded()
            .required()
            .action((value: String, config: CliConfig) => config.copy(inputs = config.inputs :+ value))
            .text("input files")
        ),
      checkConfig((config: CliConfig) =>
        if config.labels.nonEmpty || config.verbose then success
        else failure("at least one label or verbose mode is required")
      )
    )
  }

  private def delimiterParser: OParser[?, DelimiterConfig] = {
    val builder: OParserBuilder[DelimiterConfig] = OParser.builder[DelimiterConfig]
    import builder.*

    OParser.sequence(
      programName("literal-tool"),
      opt[Unit]('v', "verbose")
        .action((_: Unit, config: DelimiterConfig) => config.copy(verbose = true))
        .text("enable verbose logging"),
      arg[String]("<value>...")
        .unbounded()
        .action((value: String, config: DelimiterConfig) => config.copy(values = config.values :+ value))
        .text("literal values")
    )
  }

  private def occurrenceBoundParser: OParser[?, OccurrenceConfig] = {
    val builder: OParserBuilder[OccurrenceConfig] = OParser.builder[OccurrenceConfig]
    import builder.*

    OParser.sequence(
      programName("deploy-tool"),
      opt[String]("tag")
        .minOccurs(2)
        .maxOccurs(3)
        .action((value: String, config: OccurrenceConfig) => config.copy(tags = config.tags :+ value))
        .text("release tags to publish")
    )
  }

  private def runParser(args: Seq[String]): (Option[CliConfig], List[OEffect]) =
    OParser.runParser(commandParser, args, CliConfig())

  private def errorMessages(effects: List[OEffect]): List[String] =
    effects.collect { case OEffect.ReportError(message) => message }

  private def displayToOutMessages(effects: List[OEffect]): List[String] =
    effects.collect { case OEffect.DisplayToOut(message) => message }

  private def terminatedSuccessfully(effects: List[OEffect]): Boolean =
    effects.exists {
      case OEffect.Terminate(exitState) => exitState.isRight
      case _ => false
    }

  private final case class Port(value: Int)

  private final case class DelimiterConfig(verbose: Boolean = false, values: Seq[String] = Seq.empty)

  private final case class OccurrenceConfig(tags: Seq[String] = Seq.empty)

  private final case class CliConfig(
      command: Option[String] = None,
      format: Option[String] = None,
      inputs: Seq[String] = Seq.empty,
      labels: Seq[String] = Seq.empty,
      defines: Map[String, String] = Map.empty,
      limits: Map[String, Int] = Map.empty,
      port: Option[Port] = None,
      retries: Int = 0,
      threads: Int = 1,
      verbose: Boolean = false,
      secret: Option[String] = None
  )

  private final class CapturingEffectSetup extends OEffectSetup {
    var out: List[String] = List.empty
    var err: List[String] = List.empty
    var warnings: List[String] = List.empty
    var errors: List[String] = List.empty
    var terminations: List[Either[String, Unit]] = List.empty

    override def displayToOut(message: String): Unit = {
      out = out :+ message
    }

    override def displayToErr(message: String): Unit = {
      err = err :+ message
    }

    override def reportError(message: String): Unit = {
      errors = errors :+ message
    }

    override def reportWarning(message: String): Unit = {
      warnings = warnings :+ message
    }

    override def terminate(exitState: Either[String, Unit]): Unit = {
      terminations = terminations :+ exitState
    }
  }
}
