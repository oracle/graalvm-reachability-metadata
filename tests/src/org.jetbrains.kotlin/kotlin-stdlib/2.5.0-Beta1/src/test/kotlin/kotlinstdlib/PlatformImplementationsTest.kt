/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package kotlinstdlib

import kotlin.internal.PlatformImplementations
import kotlin.test.Test
import kotlin.test.assertContentEquals

public class PlatformImplementationsTest {
    @Test
    public fun getSuppressedReturnsExceptionsAttachedToTheCause(): Unit {
        val implementation: PlatformImplementations = PlatformImplementations()
        val cause: IllegalStateException = IllegalStateException("primary")
        val suppressed: IllegalArgumentException = IllegalArgumentException("suppressed")
        cause.addSuppressed(suppressed)

        val result: List<Throwable> = implementation.getSuppressed(cause)

        assertContentEquals(listOf<Throwable>(suppressed), result)
    }
}
