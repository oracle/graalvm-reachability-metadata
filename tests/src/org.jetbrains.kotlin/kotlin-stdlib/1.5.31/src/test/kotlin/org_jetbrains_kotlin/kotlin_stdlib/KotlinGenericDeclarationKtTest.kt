/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package org_jetbrains_kotlin.kotlin_stdlib

import kotlin.jvm.internal.findMethodBySignature
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class KotlinGenericDeclarationKtTest {
    @Test
    public fun classContainerSearchesDeclaredMethodsBySignature() {
        val declaration = KotlinVersion::class.findMethodBySignature("missingMethod()V")

        assertThat(declaration).isNull()
    }
}
