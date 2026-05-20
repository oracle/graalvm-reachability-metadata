/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_compiler_embeddable

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.config.KotlinSourceRoot
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.modules.ModuleChunk
import org.jetbrains.kotlin.cli.common.modules.ModuleXmlParser
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.modules.JavaRootPath
import org.jetbrains.kotlin.modules.Module
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

public class Kotlin_compiler_embeddableTest {
    @Test
    fun reportsVersionAndAdvancedOptions() {
        val versionResult: CompilerInvocation = invokeCompiler("-version")
        assertThat(versionResult.exitCode)
            .withFailMessage("Compiler output was:\n%s", versionResult.output)
            .isEqualTo(ExitCode.OK)
        assertThat(versionResult.output).contains("kotlinc-jvm")

        val advancedHelpResult: CompilerInvocation = invokeCompiler("-X")
        assertThat(advancedHelpResult.exitCode).isEqualTo(ExitCode.OK)
        assertThat(advancedHelpResult.output)
            .contains("Advanced options")
            .contains("-Xcontext-parameters")
    }

    @Test
    fun parsesJvmCompilerArgumentsIntoStronglyTypedOptions() {
        val arguments: K2JVMCompilerArguments = K2JVMCompiler().createArguments()

        K2JVMCompiler().parseArguments(
            arrayOf(
                "-jvm-target",
                "21",
                "-module-name",
                "sample.module",
                "-no-stdlib",
                "-no-reflect",
                "-Xcontext-parameters",
                "src/main/kotlin/Sample.kt",
            ),
            arguments,
        )

        assertThat(arguments.jvmTarget).isEqualTo("21")
        assertThat(arguments.moduleName).isEqualTo("sample.module")
        assertThat(arguments.noStdlib).isTrue()
        assertThat(arguments.noReflect).isTrue()
        assertThat(arguments.contextParameters).isTrue()
        assertThat(arguments.freeArgs).containsExactly("src/main/kotlin/Sample.kt")
    }

    @Test
    fun storesCompilerConfigurationKeysAndContentRoots(@TempDir tempDir: Path) {
        val sourceFile: Path = tempDir.resolve("Sample.kt")
        Files.writeString(sourceFile, "package sample\nfun answer(): Int = 42\n")
        val sourceRoot = KotlinSourceRoot(sourceFile.toString(), false, null)
        val languageSettings = LanguageVersionSettingsImpl(LanguageVersion.LATEST_STABLE, ApiVersion.LATEST_STABLE)
        val configuration = CompilerConfiguration()

        configuration.put(CommonConfigurationKeys.MODULE_NAME, "sample-module")
        configuration.put(CommonConfigurationKeys.LANGUAGE_VERSION_SETTINGS, languageSettings)
        configuration.put(JVMConfigurationKeys.JVM_TARGET, JvmTarget.JVM_21)
        configuration.add(CLIConfigurationKeys.CONTENT_ROOTS, sourceRoot)

        val copiedConfiguration: CompilerConfiguration = configuration.copy()

        assertThat(copiedConfiguration.getNotNull(CommonConfigurationKeys.MODULE_NAME)).isEqualTo("sample-module")
        assertThat(copiedConfiguration.getNotNull(CommonConfigurationKeys.LANGUAGE_VERSION_SETTINGS))
            .isEqualTo(languageSettings)
        assertThat(copiedConfiguration.getNotNull(JVMConfigurationKeys.JVM_TARGET)).isEqualTo(JvmTarget.JVM_21)
        assertThat(copiedConfiguration.getList(CLIConfigurationKeys.CONTENT_ROOTS)).containsExactly(sourceRoot)
        assertThat(sourceRoot.copy(sourceFile.toString(), true, "commonMain").hmppModuleName).isEqualTo("commonMain")
    }

    @Test
    fun reportsSyntaxDiagnosticsForInvalidKotlinSource(@TempDir tempDir: Path) {
        val sourceFile: Path = tempDir.resolve("Broken.kt")
        val outputDirectory: Path = Files.createDirectories(tempDir.resolve("classes"))
        Files.writeString(
            sourceFile,
            """
            package sample
            fun broken( = 1
            """.trimIndent(),
        )

        val result: CompilerInvocation = invokeCompiler(
            "-no-stdlib",
            "-no-reflect",
            "-no-jdk",
            "-d",
            outputDirectory.toString(),
            sourceFile.toString(),
        )

        assertThat(result.exitCode).isEqualTo(ExitCode.COMPILATION_ERROR)
        assertThat(result.output)
            .contains("Broken.kt")
            .contains("error:")
    }

    @Test
    fun rendersCompilerDiagnosticsWithLocationDetails(@TempDir tempDir: Path) {
        val sourceFile: Path = tempDir.resolve("Diagnostics.kt")
        val lineContent = "fun broken( = 1"
        val location: CompilerMessageSourceLocation = requireNotNull(
            CompilerMessageLocation.create(
                sourceFile.toString(),
                1,
                13,
                lineContent,
            ),
        )

        val fullPathMessage: String = MessageRenderer.PLAIN_FULL_PATHS.render(
            CompilerMessageSeverity.ERROR,
            "expecting a parameter declaration",
            location,
        )
        val withoutPathMessage: String = MessageRenderer.WITHOUT_PATHS.render(
            CompilerMessageSeverity.WARNING,
            "experimental compiler feature is enabled",
            location,
        )

        assertThat(fullPathMessage)
            .contains(sourceFile.toString())
            .contains("1:13")
            .contains("error:")
            .contains("expecting a parameter declaration")
            .contains(lineContent)
            .contains("^")
        assertThat(withoutPathMessage)
            .doesNotContain(sourceFile.toString())
            .contains("warning:")
            .contains("experimental compiler feature is enabled")
    }

