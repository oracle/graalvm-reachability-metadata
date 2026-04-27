/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_stdlib

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class KotlinGenericDeclarationKtTest {
    @Test
    public fun classContainerExposesUnderlyingJavaClass() {
        val declaration = KotlinVersion::class.java

        assertThat(declaration).isEqualTo(KotlinVersion::class.java)
    }
}
