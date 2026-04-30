/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_stdlib

import java.lang.module.FindException
import java.lang.module.ModuleFinder
import java.nio.file.Files
import java.util.Base64
import java.util.ServiceConfigurationError
import java.util.ServiceLoader
import java.util.function.Supplier
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.createCoroutine
import kotlin.coroutines.jvm.internal.CoroutineStackFrame
import kotlin.coroutines.suspendCoroutine
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class ModuleNameRetrieverTest {
    @Test
    public fun stackTraceElementForSuspendingLambdaUsesModuleNameRetriever() {
        val suspendingBlock: suspend () -> String = {
            suspendForInspection()
        }
        val continuation: Continuation<Unit> = suspendingBlock.createCoroutine(RecordingContinuation())
        val outerFrame: CoroutineStackFrame = continuation as CoroutineStackFrame
        val generatedFrame: CoroutineStackFrame = outerFrame.callerFrame ?: outerFrame

        val stackTraceElement: StackTraceElement? = generatedFrame.getStackTraceElement()
        val stringRepresentation: String = generatedFrame.toString()

        assertThat(stackTraceElement).isNotNull
        assertThat(stackTraceElement!!.fileName).isEqualTo("ModuleNameRetrieverTest.kt")
        assertThat(stringRepresentation).contains("ModuleNameRetrieverTest.kt")
    }

    @Test
    public fun stackTraceElementForNamedModuleContinuationIncludesModuleName() {
        val stackTraceElement: StackTraceElement? = NamedModuleFixture.stackTraceElementOrNull()

        if (stackTraceElement != null) {
            assertThat(stackTraceElement.className)
                .startsWith("org.jetbrains.kotlin.stdlib.modulenamefixture/")
                .endsWith("org_jetbrains_kotlin.kotlin_stdlib.modulefixture.NamedModuleStackTraceSupplier")
            assertThat(stackTraceElement.fileName).isEqualTo("ModuleNameRetrieverTest.kt")
            assertThat(stackTraceElement.methodName).isEqualTo("namedModuleFixture")
        } else {
            val suspendingBlock: suspend () -> String = { suspendForInspection() }
            val continuation: Continuation<Unit> = suspendingBlock.createCoroutine(RecordingContinuation())
            val outerFrame: CoroutineStackFrame = continuation as CoroutineStackFrame
            val generatedFrame: CoroutineStackFrame = outerFrame.callerFrame ?: outerFrame

            assertThat(generatedFrame.getStackTraceElement()).isNotNull
        }
    }
}

private suspend fun suspendForInspection(): String =
    suspendCoroutine { _: Continuation<String> ->
        // Leave the generated continuation suspended so its debug stack frame remains inspectable.
    }

private class RecordingContinuation : Continuation<String> {
    override val context: CoroutineContext = EmptyCoroutineContext

    override fun resumeWith(result: Result<String>) {
        result.getOrThrow()
    }
}

private object NamedModuleFixture {
    private const val SERVICE_NAME = "java.util.function.Supplier"
    private const val MODULE_NAME = "org.jetbrains.kotlin.stdlib.modulenamefixture"
    private const val PROVIDER_CLASS_NAME =
        "org_jetbrains_kotlin.kotlin_stdlib.modulefixture.NamedModuleStackTraceSupplier"
    private const val PROVIDER_PATH =
        "org_jetbrains_kotlin/kotlin_stdlib/modulefixture/NamedModuleStackTraceSupplier"

    private val nestedClassSeparator: Char = 36.toChar()

