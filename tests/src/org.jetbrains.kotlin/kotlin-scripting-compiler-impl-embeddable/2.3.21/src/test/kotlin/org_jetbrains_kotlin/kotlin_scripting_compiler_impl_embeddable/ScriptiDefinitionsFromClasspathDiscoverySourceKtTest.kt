/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_scripting_compiler_impl_embeddable

import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import kotlin.reflect.KClass
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.displayName
import kotlin.script.experimental.api.fileExtension
import kotlin.script.experimental.host.GetScriptingClass
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.getScriptingClass
import org.assertj.core.api.Assertions.assertThat
import org.graalvm.internal.tck.NativeImageSupport
import org.jetbrains.kotlin.scripting.definitions.SCRIPT_DEFINITION_MARKERS_EXTENSION_WITH_DOT
import org.jetbrains.kotlin.scripting.definitions.SCRIPT_DEFINITION_MARKERS_PATH
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.discoverScriptTemplatesInClasspath
import org.jetbrains.kotlin.scripting.definitions.loadScriptTemplatesFromClasspath
import org.junit.jupiter.api.Test

public class ScriptiDefinitionsFromClasspathDiscoverySourceKtTest {
    @Test
    fun loadsExplicitTemplateWithBaseClassLoader(): Unit {
        val diagnostics: MutableList<String> = mutableListOf()

        val definitions: List<ScriptDefinition> = loadScriptTemplatesFromClasspath(
            scriptTemplates = listOf(ExplicitKotlinScriptTemplate::class.java.name),
            classpath = emptyList(),
            dependenciesClasspath = emptyList(),
            baseClassLoader = javaClass.classLoader,
            hostConfiguration = scriptLoadingHostConfiguration(),
            messageReporter = recordingReporter(diagnostics),
        ).toList()

        assertThat(definitions).hasSize(1)
        val definition: ScriptDefinition = definitions.single()
        assertThat(definition.compilationConfiguration[ScriptCompilationConfiguration.displayName])
            .isEqualTo("Explicit coverage script")
        assertThat(definition.compilationConfiguration[ScriptCompilationConfiguration.fileExtension])
            .isEqualTo("explicit.coverage.kts")
        assertThat(diagnostics.filter { it.startsWith("WARNING:") }).isEmpty()
    }

    @Test
    fun discoversLegacyTemplateFromClasspathDirectory(): Unit {
        val diagnostics: MutableList<String> = mutableListOf()
        val classpathRoot: Path = createClasspathRootForLegacyTemplate()

        try {
            val definitions: List<ScriptDefinition> = discoverScriptTemplatesInClasspath(
                classpath = listOf(classpathRoot.toFile()),
                baseClassLoader = javaClass.classLoader,
                hostConfiguration = scriptLoadingHostConfiguration(),
                messageReporter = recordingReporter(diagnostics),
            ).toList()

            assertThat(definitions).hasSize(1)
            val definition: ScriptDefinition = definitions.single()
            assertThat(definition.compilationConfiguration[ScriptCompilationConfiguration.displayName])
                .isEqualTo("LegacyDiscoveredScriptTemplate")
            assertThat(diagnostics.filter { it.startsWith("WARNING:") }).isEmpty()
        } catch (error: Error) {
            rethrowUnlessUnsupportedDynamicClassLoading(error)
        }
    }

    private fun scriptLoadingHostConfiguration(): ScriptingHostConfiguration = ScriptingHostConfiguration {
        ScriptingHostConfiguration.getScriptingClass.put(
            object : GetScriptingClass {
                override fun invoke(
                    classType: KotlinType,
                    contextClass: KClass<*>,
                    hostConfiguration: ScriptingHostConfiguration,
                ): KClass<*> = requireNotNull(classType.fromClass) {
                    "Test templates are supplied as reflected Kotlin types"
                }
            },
        )
    }

    private fun recordingReporter(
        diagnostics: MutableList<String>,
    ): (ScriptDiagnostic.Severity, String) -> Unit = { severity: ScriptDiagnostic.Severity, message: String ->
        diagnostics.add("$severity:$message")
    }

    private fun createClasspathRootForLegacyTemplate(): Path {
        val root: Path = Files.createTempDirectory("kotlin-script-definitions-")
        val classResourceName: String = GENERATED_LEGACY_TEMPLATE_CLASS_NAME.replace('.', '/') + ".class"
        val classFile: Path = root.resolve(classResourceName)
        Files.createDirectories(classFile.parent)
        Files.write(classFile, Base64.getDecoder().decode(GENERATED_LEGACY_TEMPLATE_CLASS_BYTES))

        val markersDirectory: Path = root.resolve(SCRIPT_DEFINITION_MARKERS_PATH)
        Files.createDirectories(markersDirectory)
        Files.writeString(
            markersDirectory.resolve(
                GENERATED_LEGACY_TEMPLATE_CLASS_NAME + SCRIPT_DEFINITION_MARKERS_EXTENSION_WITH_DOT,
            ),
            GENERATED_LEGACY_TEMPLATE_CLASS_NAME,
        )
        return root
    }

    private fun rethrowUnlessUnsupportedDynamicClassLoading(error: Error): Unit {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error
        }
    }

    private companion object {
        const val GENERATED_LEGACY_TEMPLATE_CLASS_NAME: String = "coverage.LegacyDiscoveredScriptTemplate"
        const val GENERATED_LEGACY_TEMPLATE_CLASS_BYTES: String =
            "yv66vgAAADQADAoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClWBwAIAQAn" +
                "Y292ZXJhZ2UvTGVnYWN5RGlzY292ZXJlZFNjcmlwdFRlbXBsYXRlAQAEQ29kZQEAGVJ1bnRpbWVWaXNp" +
                "YmxlQW5ub3RhdGlvbnMBADJMa290bGluL3NjcmlwdC90ZW1wbGF0ZXMvU2NyaXB0VGVtcGxhdGVE" +
                "ZWZpbml0aW9uOwQhAAcAAgAAAAAAAQABAAUABgABAAkAAAARAAEAAQAAAAUqtwABsQAAAAAAAQAK" +
                "AAAABgABAAsAAA=="
    }
}

@KotlinScript(
    displayName = "Explicit coverage script",
    fileExtension = "explicit.coverage.kts",
)
public abstract class ExplicitKotlinScriptTemplate
