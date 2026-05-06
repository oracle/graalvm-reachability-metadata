/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_monovore.decline_3

import cats.data.Validated
import cats.syntax.all.*
import com.monovore.decline.Argument
import com.monovore.decline.Command
import com.monovore.decline.Help
import com.monovore.decline.Opts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import java.net.URI
import java.nio.file.Path
import java.util.UUID
import scala.collection.immutable.Map
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

class Decline_3Test {
  import Decline_3Test.*

  @Test
  def parsesLongAndShortOptionsFlagsDefaultsAndRepeatedValues(): Unit = {
    val greetingCommand: Command[Greeting] = Command("greet", "Create a greeting.") {
      val name: Opts[String] = Opts.option[String]("name", "Name to greet.", short = "n")
      val times: Opts[Int] = Opts.option[Int]("times", "Number of repetitions.").withDefault(1)
      val loud: Opts[Boolean] = Opts.flag("loud", "Uppercase the greeting.").orFalse
      val include: Opts[List[String]] = Opts.options[String]("include", "Additional labels.").orEmpty
      (name, times, loud, include).mapN(Greeting.apply)
    }

    val parsed: Greeting = parseSuccess(
      greetingCommand,
      List("-n", "Ada", "--times", "3", "--loud", "--include", "math", "--include=logic")
    )

    assertEquals(Greeting("Ada", 3, loud = true, List("math", "logic")), parsed)
    assertEquals(Greeting("Grace", 1, loud = false, Nil), parseSuccess(greetingCommand, List("--name", "Grace")))
  }

  @Test
  def parsesPositionalArgumentsAlongsidePathOptions(): Unit = {
    val copyCommand: Command[CopyRequest] = Command("copy", "Copy files.") {
      val verbose: Opts[Boolean] = Opts.flag("verbose", "Enable verbose output.", short = "v").orFalse
      val target: Opts[Path] = Opts.option[Path]("target", "Target directory.")
      val sources: Opts[List[Path]] = Opts.arguments[Path]("source").orEmpty
      (verbose, target, sources).mapN(CopyRequest.apply)
    }

    val parsed: CopyRequest = parseSuccess(
      copyCommand,
      List("--target", "out", "-v", "input-a.txt", "input-b.txt")
    )

    assertTrue(parsed.verbose)
    assertEquals(Path.of("out"), parsed.target)
    assertEquals(List(Path.of("input-a.txt"), Path.of("input-b.txt")), parsed.sources)
  }

  @Test
  def usesEnvironmentVariablesAsFirstClassInputsAndAllowsCommandLineOverride(): Unit = {
    val tokenCommand: Command[AuthConfig] = Command("auth", "Authenticate a request.") {
      val token: Opts[String] =
        Opts.option[String]("token", "API token.").orElse(Opts.env[String]("APP_TOKEN", "API token."))
      val endpoint: Opts[URI] = Opts.env[URI]("APP_ENDPOINT", "Service endpoint.")
      (token, endpoint).mapN(AuthConfig.apply)
    }

    val env: Map[String, String] = Map("APP_TOKEN" -> "from-env", "APP_ENDPOINT" -> "https://example.test/api")

    assertEquals(
      AuthConfig("from-env", URI.create("https://example.test/api")),
      parseSuccess(tokenCommand, Nil, env)
    )
    assertEquals(
      AuthConfig("from-cli", URI.create("https://example.test/api")),
      parseSuccess(tokenCommand, List("--token", "from-cli"), env)
    )
  }

  @Test
  def validatesOptionsAndReportsReadableErrors(): Unit = {
    val serverCommand: Command[ServerConfig] = Command("serve", "Start an HTTP server.") {
      val host: Opts[String] = Opts.option[String]("host", "Host name.").withDefault("127.0.0.1")
      val port: Opts[Int] = Opts
        .option[Int]("port", "Port to bind.")
        .validate("Port must be between 1 and 65535.")(port => port >= 1 && port <= 65535)
      (host, port).mapN(ServerConfig.apply)
    }

    assertEquals(ServerConfig("127.0.0.1", 8080), parseSuccess(serverCommand, List("--port", "8080")))

    val invalidPort: Help = parseFailure(serverCommand, List("--port", "70000"))
    val rendered: String = render(invalidPort)
    assertTrue(rendered.contains("Port must be between 1 and 65535."), rendered)
    assertTrue(rendered.contains("--port"), rendered)
  }

