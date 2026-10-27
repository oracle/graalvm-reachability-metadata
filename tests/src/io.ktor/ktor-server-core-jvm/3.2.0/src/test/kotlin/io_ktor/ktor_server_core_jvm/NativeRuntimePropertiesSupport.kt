/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_core_jvm

internal fun ensureJavaHomeSet(): Unit {
    if (!System.getProperty("java.home").isNullOrBlank()) {
        return
    }

    val javaHome: String = System.getenv("JAVA_HOME").orEmpty()
        .ifBlank { System.getenv("GRAALVM_HOME").orEmpty() }
    if (javaHome.isNotBlank()) {
        System.setProperty("java.home", javaHome)
    }
}

internal fun isNativeImageRuntime(): Boolean =
    System.getProperty("org.graalvm.nativeimage.imagecode") == "runtime"
