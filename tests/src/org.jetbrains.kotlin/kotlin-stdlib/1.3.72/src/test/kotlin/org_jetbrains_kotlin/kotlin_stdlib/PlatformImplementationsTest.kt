/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_stdlib

import java.io.Closeable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

public class PlatformImplementationsTest {
    @Test
    public fun suppressedCloseFailureIsAttachedAndReadThroughKotlinUtilities() {
        val closeFailure: IllegalArgumentException = IllegalArgumentException("close failure")
        val primaryFailure: IllegalStateException = assertThrows(IllegalStateException::class.java) {
            ThrowingCloseable(closeFailure).use {
                throw IllegalStateException("primary failure")
            }
        }

        val suppressedFailures: List<Throwable> = primaryFailure.suppressedExceptions

        assertThat(primaryFailure).hasMessage("primary failure")
        assertThat(suppressedFailures).containsExactly(closeFailure)
    }
}

private class ThrowingCloseable(
    private val closeFailure: RuntimeException
) : Closeable {
    override fun close() {
        throw closeFailure
    }
}
