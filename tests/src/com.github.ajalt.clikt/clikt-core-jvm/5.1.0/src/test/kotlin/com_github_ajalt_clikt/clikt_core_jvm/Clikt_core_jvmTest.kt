/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ajalt_clikt.clikt_core_jvm

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.CoreCliktCommand
import com.github.ajalt.clikt.core.MutuallyExclusiveGroupException
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.cooccurring
import com.github.ajalt.clikt.parameters.groups.default
import com.github.ajalt.clikt.parameters.groups.mutuallyExclusiveOptions
import com.github.ajalt.clikt.parameters.groups.single
import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.check
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.counted
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.pair
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.unique
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.sources.MapValueSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

public class Clikt_core_jvmTest {
    @Test
    fun `parses options arguments and collection transforms`() {
        val command = ProcessingCommand()

        command.parse(
            listOf(
                "--count",
                "3",
                "--mode",
                "fast",
                "--verbose",
                "-v",
                "--tag",
                "alpha",
                "--tag",
                "beta",
                "--define",
                "threads=4",
                "--define",
                "feature=enabled",
                "--coordinate",
                "10",
                "20",
                "--unique",
                "one",
                "--unique",
                "one",
                "--unique",
                "two",
                "input.txt",
                "output.txt",
            ),
        )

        assertThat(command.result).isEqualTo(
            ProcessingResult(
                count = 3,
                mode = "fast",
                verbosity = 2,
                tags = listOf("alpha", "beta"),
                definitions = mapOf("threads" to "4", "feature" to "enabled"),
                coordinate = 10 to 20,
                uniqueValues = setOf("one", "two"),
                paths = listOf("input.txt", "output.txt"),
            ),
        )
    }

    @Test
    fun `uses defaults environment variables and custom echo output`() {
        val messages = mutableListOf<EchoedMessage>()
        val command = EnvironmentCommand(messages).context {
            readEnvvar = { name -> if (name == "CLIKT_TEST_USER") "from-env" else null }
        }

        command.parse(listOf("--shout"))

        assertThat(command.greeting).isEqualTo("HELLO FROM-ENV")
        assertThat(messages).containsExactly(
            EchoedMessage(message = "HELLO FROM-ENV", trailingNewline = true, error = false),
            EchoedMessage(message = "processed user from-env", trailingNewline = true, error = true),
        )
    }

    @Test
    fun `renders help for options arguments and subcommands`() {
        val root = HelpRootCommand().subcommands(HelpChildCommand())

        val error = assertThrows<PrintHelpMessage> {
            root.parse(listOf("--help"))
        }

        assertThat(root.registeredSubcommandNames()).containsExactly("child")
        assertThat(root.getFormattedHelp(error))
            .contains("root command help")
            .contains("--config")
            .contains("config file")
            .contains("child")

        val child = HelpChildCommand()
        val childError = assertThrows<PrintHelpMessage> {
            child.parse(listOf("--help"))
        }
        assertThat(child.getFormattedHelp(childError)).contains("child command help")
    }

    @Test
    fun `runs subcommands with inherited context objects`() {
        val invocations = mutableListOf<String>()
        val root = SharedContextRootCommand(invocations).subcommands(
            SharedContextChildCommand(),
            SharedContextAuditCommand(),
        )

        root.parse(listOf("child", "--name", "Ada"))
        root.parse(listOf("audit", "release"))

        assertThat(invocations).containsExactly(
            "root:child",
            "child:Ada:root child",
            "root:audit",
            "audit:release:root audit",
        )
    }

    @Test
    fun `reports validation and conversion failures as usage errors`() {
        val negativeCommand = ProcessingCommand()
        val negative = assertThrows<UsageError> {
            negativeCommand.parse(listOf("--count", "0", "file.txt"))
        }
        assertThat(negativeCommand.getFormattedHelp(negative)).contains("count must be positive")

        val invalidChoiceCommand = ProcessingCommand()
        val invalidChoice = assertThrows<UsageError> {
            invalidChoiceCommand.parse(listOf("--mode", "unsafe", "file.txt"))
        }
        assertThat(invalidChoiceCommand.getFormattedHelp(invalidChoice)).contains("unsafe")
    }

