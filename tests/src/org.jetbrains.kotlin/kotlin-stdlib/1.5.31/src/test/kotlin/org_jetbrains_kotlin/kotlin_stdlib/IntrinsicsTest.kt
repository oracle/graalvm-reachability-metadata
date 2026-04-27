/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package org_jetbrains_kotlin.kotlin_stdlib

import kotlin.jvm.internal.Intrinsics
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test

public class IntrinsicsTest {
    @Test
    public fun checkHasClassResolvesInternalClassName() {
        assertThatCode { Intrinsics.checkHasClass("kotlin/Unit") }
            .doesNotThrowAnyException()
    }

    @Test
    public fun checkHasClassWithRequiredVersionResolvesInternalClassName() {
        val requiredVersion = KotlinVersion.CURRENT.toString()

        assertThatCode { Intrinsics.checkHasClass("kotlin/Unit", requiredVersion) }
            .doesNotThrowAnyException()
    }
}