    fun stackTraceElementOrNull(): StackTraceElement? {
        val fixtureJar = Files.createTempFile("module-name-retriever-fixture", ".jar")
        return try {
            writeFixtureJar(fixtureJar)
            val finder: ModuleFinder = ModuleFinder.of(fixtureJar)
            val parentLayer: ModuleLayer = ModuleLayer.boot()
            val moduleNames: Set<String> = finder.findAll().map { reference -> reference.descriptor().name() }.toSet()
            assertThat(moduleNames).contains(MODULE_NAME)
            val configuration = parentLayer.configuration().resolve(finder, ModuleFinder.of(), moduleNames)
            val controller: ModuleLayer.Controller = ModuleLayer.defineModulesWithOneLoader(
                configuration,
                listOf(parentLayer),
                ClassLoader.getSystemClassLoader()
            )
            val layer: ModuleLayer = controller.layer()
            layer.modules().forEach { module ->
                controller.addReads(module, Unit::class.java.module)
            }

            val provider: Supplier<*> = ServiceLoader.load(layer, Supplier::class.java).firstOrNull() ?: return null
            provider.get() as? StackTraceElement
        } catch (exception: UnsupportedOperationException) {
            null
        } catch (exception: FindException) {
            null
        } catch (exception: LinkageError) {
            null
        } catch (exception: ServiceConfigurationError) {
            null
        } finally {
            Files.deleteIfExists(fixtureJar)
        }
    }

    private fun writeFixtureJar(fixtureJar: java.nio.file.Path) {
        JarOutputStream(Files.newOutputStream(fixtureJar)).use { jar ->
            classEntries().forEach { (entryName, encodedClass) ->
                jar.putNextEntry(JarEntry(entryName))
                jar.write(Base64.getDecoder().decode(encodedClass))
                jar.closeEntry()
            }
            jar.putNextEntry(JarEntry("META-INF/services/$SERVICE_NAME"))
            jar.write(PROVIDER_CLASS_NAME.toByteArray(Charsets.UTF_8))
            jar.closeEntry()
        }
    }

