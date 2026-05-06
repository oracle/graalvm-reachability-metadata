/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_scripting_jsr223

import java.io.File
import java.util.Properties
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager
import kotlin.script.experimental.jsr223.KOTLIN_JSR223_RESOLVE_FROM_CLASSLOADER_PROPERTY
import kotlin.script.experimental.jsr223.KotlinJsr223DefaultScriptCompilationConfiguration
import kotlin.script.experimental.jsr223.KotlinJsr223DefaultScriptEngineFactory
import kotlin.script.experimental.jsr223.KotlinJsr223DefaultScriptEvaluationConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class KotlinScriptingJsr223Test {
    init {
        restoreMissingRuntimeProperties()
        initializeJvmScriptingHostConfiguration()
    }

    @Test
    public fun exposesFactoryMetadataAndSourceHelpers(): Unit {
        val factory: KotlinJsr223DefaultScriptEngineFactory = KotlinJsr223DefaultScriptEngineFactory()

        assertThat(factory.engineName).containsIgnoringCase("kotlin")
        assertThat(factory.languageName).containsIgnoringCase("kotlin")
        assertThat(factory.engineVersion).isNotBlank()
        assertThat(factory.languageVersion).isNotBlank()
        assertThat(factory.extensions).contains("kts")
        assertThat(factory.mimeTypes).contains("text/x-kotlin")
        assertThat(factory.names).contains("kotlin")
        assertThat(factory.getParameter(ScriptEngine.NAME)).isEqualTo("kotlin")
        assertThat(factory.getParameter("THREADING")).isNull()

        val methodCall: String = factory.getMethodCallSyntax("receiver", "combine", "first", "second")
        assertThat(methodCall).startsWith("receiver.combine(")
        assertThat(methodCall).contains("first", "second")

        val outputStatement: String = factory.getOutputStatement("hello \"kotlin\"")
        assertThat(outputStatement).contains("print")
        assertThat(outputStatement).contains("hello", "kotlin")

        val program: String = factory.getProgram("val value = 41", "value + 1")
        assertThat(program).contains("val value = 41")
        assertThat(program).contains("value + 1")
    }

    @Test
    public fun discoversDefaultEngineThroughJsr223ServiceProvider(): Unit {
        val manager: ScriptEngineManager = ScriptEngineManager(Thread.currentThread().contextClassLoader)

        val byName: ScriptEngine? = manager.getEngineByName("kotlin")
        val byExtension: ScriptEngine? = manager.getEngineByExtension("kts")
        val byMimeType: ScriptEngine? = manager.getEngineByMimeType("text/x-kotlin")

        assertThat(byName).isNotNull()
        assertThat(byExtension).isNotNull()
        assertThat(byMimeType).isNotNull()
        assertThat(byName!!.factory.engineName).containsIgnoringCase("kotlin")
        assertThat(byExtension!!.factory.names).contains("kotlin")
        assertThat(byMimeType!!.factory.extensions).contains("kts")
    }

    @Test
    public fun initializesDefaultScriptConfigurationsAndClassLoaderProperty(): Unit {
        assertThat(KotlinJsr223DefaultScriptCompilationConfiguration).isNotNull()
        assertThat(KotlinJsr223DefaultScriptEvaluationConfiguration).isNotNull()
        assertThat(KOTLIN_JSR223_RESOLVE_FROM_CLASSLOADER_PROPERTY)
            .contains("kotlin")
            .containsIgnoringCase("classloader")
    }

    private fun restoreMissingRuntimeProperties(): Unit {
        val embeddedRuntimeProperties: Properties = loadEmbeddedRuntimeProperties()

        if (isNativeImageRuntime() && System.getProperty(KOTLIN_JSR223_RESOLVE_FROM_CLASSLOADER_PROPERTY) == null) {
            System.setProperty(KOTLIN_JSR223_RESOLVE_FROM_CLASSLOADER_PROPERTY, "true")
        }

        if (System.getProperty("java.home").isNullOrBlank()) {
            val javaHome: String = embeddedRuntimeProperties.getProperty("java.home").orEmpty()
                .ifBlank { System.getenv("JAVA_HOME").orEmpty() }
            if (javaHome.isNotBlank()) {
                System.setProperty("java.home", javaHome)
            }
        }

        val embeddedClassPath: String = embeddedRuntimeProperties.getProperty("java.class.path").orEmpty()
        val currentClassPath: String = System.getProperty("java.class.path").orEmpty()
        val needsClasspathRestore: Boolean = currentClassPath.isBlank() ||
            (isNativeImageRuntime() && !currentClassPath.contains("kotlin-stdlib"))
        if (needsClasspathRestore) {
            val classPath: String = embeddedClassPath.ifBlank {
                javaClass.classLoader
                    .let { it as? java.net.URLClassLoader }
                    ?.urLs
                    ?.joinToString(separator = java.io.File.pathSeparator) { it.file }
                    .orEmpty()
            }
            if (classPath.isNotBlank()) {
                System.setProperty("java.class.path", classPath)
            }
        }

        if (System.getProperty("kotlin.java.stdlib.jar").isNullOrBlank()) {
            val kotlinStdlibJar: String = embeddedRuntimeProperties.getProperty("kotlin.java.stdlib.jar").orEmpty()
                .ifBlank { findClasspathJar("kotlin-stdlib").orEmpty() }
            if (kotlinStdlibJar.isNotBlank()) {
                System.setProperty("kotlin.java.stdlib.jar", kotlinStdlibJar)
            }
        }
    }

    private fun loadEmbeddedRuntimeProperties(): Properties {
        val properties: Properties = Properties()
        javaClass.getResourceAsStream("/native-runtime.properties")?.use { inputStream ->
            properties.load(inputStream)
        }
        return properties
    }

    private fun findClasspathJar(prefix: String): String? {
        val classPathEntries: List<String> = System.getProperty("java.class.path")
            ?.split(File.pathSeparatorChar)
            .orEmpty()

        val fromJavaClassPath: String? = classPathEntries.firstOrNull { entry: String ->
            entry.endsWith(".jar") && File(entry).name.startsWith(prefix)
        }
        if (!fromJavaClassPath.isNullOrBlank()) {
            return fromJavaClassPath
        }

        return javaClass.classLoader
            .let { it as? java.net.URLClassLoader }
            ?.urLs
            ?.mapNotNull { url: java.net.URL ->
                runCatching { File(url.toURI()).path }.getOrNull()
            }
            ?.firstOrNull { entry: String ->
                entry.endsWith(".jar") && File(entry).name.startsWith(prefix)
            }
    }

    private fun isNativeImageRuntime(): Boolean = System.getProperty("org.graalvm.nativeimage.imagecode") != null

    private fun initializeJvmScriptingHostConfiguration(): Unit {
        Class.forName("kotlin.script.experimental.jvm.JvmScriptingHostConfigurationKt", true, javaClass.classLoader)
    }
}
