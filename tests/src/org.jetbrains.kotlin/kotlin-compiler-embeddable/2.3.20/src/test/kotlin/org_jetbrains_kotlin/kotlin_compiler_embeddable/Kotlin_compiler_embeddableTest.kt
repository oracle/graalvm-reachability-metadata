/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_compiler_embeddable

import org.assertj.core.api.Assertions.assertThat
import org.graalvm.internal.tck.NativeImageSupport
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.parseCommandLineArguments
import org.jetbrains.kotlin.cli.common.config.KotlinSourceRoot
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path

public class KotlinCompilerEmbeddableTest {
    @Test
    fun parsesJvmCompilerCommandLineArguments() {
        val arguments = K2JVMCompiler().createArguments()

        parseCommandLineArguments(
            listOf(
                "-language-version",
                "2.3",
                "-api-version",
                "2.3",
                "-jvm-target",
                "17",
                "-Xjdk-release=17",
                "-Xjsr305=strict",
                "-Xemit-jvm-type-annotations",
                "-no-reflect",
                "-no-stdlib",
                "-progressive",
                "-module-name",
                "sample-module",
                "-d",
                "build/classes",
                "Sample.kt",
            ),
            arguments,
            false,
        )

        assertThat(arguments.languageVersion).isEqualTo("2.3")
        assertThat(arguments.apiVersion).isEqualTo("2.3")
        assertThat(arguments.jvmTarget).isEqualTo("17")
        assertThat(arguments.jdkRelease).isEqualTo("17")
        assertThat(arguments.jsr305).containsExactly("strict")
        assertThat(arguments.emitJvmTypeAnnotations).isTrue()
        assertThat(arguments.noReflect).isTrue()
        assertThat(arguments.noStdlib).isTrue()
        assertThat(arguments.progressiveMode).isTrue()
        assertThat(arguments.moduleName).isEqualTo("sample-module")
        assertThat(arguments.destination).isEqualTo("build/classes")
        assertThat(arguments.freeArgs).containsExactly("Sample.kt")
    }

    @Test
    fun configuresCompilerConfigurationForKotlinSource(@TempDir temporaryDirectory: Path) {
        val sourceFile = writeSource(
            temporaryDirectory.resolve("ConfigurationSample.kt"),
            """
            package sample.configuration

            class ConfigurationSample {
                fun answer(): Int = 42
            }
            """,
        )
        val messageCollector = RecordingMessageCollector()
        val languageSettings = LanguageVersionSettingsImpl(LanguageVersion.KOTLIN_2_3, ApiVersion.KOTLIN_2_3)
        val sourceRoot = KotlinSourceRoot(sourceFile.toString(), false, null)
        val configuration = CompilerConfiguration().apply {
            put(CommonConfigurationKeys.MODULE_NAME, "configuration-sample")
            put(CommonConfigurationKeys.LANGUAGE_VERSION_SETTINGS, languageSettings)
            put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector)
            put(JVMConfigurationKeys.NO_JDK, true)
            add(CLIConfigurationKeys.CONTENT_ROOTS, sourceRoot)
        }

        assertThat(configuration.get(CommonConfigurationKeys.MODULE_NAME)).isEqualTo("configuration-sample")
        assertThat(configuration.get(CommonConfigurationKeys.LANGUAGE_VERSION_SETTINGS)).isSameAs(languageSettings)
        assertThat(configuration.get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY)).isSameAs(messageCollector)
        assertThat(configuration.getBoolean(JVMConfigurationKeys.NO_JDK)).isTrue()
        assertThat(configuration.getList(CLIConfigurationKeys.CONTENT_ROOTS)).containsExactly(sourceRoot)
        assertThat(sourceRoot.path).isEqualTo(sourceFile.toString())
    }

    @Test
    fun compilesKotlinSourceToJvmClassFiles(@TempDir temporaryDirectory: Path) {
        try {
            val sourceFile = writeSource(
                temporaryDirectory.resolve("Greeter.kt"),
                """
                package sample.compile

                class Greeter {
                    fun answer(): Int = 42
                    fun greeting(name: String): String = "Hello, " + name
                }
                """,
            )
            val outputDirectory = Files.createDirectories(temporaryDirectory.resolve("classes"))
            val result = compile(
                "-language-version",
                "2.3",
                "-api-version",
                "2.3",
                "-jvm-target",
                "17",
                "-no-reflect",
                "-no-stdlib",
                "-classpath",
                System.getProperty("java.class.path"),
                "-module-name",
                "compile-sample",
                "-d",
                outputDirectory.toString(),
                sourceFile.toString(),
            )

            val classFile = outputDirectory.resolve("sample/compile/Greeter.class")
            assertThat(result.output).doesNotContain("error:")
            assertThat(result.exitCode).isEqualTo(ExitCode.OK)
            assertThat(classFile).exists()
            assertThat(Files.readAllBytes(classFile).copyOfRange(0, 4)).containsExactly(
                0xCA.toByte(),
                0xFE.toByte(),
                0xBA.toByte(),
                0xBE.toByte(),
            )
        } catch (error: Error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error)
        }
    }

    @Test
    fun reportsCompilationDiagnosticsForInvalidSource(@TempDir temporaryDirectory: Path) {
        try {
            val sourceFile = writeSource(
                temporaryDirectory.resolve("Broken.kt"),
                """
                package sample.compile

                fun broken( = 1
                """,
            )
            val outputDirectory = Files.createDirectories(temporaryDirectory.resolve("classes"))
            val result = compile(
                "-no-reflect",
                "-no-stdlib",
                "-d",
                outputDirectory.toString(),
                sourceFile.toString(),
            )

            assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
            assertThat(result.output).contains("Broken.kt")
            assertThat(result.output).contains("error:")
        } catch (error: Error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error)
        }
    }

    private fun compile(vararg arguments: String): CompilationResult {
        val output = ByteArrayOutputStream()
        val exitCode = PrintStream(output, true, UTF_8).use { printStream ->
            K2JVMCompiler().exec(printStream, *arguments)
        }
        return CompilationResult(exitCode, output.toString(UTF_8))
    }

    private fun writeSource(path: Path, source: String): Path {
        Files.writeString(path, source.trimIndent(), UTF_8)
        return path
    }

    private fun rethrowIfNotNativeImageDynamicClassLoadingError(error: Error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error
        }
    }

    private data class CompilationResult(
        val exitCode: ExitCode,
        val output: String,
    )

    private class RecordingMessageCollector : MessageCollector {
        private val severities = mutableListOf<CompilerMessageSeverity>()

        override fun clear() {
            severities.clear()
        }

        override fun report(
            severity: CompilerMessageSeverity,
            message: String,
            location: CompilerMessageSourceLocation?,
        ) {
            severities += severity
        }

        override fun hasErrors(): Boolean = severities.any { it.isError }
    }
}
