/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package org_jetbrains_kotlin.kotlin_stdlib

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class PlatformImplementationsTest {
    @Test
    public fun basePlatformImplementationAddsAndReturnsSuppressedExceptions() {
        val platformImplementations = kotlin.internal.PlatformImplementations()
        val primaryFailure = IllegalStateException("primary failure")
        val suppressedFailure = IllegalArgumentException("suppressed failure")

        platformImplementations.addSuppressed(primaryFailure, suppressedFailure)

        assertThat(platformImplementations.getSuppressed(primaryFailure))
            .singleElement()
            .isSameAs(suppressedFailure)
    }
}
