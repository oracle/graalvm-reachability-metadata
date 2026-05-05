/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlinx.kotlinx_cli_jvm

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.cli.default
import kotlinx.cli.delimiter
import kotlinx.cli.multiple
import kotlinx.cli.optional
import kotlinx.cli.required
import kotlinx.cli.vararg
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

public class KotlinxCliJvmTest {
    @Test
    fun parsesTypedOptionsDefaultsAndPositionalArguments() {
        val parser = ArgParser("archive")
        val outputOption = parser.option(
            ArgType.String,
            fullName = "output",
            shortName = "o",
            description = "Output file",
        ).default("archive.zip")
        val verboseOption = parser.option(
            ArgType.Boolean,
            fullName = "verbose",
            shortName = "v",
            description = "Enable verbose logging",
        ).default(false)
        val levelOption = parser.option(ArgType.Int, fullName = "level").default(3)
        val ratioOption = parser.option(ArgType.Double, fullName = "ratio").required()
        val destinationArgument = parser.argument(ArgType.String, fullName = "destination")
        val inputsArgument = parser.argument(ArgType.String, fullName = "input").vararg()

        val output by outputOption
        var verbose by verboseOption
        val level by levelOption
        val ratio by ratioOption
        val destination by destinationArgument
        val inputs by inputsArgument

        val result = parser.parse(
            arrayOf(
                "--verbose",
                "--level",
                "9",
                "--ratio",
                "2.5",
                "dist",
                "one.txt",
                "two.txt",
            ),
        )

        assertThat(result.commandName).isEqualTo("archive")
        assertThat(output).isEqualTo("archive.zip")
        assertThat(verbose).isTrue()
        assertThat(level).isEqualTo(9)
        assertThat(ratio).isEqualTo(2.5)
        assertThat(destination).isEqualTo("dist")
        assertThat(inputs).containsExactly("one.txt", "two.txt")
        assertThat(outputOption.valueOrigin).isEqualTo(ArgParser.ValueOrigin.SET_DEFAULT_VALUE)
        assertThat(verboseOption.valueOrigin).isEqualTo(ArgParser.ValueOrigin.SET_BY_USER)

        verbose = false
        assertThat(verbose).isFalse()
        assertThat(verboseOption.valueOrigin).isEqualTo(ArgParser.ValueOrigin.REDEFINED)
    }

    @Test
    fun collectsRepeatedDelimitedAndDefaultedMultiValueOptions() {
        val parser = ArgParser("compile")
        val includeOption = parser.option(
            ArgType.String,
            fullName = "include",
            shortName = "I",
        ).multiple().default(listOf("src/main"))
        val moduleOption = parser.option(
            ArgType.String,
            fullName = "module",
            shortName = "m",
        ).delimiter(",").multiple()
        val defineOption = parser.option(ArgType.String, fullName = "define", shortName = "D").multiple()
        val targetArgument = parser.argument(ArgType.String, fullName = "target").optional().default("debug")

        val includes by includeOption
        val modules by moduleOption
        val defines by defineOption
        val target by targetArgument

        parser.parse(
            arrayOf(
                "--module",
                "core,cli",
                "--module",
                "native",
                "-D",
                "trace=true",
                "-D",
                "optimize=false",
            ),
        )

        assertThat(includes).containsExactly("src/main")
        assertThat(includeOption.valueOrigin).isEqualTo(ArgParser.ValueOrigin.SET_DEFAULT_VALUE)
        assertThat(modules).containsExactly("core", "cli", "native")
        assertThat(defines).containsExactly("trace=true", "optimize=false")
        assertThat(target).isEqualTo("debug")
        assertThat(targetArgument.valueOrigin).isEqualTo(ArgParser.ValueOrigin.SET_DEFAULT_VALUE)
    }

    @Test
    fun parsesGnuStyleOptionsCollapsedFlagsAndTerminatedOptionScanning() {
        val parser = ArgParser("scan", prefixStyle = ArgParser.OptionPrefixStyle.GNU)
        val allOption = parser.option(ArgType.Boolean, fullName = "all", shortName = "a").default(false)
        val recursiveOption = parser.option(ArgType.Boolean, fullName = "recursive", shortName = "r").default(false)
        val nameOption = parser.option(ArgType.String, fullName = "name", shortName = "n").required()
        val countOption = parser.option(ArgType.Int, fullName = "count").default(1)
        val trailingArgument = parser.argument(ArgType.String, fullName = "trailing").vararg().optional()

        val all by allOption
        val recursive by recursiveOption
        val name by nameOption
        val count by countOption
        val trailing by trailingArgument

        parser.parse(arrayOf("-ar", "-nreport", "--count=7", "--", "--literal"))

        assertThat(all).isTrue()
        assertThat(recursive).isTrue()
        assertThat(name).isEqualTo("report")
        assertThat(count).isEqualTo(7)
        assertThat(trailing).containsExactly("--literal")
    }

    @Test
    fun skipsUnexpectedTrailingArgumentsWhenConfigured() {
        val parser = ArgParser("single-file", skipExtraArguments = true)
        val fileArgument = parser.argument(ArgType.String, fullName = "file")

        val file by fileArgument

        val result = parser.parse(arrayOf("manifest.txt", "ignored-one.txt", "ignored-two.txt"))

        assertThat(result.commandName).isEqualTo("single-file")
        assertThat(file).isEqualTo("manifest.txt")
        assertThat(fileArgument.valueOrigin).isEqualTo(ArgParser.ValueOrigin.SET_BY_USER)
    }

