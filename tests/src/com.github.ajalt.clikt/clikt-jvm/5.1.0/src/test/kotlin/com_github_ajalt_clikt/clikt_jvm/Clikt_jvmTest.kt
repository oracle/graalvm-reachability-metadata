/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ajalt_clikt.clikt_jvm

import com.github.ajalt.clikt.command.ChainedCliktCommand
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.test
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.check
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.prompt
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.clikt.testing.test
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

public class Clikt_jvmTest {
    @Test
    fun `test helper captures prompts environment variables stdout and stderr`() {
        val result = OnboardingCommand().test(
            argv = emptyList(),
            stdin = "Ada Lovelace\ns3cr3t\ns3cr3t\ny\n",
            envvars = mapOf("CLIKT_TEST_REGION" to "eu-west"),
            inputInteractive = true,
        )

        assertThat(result.statusCode).isZero()
        assertThat(result.stdout)
            .contains("Display name")
            .contains("Secret token")
            .contains("Repeat for confirmation")
            .contains("Approve")
            .contains("user=Ada Lovelace region=eu-west approved=true token-length=6")
        assertThat(result.stderr).contains("audit=Ada Lovelace")
        assertThat(result.output).contains("audit=Ada Lovelace")
    }

    @Test
    fun `mordant help formatter reports help and usage errors through test terminal`() {
        val help = HelpCommand().test("--help", width = 42)

        assertThat(help.statusCode).isZero()
        assertThat(help.stdout)
            .contains("Usage: render")
            .contains("Render command help")
            .contains("--count")
            .contains("file rendered by the command")
        assertThat(help.stderr).isEmpty()

        val invalidValue = HelpCommand().test("--count 0 document.md")

        assertThat(invalidValue.statusCode).isEqualTo(1)
        assertThat(invalidValue.stderr)
            .contains("count must be positive")
            .contains("--count")
        assertThat(invalidValue.stdout).isEmpty()
    }

    @Test
    fun `clikt command expands argument files using JVM file access`(@TempDir tempDir: Path) {
        val argumentsFile = tempDir.resolve("release.args")
        Files.writeString(
            argumentsFile,
            """
                --title "Release Notes"
                chapter-one
                chapter-two
            """.trimIndent(),
        )

        val result = ImportCommand().test(listOf("@${argumentsFile.toAbsolutePath()}"))

        assertThat(result.statusCode).isZero()
        assertThat(result.stdout).contains("title=Release Notes items=chapter-one,chapter-two")
        assertThat(result.stderr).isEmpty()
    }

    @Test
    fun `jvm path parameter type validates and converts filesystem paths`(@TempDir tempDir: Path) {
        val configFile = tempDir.resolve("application.conf")
        Files.writeString(configFile, "name=native")

        val success = PathConfigCommand().test(listOf("--config", configFile.toString()))

        assertThat(success.statusCode).isZero()
        assertThat(success.stdout).contains("config=application.conf content=name=native")
        assertThat(success.stderr).isEmpty()

        val directory = PathConfigCommand().test(listOf("--config", tempDir.toString()))

        assertThat(directory.statusCode).isEqualTo(1)
        assertThat(directory.stdout).isEmpty()
        assertThat(directory.stderr)
            .contains("--config")
            .contains(tempDir.toString())
    }

    @Test
    fun `chained commands pass returned values through multiple subcommands`() {
        val root = ChainRootCommand().subcommands(
            ChainAddCommand(),
            ChainMultiplyCommand(),
            ChainShowCommand(),
        )

        val result = root.test(listOf("add", "--amount", "5", "multiply", "3", "show"), initial = 2)

        assertThat(result.statusCode).isZero()
        assertThat(result.stdout).contains("value=21")
        assertThat(result.stderr).isEmpty()
    }

    @Test
    fun `suspending command parses arguments and uses mordant test terminal`() {
        runSuspendingTest {
            val command = AsyncCommand()

            val result = command.test(listOf("--factor", "4", "kotlin"))

            assertThat(result.statusCode).isZero()
            assertThat(command.summary).isEqualTo("KOTLINKOTLINKOTLINKOTLIN")
            assertThat(result.stdout).contains("async=KOTLINKOTLINKOTLINKOTLIN")
            assertThat(result.stderr).isEmpty()
        }
    }

    private class OnboardingCommand : CliktCommand("onboard") {
        private val name: String by option("--name").prompt("Display name")
        private val token: String by option("--token").prompt(
            text = "Secret token",
            hideInput = true,
            requireConfirmation = true,
        )
        private val approved: Boolean by option("--approve").flag().prompt("Approve", default = false)
        private val region: String by option("--region", envvar = "CLIKT_TEST_REGION").required()

        override fun run() {
            echo("user=$name region=$region approved=$approved token-length=${token.length}")
            echo("audit=$name", err = true)
        }
    }

    private class HelpCommand : CliktCommand("render") {
        private val count: Int by option("--count", help = "number of times to render the file")
            .int()
            .default(1)
            .check("count must be positive") { value -> value > 0 }
        private val file: String by argument("file", help = "file rendered by the command")

        override fun help(context: Context): String = "Render command help with Mordant formatting."

        override fun run() {
            repeat(count) { echo("rendered=$file") }
        }
    }

    private class ImportCommand : CliktCommand("import") {
        private val title: String by option("--title").required()
        private val items: List<String> by argument("item").multiple(required = true)

        override fun run() {
            echo("title=$title items=${items.joinToString(",")}")
        }
    }

    private class PathConfigCommand : CliktCommand("path-config") {
        private val config: Path by option("--config")
            .path(mustExist = true, canBeDir = false, mustBeReadable = true)
            .required()

        override fun run() {
            echo("config=${config.fileName} content=${Files.readString(config)}")
        }
    }

    private class ChainRootCommand : ChainedCliktCommand<Int>("chain") {
        override val allowMultipleSubcommands: Boolean = true

        override fun run(value: Int): Int = value
    }

    private class ChainAddCommand : ChainedCliktCommand<Int>("add") {
        private val amount: Int by option("--amount").int().required()

        override fun run(value: Int): Int = value + amount
    }

    private class ChainMultiplyCommand : ChainedCliktCommand<Int>("multiply") {
        private val factor: Int by argument("factor").int()

        override fun run(value: Int): Int = value * factor
    }

    private class ChainShowCommand : ChainedCliktCommand<Int>("show") {
        override fun run(value: Int): Int {
            echo("value=$value")
            return value
        }
    }

    private class AsyncCommand : SuspendingCliktCommand("async") {
        private val factor: Int by option("--factor").int().required()
        private val word: String by argument("word")

        var summary: String? = null
            private set

        override suspend fun run() {
            summary = word.uppercase().repeat(factor)
            echo("async=$summary")
        }
    }

    private fun runSuspendingTest(block: suspend () -> Unit) {
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        block.startCoroutine(
            object : Continuation<Unit> {
                override val context = EmptyCoroutineContext

                override fun resumeWith(result: Result<Unit>) {
                    failure.set(result.exceptionOrNull())
                    completed.countDown()
                }
            },
        )

        assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue()
        failure.get()?.let { throw it }
    }
}
