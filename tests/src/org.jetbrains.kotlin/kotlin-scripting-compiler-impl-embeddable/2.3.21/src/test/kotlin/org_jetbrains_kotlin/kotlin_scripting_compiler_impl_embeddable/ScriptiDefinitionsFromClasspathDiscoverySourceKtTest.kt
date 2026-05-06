/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_scripting_compiler_impl_embeddable

import java.io.File
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.host.GetScriptingClass
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.getScriptingClass
import kotlin.script.templates.ScriptTemplateDefinition
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
    public fun loadsTemplateFromTheSuppliedBaseClassLoader(): Unit {
        try {
            val messages: MutableList<DiagnosticMessage> = mutableListOf()
            val definitions: List<ScriptDefinition> = loadScriptTemplatesFromClasspath(
                scriptTemplates = listOf(DirectLoadedScriptTemplate::class.java.name),
                classpath = emptyList(),
                dependenciesClasspath = emptyList(),
                baseClassLoader = DirectLoadedScriptTemplate::class.java.classLoader,
                hostConfiguration = scriptLoadingHostConfiguration(),
                messageReporter = { severity: ScriptDiagnostic.Severity, message: String ->
                    messages.add(DiagnosticMessage(severity, message))
                },
            ).toList()

            assertThat(definitions).hasSize(1)
            assertThat(definitions.single().name).isEqualTo("direct loaded script")
            assertThat(messages.map { it.severity }).contains(ScriptDiagnostic.Severity.DEBUG)
        } catch (error: Error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error
            }
        }
    }

    @Test
    public fun discoversLegacyTemplateFromDirectoryClasspath(): Unit {
        try {
            val templateClass: Class<*> = LegacyDiscoveredScriptTemplate::class.java
            val classpathRoot: File = classpathRootContaining(templateClass)
            writeDiscoveryMarker(classpathRoot, templateClass.name)

            val messages: MutableList<DiagnosticMessage> = mutableListOf()
            val definitions: List<ScriptDefinition> = discoverScriptTemplatesInClasspath(
                classpath = listOf(classpathRoot),
                baseClassLoader = templateClass.classLoader,
                hostConfiguration = scriptLoadingHostConfiguration(),
                messageReporter = { severity: ScriptDiagnostic.Severity, message: String ->
                    messages.add(DiagnosticMessage(severity, message))
                },
            ).toList()

            assertThat(definitions.map { it.name }).contains("LegacyDiscoveredScriptTemplate")
            assertThat(messages.map { it.severity }).contains(ScriptDiagnostic.Severity.DEBUG)
        } catch (error: Error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error
            }
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
                    "Test templates are supplied as loaded Kotlin classes"
                }
            },
        )
    }

    private fun classpathRootContaining(compiledClass: Class<*>): File {
        val relativePath: String = compiledClass.name.replace('.', '/') + ".class"
        val resource = compiledClass.classLoader.getResource(relativePath)
        if (resource != null && resource.protocol == "file") {
            var root: Path = Path.of(resource.toURI())
            repeat(relativePath.split('/').size) {
                root = root.parent
            }
            return root.toFile()
        }
        return File("build/classes/kotlin/test").absoluteFile
    }

    private fun writeDiscoveryMarker(classpathRoot: File, templateClassName: String): Unit {
        val marker = File(
            classpathRoot,
            SCRIPT_DEFINITION_MARKERS_PATH + templateClassName + SCRIPT_DEFINITION_MARKERS_EXTENSION_WITH_DOT,
        )
        marker.parentFile.mkdirs()
        marker.writeText(templateClassName)
    }

    private data class DiagnosticMessage(
        val severity: ScriptDiagnostic.Severity,
        val message: String,
    )
}

@KotlinScript(displayName = "direct loaded script", fileExtension = "direct.kts")
public abstract class DirectLoadedScriptTemplate

@Suppress("DEPRECATION")
@ScriptTemplateDefinition(scriptFilePattern = ".*\\.legacy\\.kts")
public abstract class LegacyDiscoveredScriptTemplate
