/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kotest.kotest_assertions_core_jvm

import io.kotest.matchers.resource.shouldMatchResource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

public class StringMatchersKtTest {
    @Test
    fun shouldReportMissingResourcePath(): Unit {
        val resourcePath: String = "/kotest-resource-matchers/missing-resource.txt"

        val exception: IllegalStateException = assertThrows(IllegalStateException::class.java) {
            "resource contents".shouldMatchResource(resourcePath)
        }

        assertEquals("Failed to get resource at $resourcePath", exception.message)
    }
}
