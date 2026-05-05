/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_core_jvm

internal fun ensureJavaHomeProperty(): Unit {
    if (System.getProperty("java.home") != null) {
        return
    }

    val javaHome: String? = firstNonBlank(
        System.getenv("JAVA_HOME"),
        System.getenv("GRAALVM_HOME")
    )
    if (javaHome != null) {
        System.setProperty("java.home", javaHome)
    }
}

private fun firstNonBlank(primaryValue: String?, fallbackValue: String?): String? {
    if (!primaryValue.isNullOrBlank()) {
        return primaryValue
    }
    if (!fallbackValue.isNullOrBlank()) {
        return fallbackValue
    }
    return null
}
