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

public class PlatformImplementationsInnerReflectThrowableTest {
    @Test
    public fun addSuppressedAttachesTheExceptionToTheCause(): Unit {
        val implementation: PlatformImplementations = PlatformImplementations()
        val cause: IllegalStateException = IllegalStateException("primary")
        val suppressed: IllegalArgumentException = IllegalArgumentException("suppressed")

        implementation.addSuppressed(cause, suppressed)

        assertContentEquals(arrayOf<Throwable>(suppressed), cause.suppressed)
    }
}
