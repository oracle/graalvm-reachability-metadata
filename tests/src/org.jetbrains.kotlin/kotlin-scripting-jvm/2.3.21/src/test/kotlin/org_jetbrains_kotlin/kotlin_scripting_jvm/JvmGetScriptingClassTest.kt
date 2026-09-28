/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_scripting_jvm

import kotlin.reflect.KClass
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.JvmGetScriptingClass
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class JvmGetScriptingClassTest {
    @Test
    public fun loadsTypeNameThroughContextClassLoader(): Unit {
        val classLoader: RecordingClassLoader = RecordingClassLoader()
        val scriptingClass: KClass<*> = JvmGetScriptingClass().invoke(
            KotlinType(String::class.java.name),
            classLoader,
            ScriptingHostConfiguration {},
        )

        assertThat(scriptingClass).isEqualTo(String::class)
        assertThat(classLoader.loadedClassNames).contains(String::class.java.name)
    }

    private class RecordingClassLoader : ClassLoader(null) {
        val loadedClassNames: MutableList<String> = mutableListOf()

        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            loadedClassNames.add(name)
            return super.loadClass(name, resolve)
        }
    }
}