    @Test
    fun acceptsNoSourceCompilationThroughMessageCollector(@TempDir tempDir: Path) {
        val outputDirectory: Path = Files.createDirectories(tempDir.resolve("empty-classes"))
        val arguments = K2JVMCompilerArguments().apply {
            allowNoSourceFiles = true
            destination = outputDirectory.toString()
            moduleName = "empty-module"
            noJdk = true
            noReflect = true
            noStdlib = true
        }
        val collector = RecordingMessageCollector()

        val exitCode: ExitCode = K2JVMCompiler().exec(collector, Services.EMPTY, arguments)

        assertThat(exitCode).isEqualTo(ExitCode.OK)
        assertThat(collector.hasErrors()).isFalse()
        assertThat(collector.messages).noneMatch { it.severity.isError }
    }

    @Test
    fun parsesModuleBuildFileWithJvmRoots(@TempDir tempDir: Path) {
        val sourceFile: Path = Files.writeString(
            tempDir.resolve("Sample.kt"),
            "package sample\nfun answer(): Int = 42\n",
        )
        val commonSourceFile: Path = Files.writeString(
            tempDir.resolve("Common.kt"),
            "expect fun platformName(): String\n",
        )
        val javaSourceRoot: Path = Files.createDirectories(tempDir.resolve("java-sources"))
        val classpathRoot: Path = Files.createDirectories(tempDir.resolve("classpath"))
        val friendRoot: Path = Files.createDirectories(tempDir.resolve("friend-classes"))
        val outputDirectory: Path = Files.createDirectories(tempDir.resolve("module-classes"))
        val modularJdkRoot: Path = Files.createDirectories(tempDir.resolve("modular-jdk"))
        val moduleFile: Path = tempDir.resolve("module.xml")
        Files.writeString(
            moduleFile,
            """
            <modules>
                <module name="sample-module" type="java-production" outputDir="${xmlAttribute(outputDirectory)}">
                    <sources path="${xmlAttribute(sourceFile)}" />
                    <commonSources path="${xmlAttribute(commonSourceFile)}" />
                    <classpath path="${xmlAttribute(classpathRoot)}" />
                    <javaSourceRoots path="${xmlAttribute(javaSourceRoot)}" packagePrefix="sample.java" />
                    <friendDir path="${xmlAttribute(friendRoot)}" />
                    <modularJdkRoot path="${xmlAttribute(modularJdkRoot)}" />
                </module>
            </modules>
            """.trimIndent(),
        )
        val collector = RecordingMessageCollector()

        val moduleChunk: ModuleChunk = ModuleXmlParser.parseModuleScript(moduleFile.toString(), collector)

        assertThat(collector.hasErrors()).isFalse()
        assertThat(moduleChunk.modules).hasSize(1)
        val module: Module = moduleChunk.modules.single()
        assertThat(module.getModuleName()).isEqualTo("sample-module")
        assertThat(module.getModuleType()).isEqualTo(ModuleXmlParser.TYPE_PRODUCTION)
        assertThat(module.getOutputDirectory()).isEqualTo(outputDirectory.toString())
        assertThat(module.getSourceFiles()).containsExactly(sourceFile.toString())
        assertThat(module.getCommonSourceFiles()).containsExactly(commonSourceFile.toString())
        assertThat(module.getClasspathRoots()).containsExactly(classpathRoot.toString())
        assertThat(module.getJavaSourceRoots())
            .containsExactly(JavaRootPath(javaSourceRoot.toString(), "sample.java"))
        assertThat(module.getFriendPaths()).containsExactly(friendRoot.toString())
    }

    private fun invokeCompiler(vararg arguments: String): CompilerInvocation {
        val outputStream = ByteArrayOutputStream()
        val exitCode: ExitCode = PrintStream(outputStream, true, StandardCharsets.UTF_8).use { printStream ->
            K2JVMCompiler().exec(printStream, *arguments)
        }
        return CompilerInvocation(exitCode, outputStream.toString(StandardCharsets.UTF_8))
    }

    private fun xmlAttribute(value: Any): String = value.toString()
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private data class CompilerInvocation(
        val exitCode: ExitCode,
        val output: String,
    )

    private class RecordingMessageCollector : MessageCollector {
        val messages: MutableList<CompilerMessage> = mutableListOf()

        override fun clear() {
            messages.clear()
        }

        override fun hasErrors(): Boolean = messages.any { it.severity.isError }

        override fun report(
            severity: CompilerMessageSeverity,
            message: String,
            location: CompilerMessageSourceLocation?,
        ) {
            messages.add(CompilerMessage(severity, message, location))
        }
    }

    private data class CompilerMessage(
        val severity: CompilerMessageSeverity,
        val message: String,
        val location: CompilerMessageSourceLocation?,
    )
}
