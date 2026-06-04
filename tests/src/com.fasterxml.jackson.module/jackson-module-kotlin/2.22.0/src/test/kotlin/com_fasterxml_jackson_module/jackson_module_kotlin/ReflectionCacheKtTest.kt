/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_kotlin

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class ReflectionCacheKtTest {
    @Test
    fun deserializesPrimaryConstructorWithValueClassParameter() {
        val objectMapper: ObjectMapper = newObjectMapper()

        val result: ReflectionCacheValueClassHolder = objectMapper.readValue(
            """{"wrapped":"alpha"}""",
            ReflectionCacheValueClassHolder::class.java
        )

        assertThat(result.wrappedValue()).isEqualTo("alpha")
    }

    private fun newObjectMapper(): ObjectMapper = jacksonObjectMapper()
}
