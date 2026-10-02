/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package kotlinstdlib

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

public class CollectionToArrayTest {
    @Test
    public fun toTypedArrayAllocatesAnArrayMatchingTheElementType(): Unit {
        val first: SampleElement = SampleElement("first")
        val second: SampleElement = SampleElement("second")
        val collection: FixedElementsCollection<SampleElement> =
            FixedElementsCollection(listOf(first, second))

        val result: Array<SampleElement> = collection.toTypedArray()

        assertEquals(SampleElement::class.java, result.javaClass.componentType)
        assertContentEquals(arrayOf(first, second), result)
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
