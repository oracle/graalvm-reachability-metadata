/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_kotest.kotest_extensions_jvm

import io.kotest.extensions.system.OverrideMode
import io.kotest.extensions.system.withEnvironment
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SystemEnvironmentExtensionsKtTest {
    @Test
    fun withEnvironmentTemporarilyUpdatesSystemEnvironment(): Unit {
        val key: String = "KOTEST_EXTENSIONS_JVM_TEST_ENVIRONMENT"
        val value: String = "available-during-block"
        val originalValue: String? = System.getenv(key)

        val blockResult: String = withEnvironment(key, value, OverrideMode.SetOrOverride) {
            assertThat(System.getenv(key)).isEqualTo(value)
            "block-completed"
        }

        assertThat(blockResult).isEqualTo("block-completed")
        assertThat(System.getenv(key)).isEqualTo(originalValue)
    }
}
