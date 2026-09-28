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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

public class JvmGetScriptingClassTest {
    @Test
    public fun loadsTypeNameThroughContextClassLoader(): Unit {
        val originalJavaHome: String? = System.getProperty("java.home")
        if (originalJavaHome == null) {
            System.setProperty("java.home", ".")
        }

        try {
            val resolvedClass: KClass<*> = JvmGetScriptingClass().invoke(
                KotlinType("java.lang.String"),
                JvmGetScriptingClassTest::class,
                ScriptingHostConfiguration {},
            )

            assertEquals(String::class, resolvedClass)
        } finally {
            if (originalJavaHome == null) {
                System.clearProperty("java.home")
            } else {
                System.setProperty("java.home", originalJavaHome)
            }
        }
    }
}