    @Test
    fun `enforces option groups and mutually exclusive options`() {
        val command = GroupedOptionsCommand()

        command.parse(listOf("--user", "alice", "--password", "secret", "--json"))

        assertThat(command.summary).isEqualTo("alice:secret:json")

        val incompleteCredentialsCommand = GroupedOptionsCommand()
        val incompleteCredentials = assertThrows<UsageError> {
            incompleteCredentialsCommand.parse(listOf("--user", "alice"))
        }
        assertThat(incompleteCredentialsCommand.getFormattedHelp(incompleteCredentials))
            .contains("--password")

        val conflictingFormat = assertThrows<UsageError> {
            GroupedOptionsCommand().parse(
                listOf("--user", "alice", "--password", "secret", "--json", "--xml"),
            )
        }
        assertThat(conflictingFormat).isInstanceOf(MutuallyExclusiveGroupException::class.java)
    }

    @Test
    fun `uses chained map value sources for option values`() {
        val configuredCommand = ValueSourceCommand()

        configuredCommand.parse(emptyList())

        assertThat(configuredCommand.settings).isEqualTo(
            ValueSourceSettings(
                host = "primary.example.test",
                port = 9000,
                label = "fallback-label",
            ),
        )

        val commandLineCommand = ValueSourceCommand()

        commandLineCommand.parse(listOf("--host", "cli.example.test", "--label", "cli-label"))

        assertThat(commandLineCommand.settings).isEqualTo(
            ValueSourceSettings(
                host = "cli.example.test",
                port = 9000,
                label = "cli-label",
            ),
        )
    }

    @Test
    fun `expands argument files before parsing command line tokens`() {
        val command = ArgumentFileCommand(
            mapOf(
                "defaults.args" to """
                    --profile "qa team"
                    alpha
                    beta
                """.trimIndent(),
            ),
        )

        command.parse(listOf("@defaults.args", "gamma"))

        assertThat(command.request).isEqualTo(
            ArgumentFileRequest(
                profile = "qa team",
                items = listOf("alpha", "beta", "gamma"),
            ),
        )
    }

    private data class ProcessingResult(
        val count: Int,
        val mode: String,
        val verbosity: Int,
        val tags: List<String>,
        val definitions: Map<String, String>,
        val coordinate: Pair<Int, Int>,
        val uniqueValues: Set<String>,
        val paths: List<String>,
    )

    private class ProcessingCommand : CoreCliktCommand("process") {
        private val count: Int by option("--count", help = "positive repetition count")
            .int()
            .default(1)
            .check("count must be positive") { it > 0 }
        private val mode: String by option("--mode").choice("fast", "safe").default("safe")
        private val verbosity: Int by option("-v", "--verbose").counted()
        private val tags: List<String> by option("--tag").multiple()
        private val definitions: Map<String, String> by option("--define").associate()
        private val coordinate: Pair<Int, Int> by option("--coordinate").int().pair().default(0 to 0)
        private val uniqueValues: Set<String> by option("--unique").multiple().unique()
        private val paths: List<String> by argument("path").multiple(required = true)

        var result: ProcessingResult? = null
            private set

        override fun run() {
            result = ProcessingResult(
                count = count,
                mode = mode,
                verbosity = verbosity,
                tags = tags,
                definitions = definitions,
                coordinate = coordinate,
                uniqueValues = uniqueValues,
                paths = paths,
            )
        }
    }

    private data class EchoedMessage(
        val message: String,
        val trailingNewline: Boolean,
        val error: Boolean,
    )

    private class EnvironmentCommand(messages: MutableList<EchoedMessage>) : CoreCliktCommand("env") {
        private val user: String by option("--user", envvar = "CLIKT_TEST_USER").required()
        private val shout: Boolean by option("--shout").flag(default = false)
        private val punctuation: String by argument("punctuation").default("")

