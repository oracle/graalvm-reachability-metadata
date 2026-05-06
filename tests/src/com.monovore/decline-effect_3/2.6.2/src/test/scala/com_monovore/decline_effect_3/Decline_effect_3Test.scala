/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_monovore.decline_effect_3

import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicReference

import cats.Show
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.std.Console
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import com.monovore.decline.Command
import com.monovore.decline.Opts
import com.monovore.decline.effect.CommandIOApp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class Decline_effect_3Test {
  @Test
  def runParsesOptionsAndExecutesTheReturnedIOEffect(): Unit = {
    val console: RecordingConsole = new RecordingConsole()
    given Console[IO] = console
    val invocation: AtomicReference[CliInvocation] = new AtomicReference[CliInvocation]()
    val opts: Opts[IO[ExitCode]] = commandOptions(invocation)

    val exitCode: ExitCode = CommandIOApp.run[IO](
      name = "demo",
      header = "CommandIOApp integration test"
    )(opts, List("--name", "Ada", "--count", "3", "--verbose", "alpha", "beta")).unsafeRunSync()

    assertEquals(ExitCode.Success, exitCode)
    assertEquals(CliInvocation("Ada", 3, verbose = true, List("alpha", "beta")), invocation.get())
    assertThat(console.standardOutputText).isEmpty()
    assertThat(console.standardErrorText).isEmpty()
  }

  @Test
  def versionFlagPrintsVersionAndDoesNotEvaluateMainOptions(): Unit = {
    val console: RecordingConsole = new RecordingConsole()
    given Console[IO] = console
    val executed: AtomicReference[String] = new AtomicReference[String]()
    val opts: Opts[IO[ExitCode]] = Opts.apply(IO {
      executed.set("main effect executed")
      ExitCode.Error
    })

    val exitCode: ExitCode = CommandIOApp.run[IO](
      name = "demo",
      header = "Command with version",
      version = Some("test-version")
    )(opts, List("--version")).unsafeRunSync()

    assertEquals(ExitCode.Success, exitCode)
    assertNull(executed.get())
    assertThat(console.standardOutputText).isEqualTo(s"test-version${System.lineSeparator()}")
    assertThat(console.standardErrorText).isEmpty()
  }

  @Test
  def helpFlagPrintsUsageToErrorAndReturnsSuccessWithoutExecutingMainEffect(): Unit = {
    val console: RecordingConsole = new RecordingConsole()
    given Console[IO] = console
    val executed: AtomicReference[String] = new AtomicReference[String]()
    val opts: Opts[IO[ExitCode]] = Opts.option[String]("name", "Name to greet").map { name =>
      IO {
        executed.set(name)
        ExitCode.Success
      }
    }

    val exitCode: ExitCode = CommandIOApp.run[IO](
      name = "demo",
      header = "Helpful command"
    )(opts, List("--help")).unsafeRunSync()

    assertEquals(ExitCode.Success, exitCode)
    assertNull(executed.get())
    assertThat(console.standardOutputText).isEmpty()
    assertThat(console.standardErrorText)
      .contains("Helpful command")
      .contains("Usage:")
      .contains("--name")
      .contains("Name to greet")
  }

  @Test
  def invalidInputPrintsParserErrorsAndReturnsErrorExitCode(): Unit = {
    val console: RecordingConsole = new RecordingConsole()
    given Console[IO] = console
    val executed: AtomicReference[Integer] = new AtomicReference[Integer]()
    val opts: Opts[IO[ExitCode]] = Opts.option[Int]("count", "Number of repetitions").map { count =>
      IO {
        executed.set(Integer.valueOf(count))
        ExitCode.Success
      }
    }

    val exitCode: ExitCode = CommandIOApp.run[IO](
      name = "demo",
      header = "Validating command"
    )(opts, List("--count", "not-a-number")).unsafeRunSync()

    assertEquals(ExitCode.Error, exitCode)
    assertNull(executed.get())
    assertThat(console.standardOutputText).isEmpty()
    assertThat(console.standardErrorText)
      .contains("Invalid integer")
      .contains("not-a-number")
      .contains("Usage:")
  }

  @Test
  def commandOverloadDispatchesSubcommandsToTheirSelectedEffect(): Unit = {
    val console: RecordingConsole = new RecordingConsole()
    given Console[IO] = console
    val selected: AtomicReference[String] = new AtomicReference[String]()
    val greet: Command[IO[ExitCode]] = Command("greet", "Greet a person") {
      Opts.argument[String]("name").map { name =>
        IO {
          selected.set(s"greet:$name")
          ExitCode.Success
        }
      }
    }
    val repeat: Command[IO[ExitCode]] = Command("repeat", "Repeat a word") {
      (Opts.option[Int]("times", "Number of repetitions"), Opts.argument[String]("word")).mapN {
        (times: Int, word: String) =>
          IO {
            selected.set(List.fill(times)(word).mkString(" "))
            ExitCode.Success
          }
      }
    }
    val command: Command[IO[ExitCode]] = Command("tools", "Subcommand dispatcher") {
      Opts.subcommands(greet, repeat)
    }

    val exitCode: ExitCode = CommandIOApp.run[IO](command, List("repeat", "--times", "2", "echo")).unsafeRunSync()

    assertEquals(ExitCode.Success, exitCode)
    assertEquals("echo echo", selected.get())
    assertThat(console.standardOutputText).isEmpty()
    assertThat(console.standardErrorText).isEmpty()
  }

  @Test
  def commandIOAppSubclassRunsItsPublicMainParser(): Unit = {
    val greeted: AtomicReference[String] = new AtomicReference[String]()
    val app: GreetingApp = new GreetingApp(greeted)

    val exitCode: ExitCode = app.run(List("Grace")).unsafeRunSync()

    assertEquals(ExitCode.Success, exitCode)
    assertEquals("Grace", greeted.get())
  }

  private def commandOptions(invocation: AtomicReference[CliInvocation]): Opts[IO[ExitCode]] = {
    val name: Opts[String] = Opts.option[String]("name", "Name to greet").withDefault("world")
    val count: Opts[Int] = Opts.option[Int]("count", "Number of repetitions", short = "c").withDefault(1)
    val verbose: Opts[Boolean] = Opts.flag("verbose", "Enable verbose output").orFalse
    val extraArguments: Opts[List[String]] = Opts.arguments[String]("item").orEmpty[String]

    (name, count, verbose, extraArguments).mapN {
      (parsedName: String, parsedCount: Int, parsedVerbose: Boolean, parsedExtras: List[String]) =>
        IO {
          invocation.set(CliInvocation(parsedName, parsedCount, parsedVerbose, parsedExtras))
          ExitCode.Success
        }
    }
  }
}

