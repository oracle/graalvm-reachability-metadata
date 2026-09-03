/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_core_jvm

import io.ktor.server.config.HoconConfigLoader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

private const val HOCON_RESOURCE_PATH: String = "io_ktor/ktor_server_core_jvm/hocon-config-loader.conf"

public class HoconConfigLoaderTest {
    @Test
    fun `load resolves HOCON configuration from a classpath resource`(): Unit {
        val config = HoconConfigLoader().load(HOCON_RESOURCE_PATH)

        assertThat(config).isNotNull
        assertThat(config!!.property("ktor.test.message").getString()).isEqualTo("loaded from a classpath resource")
        assertThat(config.property("ktor.test.values").getList()).containsExactly("alpha", "beta")
    }
}
