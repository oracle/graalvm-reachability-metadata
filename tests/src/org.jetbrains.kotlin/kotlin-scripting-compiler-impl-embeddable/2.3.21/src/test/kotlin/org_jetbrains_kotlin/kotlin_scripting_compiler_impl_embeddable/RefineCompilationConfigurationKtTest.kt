/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_scripting_compiler_impl_embeddable

import java.io.File
import kotlin.script.experimental.api.ScriptCollectedData
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptSourceAnnotation
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.collectedAnnotations
import kotlin.script.experimental.api.refineConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.graalvm.internal.tck.NativeImageSupport
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.resolve.getScriptCollectedData
import org.junit.jupiter.api.Test

@OptIn(K1Deprecation::class)
public class RefineCompilationConfigurationKtTest {
    @Test
    public fun collectsAcceptedScriptAnnotationUsingContextClassLoader(): Unit {
        try {
            val parentDisposable: Disposable = Disposer.newDisposable("refine-compilation-configuration-test")
            try {
                val compilerConfiguration: CompilerConfiguration = CompilerConfiguration().apply {
                    put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
                    put(CommonConfigurationKeys.MODULE_NAME, "refine-compilation-configuration-test")
                }
                val environment: KotlinCoreEnvironment = KotlinCoreEnvironment.createForProduction(
                    parentDisposable,
                    compilerConfiguration,
                    EnvironmentConfigFiles.JVM_CONFIG_FILES,
                )
                val scriptText: String = """
                    @RefineCompilationConfigurationTestAnnotation(label = "native-image")
                    val answer = 42
                """.trimIndent()
                val scriptSourceFile: File = File(
                    "build/tmp/refine-compilation-configuration/annotated-refine-script.kts",
                ).absoluteFile
                scriptSourceFile.parentFile.mkdirs()
                scriptSourceFile.writeText(scriptText)
                val scriptVirtualFile: VirtualFile = requireNotNull(environment.findLocalFile(scriptSourceFile.path)) {
                    "Expected virtual file for ${scriptSourceFile.path}"
                }
                val scriptFile: KtFile = requireNotNull(
                    PsiManager.getInstance(environment.project).findFile(scriptVirtualFile) as? KtFile,
                ) {
                    "Expected Kotlin PSI file for ${scriptSourceFile.path}"
                }
                val compilationConfiguration: ScriptCompilationConfiguration = ScriptCompilationConfiguration {
                    refineConfiguration {
                        onAnnotations(RefineCompilationConfigurationTestAnnotation::class) { context ->
                            context.compilationConfiguration.asSuccess()
                        }
                    }
                }
                val contextClassLoader: RecordingClassLoader = RecordingClassLoader(
                    RefineCompilationConfigurationTestAnnotation::class.java.classLoader,
                )

                val collectedData: ScriptCollectedData = getScriptCollectedData(
                    scriptFile,
                    compilationConfiguration,
                    environment.project,
                    contextClassLoader,
                )
                val annotations: List<ScriptSourceAnnotation<*>> =
                    collectedData[ScriptCollectedData.collectedAnnotations].orEmpty()

                assertThat(contextClassLoader.loadedNames)
                    .contains(RefineCompilationConfigurationTestAnnotation::class.java.name)
                assertThat(annotations).hasSize(1)
                assertThat(annotations.single().annotation)
                    .isInstanceOf(RefineCompilationConfigurationTestAnnotation::class.java)
                assertThat((annotations.single().annotation as RefineCompilationConfigurationTestAnnotation).label)
                    .isEqualTo("native-image")
            } finally {
                Disposer.dispose(parentDisposable)
            }
        } catch (error: Error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error
            }
        }
    }
}

public annotation class RefineCompilationConfigurationTestAnnotation(val label: String)

private class RecordingClassLoader(parent: ClassLoader) : ClassLoader(parent) {
    val loadedNames: MutableList<String> = mutableListOf()

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        loadedNames.add(name)
        return super.loadClass(name, resolve)
    }
}