    private fun classEntries(): Map<String, String> = mapOf(
        "module-info.class" to
            """
            yv66vgAAADUAEQcAAgEAC21vZHVsZS1pbmZvAQAKU291cmNlRmlsZQEAEG1vZHVsZS1pbmZvLmphdmEBAAZNb2R1bGUTAAcBAC1vcmcuamV0YnJhaW5zLmtvdGxpbi5zdGRsaWIubW9kdWxlbmFtZWZpeHR1cmUTAAkBAAlqYXZhLmJhc2UBAAE5FAAMAQAwb3JnX2pldGJyYWluc19rb3RsaW4va290bGluX3N0ZGxpYi9tb2R1bGVmaXh0dXJlBwAOAQAbamF2YS91dGlsL2Z1bmN0aW9uL1N1cHBsaWVyBwAQAQBOb3JnX2pldGJyYWluc19rb3RsaW4va290bGluX3N0ZGxpYi9tb2R1bGVmaXh0dXJlL05hbWVkTW9kdWxlU3RhY2tUcmFjZVN1cHBsaWVygAAAAQAAAAAAAAAAAAIAAwAAAAIABAAFAAAAIgAGAAAAAAABAAiAAAAKAAEACwAAAAAAAAAAAAEADQABAA8=
            """.trimIndent(),
        "$PROVIDER_PATH.class" to
            """
            yv66vgAAAEUAIgoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClWBwAIAQBib3JnX2pldGJyYWluc19rb3RsaW4va290bGluX3N0ZGxpYi9tb2R1bGVmaXh0dXJlL05hbWVkTW9kdWxlU3RhY2tUcmFjZVN1cHBsaWVyJEZpeHR1cmVDb250aW51YXRpb24KAAcAAwoABwALDAAMAA0BABRnZXRTdGFja1RyYWNlRWxlbWVudAEAHygpTGphdmEvbGFuZy9TdGFja1RyYWNlRWxlbWVudDsKAA8AEAcAEQwAEgANAQBOb3JnX2pldGJyYWluc19rb3RsaW4va290bGluX3N0ZGxpYi9tb2R1bGVmaXh0dXJlL05hbWVkTW9kdWxlU3RhY2tUcmFjZVN1cHBsaWVyAQADZ2V0BwAUAQAbamF2YS91dGlsL2Z1bmN0aW9uL1N1cHBsaWVyAQAEQ29kZQEAD0xpbmVOdW1iZXJUYWJsZQEAFCgpTGphdmEvbGFuZy9PYmplY3Q7AQAJU2lnbmF0dXJlAQBOTGphdmEvbGFuZy9PYmplY3Q7TGphdmEvdXRpbC9mdW5jdGlvbi9TdXBwbGllcjxMamF2YS9sYW5nL1N0YWNrVHJhY2VFbGVtZW50Oz47AQAKU291cmNlRmlsZQEAIk5hbWVkTW9kdWxlU3RhY2tUcmFjZVN1cHBsaWVyLmphdmEBAAtOZXN0TWVtYmVycwcAHgEAWW9yZ19qZXRicmFpbnNfa290bGluL2tvdGxpbl9zdGRsaWIvbW9kdWxlZml4dHVyZS9OYW1lZE1vZHVsZVN0YWNrVHJhY2VTdXBwbGllciRDb21wbGV0aW9uAQAMSW5uZXJDbGFzc2VzAQATRml4dHVyZUNvbnRpbnVhdGlvbgEACkNvbXBsZXRpb24AMQAPAAIAAQATAAAAAwABAAUABgABABUAAAAdAAEAAQAAAAUqtwABsQAAAAEAFgAAAAYAAQAAAAoAAQASAA0AAQAVAAAAIwACAAEAAAALuwAHWbcACbYACrAAAAABABYAAAAGAAEAAAANEEEAEgAXAAEAFQAAAB0AAQABAAAABSq2AA6wAAAAAQAWAAAABgABAAAACgAEABgAAAACABkAGgAAAAIAGwAcAAAABgACAB0ABwAfAAAAEgACAAcADwAgABkAHQAPACEAGg==
            """.trimIndent(),
        "$PROVIDER_PATH$nestedClassSeparator" + "FixtureContinuation.class" to
            """
            yv66vgAAAEUAKwcAAgEAWW9yZ19qZXRicmFpbnNfa290bGluL2tvdGxpbl9zdGRsaWIvbW9kdWxlZml4dHVyZS9OYW1lZE1vZHVsZVN0YWNrVHJhY2VTdXBwbGllciRDb21wbGV0aW9uCgABAAQMAAUABgEABjxpbml0PgEAAygpVgoACAAJBwAKDAAFAAsBADlrb3RsaW4vY29yb3V0aW5lcy9qdm0vaW50ZXJuYWwvUmVzdHJpY3RlZENvbnRpbnVhdGlvbkltcGwBACMoTGtvdGxpbi9jb3JvdXRpbmVzL0NvbnRpbnVhdGlvbjspVgkADQAOBwAPDAAQABEBAGJvcmdfamV0YnJhaW5zX2tvdGxpbi9rb3RsaW5fc3RkbGliL21vZHVsZWZpeHR1cmUvTmFtZWRNb2R1bGVTdGFja1RyYWNlU3VwcGxpZXIkRml4dHVyZUNvbnRpbnVhdGlvbgEABWxhYmVsAQABSQEABENvZGUBAA9MaW5lTnVtYmVyVGFibGUBAA1pbnZva2VTdXNwZW5kAQAmKExqYXZhL2xhbmcvT2JqZWN0OylMamF2YS9sYW5nL09iamVjdDsBAApTb3VyY2VGaWxlAQAiTmFtZWRNb2R1bGVTdGFja1RyYWNlU3VwcGxpZXIuamF2YQEAGVJ1bnRpbWVWaXNpYmxlQW5ub3RhdGlvbnMBAC5Ma290bGluL2Nvcm91dGluZXMvanZtL2ludGVybmFsL0RlYnVnTWV0YWRhdGE7AQABZgEAGk1vZHVsZU5hbWVSZXRyaWV2ZXJUZXN0Lmt0AQABbAMAAAAlAQABaQEAAXMBAAFuAQABbQEAEm5hbWVkTW9kdWxlRml4dHVyZQEAAWMBAE5vcmdfamV0YnJhaW5zX2tvdGxpbi5rb3RsaW5fc3RkbGliLm1vZHVsZWZpeHR1cmUuTmFtZWRNb2R1bGVTdGFja1RyYWNlU3VwcGxpZXIBAAhOZXN0SG9zdAcAJwEATm9yZ19qZXRicmFpbnNfa290bGluL2tvdGxpbl9zdGRsaWIvbW9kdWxlZml4dHVyZS9OYW1lZE1vZHVsZVN0YWNrVHJhY2VTdXBwbGllcgEADElubmVyQ2xhc3NlcwEACkNvbXBsZXRpb24BABNGaXh0dXJlQ29udGludWF0aW9uADEADQAIAAAAAQABABAAEQAAAAIAAQAFAAYAAQASAAAAMQADAAEAAAARKrsAAVm3AAO3AAcqBLUADLEAAAABABMAAAAOAAMAAAAVAAsAEgAQABYABAAUABUAAQASAAAAGgABAAIAAAACAbAAAAABABMAAAAGAAEAAAAaAAQAFgAAAAIAFwAYAAAALAABABkABwAacwAbABxbAAFJAB0AHlsAAAAfWwAAACBbAAAAIXMAIgAjcwAkACUAAAACACYAKAAAABIAAgABACYAKQAaAA0AJgAqABk=
            """.trimIndent(),
        "$PROVIDER_PATH$nestedClassSeparator" + "Completion.class" to
            """
            yv66vgAAAEUAIAoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClWCQAIAAkHAAoMAAsADAEAJ2tvdGxpbi9jb3JvdXRpbmVzL0VtcHR5Q29yb3V0aW5lQ29udGV4dAEACElOU1RBTkNFAQApTGtvdGxpbi9jb3JvdXRpbmVzL0VtcHR5Q29yb3V0aW5lQ29udGV4dDsHAA4BAFlvcmdfamV0YnJhaW5zX2tvdGxpbi9rb3RsaW5fc3RkbGliL21vZHVsZWZpeHR1cmUvTmFtZWRNb2R1bGVTdGFja1RyYWNlU3VwcGxpZXIkQ29tcGxldGlvbgcAEAEAHmtvdGxpbi9jb3JvdXRpbmVzL0NvbnRpbnVhdGlvbgEABENvZGUBAA9MaW5lTnVtYmVyVGFibGUBAApnZXRDb250ZXh0AQAmKClMa290bGluL2Nvcm91dGluZXMvQ29yb3V0aW5lQ29udGV4dDsBAApyZXN1bWVXaXRoAQAVKExqYXZhL2xhbmcvT2JqZWN0OylWAQAJU2lnbmF0dXJlAQBGTGphdmEvbGFuZy9PYmplY3Q7TGtvdGxpbi9jb3JvdXRpbmVzL0NvbnRpbnVhdGlvbjxMamF2YS9sYW5nL09iamVjdDs+OwEAClNvdXJjZUZpbGUBACJOYW1lZE1vZHVsZVN0YWNrVHJhY2VTdXBwbGllci5qYXZhAQAITmVzdEhvc3QHAB0BAE5vcmdfamV0YnJhaW5zX2tvdGxpbi9rb3RsaW5fc3RkbGliL21vZHVsZWZpeHR1cmUvTmFtZWRNb2R1bGVTdGFja1RyYWNlU3VwcGxpZXIBAAxJbm5lckNsYXNzZXMBAApDb21wbGV0aW9uADAADQACAAEADwAAAAMAAgAFAAYAAQARAAAAHQABAAEAAAAFKrcAAbEAAAABABIAAAAGAAEAAAAeAAEAEwAUAAEAEQAAABwAAQABAAAABLIAB7AAAAABABIAAAAGAAEAAAAhAAEAFQAWAAEAEQAAABkAAAACAAAAAbEAAAABABIAAAAGAAEAAAAmAAQAFwAAAAIAGAAZAAAAAgAaABsAAAACABwAHgAAAAoAAQANABwAHwAa
            """.trimIndent()
    )
}
