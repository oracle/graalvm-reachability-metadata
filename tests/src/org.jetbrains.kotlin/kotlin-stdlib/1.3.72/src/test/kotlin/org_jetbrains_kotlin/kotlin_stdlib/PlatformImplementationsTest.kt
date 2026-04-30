/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package org_jetbrains_kotlin.kotlin_stdlib

import kotlin.internal.PlatformImplementations
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class PlatformImplementationsTest {
    @Test
    public fun basePlatformImplementationAddsAndReadsSuppressedExceptions() {
        val implementation: PlatformImplementations = PlatformImplementations()
        val cause: IllegalStateException = IllegalStateException("primary failure")
        val suppressed: IllegalArgumentException = IllegalArgumentException("suppressed failure")

        implementation.addSuppressed(cause, suppressed)
        val suppressedFailures: List<Throwable> = implementation.getSuppressed(cause)

        assertThat(suppressedFailures).containsExactly(suppressed)
    }
}
