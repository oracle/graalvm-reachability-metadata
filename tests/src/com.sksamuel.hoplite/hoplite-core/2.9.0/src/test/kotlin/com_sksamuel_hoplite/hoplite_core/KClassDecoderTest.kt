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
import kotlin.reflect.KClass

data class KClassDecoderConfig(val implementation: KClass<*>)

public class KClassDecoderTest {
    @Test
    fun decodesKClassFromFullyQualifiedClassName(): Unit {
        val loader: ConfigLoader = ConfigLoaderBuilder.empty()
            .addDefaultDecoders()
            .addDefaultParamMappers()
            .addPropertySource(MapPropertySource(mapOf("implementation" to "java.lang.String")))
            .build()

        val config: KClassDecoderConfig = loader.loadConfigOrThrow(KClassDecoderConfig::class, emptyList())

        assertThat(config.implementation).isEqualTo(String::class)
    }
}
