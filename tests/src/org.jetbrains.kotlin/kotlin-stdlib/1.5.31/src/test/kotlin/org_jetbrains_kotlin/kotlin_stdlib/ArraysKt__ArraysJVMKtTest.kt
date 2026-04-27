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
import kotlin.collections.arrayOfNulls as arrayOfNullsWithReference

public class ArraysKt__ArraysJVMKtTest {
    @Test
    public fun arrayOfNullsWithReferenceCreatesWritableArrayOfRequestedSize() {
        val reference = arrayOf(StringBuilder("seed"))

        val result = arrayOfNullsWithReference(reference, 3)

        assertThat(result).hasSize(3)
        assertThat(result).containsOnlyNulls()

        result[1] = StringBuilder("created from referenced component type")

        assertThat(result[1].toString()).isEqualTo("created from referenced component type")
    }
}
