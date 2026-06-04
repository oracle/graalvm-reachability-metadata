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

public class ValueClassStaticJsonKeySerializerTest {
    @Test
    fun serializesValueClassMapKeyUsingStaticJsonKeyMethod() {
        val objectMapper: ObjectMapper = newObjectMapper()

        val json: String = objectMapper.writeValueAsString(mapOf(boxedStaticJsonKeyExample("alpha") to "value"))

        assertThat(json).isEqualTo("""{"json-key:alpha":"value"}""")
    }

    private fun newObjectMapper(): ObjectMapper = jacksonObjectMapper()
}
