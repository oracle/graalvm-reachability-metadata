/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package kotlinstdlib

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

public class ArraysKt__ArraysJVMKtTest {
    @Test
    public fun reversedArrayCreatesAnArrayWithTheSourceComponentType(): Unit {
        val first: SampleElement = SampleElement("first")
        val second: SampleElement = SampleElement("second")
        val source: Array<SampleElement> = arrayOf(first, second)

        val reversed: Array<SampleElement> = source.reversedArray()

        assertNotSame(source, reversed)
        assertSame(source.javaClass, reversed.javaClass)
        assertContentEquals(arrayOf(second, first), reversed)
    }

    private data class SampleElement(val name: String)
}
