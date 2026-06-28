/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kotest.kotest_assertions_core_jvm

import io.kotest.matchers.resource.shouldMatchResource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class StringMatchersKtTest {
    @Test
    fun shouldMatchClasspathResource(): Unit {
        val actual: String = "kotest resource matcher\nloads classpath text"

        val result: String = actual.shouldMatchResource(
            "/io_kotest/kotest_assertions_core_jvm/string-matchers-expected.txt"
        )

        assertThat(result).isEqualTo(actual)
    }
}
