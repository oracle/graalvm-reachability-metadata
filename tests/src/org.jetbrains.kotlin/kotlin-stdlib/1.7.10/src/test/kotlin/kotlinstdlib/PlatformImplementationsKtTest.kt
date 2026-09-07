/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package kotlinstdlib

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

public class PlatformImplementationsKtTest {
    @Test
    public fun defaultRandomProducesAValueWithJavaSevenPlatformSelection(): Unit {
        val originalJavaSpecificationVersion: String? = System.getProperty("java.specification.version")
        try {
            System.setProperty("java.specification.version", "1.7")

            val value: Int = Random.Default.nextInt(100, 200)

            assertTrue(value in 100 until 200)
        } finally {
            if (originalJavaSpecificationVersion == null) {
                System.clearProperty("java.specification.version")
            } else {
                System.setProperty("java.specification.version", originalJavaSpecificationVersion)
            }
        }
    }
}