  @Test
  def supportsCustomArgumentsAndEnumeratedArgumentMaps(): Unit = {
    val sizeArgument: Argument[Int] = Argument.from("positive-size") { input =>
      input.toIntOption match {
        case Some(value) if value > 0 => Validated.valid(value)
        case Some(_) => Validated.invalidNel("Size must be positive.")
        case None => Validated.invalidNel(s"$input is not an integer.")
      }
    }
    val colorArgument: Argument[String] = Argument.fromMap("color", Map("red" -> "#ff0000", "blue" -> "#0000ff"))

    val paintCommand: Command[PaintRequest] = Command("paint", "Paint an area.") {
      val color: Opts[String] = Opts.option[String]("color", "Color name.")(using colorArgument)
      val size: Opts[Int] = Opts.option[Int]("size", "Area size.")(using sizeArgument)
      (color, size).mapN(PaintRequest.apply)
    }

    assertEquals(PaintRequest("#ff0000", 12), parseSuccess(paintCommand, List("--color", "red", "--size", "12")))

    val invalidColor: String = render(parseFailure(paintCommand, List("--color", "green", "--size", "12")))
    assertTrue(invalidColor.contains("Unknown value"), invalidColor)
    assertTrue(invalidColor.contains("green"), invalidColor)

    val invalidSize: String = render(parseFailure(paintCommand, List("--color", "blue", "--size", "0")))
    assertTrue(invalidSize.contains("Size must be positive."), invalidSize)
  }

  @Test
  def supportsCountedFlagsAndOptionalFlagArguments(): Unit = {
    val consoleCommand: Command[ConsoleConfig] = Command("console", "Configure console output.") {
      val verbosity: Opts[Int] = Opts.flags("verbose", "Increase verbosity.", short = "v").withDefault(0)
      val color: Opts[String] = Opts
        .flagOption[String]("color", "Configure color output.", metavar = "mode")
        .map(_.fold("auto")(mode => s"mode:$mode"))
        .withDefault("off")
      (verbosity, color).mapN(ConsoleConfig.apply)
    }

    assertEquals(ConsoleConfig(0, "off"), parseSuccess(consoleCommand, Nil))
    assertEquals(ConsoleConfig(2, "auto"), parseSuccess(consoleCommand, List("-v", "--verbose", "--color")))
    assertEquals(ConsoleConfig(1, "mode:always"), parseSuccess(consoleCommand, List("-v", "--color=always")))
  }

  @Test
  def parsesBuiltInArgumentTypesIncludingUuidUriAndFiniteDuration(): Unit = {
    val uuid: UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
    val typedCommand: Command[TypedValues] = Command("typed", "Parse typed values.") {
      val id: Opts[UUID] = Opts.option[UUID]("id", "Identifier.")
      val callback: Opts[URI] = Opts.option[URI]("callback", "Callback URI.")
      val timeout: Opts[FiniteDuration] = Opts.option[FiniteDuration]("timeout", "Request timeout.")
      (id, callback, timeout).mapN(TypedValues.apply)
    }

    val parsed: TypedValues = parseSuccess(
      typedCommand,
      List("--id", uuid.toString, "--callback", "https://example.test/callback", "--timeout", "5 seconds")
    )

    assertEquals(uuid, parsed.id)
    assertEquals(URI.create("https://example.test/callback"), parsed.callback)
    assertEquals(5.seconds, parsed.timeout)
  }

