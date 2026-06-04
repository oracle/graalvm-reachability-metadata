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

public class MethodValueCreatorCompanionTest {
    @Test
    fun deserializesUsingJvmStaticCreatorFromPrivateCompanionObject() {
        val objectMapper: ObjectMapper = newObjectMapper()

        val result: PrivateCompanionFactoryValue = objectMapper.readValue(
            """{"value":"alpha"}""",
            PrivateCompanionFactoryValue::class.java
        )

        assertThat(result.value).isEqualTo("alpha")
    }

    private fun newObjectMapper(): ObjectMapper = jacksonObjectMapper()
}