        var greeting: String? = null
            private set

        init {
            configureContext {
                echoMessage = { _, message, trailingNewline, error ->
                    messages += EchoedMessage(message.toString(), trailingNewline, error)
                }
            }
        }

        override fun run() {
            val value = "hello $user$punctuation"
            greeting = if (shout) value.uppercase() else value
            echo(greeting)
            echo("processed user $user", err = true)
        }
    }

    private class HelpRootCommand : CoreCliktCommand("root") {
        private val config: String? by option("--config", help = "config file")

        override fun help(context: Context): String = "root command help"

        override fun run() {
            if (config != null) {
                echo(config)
            }
        }
    }

    private class HelpChildCommand : CoreCliktCommand("child") {
        private val target: String by argument("target").default("world")

        override fun help(context: Context): String = "child command help"

        override fun run() {
            echo("child $target")
        }
    }

    private class SharedContextRootCommand(
        private val invocations: MutableList<String>,
    ) : CoreCliktCommand("root") {
        init {
            configureContext {
                data[Context.DEFAULT_OBJ_KEY] = invocations
            }
        }

        override fun run() {
            invocations += "root:${currentContext.invokedSubcommand?.commandName}"
        }
    }

    private class SharedContextChildCommand : CoreCliktCommand("child") {
        private val invocations: MutableList<String> by requireObject()
        private val name: String by option("--name").required()

        override fun run() {
            invocations += "child:$name:${currentContext.commandNameWithParents().joinToString(" ")}"
        }
    }

    private class SharedContextAuditCommand : CoreCliktCommand("audit") {
        private val invocations: MutableList<String> by requireObject()
        private val target: String by argument("target")

        override fun run() {
            invocations += "audit:$target:${currentContext.commandNameWithParents().joinToString(" ")}"
        }
    }

    private class Credentials : OptionGroup("Credentials", "Credentials required together") {
        val username: String by option("--user").required()
        val password: String by option("--password").required()
    }

    private class GroupedOptionsCommand : CoreCliktCommand("grouped") {
        private val credentials: Credentials? by Credentials().cooccurring()
        private val format: String by mutuallyExclusiveOptions(
            option("--json").flag().convert { _: Boolean -> "json" },
            option("--xml").flag().convert { _: Boolean -> "xml" },
        ).single().default("text")

        var summary: String? = null
            private set

        override fun run() {
            val value = requireNotNull(credentials)
            summary = "${value.username}:${value.password}:$format"
        }
    }

    private data class ValueSourceSettings(
        val host: String,
        val port: Int,
        val label: String,
    )

    private class ValueSourceCommand : CoreCliktCommand("service") {
        private val host: String by option("--host", valueSourceKey = "service.host").required()
        private val port: Int by option("--port", valueSourceKey = "service.port").int().required()
        private val label: String by option("--label", valueSourceKey = "service.label").required()

        var settings: ValueSourceSettings? = null
            private set

        init {
            configureContext {
                valueSources(
                    MapValueSource(
                        mapOf(
                            "service.host" to "primary.example.test",
                            "service.port" to "9000",
                        ),
                    ),
                    MapValueSource(mapOf("service.label" to "fallback-label")),
                )
            }
        }

        override fun run() {
            settings = ValueSourceSettings(host = host, port = port, label = label)
        }
    }

    private data class ArgumentFileRequest(
        val profile: String,
        val items: List<String>,
    )

    private class ArgumentFileCommand(
        private val argumentFiles: Map<String, String>,
    ) : CoreCliktCommand("import") {
        private val profile: String by option("--profile").required()
        private val items: List<String> by argument("item").multiple(required = true)

        var request: ArgumentFileRequest? = null
            private set

        init {
            configureContext {
                expandArgumentFiles = true
                readArgumentFile = { path -> argumentFiles.getValue(path) }
            }
        }

        override fun run() {
            request = ArgumentFileRequest(
                profile = profile,
                items = items,
            )
        }
    }
}
