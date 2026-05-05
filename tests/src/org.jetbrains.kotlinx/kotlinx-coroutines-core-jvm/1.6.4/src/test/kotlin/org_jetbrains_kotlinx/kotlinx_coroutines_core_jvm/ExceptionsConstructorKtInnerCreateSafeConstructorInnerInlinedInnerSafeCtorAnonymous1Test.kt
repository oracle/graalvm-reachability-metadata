/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package org_jetbrains_kotlinx.kotlinx_coroutines_core_jvm

import kotlinx.coroutines.internal.tryCopyException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class ExceptionsConstructorKtInnerCreateSafeConstructorInnerInlinedInnerSafeCtorAnonymous1Test {
    @Test
    public fun tryCopyExceptionInvokesPublicMessageAndCauseConstructor(): Unit {
        val original: ConstructorBackedException = ConstructorBackedException(
            "copy me",
            IllegalStateException("root cause")
        )

        val copy: ConstructorBackedException? = tryCopyException(original)

        assertThat(copy).isNotNull
        assertThat(copy).isNotSameAs(original)
        assertThat(copy).hasMessage(original.message)
        assertThat(copy!!.cause).isSameAs(original)
    }
}
