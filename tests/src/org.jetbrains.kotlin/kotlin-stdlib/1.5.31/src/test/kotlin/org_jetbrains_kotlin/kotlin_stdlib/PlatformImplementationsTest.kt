/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_stdlib

import java.io.Closeable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.io.use

public class PlatformImplementationsTest {
    @Test
    public fun suppressedExceptionFromClosingResourceIsAvailableThroughKotlinApi() {
        val closeFailure = IllegalArgumentException("close failure")
        val primaryFailure = assertThrows<IllegalStateException> {
            FailingCloseable(closeFailure).use {
                throw IllegalStateException("primary failure")
            }
        }

        assertThat(primaryFailure.suppressedExceptions)
            .singleElement()
            .isSameAs(closeFailure)
    }

    private class FailingCloseable(private val closeFailure: RuntimeException) : Closeable {
        override fun close() {
            throw closeFailure
        }
    }
}