  @Test
  def routesToSubcommandsAndRendersSubcommandHelp(): Unit = {
    val create: Opts[Action] = Opts.subcommand("create", "Create an item.") {
      val name: Opts[String] = Opts.argument[String]("name")
      val force: Opts[Boolean] = Opts.flag("force", "Overwrite existing item.").orFalse
      (name, force).mapN((name, force) => Action.Create(name, force): Action)
    }
    val delete: Opts[Action] = Opts.subcommand("delete", "Delete an item.") {
      Opts.argument[String]("name").map(name => Action.Delete(name): Action)
    }
    val itemCommand: Command[Action] = Command("item", "Manage items.")(create.orElse(delete))

    assertEquals(Action.Create("alpha", force = true), parseSuccess(itemCommand, List("create", "alpha", "--force")))
    assertEquals(Action.Delete("beta"), parseSuccess(itemCommand, List("delete", "beta")))

    val help: String = itemCommand.showHelp
    assertTrue(help.contains("create"), help)
    assertTrue(help.contains("delete"), help)
    assertTrue(help.contains("Manage items."), help)
  }

  @Test
  def honorsDoubleDashDelimiterForDashPrefixedArguments(): Unit = {
    val collectCommand: Command[CollectedArgs] = Command("collect", "Collect command-line arguments.") {
      val verbose: Opts[Boolean] = Opts.flag("verbose", "Enable verbose output.", short = "v").orFalse
      val values: Opts[List[String]] = Opts.arguments[String]("value").orEmpty
      (verbose, values).mapN(CollectedArgs.apply)
    }

    assertEquals(
      CollectedArgs(verbose = true, List("--literal", "-x", "plain")),
      parseSuccess(collectCommand, List("--verbose", "--", "--literal", "-x", "plain"))
    )
    assertEquals(
      CollectedArgs(verbose = false, List("--verbose")),
      parseSuccess(collectCommand, List("--", "--verbose"))
    )
  }

  @Test
  def exposesHelpForExplicitHelpFlagWithoutTreatingItAsSuccess(): Unit = {
    val command: Command[String] = Command("hello", "Say hello.") {
      Opts.option[String]("name", "Name to greet.", short = "n")
    }

    val help: Help = parseFailure(command, List("--help"))
    val rendered: String = render(help)

    assertTrue(rendered.contains("Usage:"), rendered)
    assertTrue(rendered.contains("Say hello."), rendered)
    assertTrue(rendered.contains("--name"), rendered)
    assertFalse(rendered.contains("Missing expected"), rendered)
  }

  @Test
  def reportsMissingOptionsUnknownOptionsAndInvalidValues(): Unit = {
    val command: Command[Int] = Command("number", "Read a number.") {
      Opts.option[Int]("count", "Number of entries.")
    }

    val missing: String = render(parseFailure(command, Nil))
    assertTrue(missing.contains("Missing expected"), missing)
    assertTrue(missing.contains("--count"), missing)

    val unknown: String = render(parseFailure(command, List("--count", "2", "--unknown")))
    assertTrue(unknown.contains("Unexpected"), unknown)
    assertTrue(unknown.contains("--unknown"), unknown)

    val invalid: String = render(parseFailure(command, List("--count", "not-a-number")))
    assertTrue(invalid.contains("not-a-number"), invalid)
  }

  private def parseSuccess[A](
      command: Command[A],
      args: List[String],
      env: Map[String, String] = Map.empty
  ): A = {
    command.parse(args, env) match {
      case Right(value) => value
      case Left(help) => throw new AssertionError(s"Expected parse success, but got:\n${render(help)}")
    }
  }

  private def parseFailure[A](command: Command[A], args: List[String]): Help = {
    command.parse(args) match {
      case Left(help) => help
      case Right(value) => throw new AssertionError(s"Expected parse failure, but got: $value")
    }
  }

  private def render(help: Help): String = help.render(Help.Plain)
}

object Decline_3Test {
  final case class Greeting(name: String, times: Int, loud: Boolean, include: List[String])

  final case class CopyRequest(verbose: Boolean, target: Path, sources: List[Path])

  final case class AuthConfig(token: String, endpoint: URI)

  final case class ServerConfig(host: String, port: Int)

  final case class PaintRequest(color: String, size: Int)

  final case class ConsoleConfig(verbosity: Int, color: String)

  final case class TypedValues(id: UUID, callback: URI, timeout: FiniteDuration)

  final case class CollectedArgs(verbose: Boolean, values: List[String])

  sealed trait Action

  object Action {
    final case class Create(name: String, force: Boolean) extends Action

    final case class Delete(name: String) extends Action
  }
}
