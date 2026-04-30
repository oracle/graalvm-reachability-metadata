/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_stdlib

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class CollectionToArrayTest {
    @Test
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    public fun toArrayAllocatesArrayMatchingTheSuppliedComponentType() {
        val first: SampleElement = SampleElement("first")
        val second: SampleElement = SampleElement("second")
        val collection: FixedElementsCollection<SampleElement> =
            FixedElementsCollection(listOf(first, second))
        val undersizedTarget: Array<SampleElement> = emptyArray()

        val result: Array<SampleElement> =
            (collection as java.util.Collection<SampleElement>).toArray(undersizedTarget)

        assertThat(result).isNotSameAs(undersizedTarget)
        assertThat(result).containsExactly(first, second)
    }

    private class FixedElementsCollection<T>(
        private val elements: List<T>
    ) : AbstractCollection<T>() {
        override val size: Int
            get() = elements.size

        override fun iterator(): Iterator<T> = elements.iterator()
    }

    private data class SampleElement(val name: String)
}
