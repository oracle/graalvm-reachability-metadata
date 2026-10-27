/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package kotlinstdlib

import kotlin.jvm.internal.Intrinsics
import kotlin.test.Test

public class IntrinsicsTest {
    @Test
    public fun checkHasClassAcceptsAPresentRuntimeClass(): Unit {
        Intrinsics.checkHasClass(PRESENT_INTERNAL_NAME)
    }

    @Test
    public fun versionedCheckHasClassAcceptsAPresentRuntimeClass(): Unit {
        Intrinsics.checkHasClass(PRESENT_INTERNAL_NAME, REQUIRED_RUNTIME)
    }

    private companion object {
        private const val PRESENT_INTERNAL_NAME: String = "kotlin/collections/ArrayDeque"
        private const val REQUIRED_RUNTIME: String = "a compatible runtime"
    }
}
