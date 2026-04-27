/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_stdlib

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class Kotlin_stdlibTest {
    @Test
    public fun standardLibrarySequenceBuildsAndFormatsValuesLazily() {
        var initializerInvocations: Int = 0
        val separator: Lazy<String> = lazy {
            initializerInvocations += 1
            "|"
        }
        val formatted: String = generateSequence(1) { previous: Int -> previous + 1 }
            .map { value: Int -> value * value }
            .take(4)
            .joinToString(separator.value) { value: Int ->
                buildString {
                    append("square=")
                    append(value)
                }
            }

        assertThat(formatted).isEqualTo("square=1|square=4|square=9|square=16")
        assertThat(initializerInvocations).isEqualTo(1)
        assertThat(separator.value).isEqualTo("|")
        assertThat(initializerInvocations).isEqualTo(1)
    }
}