    @Test
    fun infersNamesForDelegatedOptionsAndArguments() {
        val parser = ArgParser("infer")
        val retryOption = parser.option(ArgType.Int, shortName = "r").default(1)
        val dryRunOption = parser.option(ArgType.Boolean).default(false)
        val sourceArgument = parser.argument(ArgType.String)
        val restArgument = parser.argument(ArgType.String).vararg().optional()

        val retryCount by retryOption
        val dryRun by dryRunOption
        val sourceFile by sourceArgument
        val restFiles by restArgument

        parser.parse(arrayOf("--retryCount", "4", "--dryRun", "main.kt", "support.kt", "readme.md"))

        assertThat(retryCount).isEqualTo(4)
        assertThat(dryRun).isTrue()
        assertThat(sourceFile).isEqualTo("main.kt")
        assertThat(restFiles).containsExactly("support.kt", "readme.md")
    }

    @Test
    fun parsesEnumChoicesWithCustomCommandLineNames() {
        val parser = ArgParser("runner")
        val modeOption = parser.option(
            ArgType.Choice<Mode>(
                toVariant = { value -> Mode.entries.single { it.cliName.equals(value, ignoreCase = true) } },
                toString = { mode -> mode.cliName },
            ),
            fullName = "mode",
        ).required()
        val fallbackOption = parser.option(
            ArgType.Choice<Mode>(
                toVariant = { value -> Mode.entries.single { it.cliName.equals(value, ignoreCase = true) } },
                toString = { mode -> mode.cliName },
            ),
            fullName = "fallback",
        ).default(Mode.SAFE)

        val mode by modeOption
        val fallback by fallbackOption

        parser.parse(arrayOf("--mode", "FAST-PATH"))

        assertThat(mode).isEqualTo(Mode.FAST)
        assertThat(fallback).isEqualTo(Mode.SAFE)
        assertThat(modeOption.valueOrigin).isEqualTo(ArgParser.ValueOrigin.SET_BY_USER)
        assertThat(fallbackOption.valueOrigin).isEqualTo(ArgParser.ValueOrigin.SET_DEFAULT_VALUE)
    }

    @OptIn(ExperimentalCli::class)
    @Test
    fun dispatchesToSubcommandWithItsOwnOptionsAndArguments() {
        val parser = ArgParser("tool")
        val verbose by parser.option(ArgType.Boolean, fullName = "verbose", shortName = "v").default(false)
        val convert = ConvertCommand()
        parser.subcommands(convert)

        val result = parser.parse(arrayOf("--verbose", "convert", "--format", "json", "input.yaml"))

        assertThat(result.commandName).isEqualTo("convert")
        assertThat(verbose).isTrue()
        assertThat(convert.executed).isTrue()
        assertThat(convert.selectedFormat).isEqualTo("json")
        assertThat(convert.sourceFile).isEqualTo("input.yaml")
    }

    @Test
    fun parsesOptionsAndArgumentsWithCustomArgumentType() {
        val parser = ArgParser("publish")
        val moduleOption = parser.option(
            CoordinateArgType,
            fullName = "module",
            shortName = "m",
            description = "Published module",
        ).required()
        val dependencyArgument = parser.argument(
            CoordinateArgType,
            fullName = "dependency",
            description = "Required dependency",
        )

        val module by moduleOption
        val dependency by dependencyArgument

        val result = parser.parse(arrayOf("--module", "org.sample:app", "com.example:library"))

        assertThat(result.commandName).isEqualTo("publish")
        assertThat(module).isEqualTo(Coordinate("org.sample", "app"))
        assertThat(dependency).isEqualTo(Coordinate("com.example", "library"))
        assertThat(moduleOption.valueOrigin).isEqualTo(ArgParser.ValueOrigin.SET_BY_USER)
        assertThat(dependencyArgument.valueOrigin).isEqualTo(ArgParser.ValueOrigin.SET_BY_USER)
    }

    @Test
    fun validatesPublicConfigurationErrorsBeforeParsing() {
        val parser = ArgParser("invalid")

        assertThatThrownBy { parser.argument(ArgType.String, fullName = "file").multiple(1) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("multiple() modifier")

        assertThatThrownBy { parser.option(ArgType.String, fullName = "empty").multiple().default(emptyList()) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Default value for option")

        assertThatThrownBy {
            ArgType.Choice(
                choices = listOf("Debug", "debug"),
                toVariant = { value -> value },
                variantToString = { value -> value.lowercase() },
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("not distinct")
    }

    private enum class Mode(val cliName: String) {
        FAST("fast-path"),
        SAFE("safe"),
    }

    private data class Coordinate(val group: kotlin.String, val name: kotlin.String)

    private object CoordinateArgType : ArgType<Coordinate>(true) {
        override val description: kotlin.String = "group:name"

        override fun convert(value: kotlin.String, name: kotlin.String): Coordinate {
            val parts: List<kotlin.String> = value.split(":", limit = 2)
            require(parts.size == 2 && parts.all { it.isNotBlank() }) {
                "Expected $name to use group:name syntax"
            }
            return Coordinate(parts[0], parts[1])
        }
    }

    @OptIn(ExperimentalCli::class)
    private class ConvertCommand : Subcommand("convert", "Convert one file") {
        private val format by option(ArgType.String, fullName = "format", shortName = "f").default("text")
        private val source by argument(ArgType.String, fullName = "source")

        var executed: Boolean = false
            private set
        var selectedFormat: String? = null
            private set
        var sourceFile: String? = null
            private set

        override fun execute() {
            executed = true
            selectedFormat = format
            sourceFile = source
        }
    }
}
