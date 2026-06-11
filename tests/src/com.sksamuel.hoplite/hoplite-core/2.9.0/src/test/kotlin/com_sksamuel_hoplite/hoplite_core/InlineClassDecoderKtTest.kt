/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sksamuel_hoplite.hoplite_core

import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.sources.MapPropertySource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@JvmInline
value class ServerPort(val value: Int)

data class InlineServerConfig(val port: ServerPort)

public class InlineClassDecoderKtTest {
    @Test
    fun decodesInlineClassPropertyFromMapSource(): Unit {
        val loader: ConfigLoader = ConfigLoaderBuilder.empty()
            .addDefaultDecoders()
            .addDefaultParamMappers()
            .addPropertySource(MapPropertySource(mapOf("port" to 8080)))
            .build()

        val config: InlineServerConfig = loader.loadConfigOrThrow(InlineServerConfig::class, emptyList())

        assertThat(config.port).isEqualTo(ServerPort(8080))
    }
}
