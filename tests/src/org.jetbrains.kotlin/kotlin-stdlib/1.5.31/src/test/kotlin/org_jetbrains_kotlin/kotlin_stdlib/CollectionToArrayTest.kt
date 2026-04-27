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
    public fun javaCollectionToArrayCreatesLargerArrayWithRequestedComponentType() {
        val collection = ReadOnlyCollection(listOf("first", "second"))
        val destination = emptyArray<CharSequence>()

        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
        val javaCollection = collection as java.util.Collection<CharSequence>
        val result = javaCollection.toArray(destination)

        assertThat(result).isNotSameAs(destination)
        assertThat(result).containsExactly("first", "second")

        result[1] = StringBuilder("replacement")

        assertThat(result[1].toString()).isEqualTo("replacement")
    }

    private class ReadOnlyCollection<T>(
        private val elements: List<T>
    ) : AbstractCollection<T>() {
        override val size: Int
            get() = elements.size

        override fun iterator(): Iterator<T> = elements.iterator()
    }
}
