/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_stdlib

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class ArraysKt__ArraysJVMKtTest {
    @Test
    public fun reversedArrayCreatesTypedArrayFromReferenceArray() {
        val first: SampleElement = SampleElement("first")
        val second: SampleElement = SampleElement("second")
        val source: Array<SampleElement> = arrayOf(first, second)

        val reversed: Array<SampleElement> = source.reversedArray()

        assertThat(reversed).containsExactly(second, first)
    }

    @Test
    public fun sliceArrayCreatesTypedArrayFromReferenceArray() {
        val first: SampleElement = SampleElement("first")
        val second: SampleElement = SampleElement("second")
        val third: SampleElement = SampleElement("third")
        val source: Array<SampleElement> = arrayOf(first, second, third)

        val sliced: Array<SampleElement> = source.sliceArray(listOf(2, 0))

        assertThat(sliced).containsExactly(third, first)
    }

    private data class SampleElement(val name: String)
}
