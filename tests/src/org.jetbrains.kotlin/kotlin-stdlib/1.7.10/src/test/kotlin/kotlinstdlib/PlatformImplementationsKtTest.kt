/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package kotlinstdlib

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertSame

public class PlatformImplementationsKtTest {
    @Test
    public fun defaultRandomFillsAndReturnsTheProvidedArray(): Unit {
        val bytes: ByteArray = ByteArray(32)

        val result: ByteArray = Random.Default.nextBytes(bytes)

        assertSame(bytes, result)
    }
}