private final case class CliInvocation(name: String, count: Int, verbose: Boolean, extraArguments: List[String])

private final class GreetingApp(greeted: AtomicReference[String])
    extends CommandIOApp("greeter", "Greeting application", version = "test-version") {
  override def main: Opts[IO[ExitCode]] = {
    Opts.argument[String]("name").map { name =>
      IO {
        greeted.set(name)
        ExitCode.Success
      }
    }
  }
}

private final class RecordingConsole extends Console[IO] {
  private val standardOutput: StringBuffer = new StringBuffer()
  private val standardError: StringBuffer = new StringBuffer()

  def standardOutputText: String = standardOutput.toString

  def standardErrorText: String = standardError.toString

  override def readLineWithCharset(charset: Charset): IO[String] = {
    IO.raiseError(new UnsupportedOperationException("The tests do not read console input"))
  }

  override def print[A](a: A)(implicit show: Show[A] = Show.fromToString[A]): IO[Unit] = {
    IO {
      standardOutput.append(show.show(a))
      ()
    }
  }

  override def println[A](a: A)(implicit show: Show[A] = Show.fromToString[A]): IO[Unit] = {
    IO {
      standardOutput.append(show.show(a)).append(System.lineSeparator())
      ()
    }
  }

  override def error[A](a: A)(implicit show: Show[A] = Show.fromToString[A]): IO[Unit] = {
    IO {
      standardError.append(show.show(a))
      ()
    }
  }

  override def errorln[A](a: A)(implicit show: Show[A] = Show.fromToString[A]): IO[Unit] = {
    IO {
      standardError.append(show.show(a)).append(System.lineSeparator())
      ()
    }
  }
}
